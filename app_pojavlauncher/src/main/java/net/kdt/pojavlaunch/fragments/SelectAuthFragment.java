package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment(){
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button microsoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button elyByButton = view.findViewById(R.id.button_elyby_authentication);
        Button offlineButton = view.findViewById(R.id.button_local_authentication);

        microsoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        elyByButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), ElyByLoginFragment.class, ElyByLoginFragment.TAG, null));
        offlineButton.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_offline_warning_title)
                .setMessage(R.string.auth_offline_warning_text)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null))
                .show());
    }
}
