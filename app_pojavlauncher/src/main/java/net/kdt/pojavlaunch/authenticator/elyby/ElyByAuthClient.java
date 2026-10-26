package net.kdt.pojavlaunch.authenticator.elyby;

import android.content.Context;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

/** Client for Ely.by's documented Yggdrasil-compatible Minecraft authentication protocol. */
public final class ElyByAuthClient {
    public static final String AUTH_SERVER = "https://authserver.ely.by";
    private static final String PREFS = "elyby_auth";
    private static final String PREF_CLIENT_TOKEN = "client_token";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private ElyByAuthClient() { }

    public enum ErrorKind {
        TWO_FACTOR_REQUIRED,
        INVALID_CREDENTIALS,
        INVALID_SESSION,
        NETWORK,
        SERVICE,
        GENERAL
    }

    public static final class AuthException extends Exception {
        public final ErrorKind kind;
        public final int statusCode;
        public final String serviceError;

        AuthException(ErrorKind kind, String message, int statusCode, String serviceError) {
            super(message);
            this.kind = kind;
            this.statusCode = statusCode;
            this.serviceError = serviceError;
        }

        public boolean isTwoFactorRequired() { return kind == ErrorKind.TWO_FACTOR_REQUIRED; }
        public boolean isInvalidSession() { return kind == ErrorKind.INVALID_SESSION; }
    }

    public static MinecraftAccount authenticate(Context context, String login, String password, String verificationCode)
            throws AuthException {
        if (login == null || login.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AuthException(ErrorKind.GENERAL, "Enter your Ely.by username/email and password.", 0, null);
        }

        String clientToken = getClientToken(context);
        JSONObject body = new JSONObject();
        try {
            body.put("username", login.trim());
            body.put("password", verificationCode == null || verificationCode.isEmpty()
                    ? password : password + ":" + verificationCode.trim());
            body.put("clientToken", clientToken);
            body.put("requestUser", true);
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.GENERAL, "Could not prepare the authentication request.", 0, null);
        }

