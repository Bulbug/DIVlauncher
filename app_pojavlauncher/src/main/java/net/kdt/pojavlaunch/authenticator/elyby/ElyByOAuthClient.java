package net.kdt.pojavlaunch.authenticator.elyby;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Ely.by's documented OAuth 2.0 flow for browser-based sign-in. */
public final class ElyByOAuthClient {
    public static final String REDIRECT_URI = "http://127.0.0.1:18763/elyby-callback";
    private static final String AUTHORIZATION_ENDPOINT = "https://account.ely.by/oauth2/v1";
    private static final String TOKEN_ENDPOINT = "https://account.ely.by/api/oauth2/v1/token";
    private static final String ACCOUNT_INFO_ENDPOINT = "https://account.ely.by/api/account/v1/info";
    private static final String SCOPES = "account_info minecraft_server_session offline_access";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private ElyByOAuthClient() { }

    public static boolean isConfigured() {
        return BuildConfig.ELYBY_OAUTH_CLIENT_ID != null && !BuildConfig.ELYBY_OAUTH_CLIENT_ID.trim().isEmpty()
                && BuildConfig.ELYBY_OAUTH_CLIENT_SECRET != null && !BuildConfig.ELYBY_OAUTH_CLIENT_SECRET.trim().isEmpty();
    }

    public static String getAuthorizationUrl(String state) throws IOException {
        if (!isConfigured()) {
            throw new IOException("Browser login is not configured yet. Register a DIVlauncher OAuth app with Ely.by, then build with ELYBY_OAUTH_CLIENT_ID and ELYBY_OAUTH_CLIENT_SECRET set. Register this exact redirect URI: " + REDIRECT_URI);
        }
        return AUTHORIZATION_ENDPOINT + "?client_id=" + enc(BuildConfig.ELYBY_OAUTH_CLIENT_ID)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&response_type=code&scope=" + enc(SCOPES)
                + "&state=" + enc(state) + "&prompt=select_account";
    }

