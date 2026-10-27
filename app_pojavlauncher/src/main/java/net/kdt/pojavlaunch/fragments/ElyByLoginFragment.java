package net.kdt.pojavlaunch.fragments;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByAuthlibInjector;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByOAuthClient;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByTokenStore;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Browser-based Ely.by OAuth flow. Credentials are entered only on Ely.by's own website. */
public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";
    private static final int CALLBACK_PORT = 18763;
    private static final int CALLBACK_TIMEOUT_MS = 180000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ProgressBar progressBar;
    private TextView statusText;
    private View signInButton;
    private volatile boolean loginInProgress;
    private volatile ServerSocket callbackSocket;

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        progressBar = view.findViewById(R.id.elyby_login_progress);
        statusText = view.findViewById(R.id.elyby_login_status);
        signInButton = view.findViewById(R.id.elyby_login_button);
        signInButton.setOnClickListener(v -> beginBrowserAuthentication());
        if (!ElyByOAuthClient.isConfigured()) {
            setBusy(false, getString(R.string.auth_elyby_oauth_not_configured));
        } else {
            // Selecting Ely.by should go straight to its official browser authorization page.
            view.post(this::beginBrowserAuthentication);
        }
    }

    private void beginBrowserAuthentication() {
        if (loginInProgress) return;
        if (!ElyByTokenStore.isSupported()) {
            showError(getString(R.string.auth_elyby_secure_storage_unavailable));
            return;
        }
        if (!ElyByOAuthClient.isConfigured()) {
            showError(getString(R.string.auth_elyby_oauth_not_configured));
            return;
        }

        final String state = createState();
        final Context appContext = requireContext().getApplicationContext();
        final ServerSocket server;
        final String authorizationUrl;
        try {
            authorizationUrl = ElyByOAuthClient.getAuthorizationUrl(state);
            ServerSocket preparedServer = new ServerSocket();
            preparedServer.setReuseAddress(false);
            preparedServer.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), CALLBACK_PORT));
            preparedServer.setSoTimeout(CALLBACK_TIMEOUT_MS);
            server = preparedServer;
            callbackSocket = server;
        } catch (IOException exception) {
            showError("Could not start Ely.by's secure return connection. Close any other login attempt and try again.\n\n" + exception.getMessage());
            return;
        }

        loginInProgress = true;
        setBusy(true, getString(R.string.auth_elyby_browser_progress));
        PojavApplication.sExecutorService.execute(() -> waitForBrowserCallback(server, state, appContext));
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl));
            startActivity(browserIntent);
        } catch (ActivityNotFoundException exception) {
            closeCallbackServer(server);
            failLogin("No browser is available to open Ely.by. Install or enable a browser, then try again.");
        } catch (Exception exception) {
            closeCallbackServer(server);
            failLogin("Could not open Ely.by in your browser. Try again.");
        }
    }

    private void waitForBrowserCallback(ServerSocket server, String expectedState, Context appContext) {
        try (ServerSocket ownedServer = server; Socket client = ownedServer.accept()) {
            client.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) { /* Consume HTTP headers. */ }
            String target = "";
            if (requestLine != null) {
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2 && "GET".equalsIgnoreCase(parts[0])) target = parts[1];
            }
            Map<String, String> query = parseQuery(target);
            sendCallbackResponse(client, query.containsKey("code") && !query.containsKey("error"));
            if (query.containsKey("state") == false || !expectedState.equals(query.get("state"))) {
                throw new IOException("The Ely.by response could not be verified. Start the sign-in process again.");
            }
            if (query.containsKey("error")) {
                String error = query.get("error_description");
                if (error == null || error.trim().isEmpty()) error = query.get("error");
                throw new IOException("Ely.by sign-in was cancelled or denied" + (error == null ? "." : ": " + error));
            }
            String code = query.get("code");
            if (code == null || code.trim().isEmpty()) throw new IOException("Ely.by did not return an authorization code. Try again.");

            MinecraftAccount account = ElyByOAuthClient.exchangeCode(code);
            File existingAccountFile = new File(Tools.DIR_ACCOUNT_NEW, account.username + ".json");
            MinecraftAccount existing = MinecraftAccount.load(account.username);
            if (existingAccountFile.exists() && (existing == null || !existing.isElyByOAuth())) {
                throw new IOException("A different or unreadable saved account already uses the Minecraft name '"
                        + account.username + "'. Rename/remove it first, then sign in again.");
            }
            ElyByAuthlibInjector.ensureInstalled(appContext);
            account.save();
            mainHandler.post(() -> finishLogin(account));
        } catch (IOException exception) {
            mainHandler.post(() -> failLogin(exception.getMessage() == null
                    ? "Ely.by sign-in could not be completed. Try again." : exception.getMessage()));
        } catch (Exception exception) {
            mainHandler.post(() -> failLogin("Ely.by sign-in could not be completed. Check your connection and try again."));
        } finally {
            closeCallbackServer(server);
        }
    }

    private static Map<String, String> parseQuery(String target) {
        Map<String, String> values = new HashMap<>();
        try {
            URI uri = new URI("http://127.0.0.1" + target);
            String query = uri.getRawQuery();
            if (query == null) return values;
            for (String item : query.split("&")) {
                int equals = item.indexOf('=');
                String key = equals < 0 ? item : item.substring(0, equals);
                String value = equals < 0 ? "" : item.substring(equals + 1);
                values.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            }
        } catch (Exception ignored) { }
        return values;
    }

    private static void sendCallbackResponse(Socket client, boolean success) {
        String body = success
                ? "<!doctype html><html><meta name=\"viewport\" content=\"width=device-width\"><title>DIVlauncher</title><body style=\"font-family:sans-serif;padding:2em\"><h2>Sign-in received</h2><p>You can return to DIVlauncher now.</p></body></html>"
                : "<!doctype html><html><meta name=\"viewport\" content=\"width=device-width\"><title>DIVlauncher</title><body style=\"font-family:sans-serif;padding:2em\"><h2>Sign-in response received</h2><p>Return to DIVlauncher to continue.</p></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: "
                + bytes.length + "\r\nCache-Control: no-store\r\n\r\n";
        try {
            OutputStream output = client.getOutputStream();
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.flush();
        } catch (IOException ignored) { }
    }

    private static String createState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private void closeCallbackServer(ServerSocket server) {
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
        }
        if (callbackSocket == server) callbackSocket = null;
    }

    private void finishLogin(MinecraftAccount account) {
        if (!isAdded()) return;
        loginInProgress = false;
        setBusy(false, getString(R.string.auth_elyby_browser_success));
        ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_DONE, account);
        Tools.backToMainMenu(requireActivity());
    }

    private void failLogin(String message) {
        loginInProgress = false;
        ServerSocket server = callbackSocket;
        if (server != null) closeCallbackServer(server);
        setBusy(false, null);
        showError(message);
    }

    private void setBusy(boolean busy, String status) {
        if (!isAdded()) return;
        loginInProgress = busy;
        if (signInButton != null) signInButton.setEnabled(!busy);
        if (progressBar != null) progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (statusText != null) {
            statusText.setVisibility(status == null ? View.GONE : View.VISIBLE);
            statusText.setText(status == null ? "" : status);
        }
    }

    private void showError(String message) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.global_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        ServerSocket server = callbackSocket;
        if (server != null) closeCallbackServer(server);
        super.onDestroyView();
    }
}
