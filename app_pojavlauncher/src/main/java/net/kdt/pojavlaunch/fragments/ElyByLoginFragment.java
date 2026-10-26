package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByAuthClient;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByAuthlibInjector;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByTokenStore;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.IOException;

public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText loginEditText;
    private EditText passwordEditText;
    private ProgressBar progressBar;
    private TextView statusText;
    private View signInButton;
    private boolean loginInProgress;

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        loginEditText = view.findViewById(R.id.elyby_login_name);
        passwordEditText = view.findViewById(R.id.elyby_login_password);
        progressBar = view.findViewById(R.id.elyby_login_progress);
        statusText = view.findViewById(R.id.elyby_login_status);
        signInButton = view.findViewById(R.id.elyby_login_button);
        signInButton.setOnClickListener(v -> beginAuthentication(
                loginEditText.getText().toString().trim(),
                passwordEditText.getText().toString(), null));
    }

    private void beginAuthentication(String login, String password, String verificationCode) {
        if (loginInProgress) return;
        if (login.trim().isEmpty() || password.isEmpty()) {
            showError("Enter your Ely.by username/email and password.");
            return;
        }
        if (!ElyByTokenStore.isSupported()) {
            showError(getString(R.string.auth_elyby_secure_storage_unavailable));
            return;
        }

        final Context appContext = requireContext().getApplicationContext();
        loginInProgress = true;
        setBusy(true, getString(R.string.auth_elyby_progress));

        PojavApplication.sExecutorService.execute(() -> {
            try {
                MinecraftAccount account = ElyByAuthClient.authenticate(appContext, login, password, verificationCode);
                File existingAccountFile = new File(Tools.DIR_ACCOUNT_NEW, account.username + ".json");
                MinecraftAccount existing = MinecraftAccount.load(account.username);
                if (existingAccountFile.exists() && (existing == null || !existing.isElyBy())) {
                    throw new IOException("A different or unreadable saved account already uses the Minecraft name '"
                            + account.username + "'. Rename/remove it first, then sign in again.");
                }
                mainHandler.post(() -> setBusy(true, getString(R.string.auth_elyby_prepare_library)));
                ElyByAuthlibInjector.ensureInstalled(appContext);
                // save() encrypts the token with Android Keystore and writes only a marker to account JSON.
                account.save();
                mainHandler.post(() -> finishLogin(account));
            } catch (ElyByAuthClient.AuthException exception) {
                mainHandler.post(() -> {
                    if (exception.isTwoFactorRequired() && verificationCode == null) {
                        requestVerificationCode(login, password);
                    } else {
                        failLogin(exception.getMessage());
                    }
                });
            } catch (IOException exception) {
                String message = exception.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "Could not prepare Ely.by authentication securely. Check your connection and try again.";
                }
                final String errorMessage = message;
                mainHandler.post(() -> failLogin(errorMessage));
            } catch (Exception exception) {
                mainHandler.post(() -> failLogin("Ely.by sign-in could not be completed. Check your connection and try again."));
            }
        });
    }

    private void requestVerificationCode(String login, String password) {
        loginInProgress = false;
        setBusy(false, null);
        EditText codeInput = new EditText(requireContext());
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        codeInput.setHint("6-digit verification code");
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        codeInput.setPadding(padding, padding / 2, padding, padding / 2);

        new AlertDialog.Builder(requireContext())
                .setTitle("Ely.by two-factor authentication")
                .setMessage("Enter the current verification code from your authenticator app. The code is sent only to Ely.by.")
                .setView(codeInput)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> clearPassword())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String code = codeInput.getText().toString().trim();
                    if (code.isEmpty()) {
                        showError("Enter the verification code and try again.");
                        clearPassword();
                    } else {
                        beginAuthentication(login, password, code);
                    }
                })
                .setOnCancelListener(dialog -> clearPassword())
                .show();
    }

    private void finishLogin(MinecraftAccount account) {
        loginInProgress = false;
        setBusy(false, null);
        clearPassword();
        ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_DONE, account);
        Tools.backToMainMenu(requireActivity());
    }

    private void failLogin(String message) {
        loginInProgress = false;
        setBusy(false, null);
        clearPassword();
        showError(message);
    }

    private void clearPassword() {
        if (passwordEditText != null) passwordEditText.setText("");
    }

    private void setBusy(boolean busy, String status) {
        if (!isAdded()) return;
        loginInProgress = busy;
        if (signInButton != null) signInButton.setEnabled(!busy);
        if (loginEditText != null) loginEditText.setEnabled(!busy);
        if (passwordEditText != null) passwordEditText.setEnabled(!busy);
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
}
