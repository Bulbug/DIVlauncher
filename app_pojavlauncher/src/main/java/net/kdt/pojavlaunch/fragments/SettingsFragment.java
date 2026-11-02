package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

import java.util.ArrayList;
import java.util.List;

/** Two-pane settings: category list on the left, the selected preference screen in a panel on the right */
public class SettingsFragment extends Fragment {
    public static final String TAG = "SettingsFragment";
    private static final String STATE_PAGE = "settings_page";
    private static final String PAGE_TAG = "settings_page";

    private static final int[] TITLES = {
            R.string.settings_general, R.string.preference_video_title, R.string.preference_control_title,
            R.string.preference_java_title, R.string.preference_misc_title, R.string.preference_experimental_title};
    private static final int[] ICONS = {
            R.drawable.ic_menu_settings, R.drawable.ic_setting_video, R.drawable.ic_menu_custom_controls,
            R.drawable.ic_setting_java_runtime, R.drawable.ic_setting_misc, R.drawable.ic_setting_engine};
    private static final List<Class<? extends Fragment>> PAGES = new ArrayList<>();
    static {
        PAGES.add(LauncherPreferenceFragment.class);
        PAGES.add(LauncherPreferenceVideoFragment.class);
        PAGES.add(LauncherPreferenceControlFragment.class);
        PAGES.add(LauncherPreferenceJavaFragment.class);
        PAGES.add(LauncherPreferenceMiscellaneousFragment.class);
        PAGES.add(LauncherPreferenceExperimentalFragment.class);
    }

    private final List<View> mItems = new ArrayList<>();
    private int mPage = 0;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.settings_version)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        LinearLayout list = view.findViewById(R.id.settings_categories);
        LayoutInflater inflater = LayoutInflater.from(view.getContext());
        mItems.clear();
        for(int i = 0; i < PAGES.size(); i++) {
            View item = inflater.inflate(R.layout.item_settings_category, list, false);
            ((ImageView) item.findViewById(R.id.category_icon)).setImageResource(ICONS[i]);
            ((TextView) item.findViewById(R.id.category_title)).setText(TITLES[i]);
            final int page = i;
            item.setOnClickListener(v -> showPage(page, false));
            list.addView(item);
            mItems.add(item);
        }
        if(savedInstanceState != null) {
            // The panel fragment is restored by the child fragment manager, only restore the highlight
            mPage = savedInstanceState.getInt(STATE_PAGE, 0);
            showPage(mPage, true);
        } else {
            showPage(0, false);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, mPage);
    }

    private void showPage(int page, boolean highlightOnly) {
        mPage = page;
        for(int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == page);
        if(highlightOnly) return;
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.settings_panel, PAGES.get(page), null, PAGE_TAG)
                .commit();
    }
}
