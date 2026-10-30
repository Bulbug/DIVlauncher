package org.lwjgl.glfw;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/**
 * Opt-in frame statistics. Enabled only when the launcher starts the game with
 * -Ddiv.benchmark=&lt;path to frames.csv&gt;. Counts glfwSwapBuffers calls and appends one line every
 * five seconds: epoch_ms,avg_fps,low1_fps,max_frame_ms,frames
 * (low1_fps is the average FPS of the slowest 1% of frames). Does nothing when the property is absent.
 */
final class FrameStats {
    private static final String PATH = System.getProperty("div.benchmark");
    private static final boolean ON = PATH != null && !PATH.isEmpty();
    private static final long WINDOW_NS = 5000000000L;

    private static long last, windowStart;
    private static int count;
    private static final long[] DTS = new long[1 << 14];
    private static PrintWriter out;

    private FrameStats() {}

    static void onFrame() {
        if (!ON) return;
        long now = System.nanoTime();
        if (last == 0) { last = now; windowStart = now; return; }
        long dt = now - last;
        last = now;
        if (count < DTS.length) DTS[count] = dt;
        count++;
        if (now - windowStart >= WINDOW_NS) flush(now);
    }

    private static void flush(long now) {
        int n = Math.min(count, DTS.length);
        if (n > 0) {
            double avg = count * 1e9 / (now - windowStart);
            long[] sorted = Arrays.copyOf(DTS, n);
            Arrays.sort(sorted);
            int k = Math.max(1, n / 100);
            long worstSum = 0;
            for (int i = n - k; i < n; i++) worstSum += sorted[i];
            double low1 = 1e9 / ((double) worstSum / k);
            double maxMs = sorted[n - 1] / 1e6;
            write(String.format(Locale.US, "%d,%.1f,%.1f,%.1f,%d",
                    System.currentTimeMillis(), avg, low1, maxMs, count));
        }
        count = 0;
        windowStart = now;
    }

    private static void write(String line) {
        try {
            if (out == null) {
                File file = new File(PATH);
                boolean fresh = !file.exists() || file.length() == 0;
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                out = new PrintWriter(new FileWriter(file, true));
                if (fresh) out.println("epoch_ms,avg_fps,low1_fps,max_frame_ms,frames");
            }
            out.println(line);
            out.flush();
        } catch (IOException ignored) {
            // measuring must never disturb the game
        }
    }
}