        JSONObject response = postJson("/auth/authenticate", body, false);
        try {
            String accessToken = nonEmpty(response, "accessToken");
            JSONObject profile = response.optJSONObject("selectedProfile");
            if (profile == null) {
                throw new AuthException(ErrorKind.SERVICE,
                        "Ely.by authenticated the request but did not return a selected Minecraft profile.", 200, null);
            }
            String profileId = normalizeUuid(nonEmpty(profile, "id"));
            String profileName = nonEmpty(profile, "name");
            if (!MinecraftAccount.isValidMinecraftUsername(profileName)) {
                throw new AuthException(ErrorKind.SERVICE,
                        "Ely.by returned an invalid Minecraft profile name.", 200, null);
            }

            MinecraftAccount account = new MinecraftAccount();
            account.username = profileName;
            account.profileId = profileId;
            account.accessToken = accessToken;
            account.clientToken = clientToken;
            account.authType = MinecraftAccount.TYPE_ELYBY;
            account.isMicrosoft = false;
            account.requiresReauthentication = false;
            account.msaRefreshToken = "0";
            account.xuid = null;
            return account;
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an incomplete authentication response.", 200, null);
        }
    }

    /** Confirms a stored session is still accepted by the provider. */
    public static void validate(String accessToken) throws AuthException {
        if (accessToken == null || accessToken.isEmpty()
                || ElyByTokenStore.SECURE_TOKEN_MARKER.equals(accessToken) || "0".equals(accessToken)) {
            throw new AuthException(ErrorKind.INVALID_SESSION, "The saved Ely.by session is missing. Sign in again.", 401, null);
        }
        JSONObject body = new JSONObject();
        try {
            body.put("accessToken", accessToken);
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.GENERAL, "Could not prepare the session validation request.", 0, null);
        }
        postJson("/auth/validate", body, true);
    }

    /** Best-effort remote logout using Ely.by's documented /auth/invalidate endpoint. */
    public static void invalidate(String accessToken, String clientToken) throws AuthException {
        if (accessToken == null || accessToken.isEmpty()
                || ElyByTokenStore.SECURE_TOKEN_MARKER.equals(accessToken) || "0".equals(accessToken)) return;
        JSONObject body = new JSONObject();
        try {
            body.put("accessToken", accessToken);
            body.put("clientToken", clientToken == null ? "0" : clientToken);
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.GENERAL, "Could not prepare the logout request.", 0, null);
        }
        postJson("/auth/invalidate", body, true);
    }

    /** Refreshes a validated token using Ely.by's documented /auth/refresh endpoint. */
    public static void refresh(MinecraftAccount account) throws AuthException {
        if (account == null || !account.isElyBy() || !account.hasElyBySession()) {
            throw new AuthException(ErrorKind.INVALID_SESSION, "The saved Ely.by session is missing. Sign in again.", 401, null);
        }
        JSONObject body = new JSONObject();
        try {
            body.put("accessToken", account.accessToken);
            body.put("clientToken", account.clientToken);
            body.put("requestUser", true);
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.GENERAL, "Could not prepare the session refresh request.", 0, null);
        }

        JSONObject response = postJson("/auth/refresh", body, false);
        try {
            account.accessToken = nonEmpty(response, "accessToken");
            String refreshedClientToken = response.optString("clientToken", account.clientToken);
            if (refreshedClientToken != null && !refreshedClientToken.isEmpty()) account.clientToken = refreshedClientToken;
            JSONObject selectedProfile = response.optJSONObject("selectedProfile");
            if (selectedProfile == null) {
                throw new AuthException(ErrorKind.SERVICE,
                        "Ely.by refreshed the session without returning the selected profile.", 200, null);
            }
            String refreshedName = nonEmpty(selectedProfile, "name");
            if (!MinecraftAccount.isValidMinecraftUsername(refreshedName)) {
                throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an invalid profile name.", 200, null);
            }
            account.username = refreshedName;
            account.profileId = normalizeUuid(nonEmpty(selectedProfile, "id"));
            account.requiresReauthentication = false;
        } catch (JSONException exception) {
            throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an incomplete session refresh response.", 200, null);
        }
    }

    private static String getClientToken(Context context) {
        android.content.SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(PREF_CLIENT_TOKEN, null);
        if (value == null || value.isEmpty()) {
            value = UUID.randomUUID().toString();
            preferences.edit().putString(PREF_CLIENT_TOKEN, value).apply();
        }
        return value;
    }

    private static JSONObject postJson(String path, JSONObject body, boolean allowEmpty) throws AuthException {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(AUTH_SERVER + path);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] request = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request);
                output.flush();
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = responseStream == null ? "" : readAll(responseStream);
            if (status < 200 || status >= 300) {
                throw parseHttpError(status, responseText);
            }
            // The Yggdrasil validate endpoint may report success with an empty 2xx response.
            if (responseText.trim().isEmpty() && allowEmpty) return new JSONObject();
            try {
                return new JSONObject(responseText);
            } catch (JSONException exception) {
                if (allowEmpty && responseText.trim().isEmpty()) return new JSONObject();
                throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an unreadable response. Try again later.", status, null);
            }
        } catch (AuthException exception) {
            throw exception;
        } catch (SocketTimeoutException exception) {
            throw new AuthException(ErrorKind.NETWORK, "Ely.by did not respond in time. Check your connection and try again.", 0, null);
        } catch (IOException exception) {
            throw new AuthException(ErrorKind.NETWORK, "Could not connect securely to Ely.by. Check your internet connection.", 0, null);
        } catch (Exception exception) {
            throw new AuthException(ErrorKind.GENERAL, "Could not complete Ely.by authentication.", 0, null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static AuthException parseHttpError(int status, String bodyText) {
        String error = null;
        String message = null;
        try {
            JSONObject body = new JSONObject(bodyText);
            error = body.optString("error", null);
            message = body.optString("errorMessage", null);
        } catch (JSONException ignored) { }

        String lowerMessage = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        String lowerError = error == null ? "" : error.toLowerCase(java.util.Locale.ROOT);
        if (lowerMessage.contains("two factor") || lowerMessage.contains("two-factor") || lowerMessage.contains("2fa")) {
            return new AuthException(ErrorKind.TWO_FACTOR_REQUIRED,
                    "This Ely.by account requires a two-factor verification code.", status, error);
        }
        if (status == 401 && (lowerMessage.contains("invalid credentials")
                || lowerMessage.contains("invalid email") || lowerMessage.contains("password"))) {
            return new AuthException(ErrorKind.INVALID_CREDENTIALS,
                    "Ely.by rejected the username/email or password. Check the details and try again.", status, error);
        }
        if ((status == 400 || status == 401) && (lowerMessage.contains("token")
                || lowerMessage.contains("expired") || lowerError.contains("forbiddenoperationexception"))) {
            return new AuthException(ErrorKind.INVALID_SESSION,
                    "The Ely.by session has expired or is no longer valid. Sign in to Ely.by again.", status, error);
        }
        if (status == 401 || status == 403) {
            return new AuthException(ErrorKind.INVALID_CREDENTIALS,
                    "Ely.by rejected this request. Check the account details and try again.", status, error);
        }
        if (status >= 500) {
            return new AuthException(ErrorKind.SERVICE,
                    "The Ely.by authentication service is temporarily unavailable. Try again later.", status, error);
        }
        return new AuthException(ErrorKind.GENERAL,
                message == null || message.isEmpty() ? "Ely.by authentication failed (HTTP " + status + ")." : message,
                status, error);
    }

    private static String readAll(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String nonEmpty(JSONObject json, String key) throws JSONException, AuthException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value)) {
            throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an incomplete response (missing " + key + ").", 200, null);
        }
        return value.trim();
    }

    private static String normalizeUuid(String value) throws AuthException {
        String normalized = value.replace("-", "");
        if (!normalized.matches("(?i)[0-9a-f]{32}")) {
            throw new AuthException(ErrorKind.SERVICE, "Ely.by returned an invalid Minecraft profile UUID.", 200, null);
        }
        return normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-"
                + normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-"
                + normalized.substring(20, 32);
    }
}
