package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;

public class ModpackInstaller {

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        if (modDetail == null || modDetail.versionUrls == null || modDetail.versionHashes == null
                || selectedVersion < 0 || selectedVersion >= modDetail.versionUrls.length
                || selectedVersion >= modDetail.versionHashes.length) {
            throw new IOException("No valid modpack version was selected.");
        }
        String versionUrl = modDetail.versionUrls[selectedVersion];
        if (versionUrl == null || !versionUrl.startsWith("https://")) {
            throw new IOException("The modpack provider did not return a secure HTTPS download URL.");
        }
        String versionHash = modDetail.versionHashes[selectedVersion];
        String modpackName = (modDetail.title.toLowerCase(Locale.ROOT) + " " + modDetail.versionNames[selectedVersion])
                .trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_" );
        if (versionHash != null) {
            modpackName += "_" + versionHash;
        }
        if (modpackName.length() > 255){
            modpackName = modpackName.substring(0,255);
        }

        // Build a new isolated Minecraft instance directory. Never overwrite an existing instance.
        String instanceFolderName = modpackName;
        File instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + instanceFolderName);
        int suffix = 2;
        while (instanceDestination.exists()) {
            String suffixText = "_" + suffix++;
            int maxBase = Math.max(1, 240 - suffixText.length());
            String base = modpackName.length() > maxBase ? modpackName.substring(0, maxBase) : modpackName;
            instanceFolderName = base + suffixText;
            instanceDestination = new File(Tools.DIR_GAME_HOME, "custom_instances/" + instanceFolderName);
        }

        // Get the modpack file
        File modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf"); // Cache File
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Install the modpack
            modLoaderInfo = installFunction.installModpack(modpackFile, instanceDestination);

        } finally {
            modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        if(modLoaderInfo == null) {
            return null;
        }

        // Create the instance
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        MinecraftProfile profile = new MinecraftProfile();
        profile.gameDir = "./custom_instances/" + instanceFolderName;
        profile.name = modDetail.title;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        profile.icon = ModIconCache.getBase64Image(modDetail.getIconCacheTag());


        LauncherProfiles.insertMinecraftProfile(profile);
        LauncherProfiles.write();

        return modLoaderInfo;
    }

    interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }
}
