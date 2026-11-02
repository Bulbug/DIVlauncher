package net.kdt.pojavlaunch.prefs;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/**
 * Remembers which game profile (Survival, Vanilla, ...) each launcher account last used, so that
 * switching accounts also switches the profile. Stored in the launcher preferences, one entry per account.
 */
public final class AccountProfiles {
    private static final String PREFIX = "accountProfile:";

    private AccountProfiles() {}

    private static String key(String account) { return PREFIX + account; }

    /** The profile key remembered for this account, or null if none */
    @Nullable
    public static String get(@Nullable String account) {
        if(!Tools.isValidString(account) || LauncherPreferences.DEFAULT_PREF == null) return null;
        return LauncherPreferences.DEFAULT_PREF.getString(key(account), null);
    }

    public static void remember(@Nullable String account, @Nullable String profileKey) {
        if(!Tools.isValidString(account) || !Tools.isValidString(profileKey) || LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit().putString(key(account), profileKey).apply();
    }

    public static void forget(@Nullable String account) {
        if(!Tools.isValidString(account) || LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit().remove(key(account)).apply();
    }

    /** Keep the memory when an offline account is renamed */
    public static void rename(String oldName, String newName) {
        String profile = get(oldName);
        forget(oldName);
        remember(newName, profile);
    }

    /** True if the profile still exists in launcher_profiles.json */
    public static boolean exists(@Nullable String profileKey) {
        if(!Tools.isValidString(profileKey)) return false;
        if(LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson != null
                && LauncherProfiles.mainProfileJson.profiles != null
                && LauncherProfiles.mainProfileJson.profiles.containsKey(profileKey);
    }

    /** Name to show for the remembered profile of an account, or null if none/deleted */
    @Nullable
    public static String displayName(@Nullable String account) {
        String key = get(account);
        if(!exists(key)) return null;
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        return profile != null && Tools.isValidString(profile.name) ? profile.name : key;
    }
}
