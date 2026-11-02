package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import android.widget.Toast;

/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    /**
     * Download and install the mod(pack)
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        handleInstallation(context, modDetail, selectedVersion, null);
    }

    /** Install a mod, resource pack or shader into the selected instance; modpacks create a new instance. */
    default void handleInstallation(Context context, ModDetail modDetail, int selectedVersion, File targetGameDirectory) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (modDetail.isModpack) {
                    ModLoader loaderInfo = installMod(modDetail, selectedVersion);
                    if (loaderInfo == null) throw new IOException("This modpack is invalid or uses a loader this DIVlauncher build cannot install automatically.");
                    Runnable task = loaderInfo.getDownloadTask(new NotificationDownloadListener(context, loaderInfo));
                    if (task == null) throw new IOException("The modpack requires a loader that this launcher cannot install automatically.");
                    task.run();
                } else {
                    if ("mod".equals(modDetail.projectType)
                            && (modDetail.targetLoader == null || modDetail.targetLoader.trim().isEmpty())) {
                        throw new IOException("This instance does not appear to have Fabric, Forge, Quilt, or NeoForge installed. Install a supported mod loader first.");
                    }
                    File installDirectory = targetGameDirectory;
                    if (modDetail.targetProfileKey != null && !modDetail.targetProfileKey.isEmpty()) {
                        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
                        MinecraftProfile selectedProfile = LauncherProfiles.mainProfileJson.profiles.get(modDetail.targetProfileKey);
                        if (selectedProfile == null) throw new IOException("The selected instance no longer exists.");
                        installDirectory = InstanceDirectoryResolver.resolveIsolated(modDetail.targetProfileKey, selectedProfile);
                    }
                    if (installDirectory == null) throw new IOException("Select an instance before installing this content.");
                    installProject(modDetail, selectedVersion, installDirectory);
                    Tools.runOnUiThread(() -> Toast.makeText(context,
                            "Installed " + modDetail.title + " into the selected instance.", Toast.LENGTH_LONG).show());
                }
            } catch (IOException e) {
                Tools.runOnUiThread(() -> Toast.makeText(context, "Installation failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (!modDetail.isModpack) ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    /** Install a single project artifact into a selected instance directory. */
    void installProject(ModDetail modDetail, int selectedVersion, File targetGameDirectory) throws IOException;

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException;
}
