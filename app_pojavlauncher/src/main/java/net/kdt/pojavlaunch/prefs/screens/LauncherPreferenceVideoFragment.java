package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.FpsOptimization;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        setupFpsOptimization();
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        //Disable notch checking behavior on android 8.1 and below.
        requirePreference("ignoreNotch").setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && LauncherPreferences.PREF_NOTCH_SIZE > 0);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        Tools.RenderersList renderersList = Tools.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(renderersList.rendererDisplayNames);
        rendererListPreference.setEntryValues(renderersList.rendererIds.toArray(new String[0]));

        computeVisibility();
    }

    private void setupFpsOptimization() {
        androidx.preference.Preference optimize = requirePreference("fps_optimization");
        androidx.preference.Preference restore = requirePreference("fps_optimization_restore");
        java.io.File gameDirectory = FpsOptimization.currentGameDirectory();
        restore.setVisible(FpsOptimization.hasBackup(gameDirectory));
        optimize.setOnPreferenceClickListener(preference -> {
            FpsOptimization.Recommendation recommendation = FpsOptimization.recommend(requireContext());
            String preview = getString(R.string.fps_optimization_preview_body,
                    recommendation.tier, recommendation.renderDistance);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.fps_optimization_preview_title)
                    .setMessage(preview)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.fps_optimization_apply, (dialog, which) -> {
                        try {
                            FpsOptimization.apply(FpsOptimization.currentGameDirectory(), recommendation.renderDistance);
                            restore.setVisible(true);
                            android.widget.Toast.makeText(requireContext(), R.string.fps_optimization_applied,
                                    android.widget.Toast.LENGTH_LONG).show();
                        } catch (java.io.IOException e) {
                            android.widget.Toast.makeText(requireContext(),
                                    getString(R.string.fps_optimization_error, e.getMessage()),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }).show();
            return true;
        });
        restore.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.fps_optimization_restore_title)
                    .setMessage(R.string.fps_optimization_restore_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.fps_optimization_restore_title, (dialog, which) -> {
                        try {
                            FpsOptimization.restore(FpsOptimization.currentGameDirectory());
                            restore.setVisible(false);
                            android.widget.Toast.makeText(requireContext(), R.string.fps_optimization_restored,
                                    android.widget.Toast.LENGTH_SHORT).show();
                        } catch (java.io.IOException e) {
                            android.widget.Toast.makeText(requireContext(),
                                    getString(R.string.fps_optimization_error, e.getMessage()),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }).show();
            return true;
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }
}
