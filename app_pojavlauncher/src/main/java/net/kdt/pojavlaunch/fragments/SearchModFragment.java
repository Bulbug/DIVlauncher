package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.InstalledContentStore;
import net.kdt.pojavlaunch.modloaders.modpacks.api.InstanceDirectoryResolver;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Built-in browser for mods, modpacks, resource packs, and shaders. */
public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {
    public static final String TAG = "SearchModFragment";
    private static final String[] TYPE_KEYS = {"mod", "modpack", "resourcepack", "shader"};
    private static final String[] TYPE_LABELS = {"Mods", "Modpacks", "Resource packs", "Shaders"};
    private static final String[] LOADER_KEYS = {"", "fabric", "forge", "quilt", "neoforge"};
    private static final String[] LOADER_LABELS = {"Any / not selected", "Fabric", "Forge", "Quilt", "NeoForge"};
    private static final String[] PROVIDER_KEYS = {"both", "modrinth", "curseforge"};
    private static final String[] PROVIDER_LABELS = {"Both providers", "Modrinth", "CurseForge"};

    private View mOverlay;
    private float mOverlayTopCache;
    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (mOverlay != null) mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private TextView mProviderNotice;
    private Spinner mTypeSpinner;
    private Spinner mInstanceSpinner;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;
    private final SearchFilters mSearchFilters = new SearchFilters();
    private final List<InstanceChoice> mInstances = new ArrayList<>();
    private ArrayAdapter<InstanceChoice> mInstanceAdapter;
    private boolean mReady;

    public SearchModFragment() { super(R.layout.fragment_mod_search); }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        String proxy = context.getString(R.string.curseforge_proxy_base_url);
        modpackApi = new CommonApi(proxy);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mProviderNotice = view.findViewById(R.id.mod_browser_provider_notice);
        mTypeSpinner = view.findViewById(R.id.mod_browser_type_spinner);
        mInstanceSpinner = view.findViewById(R.id.mod_browser_instance_spinner);

        mDefaultTextColor = mStatusTextView.getTextColors();
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item,
                Arrays.asList(TYPE_LABELS));
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner.setAdapter(typeAdapter);

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mRecyclerview.setAdapter(mModItemAdapter);
        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        refreshInstances();
        mSearchFilters.setProjectType("mod");
        Bundle args = getArguments();
        if (args != null) mSearchFilters.setProjectType(args.getString("project_type", "mod"));
        mTypeSpinner.setSelection(indexOf(TYPE_KEYS, mSearchFilters.projectType));
        String proxy = getString(R.string.curseforge_proxy_base_url);
        mProviderNotice.setText(R.string.curseforge_unavailable_notice);
        mProviderNotice.setVisibility(proxy != null && proxy.trim().startsWith("https://") ? View.GONE : View.VISIBLE);

        mTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View child, int position, long id) {
                if (!mReady) return;
                mSearchFilters.setProjectType(TYPE_KEYS[Math.max(0, Math.min(position, TYPE_KEYS.length - 1))]);
                updateTargetFromSelectedInstance(true);
                updateSearchHint();
                searchMods(mSearchEditText.getText().toString());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        mInstanceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View child, int position, long id) {
                if (!mReady) return;
                updateTargetFromSelectedInstance(true);
                searchMods(mSearchEditText.getText().toString());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        view.findViewById(R.id.mod_browser_installed_button).setOnClickListener(v -> openInstalledDialog());

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return true;
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        mOverlay.post(() -> {
            if (mOverlay == null || mRecyclerview == null) return;
            int overlayHeight = mOverlay.getHeight();
            mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(), mRecyclerview.getPaddingTop() + overlayHeight,
                    mRecyclerview.getPaddingRight(), mRecyclerview.getPaddingBottom());
        });
        mReady = true;
        updateTargetFromSelectedInstance(true);
        updateSearchHint();
        searchMods(null);
    }

    private void refreshInstances() {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        mInstances.clear();
        String currentKey = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
            for (java.util.Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                if (entry.getValue() != null) mInstances.add(new InstanceChoice(entry.getKey(), entry.getValue()));
            }
        }
        Collections.sort(mInstances, new Comparator<InstanceChoice>() {
            @Override public int compare(InstanceChoice left, InstanceChoice right) {
                return safe(left.profile.name).toLowerCase(Locale.ROOT).compareTo(safe(right.profile.name).toLowerCase(Locale.ROOT));
            }
        });
        mInstanceAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, mInstances);
        mInstanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mInstanceSpinner.setAdapter(mInstanceAdapter);
        int selected = 0;
        for (int i = 0; i < mInstances.size(); i++) if (currentKey.equals(mInstances.get(i).key)) { selected = i; break; }
        if (!mInstances.isEmpty()) mInstanceSpinner.setSelection(selected);
    }

    private void updateTargetFromSelectedInstance(boolean resetCompatibilityFilters) {
        if (mInstances.isEmpty() || mInstanceSpinner.getSelectedItemPosition() < 0) {
            mSearchFilters.targetProfileKey = null;
            mSearchFilters.targetGameVersion = null;
            mSearchFilters.targetLoader = null;
            if (resetCompatibilityFilters) {
                mSearchFilters.mcVersion = null;
                mSearchFilters.loader = null;
            }
            return;
        }
        InstanceChoice choice = mInstances.get(mInstanceSpinner.getSelectedItemPosition());
        mSearchFilters.targetProfileKey = choice.key;
        ProfileCompatibility compatibility = inferCompatibility(choice.profile.lastVersionId);
        mSearchFilters.targetGameVersion = compatibility.gameVersion;
        mSearchFilters.targetLoader = compatibility.loader;
        if (resetCompatibilityFilters) {
            if ("modpack".equals(mSearchFilters.projectType)) {
                // Modpacks create a new instance, so don't accidentally filter them to the existing instance's version.
                mSearchFilters.mcVersion = null;
                mSearchFilters.loader = null;
            } else {
                mSearchFilters.mcVersion = compatibility.gameVersion;
                mSearchFilters.loader = compatibility.loader;
            }
        }
        if (mModItemAdapter != null) mModItemAdapter.setTargetGameDirectory(Tools.getGameDirPath(choice.profile));
    }

    private void updateSearchHint() {
        if (mSearchEditText == null) return;
        if ("modpack".equals(mSearchFilters.projectType)) mSearchEditText.setHint("Search modpacks");
        else if ("resourcepack".equals(mSearchFilters.projectType)) mSearchEditText.setHint("Search resource packs");
        else if ("shader".equals(mSearchFilters.projectType)) mSearchEditText.setHint("Search shaders");
        else mSearchEditText.setHint("Search mods");
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (mModItemAdapter != null) ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        if (mRecyclerview != null) mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
        mReady = false;
    }

    @Override public void onSearchFinished() {
        if (mSearchProgressBar != null) mSearchProgressBar.setVisibility(View.GONE);
        if (mStatusTextView != null) mStatusTextView.setVisibility(View.GONE);
    }

    @Override public void onSearchError(int error) {
        if (mSearchProgressBar != null) mSearchProgressBar.setVisibility(View.GONE);
        if (mStatusTextView == null) return;
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        if (mSearchProgressBar == null || mModItemAdapter == null) return;
        if (!"modpack".equals(mSearchFilters.projectType) && mInstances.isEmpty()) {
            Toast.makeText(requireContext(), R.string.mod_browser_no_instances, Toast.LENGTH_LONG).show();
            return;
        }
        updateTargetFromSelectedInstance(false);
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name.trim();
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(R.layout.dialog_mod_filters).create();
        dialog.setOnShowListener(dialogInterface -> {
            TextView selectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button selectVersion = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button apply = dialog.findViewById(R.id.search_mod_apply_filters);
            Spinner loaderSpinner = dialog.findViewById(R.id.search_mod_loader_spinner);
            Spinner providerSpinner = dialog.findViewById(R.id.search_mod_provider_spinner);
            if (selectedVersion == null || selectVersion == null || apply == null || loaderSpinner == null || providerSpinner == null) return;
            selectedVersion.setText(mSearchFilters.mcVersion == null ? "" : mSearchFilters.mcVersion);
            ArrayAdapter<String> loaderAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, Arrays.asList(LOADER_LABELS));
            loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            loaderSpinner.setAdapter(loaderAdapter);
            loaderSpinner.setSelection(indexOf(LOADER_KEYS, safe(mSearchFilters.loader)));
            ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, Arrays.asList(PROVIDER_LABELS));
            providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            providerSpinner.setAdapter(providerAdapter);
            providerSpinner.setSelection(indexOf(PROVIDER_KEYS, safe(mSearchFilters.provider)));
            selectVersion.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true,
                    (id, snapshot) -> selectedVersion.setText(id)));
            apply.setOnClickListener(v -> {
                String chosenVersion = selectedVersion.getText().toString().trim();
                mSearchFilters.mcVersion = chosenVersion.isEmpty() ? null : chosenVersion;
                String chosenLoader = LOADER_KEYS[Math.max(0, Math.min(loaderSpinner.getSelectedItemPosition(), LOADER_KEYS.length - 1))];
                mSearchFilters.loader = chosenLoader.isEmpty() ? null : chosenLoader;
                mSearchFilters.provider = PROVIDER_KEYS[Math.max(0, Math.min(providerSpinner.getSelectedItemPosition(), PROVIDER_KEYS.length - 1))];
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });
        dialog.show();
    }

    private void openInstalledDialog() {
        if (mInstances.isEmpty() || mInstanceSpinner.getSelectedItemPosition() < 0) {
            Toast.makeText(requireContext(), R.string.mod_browser_choose_instance, Toast.LENGTH_LONG).show();
            return;
        }
        InstanceChoice choice = mInstances.get(mInstanceSpinner.getSelectedItemPosition());
        File gameDirectory;
        try {
            if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(choice.key);
            gameDirectory = InstanceDirectoryResolver.resolveIsolated(choice.key, profile);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not prepare this instance: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        List<InstalledContentStore.Entry> entries = collectInstalled(gameDirectory);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(requireContext()).setTitle("Installed content")
                    .setMessage(R.string.mod_browser_no_installed_content).setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        list.setPadding(padding, padding, padding, padding);
        scrollView.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Installed content · " + choice).setView(scrollView)
                .setPositiveButton(android.R.string.ok, null).create();
        for (InstalledContentStore.Entry entry : entries) addInstalledRow(list, entry, gameDirectory, choice.key, dialog);
        dialog.show();
    }

    private List<InstalledContentStore.Entry> collectInstalled(File gameDirectory) {
        List<InstalledContentStore.Entry> result = new ArrayList<>();
        List<InstalledContentStore.Entry> stored = InstalledContentStore.read(gameDirectory);
        for (InstalledContentStore.Entry entry : stored) {
            File active = new File(new File(gameDirectory, InstalledContentStore.folderFor(entry.projectType)), cleanDisabledSuffix(entry.fileName));
            File disabled = new File(active.getParentFile(), active.getName() + ".disabled");
            if (active.isFile()) entry.enabled = true;
            else if (disabled.isFile()) entry.enabled = false;
            result.add(entry);
        }
        String[][] folders = {{"mods", "mod"}, {"resourcepacks", "resourcepack"}, {"shaderpacks", "shader"}};
        for (String[] pair : folders) {
            File folder = new File(gameDirectory, pair[0]);
            File[] files = folder.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (!file.isFile() || file.getName().contains(".divlauncher.")) continue;
                String filename = cleanDisabledSuffix(file.getName());
                boolean enabled = !file.getName().endsWith(".disabled");
                boolean known = false;
                for (InstalledContentStore.Entry entry : result) {
                    if (pair[1].equals(entry.projectType) && filename.equals(cleanDisabledSuffix(entry.fileName))) { known = true; break; }
                }
                if (known) continue;
                InstalledContentStore.Entry unmanaged = new InstalledContentStore.Entry();
                unmanaged.apiSource = -1;
                unmanaged.projectType = pair[1];
                unmanaged.title = filename;
                unmanaged.fileName = filename;
                unmanaged.enabled = enabled;
                unmanaged.managed = false;
                result.add(unmanaged);
            }
        }
        Collections.sort(result, new Comparator<InstalledContentStore.Entry>() {
            @Override public int compare(InstalledContentStore.Entry left, InstalledContentStore.Entry right) {
                return safe(left.title).toLowerCase(Locale.ROOT).compareTo(safe(right.title).toLowerCase(Locale.ROOT));
            }
        });
        return result;
    }

    private void addInstalledRow(LinearLayout parent, InstalledContentStore.Entry entry, File gameDirectory, String profileKey, AlertDialog dialog) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackgroundResource(R.drawable.background_line);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        parent.addView(row, rowParams);

        TextView title = new TextView(requireContext());
        title.setText(entry.title == null || entry.title.isEmpty() ? entry.fileName : entry.title);
        title.setTextSize(15);
        title.setTextColor(Color.WHITE);
        row.addView(title);
        TextView metadata = new TextView(requireContext());
        String typeLabel = "resourcepack".equals(entry.projectType) ? "Resource pack" : "shader".equals(entry.projectType) ? "Shader" : "mod".equals(entry.projectType) ? "Mod" : "Modpack";
        metadata.setText(typeLabel + " · " + entry.fileName + (entry.managed ? "" : "\n" + getString(R.string.mod_browser_unmanaged)));
        metadata.setTextSize(12);
        row.addView(metadata);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(actions);
        Switch enabledSwitch = new Switch(requireContext());
        enabledSwitch.setText(entry.enabled ? getString(R.string.mod_browser_disable) : getString(R.string.mod_browser_enable));
        enabledSwitch.setChecked(entry.enabled);
        actions.addView(enabledSwitch, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            try {
                if (!entry.managed) InstalledContentStore.upsert(gameDirectory, entry);
                InstalledContentStore.setEnabled(gameDirectory, entry, checked);
                entry.enabled = checked;
                enabledSwitch.setText(checked ? getString(R.string.mod_browser_disable) : getString(R.string.mod_browser_enable));
            } catch (IOException e) {
                enabledSwitch.setOnCheckedChangeListener(null);
                enabledSwitch.setChecked(!checked);
                enabledSwitch.setOnCheckedChangeListener((b, isChecked) -> {
                    try {
                        if (!entry.managed) InstalledContentStore.upsert(gameDirectory, entry);
                        InstalledContentStore.setEnabled(gameDirectory, entry, isChecked);
                    } catch (IOException ex) { Toast.makeText(requireContext(), ex.getMessage(), Toast.LENGTH_LONG).show(); }
                });
                Toast.makeText(requireContext(), "Could not change file state: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        if (entry.managed) {
            Button update = new Button(requireContext());
            update.setText(R.string.mod_browser_update);
            actions.addView(update);
            update.setOnClickListener(v -> updateInstalled(entry, profileKey, gameDirectory, dialog));
        }
        Button remove = new Button(requireContext());
        remove.setText(R.string.mod_browser_remove);
        actions.addView(remove);
        remove.setOnClickListener(v -> new AlertDialog.Builder(requireContext()).setTitle(R.string.mod_browser_remove)
                .setMessage(R.string.mod_browser_confirm_remove)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mod_browser_remove, (d, which) -> {
                    try {
                        if (!entry.managed) InstalledContentStore.upsert(gameDirectory, entry);
                        InstalledContentStore.remove(gameDirectory, entry);
                        dialog.dismiss();
                        openInstalledDialog();
                    } catch (IOException e) { Toast.makeText(requireContext(), "Could not remove file: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                }).show());
    }

    private void updateInstalled(InstalledContentStore.Entry entry, String profileKey, File gameDirectory, AlertDialog dialog) {
        if (entry.apiSource < 0 || Constants.SOURCE_TECHNIC == entry.apiSource) {
            Toast.makeText(requireContext(), "This file does not have update metadata.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), "Checking for an update…", Toast.LENGTH_SHORT).show();
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            final ModDetail detail;
            try {
                ModItem item = new ModItem(entry.apiSource, entry.projectType, entry.projectId, entry.title, "", "");
                item.targetProfileKey = profileKey;
                ProfileCompatibility target = null;
                if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
                MinecraftProfile targetProfile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
                if (targetProfile != null) target = inferCompatibility(targetProfile.lastVersionId);
                item.targetGameVersion = target == null ? mSearchFilters.targetGameVersion : target.gameVersion;
                item.targetLoader = target == null ? mSearchFilters.targetLoader : target.loader;
                detail = modpackApi.getModDetails(item);
            } catch (Exception error) {
                Tools.runOnUiThread(() -> Toast.makeText(appContext,
                        "Could not check updates. Check the provider configuration and network connection.", Toast.LENGTH_LONG).show());
                return;
            }
            Tools.runOnUiThread(() -> {
                if (detail == null || detail.versionNames == null || detail.versionNames.length == 0) {
                    Toast.makeText(appContext, "No compatible update is available.", Toast.LENGTH_LONG).show();
                } else {
                    dialog.dismiss();
                    modpackApi.handleInstallation(appContext, detail, 0, gameDirectory);
                }
            });
        });
    }

    private static String cleanDisabledSuffix(String name) { return name != null && name.endsWith(".disabled") ? name.substring(0, name.length() - 9) : safe(name); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int indexOf(String[] source, String value) { for (int i = 0; i < source.length; i++) if (source[i].equals(value)) return i; return 0; }
    private static String safe(String value) { return value == null ? "" : value; }

    private static ProfileCompatibility inferCompatibility(String versionId) {
        if (versionId == null) return new ProfileCompatibility(null, null);
        Matcher fabric = Pattern.compile("^fabric-loader-[^-]+-(\\d+(?:\\.\\d+){1,2}).*$").matcher(versionId);
        if (fabric.matches()) return new ProfileCompatibility(fabric.group(1), "fabric");
        Matcher quilt = Pattern.compile("^quilt-loader-[^-]+-(\\d+(?:\\.\\d+){1,2}).*$").matcher(versionId);
        if (quilt.matches()) return new ProfileCompatibility(quilt.group(1), "quilt");
        Matcher forge = Pattern.compile("^(\\d+(?:\\.\\d+){1,2})-forge-").matcher(versionId);
        if (forge.find()) return new ProfileCompatibility(forge.group(1), "forge");
        Matcher neo = Pattern.compile("^(\\d+(?:\\.\\d+){1,2})-neoforge-").matcher(versionId);
        if (neo.find()) return new ProfileCompatibility(neo.group(1), "neoforge");
        Matcher vanilla = Pattern.compile("^(\\d+(?:\\.\\d+){1,2})$").matcher(versionId);
        if (vanilla.matches()) return new ProfileCompatibility(versionId, null);
        return new ProfileCompatibility(null, null);
    }

    private static class ProfileCompatibility {
        final String gameVersion; final String loader;
        ProfileCompatibility(String version, String loader) { this.gameVersion = version; this.loader = loader; }
    }
    private static class InstanceChoice {
        final String key; final MinecraftProfile profile;
        InstanceChoice(String key, MinecraftProfile profile) { this.key = key; this.profile = profile; }
        @NonNull @Override public String toString() { return safe(profile.name) + " · " + safe(profile.lastVersionId); }
    }
}
