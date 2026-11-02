package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import com.kdt.mcgui.ProgressLayout;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** Safe, checksum-aware download and atomic replacement for a project file. */
public final class ProjectFileInstaller {
    private ProjectFileInstaller() { }

    public static void downloadAndRecord(File gameDirectory, String projectType, int provider,
                                         String projectId, String title, String versionId,
                                         String versionName, String downloadUrl, String fileName,
                                         String hashAlgorithm, String expectedHash) throws IOException {
        if (downloadUrl == null || !downloadUrl.startsWith("https://"))
            throw new IOException("The provider did not return a secure HTTPS download URL.");
        if (fileName == null || fileName.trim().isEmpty()) throw new IOException("The provider returned no file name.");
        String safeName = new File(fileName).getName().replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")) throw new IOException("Invalid download file name.");

        File directory = InstalledContentStore.contentDirectory(gameDirectory, projectType);
        File part = new File(directory, safeName + ".divlauncher.part");
        if (part.exists() && !part.delete()) throw new IOException("Could not clear an incomplete prior download.");

        try {
            DownloadUtils.downloadFileMonitored(downloadUrl, part, new byte[32768],
                    new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            if (expectedHash != null && !expectedHash.trim().isEmpty()
                    && !verifyHash(part, hashAlgorithm, expectedHash)) {
                throw new IOException("The downloaded file failed its " + hashAlgorithm + " checksum.");
            }

            // Keep the previous installation until the replacement has downloaded and verified.
            InstalledContentStore.Entry previous = findManaged(gameDirectory, provider, projectId, projectType);
            boolean wasEnabled = previous == null || previous.enabled;
            File destination = new File(directory, safeName + (wasEnabled ? "" : ".disabled"));
            File previousFile = null;
            if (previous != null) previousFile = InstalledContentStore.resolveEntryFile(gameDirectory, previous);
            // Never overwrite an unrelated/local file merely because the provider chose the same filename.
            if (destination.exists() && (previousFile == null || !destination.equals(previousFile))) {
                throw new IOException("A different file already uses " + safeName + ". Remove or rename it before installing this version.");
            }
            File backup = null;
            if (previousFile != null && previousFile.exists()) {
                backup = new File(previousFile.getParentFile(), previousFile.getName() + ".divlauncher.bak");
                if (backup.exists() && !backup.delete()) throw new IOException("Could not clear a previous backup.");
                if (!previousFile.renameTo(backup)) throw new IOException("Could not safely prepare the previous version for replacement.");
            }
            if (destination.exists()) {
                if (backup != null && backup.exists()) backup.renameTo(previousFile);
                throw new IOException("The destination file could not be prepared for installation.");
            }
            if (!part.renameTo(destination)) {
                if (backup != null && backup.exists()) backup.renameTo(previousFile);
                throw new IOException("Could not finish installing the downloaded file.");
            }
            if (backup != null && backup.exists() && !backup.delete()) {
                // The new version is installed and verified. A leftover backup is harmless but report it.
                android.util.Log.w("ProjectFileInstaller", "Could not remove old file backup: " + backup.getName());
            }

            InstalledContentStore.Entry entry = new InstalledContentStore.Entry();
            entry.apiSource = provider;
            entry.projectId = projectId;
            entry.projectType = projectType;
            entry.title = title == null ? safeName : title;
            entry.versionId = versionId == null ? "" : versionId;
            entry.versionName = versionName == null ? "" : versionName;
            entry.fileName = safeName;
            entry.sha1 = "sha1".equalsIgnoreCase(hashAlgorithm) ? (expectedHash == null ? "" : expectedHash) : "";
            entry.enabled = wasEnabled;
            entry.managed = true;
            InstalledContentStore.upsert(gameDirectory, entry);
        } finally {
            if (part.exists()) part.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private static InstalledContentStore.Entry findManaged(File gameDirectory, int provider,
                                                           String projectId, String type) {
        for (InstalledContentStore.Entry entry : InstalledContentStore.read(gameDirectory)) {
            if (entry.managed && entry.apiSource == provider && safe(entry.projectId).equals(safe(projectId))
                    && safe(entry.projectType).equals(safe(type))) return entry;
        }
        return null;
    }

    private static boolean verifyHash(File file, String algorithm, String expected) throws IOException {
        String jca = "sha512".equalsIgnoreCase(algorithm) ? "SHA-512" : "SHA-1";
        try {
            MessageDigest digest = MessageDigest.getInstance(jca);
            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder actual = new StringBuilder();
            for (byte b : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return actual.toString().equalsIgnoreCase(expected.trim());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("This device does not support the required checksum algorithm.", e);
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