    public static MinecraftAccount exchangeCode(String code) throws IOException {
        if (!isConfigured()) throw new IOException("Ely.by OAuth is not configured for this build.");
        JSONObject tokens = requestToken("grant_type=authorization_code"
                + "&client_id=" + enc(BuildConfig.ELYBY_OAUTH_CLIENT_ID)
                + "&client_secret=" + enc(BuildConfig.ELYBY_OAUTH_CLIENT_SECRET)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code=" + enc(code));
        String accessToken = required(tokens, "access_token");
        String refreshToken = tokens.optString("refresh_token", "");
        long expiresIn = tokens.optLong("expires_in", 0L);
        if (refreshToken.isEmpty()) throw new IOException("Ely.by did not return a refresh token. Confirm that the offline_access permission is enabled for the registered OAuth app.");

        JSONObject info = requestAccountInfo(accessToken);
        String username = required(info, "username");
        if (!MinecraftAccount.isValidMinecraftUsername(username)) {
            throw new IOException("Ely.by returned a username that is not valid for a Minecraft profile.");
        }
        String uuid = normalizeUuid(required(info, "uuid"));
        MinecraftAccount account = new MinecraftAccount();
        account.username = username;
        account.profileId = uuid;
        account.accessToken = accessToken;
        account.clientToken = UUID.randomUUID().toString();
        account.authType = MinecraftAccount.TYPE_ELYBY_OAUTH;
        account.isMicrosoft = false;
        account.requiresReauthentication = false;
        account.msaRefreshToken = "0";
        account.xuid = null;
        account.elyByOAuthRefreshToken = refreshToken;
        account.elyByOAuthExpiresAt = expiresIn > 0 ? System.currentTimeMillis() + (expiresIn * 1000L) : 0L;
        return account;
    }

    /** Refreshes expiring browser-OAuth credentials, and validates them via Ely.by account info. */
    public static void refreshAndValidate(MinecraftAccount account) throws IOException {
        if (account == null || !account.isElyByOAuth() || !account.hasElyBySession()) {
            throw new IOException("The Ely.by browser session is missing. Sign in again.");
        }
        if (account.elyByOAuthRefreshToken == null || account.elyByOAuthRefreshToken.isEmpty()) {
            throw new IOException("The saved Ely.by browser session has no refresh token. Sign in again.");
        }
        if (account.elyByOAuthExpiresAt > 0 && account.elyByOAuthExpiresAt < System.currentTimeMillis() + 120000L) {
            JSONObject tokens = requestToken("grant_type=refresh_token"
                    + "&client_id=" + enc(BuildConfig.ELYBY_OAUTH_CLIENT_ID)
                    + "&client_secret=" + enc(BuildConfig.ELYBY_OAUTH_CLIENT_SECRET)
                    + "&scope=" + enc(SCOPES)
                    + "&refresh_token=" + enc(account.elyByOAuthRefreshToken));
            account.accessToken = required(tokens, "access_token");
            String replacementRefreshToken = tokens.optString("refresh_token", "");
            if (!replacementRefreshToken.isEmpty()) account.elyByOAuthRefreshToken = replacementRefreshToken;
            long expiresIn = tokens.optLong("expires_in", 0L);
            account.elyByOAuthExpiresAt = expiresIn > 0 ? System.currentTimeMillis() + expiresIn * 1000L : 0L;
        }
        JSONObject info = requestAccountInfo(account.accessToken);
        String username = required(info, "username");
        if (!MinecraftAccount.isValidMinecraftUsername(username)) {
            throw new IOException("Ely.by returned an invalid Minecraft profile name.");
        }
        account.username = username;
        account.profileId = normalizeUuid(required(info, "uuid"));
        account.requiresReauthentication = false;
    }

    private static JSONObject requestToken(String formBody) throws IOException {
        HttpsRequest request = new HttpsRequest(TOKEN_ENDPOINT);
        return request.postForm(formBody);
    }

    private static JSONObject requestAccountInfo(String accessToken) throws IOException {
        HttpsRequest request = new HttpsRequest(ACCOUNT_INFO_ENDPOINT);
        return request.getBearer(accessToken);
    }

    private static String normalizeUuid(String value) throws IOException {
        String normalized = value.replace("-", "");
        if (!normalized.matches("(?i)[0-9a-f]{32}")) throw new IOException("Ely.by returned an invalid profile UUID.");
        normalized = normalized.toLowerCase(Locale.ROOT);
        return normalized.substring(0, 8) + "-" + normalized.substring(8, 12) + "-"
                + normalized.substring(12, 16) + "-" + normalized.substring(16, 20) + "-"
                + normalized.substring(20, 32);
    }

    private static String required(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "");
        if (value.trim().isEmpty() || "null".equals(value)) throw new IOException("Ely.by did not return the required field: " + key + ".");
        return value.trim();
    }

    private static String enc(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static final class HttpsRequest {
        private final String url;
        HttpsRequest(String url) { this.url = url; }

        JSONObject postForm(String body) throws IOException {
            return execute("POST", body, null);
        }

        JSONObject getBearer(String token) throws IOException {
            return execute("GET", null, token);
        }

        private JSONObject execute(String method, String body, String bearerToken) throws IOException {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                if (bearerToken != null) connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                        output.flush();
                    }
                }
                int status = connection.getResponseCode();
                InputStream responseStream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                String responseText = responseStream == null ? "" : readAll(responseStream);
                JSONObject result;
                try {
                    result = new JSONObject(responseText);
                } catch (JSONException exception) {
                    throw new IOException(status >= 200 && status < 300
                            ? "Ely.by returned an unreadable response. Try again later."
                            : "Ely.by rejected the browser-login request (HTTP " + status + ").");
                }
                if (status < 200 || status >= 300) {
                    String message = result.optString("error_description", result.optString("errorMessage", ""));
                    if (message.trim().isEmpty()) message = "HTTP " + status;
                    throw new IOException("Ely.by OAuth failed: " + message);
                }
                return result;
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException("Could not connect to Ely.by securely. Check the connection and try again.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
