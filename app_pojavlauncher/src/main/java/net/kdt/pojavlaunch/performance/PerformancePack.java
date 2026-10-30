package net.kdt.pojavlaunch.performance;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.InstalledContentStore;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-profile Performance pack: when a Fabric profile has one of its Performance switches on, the matching
 * optimisation mods are downloaded from Modrinth into that profile's mods folder right before the game starts;
 * when a switch is turned off, only the mods this class added are removed again. The user's own mods are never touched.
 * Everything is recorded in the profile's installed-content inventory (see InstalledContentStore.Entry.origin).
 */
public final class PerformancePack {
    private static final String TAG = "PerformancePack";
    public static final String ORIGIN_MOD = "performance:";
    public static final String ORIGIN_DEP = "performance-dep";
    private static final Pattern FABRIC_ID = Pattern.compile("^fabric-loader-[0-9][0-9.]*-(.+)$");

    public static final class Mod {
        public final String slug;   // Modrinth slug
        public final String name;
        final String match;         // fragment of the usual jar file name, to recognise a copy the user already has
        Mod(String slug, String name, String match) { this.slug = slug; this.name = name; this.match = match; }
    }

    public static final Mod[] MAIN = {
            new Mod("lithium", "Lithium", "lithium"),
            new Mod("ferrite-core", "FerriteCore", "ferritecore"),
            new Mod("modernfix", "ModernFix", "modernfix"),
            new Mod("entityculling", "Entity Culling", "entityculling"),
            new Mod("moreculling", "MoreCulling", "moreculling"),
            new Mod("scalablelux", "ScalableLux", "scalablelux"),
            new Mod("zfastnoise", "Fast Noise", "fastnoise"),
            new Mod("language-reload", "Language Reload", "languagereload"),
            new Mod("alternate-current", "Alternate Current", "alternatecurrent"),
    };
    public static final Mod[] EXPERIMENTAL = {
            new Mod("sodium", "Sodium", "sodiumfabric"),
            new Mod("immediatelyfast", "ImmediatelyFast", "immediatelyfast"),
    };
    public static final Mod[] ADVANCED = {
            new Mod("c2me-fabric", "C2ME", "c2mefabric"),
            new Mod("dynamic-fps", "Dynamic FPS", "dynamicfps"),
    };

    private PerformancePack() {}

    /** Only Fabric profiles are supported for now */
    public static boolean isSupported(@Nullable MinecraftProfile profile) {
        return profile != null && profile.lastVersionId != null && FABRIC_ID.matcher(profile.lastVersionId).matches();
    }

    @Nullable
    public static String minecraftVersion(@Nullable MinecraftProfile profile) {
        if (profile == null || profile.lastVersionId == null) return null;
        Matcher m = FABRIC_ID.matcher(profile.lastVersionId);
        return m.matches() ? m.group(1) : null;
    }

    private static List<Mod> wantedMods(MinecraftProfile profile) {
        List<Mod> wanted = new ArrayList<>();
        if (!isSupported(profile)) return wanted;
        if (profile.perfMods) for (Mod m : MAIN) wanted.add(m);
        if (profile.perfExperimental) for (Mod m : EXPERIMENTAL) wanted.add(m);
        if (profile.perfAdvanced) for (Mod m : ADVANCED) wanted.add(m);
        return wanted;
    }

    /** True if this profile needs the pack applied (a switch is on) or cleaned up (mods from an earlier run remain) */
    public static boolean hasWork(@Nullable MinecraftProfile profile) {
        if (profile == null) return false;
        if (!wantedMods(profile).isEmpty()) return true;
        for (InstalledContentStore.Entry e : InstalledContentStore.read(Tools.getGameDirPath(profile))) {
            if (e.origin != null && e.origin.startsWith(ORIGIN_MOD)) return true;
        }
        return false;
    }

    private static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private static final class Report {
        int total, added, already, usingCopy, unavailable, failed, missingOffline;
        int have() { return added + already + usingCopy; }
    }

    private static InstalledContentStore.Entry findPackEntry(List<InstalledContentStore.Entry> entries, String slug) {
        for (InstalledContentStore.Entry e : entries)
            if ((ORIGIN_MOD + slug).equals(e.origin)) return e;
        return null;
    }

