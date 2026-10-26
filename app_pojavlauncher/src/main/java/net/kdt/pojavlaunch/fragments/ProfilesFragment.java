package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Card grid of all profiles: tap to select, pencil to edit, button to create a new one */
public class ProfilesFragment extends Fragment {
    public static final String TAG = "ProfilesFragment";
    private ProfilesAdapter mAdapter;
    // Refresh the "Downloaded" labels when a download finishes
    private final TaskCountListener mTaskCountListener = taskCount -> {
        if(taskCount == 0) Tools.runOnUiThread(() -> {
            if(isAdded() && mAdapter != null) mAdapter.reload();
        });
    };

    public ProfilesFragment() {
        super(R.layout.fragment_profiles);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        int span = Math.max(2, Math.min(4, (int) (widthDp / 240f)));

        RecyclerView list = view.findViewById(R.id.profiles_list);
        list.setLayoutManager(new GridLayoutManager(requireContext(), span));
        mAdapter = new ProfilesAdapter();
        list.setAdapter(mAdapter);

        view.findViewById(R.id.profiles_new_button).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mAdapter != null) mAdapter.reload();
    }

    @Override
    public void onStart() {
        super.onStart();
        ProgressKeeper.addTaskCountListener(mTaskCountListener, false);
    }

    @Override
    public void onStop() {
        super.onStop();
        ProgressKeeper.removeTaskCountListener(mTaskCountListener);
    }

    /** Whether the files of the version this profile uses are already on the device */
    private static boolean isDownloaded(MinecraftProfile profile) {
        if(profile.lastVersionId == null || Tools.DIR_HOME_VERSION == null) return false;
        String id = AsyncMinecraftDownloader.normalizeVersionId(profile.lastVersionId);
        File dir = new File(Tools.DIR_HOME_VERSION, id);
        File json = new File(dir, id + ".json");
        if(!json.exists()) return false;
        if(new File(dir, id + ".jar").exists()) return true;
        // Modded versions reuse the jar of the version they inherit from
        try {
            return Tools.read(json).contains("\"inheritsFrom\"");
        } catch (IOException e) {
            return false;
        }
    }

    private static String versionLabel(Context context, MinecraftProfile profile) {
        String id = profile.lastVersionId;
        if(id == null) return "";
        if(MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(id)) return context.getString(R.string.profiles_latest_release);
        if(MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(id)) return context.getString(R.string.profiles_latest_snapshot);
        return id;
    }

    /** Best-effort guess of the loader from the version id, empty if nothing to show */
    private static String loaderTag(MinecraftProfile profile) {
        String id = profile.lastVersionId;
        if(id == null) return "";
        String lower = id.toLowerCase(Locale.ROOT);
        if(lower.contains("neoforge")) return "NeoForge";
        if(lower.contains("forge")) return "Forge";
        if(lower.contains("fabric")) return "Fabric";
        if(lower.contains("quilt")) return "Quilt";
        if(lower.contains("optifine")) return "OptiFine";
        return "Vanilla";
    }

    private static String title(Context context, String key, MinecraftProfile profile) {
        if(profile.name != null && !profile.name.isEmpty()) return profile.name;
        String version = versionLabel(context, profile);
        return version.isEmpty() ? key : version;
    }

    private class ProfilesAdapter extends RecyclerView.Adapter<ProfilesAdapter.Holder> {
        private final List<String> mKeys = new ArrayList<>();

        void reload() {
            mKeys.clear();
            if(LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
            if(LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
                final Context context = requireContext();
                mKeys.addAll(LauncherProfiles.mainProfileJson.profiles.keySet());
                Collections.sort(mKeys, (a, b) -> {
                    MinecraftProfile pa = LauncherProfiles.mainProfileJson.profiles.get(a);
                    MinecraftProfile pb = LauncherProfiles.mainProfileJson.profiles.get(b);
                    if(pa == null || pb == null) return 0;
                    return title(context, a, pa).compareToIgnoreCase(title(context, b, pb));
                });
            }
            notifyDataSetChanged();
        }

        private void select(String key) {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key)
                    .apply();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final String key = mKeys.get(position);
            final MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            if(profile == null) return;
            Context context = holder.itemView.getContext();

            holder.name.setText(title(context, key, profile));
            boolean downloaded = isDownloaded(profile);
            holder.version.setText(versionLabel(context, profile) + " \u00b7 " + context.getString(
                    downloaded ? R.string.profile_downloaded : R.string.profile_not_downloaded));
            holder.download.setVisibility(downloaded ? View.GONE : View.VISIBLE);
            holder.download.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.DOWNLOAD_VERSION, key));
            String tag = loaderTag(profile);
            holder.tag.setText(tag);
            holder.tag.setVisibility(tag.isEmpty() ? View.GONE : View.VISIBLE);
            holder.icon.setImageDrawable(ProfileIconCache.fetchIcon(context.getResources(), key, profile.icon));

            String current = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            holder.itemView.setSelected(key.equals(current));
            holder.itemView.setOnClickListener(v -> select(key));
            holder.edit.setOnClickListener(v -> {
                select(key);
                Tools.swapFragment(requireActivity(), ProfileEditorFragment.class, ProfileEditorFragment.TAG, null);
            });
        }

        @Override
        public int getItemCount() {
            return mKeys.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final ImageButton edit, download;
            final TextView name, version, tag;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.profile_icon);
                edit = itemView.findViewById(R.id.profile_edit);
                download = itemView.findViewById(R.id.profile_download);
                name = itemView.findViewById(R.id.profile_name);
                version = itemView.findViewById(R.id.profile_version);
                tag = itemView.findViewById(R.id.profile_tag);
            }
        }
    }
}
