package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Reversible Minecraft video options preset. It does not change Android system settings. */
public final class FpsOptimization {
    private static final String BACKUP_SUFFIX = ".divlauncher-fps-backup";
    private static final String MISSING = "__DIVLAUNCHER_MISSING__";
    private static final String[] KEYS = {
            "renderDistance", "graphics", "fancyGraphics", "particles", "clouds", "entityShadows"
    };

    private FpsOptimization() { }

    public static final class Recommendation {
        public final String tier;
        public final int renderDistance;

        Recommendation(String tier, int renderDistance) {
            this.tier = tier;
            this.renderDistance = renderDistance;
        }
    }

    public static Recommendation recommend(Context context) {
        int ramMb = Tools.getTotalDeviceMemory(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int shortestSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (ramMb >= 6144 && shortestSide <= 2400) return new Recommendation("higher-memory phone", 8);
        if (ramMb >= 4096) return new Recommendation("mid-range phone", 6);
        return new Recommendation("lower-memory phone", 4);
    }

    public static boolean hasBackup(File gameDirectory) {
        return backupFile(gameDirectory).isFile();
    }

    public static void apply(File gameDirectory, int renderDistance) throws IOException {
        if (gameDirectory == null || (!gameDirectory.isDirectory() && !gameDirectory.mkdirs())) {
            throw new IOException("profile folder is unavailable");
        }
        File options = new File(gameDirectory, "options.txt");
        LinkedHashMap<String, String> values = readOptions(options);
        File backup = backupFile(gameDirectory);
        if (!backup.exists()) {
            Properties previous = new Properties();
            for (String key : KEYS) previous.setProperty(key,
                    values.containsKey(key) ? values.get(key) : MISSING);
            try (FileOutputStream out = new FileOutputStream(backup)) {
                previous.store(out, "DIVlauncher FPS preset restore data");
            }
        }
        values.put("renderDistance", Integer.toString(renderDistance));
        values.put("graphics", "fast");
        values.put("fancyGraphics", "false");
        values.put("particles", "1");
        values.put("clouds", "false");
        values.put("entityShadows", "false");
        writeOptions(options, values);
    }

    public static void restore(File gameDirectory) throws IOException {
        File backup = backupFile(gameDirectory);
        if (!backup.isFile()) throw new IOException("no saved settings");
        Properties previous = new Properties();
        try (FileInputStream in = new FileInputStream(backup)) { previous.load(in); }
        File options = new File(gameDirectory, "options.txt");
        LinkedHashMap<String, String> values = readOptions(options);
        for (String key : KEYS) {
            String oldValue = previous.getProperty(key, MISSING);
            if (MISSING.equals(oldValue)) values.remove(key);
            else values.put(key, oldValue);
        }
        writeOptions(options, values);
        if (!backup.delete()) throw new IOException("restored options but could not remove backup");
    }

    public static File currentGameDirectory() {
        return Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
    }

    private static File backupFile(File directory) {
        return new File(directory, "options.txt" + BACKUP_SUFFIX);
    }

    private static LinkedHashMap<String, String> readOptions(File file) throws IOException {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (!file.isFile()) return values;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator > 0) values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }

    private static void writeOptions(File file, Map<String, String> values) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temporary)) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey());
                writer.write(':');
                writer.write(entry.getValue());
                writer.write('\n');
            }
        }
        if (file.exists() && !file.delete()) throw new IOException("could not replace options.txt");
        if (!temporary.renameTo(file)) throw new IOException("could not save options.txt");
    }
}
