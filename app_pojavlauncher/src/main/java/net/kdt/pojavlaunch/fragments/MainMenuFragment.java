package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

/**
 * Home screen. The profile switcher and the Play button now live in the activity's top bar
 * (see activity_div_launcher.xml) so they are reachable from every screen; this fragment shows
 * the selected profile, shortcuts to the main areas and the less common tools.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private TextView mHeroProfile, mHeroVersion;

    /** Keeps the hero card in sync when the profile is changed from the top bar */
    private final TextWatcher mProfileWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            // The spinner stores the new selection right after setting its text, so wait a frame
            if(mHeroProfile != null) mHeroProfile.post(MainMenuFragment.this::refreshHero);
        }
    };

    public MainMenuFragment(){
        super(R.layout.fragment_div_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mNewsButton = view.findViewById(R.id.news_button);
        View mDiscordButton = view.findViewById(R.id.discord_button);
        View mCustomControlButton = view.findViewById(R.id.custom_control_button);
        View mInstallJarButton = view.findViewById(R.id.install_jar_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        View mEditProfileButton = view.findViewById(R.id.edit_profile_button);

        mHeroProfile = view.findViewById(R.id.hero_profile);
        mHeroVersion = view.findViewById(R.id.hero_version);
        mVersionSpinner = requireActivity().findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v->{
            runInstallerWithConfirmation(true);
            return true;
        });
        mEditProfileButton.setOnClickListener(v -> {
            if(mVersionSpinner != null) mVersionSpinner.openProfileEditor(requireActivity());
        });

        view.findViewById(R.id.tile_new_profile).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        // Reuse the rail's own navigation so Home and the rail can never disagree
        view.findViewById(R.id.tile_mods).setOnClickListener(v -> requireActivity().findViewById(R.id.nav_mods).performClick());
        view.findViewById(R.id.tile_settings).setOnClickListener(v -> requireActivity().findViewById(R.id.nav_settings).performClick());

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            Tools.switchDemo(Tools.isDemoProfile(v.getContext()));
            openPath(v.getContext(), getCurrentProfileDirectory(), false);
        });

        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });

        if(mVersionSpinner != null) mVersionSpinner.addTextChangedListener(mProfileWatcher);
    }

    @Override
    public void onDestroyView() {
        if(mVersionSpinner != null) mVersionSpinner.removeTextChangedListener(mProfileWatcher);
        mHeroProfile = null;
        mHeroVersion = null;
        super.onDestroyView();
    }

    @Nullable
    private MinecraftProfile getCurrentProfile() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return null;
        LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
    }

    private File getCurrentProfileDirectory() {
        MinecraftProfile profileObject = getCurrentProfile();
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    /** Shows the selected profile's name and version in the hero card */
    private void refreshHero() {
        if(mHeroProfile == null || mHeroVersion == null) return;
        String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        MinecraftProfile profile = getCurrentProfile();
        if(profile == null) {
            mHeroProfile.setText(R.string.home_no_profile);
            mHeroVersion.setText(R.string.home_no_profile_hint);
            return;
        }
        mHeroProfile.setText(Tools.isValidString(profile.name) ? profile.name : key);
        mHeroVersion.setText(Tools.isValidString(profile.lastVersionId) ? profile.lastVersionId : "");
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHero();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
