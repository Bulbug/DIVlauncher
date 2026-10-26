package net.kdt.pojavlaunch.authenticator.elyby;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.net.ssl.HttpsURLConnection;

/** Downloads and validates the pinned authlib-injector artifact from its official distribution site. */
public final class ElyByAuthlibInjector {
    public static final String VERSION = "1.2.8";
    private static final int BUILD_NUMBER = 56;
    private static final String RELEASE_URL = "https://authlib-injector.yushi.moe/artifact/" + BUILD_NUMBER
            + "/authlib-injector-" + VERSION + ".jar";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final long MIN_JAR_BYTES = 10000L;
    // SHA-256 published by the official authlib-injector download page for v1.2.8/build 56.
    private static final String EXPECTED_SHA256 = "9c7f4343e6c82034958ffb48c14a2cb0c85928be7283103ce17da00c6d5a7b10";

    private ElyByAuthlibInjector() { }

    public static File getJarFile() {
        String data = Tools.DIR_DATA == null ? "" : Tools.DIR_DATA;
        return new File(new File(data, "elyby"), "authlib-injector-" + VERSION + ".jar");
    }

    public static synchronized File ensureInstalled(Context context) throws IOException {
        if (Tools.DIR_DATA == null || Tools.DIR_DATA.isEmpty()) {
            throw new IOException("Launcher private storage is not initialized.");
        }
        File jar = getJarFile();
        File parent = jar.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Could not create the Ely.by authentication library folder.");
        }
        if (isValidJar(jar)) return jar;
        if (jar.exists()) jar.delete();

        File temporary = new File(parent, jar.getName() + ".download");
        if (temporary.exists()) temporary.delete();
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(RELEASE_URL);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("The official authlib-injector download returned HTTP " + status + ".");
            }
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
            }
            if (!isValidJar(temporary)) {
                temporary.delete();
                throw new IOException("The downloaded authlib-injector file failed JAR validation.");
            }
            if (!temporary.renameTo(jar)) {
                temporary.delete();
                throw new IOException("Could not install the downloaded authlib-injector file.");
            }
            return jar;
        } catch (IOException exception) {
            temporary.delete();
            throw new IOException("Could not download the official Ely.by authlib-injector library. Check your connection and try again.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        byte[] hash = digest.digest();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    public static boolean isValidJar(File file) {
        if (file == null || !file.isFile() || file.length() < MIN_JAR_BYTES) return false;
        try {
            if (!EXPECTED_SHA256.equals(sha256(file))) return false;
        } catch (Exception exception) {
            return false;
        }
        try (JarFile jar = new JarFile(file, true)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return false;
            Attributes attributes = manifest.getMainAttributes();
            String premainClass = attributes.getValue("Premain-Class");
            if (premainClass == null || premainClass.trim().isEmpty()) return false;
            // Confirm the archive contains the agent class declared by its own manifest.
            String classEntry = premainClass.replace('.', '/') + ".class";
            return jar.getJarEntry(classEntry) != null;
        } catch (Exception exception) {
            return false;
        }
    }
}
