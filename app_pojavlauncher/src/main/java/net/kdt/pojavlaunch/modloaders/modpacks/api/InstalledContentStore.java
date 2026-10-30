package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.reflect.TypeToken;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores an inventory inside each profile's own game directory. */
public final class InstalledContentStore {
    private static final String INVENTORY_PATH = ".divlauncher/installed-content.json";
    private InstalledContentStore() { }

    public static class Entry {
        public int apiSource = -1;
        public String projectId = "";
        public String projectType = "mod";
        public String title = "";
        public String versionId = "";
        public String versionName = "";
        public String fileName = "";
        public String sha1 = "";
        public boolean enabled = true;
        public boolean managed = true;
        /** "" for normal installs, "performance:<slug>" or "performance-dep" for the Performance pack */
        public String origin = "";

        public Entry() { }
    }

    private static File inventoryFile(File gameDirectory) {
        return new File(gameDirectory, INVENTORY_PATH);
    }

    public static synchronized List<Entry> read(File gameDirectory) {
        if (gameDirectory == null) return Collections.emptyList();
        File file = inventoryFile(gameDirectory);
        if (!file.isFile()) return new ArrayList<>();
        try {
            Type type = new TypeToken<ArrayList<Entry>>() { }.getType();
            List<Entry> entries = Tools.GLOBAL_GSON.fromJson(Tools.read(file), type);
            return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        } catch (Exception ignored) {
            // A broken inventory must not prevent Minecraft from launching or mods from being managed.
            return new ArrayList<>();
        }
    }

    public static synchronized void write(File gameDirectory, List<Entry> entries) throws IOException {
        if (gameDirectory == null) throw new IOException("No instance was selected.");
        File file = inventoryFile(gameDirectory);
        FileUtils.ensureParentDirectory(file);
        Tools.write(file.getAbsolutePath(), Tools.GLOBAL_GSON.toJson(entries));
    }

    public static synchronized void upsert(File gameDirectory, Entry entry) throws IOException {
        List<Entry> entries = read(gameDirectory);
        for (int i = 0; i < entries.size(); i++) {
            Entry old = entries.get(i);
            if (sameEntry(old, entry)) {
                entries.set(i, entry);
                write(gameDirectory, entries);
                return;
            }
        }
        entries.add(entry);
        write(gameDirectory, entries);
    }

    public static File contentDirectory(File gameDirectory, String projectType) throws IOException {
        if (gameDirectory == null) throw new IOException("Choose an instance before installing content.");
        String child;
        if ("resourcepack".equals(projectType)) child = "resourcepacks";
        else if ("shader".equals(projectType)) child = "shaderpacks";
        else child = "mods";
        File directory = new File(gameDirectory, child);
        FileUtils.ensureDirectory(directory);
        return directory;
    }

    public static File resolveEntryFile(File gameDirectory, Entry entry) throws IOException {
        File directory = contentDirectory(gameDirectory, entry.projectType);
        String name = safe(entry.fileName);
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IOException("The installed file name is invalid.");
        }
        if (!entry.enabled && !name.endsWith(".disabled")) name += ".disabled";
        return new File(directory, name);
    }

    public static synchronized void setEnabled(File gameDirectory, Entry entry, boolean enabled) throws IOException {
        File directory = contentDirectory(gameDirectory, entry.projectType);
        String original = safe(entry.fileName);
        if (original.endsWith(".disabled")) original = original.substring(0, original.length() - ".disabled".length());
        File from = new File(directory, enabled ? original + ".disabled" : original);
        File to = new File(directory, enabled ? original : original + ".disabled");
        if (!from.isFile()) throw new IOException("The installed file could not be found: " + original);
        if (to.exists()) throw new IOException("Another file already exists at the destination; refusing to overwrite it.");
        if (!from.renameTo(to)) throw new IOException("Could not change this file's enabled state.");
        entry.fileName = original;
        entry.enabled = enabled;
        List<Entry> entries = read(gameDirectory);
        for (Entry current : entries) {
            if (sameEntry(current, entry)) {
                current.fileName = entry.fileName;
                current.enabled = entry.enabled;
            }
        }
        write(gameDirectory, entries);
    }

    public static synchronized void remove(File gameDirectory, Entry entry) throws IOException {
        File file = resolveEntryFile(gameDirectory, entry);
        if (file.exists() && !file.delete()) throw new IOException("Could not remove " + entry.fileName);
        List<Entry> entries = read(gameDirectory);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (sameEntry(entries.get(i), entry)) entries.remove(i);
        }
        write(gameDirectory, entries);
    }

    public static boolean sameEntry(Entry a, Entry b) {
        if (a == null || b == null) return false;
        if (a.managed != b.managed) return false;
        if (a.managed) return a.apiSource == b.apiSource
                && safe(a.projectId).equals(safe(b.projectId))
                && safe(a.projectType).equals(safe(b.projectType));
        return safe(a.fileName).equals(safe(b.fileName)) && safe(a.projectType).equals(safe(b.projectType));
    }

    public static String folderFor(String projectType) {
        if ("resourcepack".equals(projectType)) return "resourcepacks";
        if ("shader".equals(projectType)) return "shaderpacks";
        if ("modpack".equals(projectType)) return "modpacks";
        return "mods";
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
