package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Opt-in (Settings > Experimental > Performance log). Every 5 seconds appends
 * epoch_ms,battery_temp_c,battery_percent,thermal_status to benchmark/device.csv, next to the
 * game's frames.csv, so FPS drops can be matched with the phone heating up.
 * thermal_status: 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown, -1 unknown.
 */
public final class DeviceStatsLogger {
    private static final long INTERVAL_MS = 5000;
    private static Handler sHandler;
    private static Runnable sTask;
    private static PrintWriter sOut;

    private DeviceStatsLogger() {}

    public static void start(Context context) {
        if (!LauncherPreferences.PREF_BENCHMARK_LOG || sHandler != null) return;
        final Context app = context.getApplicationContext();
        try {
            File dir = new File(Tools.DIR_GAME_HOME, "benchmark");
            dir.mkdirs();
            File file = new File(dir, "device.csv");
            boolean fresh = !file.exists() || file.length() == 0;
            sOut = new PrintWriter(new FileWriter(file, true));
            if (fresh) sOut.println("epoch_ms,battery_temp_c,battery_percent,thermal_status");
        } catch (IOException e) {
            return;
        }
        sHandler = new Handler(Looper.getMainLooper());
        sTask = new Runnable() {
            @Override
            public void run() {
                sample(app);
                if (sHandler != null) sHandler.postDelayed(this, INTERVAL_MS);
            }
        };
        sHandler.post(sTask);
    }

    public static void stop() {
        if (sHandler != null && sTask != null) sHandler.removeCallbacks(sTask);
        sHandler = null;
        sTask = null;
        if (sOut != null) { sOut.close(); sOut = null; }
    }

    private static void sample(Context context) {
        if (sOut == null) return;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        float temp = -1; int percent = -1;
        if (battery != null) {
            int t = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t != Integer.MIN_VALUE) temp = t / 10f;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) percent = Math.round(level * 100f / scale);
        }
        int thermal = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) thermal = pm.getCurrentThermalStatus();
        }
        sOut.println(String.format(Locale.US, "%d,%.1f,%d,%d", System.currentTimeMillis(), temp, percent, thermal));
        sOut.flush();
    }
}