    /** Does the mods folder already hold a jar of this mod that we did not put there? */
    private static boolean hasLocalCopy(File modsDir, List<InstalledContentStore.Entry> entries, Mod mod) {
        File[] files = modsDir.listFiles();
        if (files == null) return false;
        Set<String> ours = new HashSet<>();
        for (InstalledContentStore.Entry e : entries)
            if (e.origin != null && e.origin.startsWith("performance")) ours.add(e.fileName);
        for (File f : files) {
            String name = f.getName();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".jar") || ours.contains(name)) continue;
            if (name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains(mod.match)) return true;
        }
        return false;
    }

    private static Report sync(Context context, MinecraftProfile profile) {
        Report report = new Report();
        File gameDir = Tools.getGameDirPath(profile);
        List<Mod> wanted = wantedMods(profile);
        Set<String> wantedSlugs = new HashSet<>();
        for (Mod m : wanted) wantedSlugs.add(m.slug);

        // 1. Remove what this pack added earlier and is no longer wanted (the user's own mods are not recorded as ours)
        for (InstalledContentStore.Entry e : InstalledContentStore.read(gameDir)) {
            if (e.origin == null || !e.origin.startsWith(ORIGIN_MOD)) continue;
            if (wantedSlugs.contains(e.origin.substring(ORIGIN_MOD.length()))) continue;
            try { InstalledContentStore.remove(gameDir, e); }
            catch (IOException ex) { Log.w(TAG, "Could not remove " + e.fileName, ex); }
        }
        report.total = wanted.size();
        if (wanted.isEmpty()) return report;

        // 2. Add what is wanted and missing
        String gameVersion = minecraftVersion(profile);
        boolean online = isOnline(context);
        File modsDir = new File(gameDir, "mods");
        ModrinthApi api = new ModrinthApi();
        try {
            for (int i = 0; i < wanted.size(); i++) {
                Mod mod = wanted.get(i);
                ProgressKeeper.submitProgress(ProgressLayout.PERFORMANCE_MODS, i * 100 / wanted.size(),
                        R.string.perf_progress, i + 1, wanted.size());
                List<InstalledContentStore.Entry> entries = InstalledContentStore.read(gameDir);
                InstalledContentStore.Entry existing = findPackEntry(entries, mod.slug);
                try {
                    if (existing != null && InstalledContentStore.resolveEntryFile(gameDir, existing).exists()) { report.already++; continue; }
                } catch (IOException ignored) {}
                if (hasLocalCopy(modsDir, entries, mod)) { report.usingCopy++; continue; }
                if (!online) { report.missingOffline++; continue; }

                Set<String> before = new HashSet<>();
                for (InstalledContentStore.Entry e : entries) before.add(e.projectId);
                String[] projectId = new String[1];
                try {
                    int result = api.installLatestRelease(gameDir, mod.slug, gameVersion, "fabric", projectId);
                    if (result != ModrinthApi.PERF_INSTALLED) { report.unavailable++; continue; }
                } catch (IOException ex) {
                    Log.w(TAG, "Could not add " + mod.name + ": " + ex.getMessage());
                    report.failed++;
                    continue;
                }
                // Remember that we added it (and anything it pulled in), so switching off removes exactly that
                List<InstalledContentStore.Entry> after = InstalledContentStore.read(gameDir);
                boolean changed = false;
                for (InstalledContentStore.Entry e : after) {
                    if (before.contains(e.projectId) && !e.projectId.equals(projectId[0])) continue;
                    if (e.projectId.equals(projectId[0])) { e.origin = ORIGIN_MOD + mod.slug; changed = true; }
                    else if (e.origin == null || e.origin.isEmpty()) { e.origin = ORIGIN_DEP; changed = true; }
                }
                if (changed) {
                    try { InstalledContentStore.write(gameDir, after); }
                    catch (IOException ex) { Log.w(TAG, "Could not record the added mods", ex); }
                }
                report.added++;
            }
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.PERFORMANCE_MODS);
        }
        return report;
    }

    /**
     * Applies the profile's Performance switches in the background, then runs {@code launch} on the UI thread.
     * Without internet and with mods missing, the player is asked whether to start anyway.
     */
    public static void syncThenRun(final Activity activity, final MinecraftProfile profile, final Runnable launch) {
        PojavApplication.sExecutorService.execute(() -> {
            Report r;
            try {
                r = sync(activity, profile);
            } catch (Throwable t) {
                Log.e(TAG, "Performance pack failed", t);
                r = new Report();
                r.failed = 1;
            }
            final Report report = r;
            final String mc = String.valueOf(minecraftVersion(profile));
            Tools.runOnUiThread(() -> {
                if (report.missingOffline > 0) {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.perf_offline_title)
                            .setMessage(activity.getString(R.string.perf_offline_message, report.have(), report.total))
                            .setPositiveButton(R.string.perf_play_anyway, (d, w) -> launch.run())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return;
                }
                List<String> parts = new ArrayList<>();
                if (report.added > 0) parts.add(activity.getString(R.string.perf_summary_added, report.added));
                if (report.unavailable > 0) parts.add(activity.getString(R.string.perf_summary_unavailable, report.unavailable, mc));
                if (report.failed > 0) parts.add(activity.getString(R.string.perf_summary_failed, report.failed));
                if (!parts.isEmpty()) {
                    StringBuilder text = new StringBuilder(activity.getString(R.string.perf_summary_prefix));
                    for (int i = 0; i < parts.size(); i++) text.append(i > 0 ? ", " : "").append(parts.get(i));
                    Toast.makeText(activity, text.toString(), Toast.LENGTH_LONG).show();
                }
                launch.run();
            });
        });
    }
}
