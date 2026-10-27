package net.kdt.pojavlaunch.authenticator.elyby;

import android.os.Build;

import net.kdt.pojavlaunch.Tools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores Ely.by session credentials (including OAuth refresh tokens) encrypted by Android Keystore. */
public final class ElyByTokenStore {
    public static final String SECURE_TOKEN_MARKER = "__ELYBY_TOKEN_IN_ANDROID_KEYSTORE__";
    private static final String KEY_ALIAS = "divlauncher.elyby.access-token.aes";
    private static final String STORE_DIRECTORY = "elyby-secure";
    private static final int FILE_VERSION = 2;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private ElyByTokenStore() { }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static synchronized boolean storeToken(String accountName, String accessToken) throws IOException {
        if (!isSupported()) return false;
        if (Tools.DIR_DATA == null || Tools.DIR_DATA.isEmpty()) {
            throw new IOException("Launcher private storage is not initialized.");
        }
        if (accountName == null || accountName.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            throw new IOException("The account session is incomplete.");
        }

        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(accountName.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));

            File output = getTokenFile(accountName);
            File parent = output.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                throw new IOException("Could not create the secure account storage directory.");
            }
            File temporary = new File(parent, output.getName() + ".tmp");
            try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(temporary))) {
                stream.writeInt(FILE_VERSION);
                stream.writeInt(iv.length);
                stream.write(iv);
                stream.writeInt(encrypted.length);
                stream.write(encrypted);
                stream.flush();
            }
            if (output.exists() && !output.delete()) {
                temporary.delete();
                throw new IOException("Could not replace the encrypted account session.");
            }
            if (!temporary.renameTo(output)) {
                temporary.delete();
                throw new IOException("Could not save the encrypted account session.");
            }
            return true;
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not log token material or cryptographic exception details.
            throw new IOException("Secure account storage is unavailable on this device.");
        }
    }

    public static synchronized String readToken(String accountName) {
        if (!isSupported() || Tools.DIR_DATA == null || Tools.DIR_DATA.isEmpty()
                || accountName == null || accountName.isEmpty()) return null;
        File input = getTokenFile(accountName);
        if (!input.isFile()) return null;

        try (DataInputStream stream = new DataInputStream(new FileInputStream(input))) {
            int version = stream.readInt();
            int ivLength = stream.readInt();
            if (version != FILE_VERSION || ivLength != IV_LENGTH) return null;
            byte[] iv = new byte[ivLength];
            stream.readFully(iv);
            int encryptedLength = stream.readInt();
            if (encryptedLength < 1 || encryptedLength > 65536) return null;
            byte[] encrypted = new byte[encryptedLength];
            stream.readFully(encrypted);
            if (stream.read() != -1) return null;

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(accountName.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // A missing/lost Keystore key or damaged file requires the user to sign in again.
            return null;
        }
    }

    public static synchronized void deleteToken(String accountName) {
        if (Tools.DIR_DATA == null || Tools.DIR_DATA.isEmpty() || accountName == null || accountName.isEmpty()) return;
        File file = getTokenFile(accountName);
        if (file.exists()) file.delete();
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        if (temporary.exists()) temporary.delete();
    }

    private static SecretKey getExistingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key key = keyStore.getKey(KEY_ALIAS, null);
        if (!(key instanceof SecretKey)) throw new IOException("Secure key is missing.");
        return (SecretKey) key;
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        // Reflect API-23-only Keystore classes so the launcher still loads on API 21–22;
        // isSupported() prevents these reflective calls on those devices.
        Class<?> builderClass = Class.forName("android.security.keystore.KeyGenParameterSpec$Builder");
        Constructor<?> constructor = builderClass.getConstructor(String.class, int.class);
        Object builder = constructor.newInstance(KEY_ALIAS, 3 /* PURPOSE_ENCRYPT | PURPOSE_DECRYPT */);
        Method setBlockModes = builderClass.getMethod("setBlockModes", String[].class);
        setBlockModes.invoke(builder, (Object) new String[]{"GCM"});
        Method setPaddings = builderClass.getMethod("setEncryptionPaddings", String[].class);
        setPaddings.invoke(builder, (Object) new String[]{"NoPadding"});
        Method setRandomized = builderClass.getMethod("setRandomizedEncryptionRequired", boolean.class);
        setRandomized.invoke(builder, true);
        Object specification = builderClass.getMethod("build").invoke(builder);

        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init((AlgorithmParameterSpec) specification);
        return generator.generateKey();
    }

    private static File getTokenFile(String accountName) {
        return new File(new File(Tools.DIR_DATA, STORE_DIRECTORY), sha256Hex(accountName) + ".bin");
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (Exception exception) {
            // SHA-256 is required by Android; fail closed if the provider is unavailable.
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }
}
