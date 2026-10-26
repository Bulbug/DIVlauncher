package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.widget.Toast;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;

public class LocalLoginFragment extends Fragment {
    public static final String TAG = "LOCAL_LOGIN_FRAGMENT";

    private EditText mUsernameEditText;

    public LocalLoginFragment(){
        super(R.layout.fragment_local_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.login_edit_email);
        view.findViewById(R.id.login_button).setOnClickListener(v -> {
            String username = mUsernameEditText.getText().toString().trim();
            Context context = v.getContext();
            if (!MinecraftAccount.isValidMinecraftUsername(username)) {
                Tools.dialog(context, context.getString(R.string.local_login_bad_username_title), context.getString(R.string.local_login_bad_username_text));
                return;
            }

            File existingFile = new File(Tools.DIR_ACCOUNT_NEW, username + ".json");
            if (existingFile.exists()) {
                MinecraftAccount existing = MinecraftAccount.load(username);
                if (existing != null && existing.isOffline()) {
                    if (requireActivity() instanceof LauncherActivity
                            && ((LauncherActivity) requireActivity()).activateSavedAccount(username)) {
                        Toast.makeText(context, "Selected saved offline account: " + username, Toast.LENGTH_SHORT).show();
                        Tools.backToMainMenu(requireActivity());
                    } else {
                        Tools.dialog(context, context.getString(R.string.local_login_account_exists_title), context.getString(R.string.local_login_account_exists_text));
                    }
                } else {
                    Tools.dialog(context, context.getString(R.string.local_login_account_exists_title), context.getString(R.string.local_login_account_exists_text));
                }
                return;
            }

            // Each distinct valid username is saved as its own offline account JSON, so users
            // can keep and switch between multiple offline accounts. We never overwrite an
            // existing Microsoft/Ely.by/offline identity silently.
            ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{username, ""});
            Tools.backToMainMenu(requireActivity());
        });
    }

}
