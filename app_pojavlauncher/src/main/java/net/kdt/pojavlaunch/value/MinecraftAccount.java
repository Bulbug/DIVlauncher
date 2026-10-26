package net.kdt.pojavlaunch.value;


import android.graphics.BitmapFactory;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByTokenStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.google.gson.*;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.Keep;

import org.apache.commons.io.IOUtils;

@SuppressWarnings("IOStreamConstructor")
@Keep
public class MinecraftAccount {
    public static final String TYPE_OFFLINE = "offline";
    public static final String TYPE_MICROSOFT = "microsoft";
    public static final String TYPE_ELYBY = "elyby";
    public static final String TYPE_DEMO = "demo";
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    public String accessToken = "0"; // access token; Ely.by tokens are kept in Android Keystore storage
    public String clientToken = "0"; // clientID: refresh and invalidate
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public String selectedVersion = "1.7.10";
    public boolean isMicrosoft = false;
    /** Explicit provider identifier. Null is supported for old account files. */
    public String authType;
    /** True when the saved Ely.by session must be authenticated again. */
    public boolean requiresReauthentication = false;
    public String msaRefreshToken = "0";
    public String xuid;
    public long expiresAt;
    public String skinFaceBase64;
    private Bitmap mFaceCache;
    
    void updateSkinFace(String uuid) {
        try {
            File skinFile = getSkinFaceFile(username);
            Tools.downloadFile("https://mc-heads.net/head/" + uuid + "/100", skinFile.getAbsolutePath());
            
            Log.i("SkinLoader", "Update skin face success");
        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc...
            // Simply ignore updating skin face
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    public boolean isLocal(){
        // Provider identity is authoritative. A damaged/missing Ely.by token must never
        // silently turn a premium account into an offline account.
        if (TYPE_ELYBY.equals(authType) || TYPE_MICROSOFT.equals(authType) || isMicrosoft) return false;
        return (accessToken == null || accessToken.equals("0")) && !isDemo();
    }

    public boolean isDemo(){
        // Explicit authentication type takes precedence over the legacy Demo.* username
        // convention. An offline profile must never inherit --demo just because of a
        // stale/legacy display name; a real demo account is still recognized by its type
        // or by its old untyped Demo.* username.
        if (TYPE_OFFLINE.equals(authType) || TYPE_MICROSOFT.equals(authType) || TYPE_ELYBY.equals(authType)) {
            return false;
        }
        return TYPE_DEMO.equals(authType) || (username != null && username.startsWith("Demo."));
    }

    public String getAuthType(){
        if (TYPE_OFFLINE.equals(authType) || TYPE_MICROSOFT.equals(authType)
                || TYPE_ELYBY.equals(authType) || TYPE_DEMO.equals(authType)) return authType;
        if (isMicrosoft) return TYPE_MICROSOFT;
        if (isDemo()) return TYPE_DEMO;
        if (isLocal()) return TYPE_OFFLINE;
        return "legacy";
    }

    public boolean isOffline(){
        return TYPE_OFFLINE.equals(getAuthType()) || (authType == null && isLocal() && !isDemo());
    }

    public boolean isElyBy(){
        return TYPE_ELYBY.equals(getAuthType());
    }

    public boolean hasElyBySession(){
        return isElyBy() && !requiresReauthentication && accessToken != null
                && !accessToken.isEmpty() && !"0".equals(accessToken)
                && !ElyByTokenStore.SECURE_TOKEN_MARKER.equals(accessToken);
    }

    public String getAuthTypeLabel(){
        if (isElyBy()) return hasElyBySession() ? "Ely.by · Signed in" : "Ely.by · Re-authenticate";
        if (isOffline()) return "Offline · Unverified";
        if (isMicrosoft) return "Microsoft · Signed in";
        if (isDemo()) return "Demo";
        return "Legacy account";
    }

    /** Standard Minecraft offline-mode identity for this exact username. */
    public static String offlineUuid(String username){
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static boolean isValidMinecraftUsername(String username){
        return username != null && username.matches("[A-Za-z0-9_]{3,16}");
    }
    
    public void updateSkinFace() {
        updateSkinFace(profileId);
    }
    
    public String save(String outPath) throws IOException {
        String jsonText = Tools.GLOBAL_GSON.toJson(this);
        if (isElyBy()) {
            com.google.gson.JsonObject json = new com.google.gson.JsonParser().parse(jsonText).getAsJsonObject();
            if (hasElyBySession()) {
                if (!ElyByTokenStore.storeToken(username, accessToken)) {
                    throw new IOException("Secure Ely.by token storage requires Android 6.0 or newer. The session was not saved.");
                }
            }
            // Never write Ely.by access tokens to the account JSON file.
            json.addProperty("accessToken", ElyByTokenStore.SECURE_TOKEN_MARKER);
            jsonText = Tools.GLOBAL_GSON.toJson(json);
        }
        Tools.write(outPath, jsonText);
        return username;
    }
    
    public String save() throws IOException {
        return save(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json");
    }
    
    public static MinecraftAccount parse(String content) throws JsonSyntaxException {
        return Tools.GLOBAL_GSON.fromJson(content, MinecraftAccount.class);
    }

    public static MinecraftAccount load(String name) {
        if(!accountExists(name)) return null;
        try {
            MinecraftAccount acc = parse(Tools.read(Tools.DIR_ACCOUNT_NEW + "/" + name + ".json"));
            if (acc.accessToken == null) {
                acc.accessToken = "0";
            }
            if (acc.clientToken == null) {
                acc.clientToken = "0";
            }
            if (acc.profileId == null) {
                acc.profileId = "00000000-0000-0000-0000-000000000000";
            }
            if (acc.username == null) {
                acc.username = "0";
            }
            if (acc.selectedVersion == null) {
                acc.selectedVersion = "1.7.10";
            }
            if (acc.msaRefreshToken == null) {
                acc.msaRefreshToken = "0";
            }
            if (acc.isElyBy() && ElyByTokenStore.SECURE_TOKEN_MARKER.equals(acc.accessToken)) {
                String secureToken = ElyByTokenStore.readToken(acc.username);
                if (secureToken == null || secureToken.isEmpty()) {
                    acc.accessToken = ElyByTokenStore.SECURE_TOKEN_MARKER;
                    acc.requiresReauthentication = true;
                } else {
                    acc.accessToken = secureToken;
                    acc.requiresReauthentication = false;
                }
            }
            // Migrate older offline accounts away from the all-zero UUID identity.
            if (acc.isLocal() && !acc.isDemo()) {
                if (acc.authType == null) acc.authType = TYPE_OFFLINE;
                if (acc.profileId == null || ZERO_UUID.equals(acc.profileId)) {
                    acc.profileId = offlineUuid(acc.username);
                    try { acc.save(); } catch (IOException ignored) { }
                }
            }
            return acc;
        } catch(IOException | JsonSyntaxException e) {
            Log.e(MinecraftAccount.class.getName(), "Caught an exception while loading the profile",e);
            return null;
        }
    }

    public Bitmap getSkinFace(){
        if(isLocal()) return null;

        File skinFaceFile = getSkinFaceFile(username);
        if (!skinFaceFile.exists()) {
            // Legacy version, storing the head inside the json as base 64
            if(skinFaceBase64 == null) return null;
            byte[] faceIconBytes = Base64.decode(skinFaceBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(faceIconBytes, 0, faceIconBytes.length);
        } else {
            if(mFaceCache == null) {
                mFaceCache = BitmapFactory.decodeFile(skinFaceFile.getAbsolutePath());
            }
        }

        return mFaceCache;
    }

    public static Bitmap getSkinFace(String username) {
        return BitmapFactory.decodeFile(getSkinFaceFile(username).getAbsolutePath());
    }

    private static File getSkinFaceFile(String username) {
        return new File(Tools.DIR_CACHE, username + ".png");
    }

    private static boolean accountExists(String username){
        return new File(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json").exists();
    }
}
