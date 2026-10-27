package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.authenticator.elyby.ElyByAuthClient;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByOAuthClient;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByTokenStore;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.ModVersionListFragment;
import net.kdt.pojavlaunch.fragments.ProfileEditorFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.ProfilesFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.fragments.SettingsFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.lang.ref.WeakReference;
import java.text.ParseException;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(this, data);
            });

    private mcAccountSpinner mAccountSpinner;
    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private mcVersionSpinner mVersionSpinner;
    private TextView mPageTitle;
    private View mNavHome, mNavProfiles, mNavMods, mNavControls, mNavSettings;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof MainMenuFragment
                    ? R.drawable.ic_menu_settings : R.drawable.ic_menu_home));
            updateNavSelection(f);
            // The profile switcher lives in the top bar now, so refresh it whenever a top-level screen shows
            if(mVersionSpinner != null && f.getId() == R.id.container_fragment) mVersionSpinner.syncSelection();
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        openAddAccountScreen();
        return false;
    };

    /** Open the authentication chooser directly from the account control.
     * This avoids relying solely on the global ExtraCore event for a UI tap.
     */
    public void openAddAccountScreen() {
        if (isFinishing() || isDestroyed()) {
            android.util.Log.w("DIVlauncher", "Ignoring Add Account tap because LauncherActivity is finishing");
            return;
        }

        // Resolve the container at the moment of the tap instead of relying only on
        // bindViews() having populated the cached field. This also avoids a silent
        // no-op if a tap arrives during early activity setup.
        FragmentContainerView container = mFragmentView;
        if (container == null) container = findViewById(R.id.container_fragment);
        if (container == null) {
            android.util.Log.e("DIVlauncher", "Add Account tap received, but container_fragment is missing");
            Toast.makeText(this, "Unable to open Add Account: screen container is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(container.getId());
        if (current instanceof SelectAuthFragment) {
            // The chooser may already be on screen; still ensure the tap isn't silently lost.
            return;
        }

        try {
            // Do not silently discard the user's tap just because Android has saved
            // FragmentManager state during a lifecycle transition. This UI navigation
            // is safe to restore on the next activity recreation.
            fm.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack(SelectAuthFragment.class.getName())
                    .replace(container.getId(), SelectAuthFragment.class, null, SelectAuthFragment.TAG)
                    .commitAllowingStateLoss();
        } catch (RuntimeException e) {
            android.util.Log.e("DIVlauncher", "Failed to open Add Account screen", e);
            Toast.makeText(this, "Unable to open Add Account: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if(fragment instanceof MainMenuFragment){
            Tools.swapFragment(this, SettingsFragment.class, SETTING_FRAGMENT_TAG, null);
        } else{
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private boolean mElyByLaunchValidationInProgress = false;
    private boolean mElyByLaunchValidationPassed = false;

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        MinecraftAccount selectedAccountForLaunch = mAccountSpinner == null ? null : mAccountSpinner.getSelectedAccount();
        if (selectedAccountForLaunch != null && selectedAccountForLaunch.isElyBy()) {
            if (mElyByLaunchValidationPassed) {
                // The re-fired launch event below has completed provider validation.
                mElyByLaunchValidationPassed = false;
            } else {
                if (mElyByLaunchValidationInProgress) {
                    Toast.makeText(this, "Ely.by session validation is already running.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (!selectedAccountForLaunch.hasElyBySession()) {
                    Toast.makeText(this, R.string.auth_elyby_session_expired, Toast.LENGTH_LONG).show();
                    ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                    return false;
                }
                mElyByLaunchValidationInProgress = true;
                final String previousAccountName = selectedAccountForLaunch.username;
                PojavApplication.sExecutorService.execute(() -> {
                    try {
                        if (selectedAccountForLaunch.isElyByOAuth()) {
                            ElyByOAuthClient.refreshAndValidate(selectedAccountForLaunch);
                        } else {
                            ElyByAuthClient.validate(selectedAccountForLaunch.accessToken);
                            ElyByAuthClient.refresh(selectedAccountForLaunch);
                        }
                        if (!previousAccountName.equals(selectedAccountForLaunch.username)) {
                            File collidingAccount = new File(Tools.DIR_ACCOUNT_NEW, selectedAccountForLaunch.username + ".json");
                            if (collidingAccount.exists()) {
                                selectedAccountForLaunch.username = previousAccountName;
                                throw new IOException("Ely.by returned a profile name already used by another saved account. Remove or rename that account first.");
                            }
                        }
                        selectedAccountForLaunch.save();
                        if (!previousAccountName.equals(selectedAccountForLaunch.username)) {
                            File previousFile = new File(Tools.DIR_ACCOUNT_NEW, previousAccountName + ".json");
                            if (previousFile.exists()) previousFile.delete();
                            ElyByTokenStore.deleteToken(previousAccountName);
                            if (previousAccountName.equals(PojavProfile.getCurrentProfileName(this))) {
                                PojavProfile.setCurrentProfile(this, selectedAccountForLaunch.username);
                            }
                        }
                        Tools.runOnUiThread(() -> {
                            mElyByLaunchValidationInProgress = false;
                            if (mAccountSpinner != null) mAccountSpinner.refreshAccounts();
                            mElyByLaunchValidationPassed = true;
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                        });
                    } catch (ElyByAuthClient.AuthException exception) {
                        if (exception.isInvalidSession()) {
                            ElyByTokenStore.deleteToken(previousAccountName);
                            selectedAccountForLaunch.accessToken = ElyByTokenStore.SECURE_TOKEN_MARKER;
                            selectedAccountForLaunch.requiresReauthentication = true;
                            try { selectedAccountForLaunch.save(); } catch (IOException ignored) { }
                        }
                        final String message = exception.isInvalidSession()
                                ? getString(R.string.auth_elyby_session_expired)
                                : exception.kind == ElyByAuthClient.ErrorKind.NETWORK
                                    ? getString(R.string.auth_elyby_refresh_network_error) : exception.getMessage();
                        Tools.runOnUiThread(() -> {
                            mElyByLaunchValidationInProgress = false;
                            if (exception.isInvalidSession() && mAccountSpinner != null) mAccountSpinner.refreshAccounts();
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            if (exception.isInvalidSession()) ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                        });
                    } catch (IOException exception) {
                        String message = exception.getMessage() == null
                                ? "Could not save the updated Ely.by session securely." : exception.getMessage();
                        Tools.runOnUiThread(() -> {
                            mElyByLaunchValidationInProgress = false;
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception exception) {
                        Tools.runOnUiThread(() -> {
                            mElyByLaunchValidationInProgress = false;
                            Toast.makeText(this, "Could not validate the Ely.by session. Please sign in again if this continues.", Toast.LENGTH_LONG).show();
                        });
                    }
                });
                return false;
            }
        }

        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,"");
        if (LauncherProfiles.mainProfileJson == null || !LauncherProfiles.mainProfileJson.profiles.containsKey(selectedProfile)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }

        if(mAccountSpinner.getSelectedAccount() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);

        // Do not load when is a modded version or older than minecraft 1.3 on demo account
        if (mAccountSpinner.getSelectedAccount().isDemo()) {
            boolean isOlderThan13 = true;

            if (mcVersion != null) {
                try {
                    isOlderThan13 = DateUtils.dateBefore(DateUtils.parseReleaseDate(mcVersion.releaseTime), 2012, 6, 22);
                } catch (ParseException ignored) {}
            }

            if (isOlderThan13) {
                Toast.makeText(this, R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return false;
            }
        }

        new MinecraftDownloader().start(
                this,
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final ExtraListener<String> mDownloadVersionListener = (key, profileKey) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        MinecraftProfile prof = LauncherProfiles.mainProfileJson == null ? null
                : LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }
        final String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);
        final Context appContext = getApplicationContext();

        new MinecraftDownloader().start(this, mcVersion, normalizedVersionId,
                new AsyncMinecraftDownloader.DoneListener() {
                    @Override
                    public void onDownloadDone() {
                        ProgressKeeper.waitUntilDone(() -> Tools.runOnUiThread(() ->
                                Toast.makeText(appContext,
                                        appContext.getString(R.string.profile_download_done, normalizedVersionId),
                                        Toast.LENGTH_LONG).show()));
                    }

                    @Override
                    public void onDownloadFailed(Throwable throwable) {
                        Tools.showErrorRemote(appContext.getString(R.string.mc_download_failed), throwable);
                    }
                });
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_div_launcher);
        FragmentManager fragmentManager = getSupportFragmentManager();
        // If we don't have a back stack root yet...
        if(fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }


        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        getWindow().setBackgroundDrawable(null);
        bindViews();
        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);
        ExtraCore.addExtraListener(ExtraConstants.DOWNLOAD_VERSION, mDownloadVersionListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions), false);

        mInstallTracker = new ModloaderInstallTracker(this);

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.DOWNLOAD_VERSION, mDownloadVersionListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        // Check if we are at the root then
        if(getVisibleFragment("ROOT") != null){
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            checkForNotificationPermission()) {
            return;
        }

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if(Build.VERSION.SDK_INT < 33) return;
        if(onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Highlights the navigation rail item that matches the screen currently shown */
    private void updateNavSelection(Fragment f) {
        // Only top-level screens count, nested fragments (e.g. inside settings) are ignored
        if(f.getId() != R.id.container_fragment) return;
        setNavItemSelected(mNavHome, f instanceof MainMenuFragment);
        setNavItemSelected(mNavProfiles, f instanceof ProfilesFragment || f instanceof ProfileTypeSelectFragment || f instanceof ProfileEditorFragment);
        setNavItemSelected(mNavMods, f instanceof SearchModFragment || f instanceof ModVersionListFragment);
        setNavItemSelected(mNavSettings, f instanceof SettingsFragment);

        int title = f instanceof MainMenuFragment ? R.string.nav_home
                : (f instanceof ProfilesFragment || f instanceof ProfileTypeSelectFragment || f instanceof ProfileEditorFragment) ? R.string.nav_profiles
                : (f instanceof SearchModFragment || f instanceof ModVersionListFragment) ? R.string.nav_mods
                : f instanceof SettingsFragment ? R.string.nav_settings : 0;
        if(title != 0 && mPageTitle != null) mPageTitle.setText(title);
    }

    /** Apply selection to the rail item and its icon/label so the accent state is visible. */
    private void setNavItemSelected(View item, boolean selected) {
        if(item == null) return;
        item.setSelected(selected);
        if(item instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) item;
            for(int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setSelected(selected);
            }
        }
    }

    /** Opens a top-level screen from the navigation rail, without stacking screens on top of each other */
    private void navigateTo(Class<? extends Fragment> fragmentClass, String tag) {
        Fragment current = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if(current != null && fragmentClass.isInstance(current)) return;
        if(!(current instanceof MainMenuFragment)) Tools.backToMainMenu(this);
        Tools.swapFragment(this, fragmentClass, tag, null);
    }

    private void bindNavigation() {
        mNavHome = findViewById(R.id.nav_home);
        mNavProfiles = findViewById(R.id.nav_profiles);
        mNavMods = findViewById(R.id.nav_mods);
        mNavControls = findViewById(R.id.nav_controls);
        mNavSettings = findViewById(R.id.nav_settings);

        mNavHome.setOnClickListener(v -> Tools.backToMainMenu(this));
        mNavProfiles.setOnClickListener(v -> navigateTo(ProfilesFragment.class, ProfilesFragment.TAG));
        mNavMods.setOnClickListener(v -> navigateTo(SearchModFragment.class, SearchModFragment.TAG));
        mNavControls.setOnClickListener(v -> startActivity(new Intent(this, CustomControlsActivity.class)));
        mNavSettings.setOnClickListener(v -> navigateTo(SettingsFragment.class, SETTING_FRAGMENT_TAG));
    }

    /** Activate a saved account from the account/login screens without creating a duplicate entry. */
    public boolean activateSavedAccount(String username) {
        return mAccountSpinner != null && mAccountSpinner.activateAccount(username);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mAccountSpinner = findViewById(R.id.account_spinner);
        mProgressLayout = findViewById(R.id.progress_layout);
        mPageTitle = findViewById(R.id.page_title);
        mVersionSpinner = findViewById(R.id.mc_version_spinner);
        findViewById(R.id.play_button).setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        bindNavigation();
    }
}
