package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Ensures each selectable launcher profile has an isolated mutable game directory before installing content. */
public final class InstanceDirectoryResolver {
    private InstanceDirectoryResolver() { }

    public static synchronized File resolveIsolated(String profileKey, MinecraftProfile profile) throws IOException {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        if (profileKey == null || profile == null) throw new IOException("Select an existing Minecraft instance first.");
        File currentDirectory = Tools.getGameDirPath(profile).getCanonicalFile();
        boolean shared = profile.gameDir == null || profile.gameDir.trim().isEmpty();
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (entry.getKey().equals(profileKey) || entry.getValue() == null) continue;
            if (Tools.getGameDirPath(entry.getValue()).getCanonicalFile().equals(currentDirectory)) {
                shared = true;
                break;
            }
        }
        if (!shared) {
            FileUtils.ensureDirectory(currentDirectory);
            return currentDirectory;
        }

        String safeKey = profileKey.replaceAll("[^A-Za-z0-9_-]", "_");
        String path = "./custom_instances/divlauncher_" + safeKey;
        File isolatedDirectory = new File(Tools.DIR_GAME_HOME, path.substring(2)).getCanonicalFile();
        // Reuse a prior directory only if this profile already points to it.
        if (isolatedDirectory.exists() && !path.equals(profile.gameDir)) {
            path = "./custom_instances/divlauncher_" + safeKey + "_" + UUID.randomUUID().toString().substring(0, 8);
            isolatedDirectory = new File(Tools.DIR_GAME_HOME, path.substring(2)).getCanonicalFile();
        }
        FileUtils.ensureDirectory(isolatedDirectory);
        copyMutableContent(currentDirectory, isolatedDirectory);
        profile.gameDir = path;
        LauncherProfiles.write();
        return isolatedDirectory;
    }

    private static void copyMutableContent(File source, File destination) throws IOException {
        if (source == null || !source.isDirectory() || source.getCanonicalFile().equals(destination.getCanonicalFile())) return;
        String[] dirs = {"mods", "resourcepacks", "shaderpacks", "config", "saves", "screenshots", "logs", "resourcepacks", "schematics"};
        for (String name : dirs) {
            File from = new File(source, name);
            if (from.isDirectory()) copyTree(from, new File(destination, name));
        }
        String[] files = {"options.txt", "optionsof.txt", "servers.dat", "servers.dat_old"};
        for (String name : files) {
            File from = new File(source, name);
            if (from.isFile()) copyFile(from, new File(destination, name));
        }
    }

    private static void copyTree(File source, File destination) throws IOException {
        FileUtils.ensureDirectory(destination);
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory()) copyTree(child, target);
            else if (child.isFile() && !target.exists()) copyFile(child, target);
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        FileUtils.ensureParentDirectory(destination);
        try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }
}
