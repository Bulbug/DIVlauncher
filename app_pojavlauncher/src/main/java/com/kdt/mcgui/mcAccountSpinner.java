package com.kdt.mcgui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.res.ResourcesCompat;


import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByTokenStore;
import net.kdt.pojavlaunch.authenticator.elyby.ElyByAuthClient;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.authenticator.microsoft.PresentedException;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import fr.spse.extended_view.ExtendedTextView;

public class mcAccountSpinner extends AppCompatSpinner {
    public mcAccountSpinner(@NonNull Context context) {
        this(context, null);
    }
    public mcAccountSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private final List<String> mAccountList = new ArrayList<>(2);
    private MinecraftAccount mSelectecAccount = null;
    private final Set<String> mElyByRefreshInProgress = Collections.synchronizedSet(new HashSet<>());

    /* The account popup currently on screen, if any, so it can be closed after delete/detach */
    private ListPopupWindow mAccountPopup;

    /* Display the head of the current profile, here just to allow bitmap recycling */
    private BitmapDrawable mHeadDrawable;

    /* Current animator to for the login bar, is swapped when changing step */
    private ObjectAnimator mLoginBarAnimator;
    private float mLoginBarWidth = -1;

    /* Paint used to display the bottom bar, to show the login progress. */
    private final Paint mLoginBarPaint = new Paint();

    /* When a login is performed in the background, we need to know where we are */
    private final static int MAX_LOGIN_STEP = 5;
    private int mLoginStep = 0;

    /* Login listeners */
    private final ProgressListener mProgressListener = step -> {
        // Animate the login bar, cosmetic purposes only
        mLoginStep = step;
        if(mLoginBarAnimator != null){
            mLoginBarAnimator.cancel();
            mLoginBarAnimator.setFloatValues( mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }else{
            mLoginBarAnimator = ObjectAnimator.ofFloat(this, "LoginBarWidth", mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }
        mLoginBarAnimator.start();
    };

    private final DoneListener mDoneListener = account -> {
        Toast.makeText(getContext(), R.string.main_login_done, Toast.LENGTH_SHORT).show();
        // Always select the account that just completed login. File.list() order is not
        // guaranteed, so deriving the selected position from list size can leave an old
        // Demo account active even after an offline account was created successfully.
        reloadAccounts(true, 0, account.username);
        invalidate();
    };

    private final ErrorListener mErrorListener = errorMessage -> {
        mLoginBarPaint.setColor(Color.RED);
        Context context = getContext();
        if(errorMessage instanceof PresentedException) {
            PresentedException exception = (PresentedException) errorMessage;
            Throwable cause = exception.getCause();
            if(cause == null) {
                Tools.dialog(context, context.getString(R.string.global_error), exception.toString(context));
            }else {
                Tools.showError(context, exception.toString(context), exception.getCause());
            }
        }else {
            Tools.showError(getContext(), errorMessage);
        }
        invalidate();
    };

    /* Triggered when we need to do microsoft login */
    private final ExtraListener<Uri> mMicrosoftLoginListener = (key, value) -> {
        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        new MicrosoftBackgroundLogin(false, value.getQueryParameter("code")).performLogin(
                mProgressListener, mDoneListener, mErrorListener);
        return false;
    };

    /* Triggered when we need to perform mojang login */
    private final ExtraListener<String[]> mMojangLoginListener = (key, value) -> {
        if(value[1].isEmpty()){ // Test mode
            MinecraftAccount account = new MinecraftAccount();
            account.username = value[0];
            account.authType = MinecraftAccount.TYPE_OFFLINE;
            account.accessToken = "0";
            account.clientToken = "0";
            account.profileId = MinecraftAccount.offlineUuid(account.username);
            account.isMicrosoft = false;
            try {
                account.save();
                mDoneListener.onLoginDone(account);
            } catch (IOException e){
                Tools.showError(getContext(), "Could not save the offline account.", e);
            }
        }
        return false;
    };


    private final ExtraListener<MinecraftAccount> mElyByLoginListener = (key, account) -> {
        if (account != null && account.isElyBy() && account.hasElyBySession()) {
            mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
            mDoneListener.onLoginDone(account);
        }
        return false;
    };

    private void init(){
        // Set visual properties
        setBackgroundColor(getResources().getColor(R.color.background_status_bar));
        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        mLoginBarPaint.setStrokeWidth(getResources().getDimensionPixelOffset(R.dimen._2sdp));

        // Set behavior
        // AdapterView (including Spinner) forbids setOnClickListener and throws during
        // XML inflation. Route taps through performClick() below instead.
        reloadAccounts(true, 0);

        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, mMojangLoginListener);
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, mMicrosoftLoginListener);
        ExtraCore.addExtraListener(ExtraConstants.ELYBY_LOGIN_DONE, mElyByLoginListener);
    }


    /**
     * Use an explicit popup list instead of Spinner's selection callback for the action row.
     * Android does not dispatch onItemSelected when the user taps the already-selected row;
     * this is especially visible when no accounts exist and "Add account" is position zero.
     * A ListPopupWindow reports an item click independently of the current selection.
     */
    @Override
    public boolean performClick() {
        // When there are no saved accounts, the collapsed spinner itself displays
        // "Add account". Treat tapping that visible row as the action directly;
        // otherwise the user has to open a popup containing only the same row.
        if (mAccountList.size() <= 1) {
            // There is only the Add account action row, so launch the account flow
            // directly. AdapterView cannot use setOnClickListener/callOnClick here.
            openAddAccountScreen();
            return true;
        }

        // Do not depend on AppCompatSpinner's internal adapter wrapper implementing
        // ListAdapter. Some AppCompat versions wrap SpinnerAdapter differently, which
        // caused the old code to fall back to Spinner's selection-only callback. That
        // callback does not fire when the already-selected Add account row is tapped.
        dismissAccountPopup();
        final ListPopupWindow popup = new ListPopupWindow(getContext());
        mAccountPopup = popup;
        popup.setOnDismissListener(() -> { if (mAccountPopup == popup) mAccountPopup = null; });
        popup.setAnchorView(this);
        String[] accountArray = mAccountList.toArray(new String[0]);
        AccountAdapter popupAdapter = new AccountAdapter(
                getContext(), R.layout.item_minecraft_account, accountArray);
        popupAdapter.setDropDownViewResource(R.layout.item_minecraft_account);
        popup.setAdapter(popupAdapter);
        popup.setModal(true);
        float densityScale = getResources().getDisplayMetrics().density;
        int maxPopupWidth = (int) (380f * densityScale);
        int minimumPopupWidth = (int) (240f * densityScale);
        int anchorWidth = Math.max(getWidth(), minimumPopupWidth);
        popup.setWidth(Math.min(anchorWidth, maxPopupWidth));
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setOnItemClickListener((parent, rowView, position, id) -> {
            popup.dismiss();
            handleAccountRowClick(position);
        });
        popup.show();
        return true;
    }

    private void dismissAccountPopup() {
        if (mAccountPopup != null) {
            mAccountPopup.dismiss();
            mAccountPopup = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Avoid a leaked popup window if the launcher screen is torn down while it is open
        dismissAccountPopup();
        super.onDetachedFromWindow();
    }

    private void openAddAccountScreen() {
        // Prefer a direct activity call for user taps. The old event-only route could
        // silently do nothing if its listener was not registered/available at that moment.
        Context context = getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LauncherActivity) {
            ((LauncherActivity) context).openAddAccountScreen();
        } else {
            // Keep compatibility for any non-standard host context.
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
        }
    }

    private void handleAccountRowClick(int position) {
        if (position == 0) {
            // The action row is never allowed to become the active profile.
            openAddAccountScreen();
            return;
        }
        if (position < 0 || position >= mAccountList.size()) return;

        pickAccount(position);
        if (mSelectecAccount != null) {
            performLogin(mSelectecAccount);
            Toast.makeText(getContext(), "Selected account: " + mSelectecAccount.username, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mLoginBarWidth == -1) mLoginBarWidth = getWidth(); // Initial draw

        float bottom = getHeight() - mLoginBarPaint.getStrokeWidth()/2;
        canvas.drawLine(0, bottom, mLoginBarWidth, bottom, mLoginBarPaint);
    }

    public void removeCurrentAccount(){
        removeAccount(getSelectedItemPosition());
    }

    private void removeAccount(int position) {
        if(position <= 0 || position >= mAccountList.size()) return;
        String accountName = mAccountList.get(position);
        boolean wasActive = accountName.equals(PojavProfile.getCurrentProfileName(getContext()));
        MinecraftAccount removedAccount = MinecraftAccount.load(accountName);
        if (removedAccount != null && removedAccount.isElyBy() && !removedAccount.isElyByOAuth() && removedAccount.hasElyBySession()) {
            final String accessToken = removedAccount.accessToken;
            final String clientToken = removedAccount.clientToken;
            // A failed network logout must never prevent local removal. Tokens are not logged.
            PojavApplication.sExecutorService.execute(() -> {
                try { ElyByAuthClient.invalidate(accessToken, clientToken); } catch (Exception ignored) { }
            });
        }
        ElyByTokenStore.deleteToken(accountName);
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, accountName + ".json");
        if(accountFile.exists()) accountFile.delete();
        if (wasActive) PojavProfile.setCurrentProfile(getContext(), null);
        reloadAccounts(true, 0);
    }

    /** Refresh account labels after the active session was updated. */
    public void refreshAccounts() {
        reloadAccounts(true, 0);
    }

    /** Switch to an existing account by its saved username and refresh the displayed account list. */
    public boolean activateAccount(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        reloadAccounts(true, 0, username);
        return mSelectecAccount != null && username.equals(mSelectecAccount.username);
    }

    @Keep
    public void setLoginBarWidth(float value){
        mLoginBarWidth = value;
        invalidate(); // Need to redraw each time this is changed
    }

    /** Allows checking whether we have an online account */
    public boolean isAccountOnline(){
        return mSelectecAccount != null && !mSelectecAccount.isLocal();
    }

    public MinecraftAccount getSelectedAccount(){
        return mSelectecAccount;
    }

    public int getLoginState(){
        return mLoginStep;
    }

    public boolean isLoginDone(){
        return mLoginStep >= MAX_LOGIN_STEP;
    }

    /**
     * Reload the spinner, from memory or from scratch. A default account can be selected
     * @param fromFiles Whether we use files as the source of truth
     * @param overridePosition Force the spinner to be at this position, if not 0
     */
    private void reloadAccounts(boolean fromFiles, int overridePosition){
        reloadAccounts(fromFiles, overridePosition, null);
    }

    private void reloadAccounts(boolean fromFiles, int overridePosition, @Nullable String preferredUsername){
        if(fromFiles){
            mAccountList.clear();

            mAccountList.add(getContext().getString(R.string.main_add_account));
            File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
            if(accountFolder.exists()){
                String[] fileNames = accountFolder.list();
                List<String> savedNames = new ArrayList<>();
                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        if (fileName.endsWith(".json") && fileName.length() > 5) {
                            savedNames.add(fileName.substring(0, fileName.length() - 5));
                        }
                    }
                }
                savedNames.sort(String.CASE_INSENSITIVE_ORDER);
                mAccountList.addAll(savedNames);
            }
        }

        if (preferredUsername != null) {
            int preferredPosition = mAccountList.indexOf(preferredUsername);
            if (preferredPosition > 0) overridePosition = preferredPosition;
        }

        String[] accountArray = mAccountList.toArray(new String[0]);
        AccountAdapter accountAdapter = new AccountAdapter(getContext(), R.layout.item_minecraft_account, accountArray);
        accountAdapter.setDropDownViewResource(R.layout.item_minecraft_account);
        setAdapter(accountAdapter);

        // Pick what's available, might just be the the add account "button"
        pickAccount(overridePosition == 0 ? -1 : overridePosition);
        if(mSelectecAccount != null)
            performLogin(mSelectecAccount);

    }

    private void performLogin(MinecraftAccount minecraftAccount){
        if (minecraftAccount == null || minecraftAccount.isLocal()) return;

        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        if(minecraftAccount.isMicrosoft){
            if(System.currentTimeMillis() > minecraftAccount.expiresAt){
                // Perform login only if needed
                new MicrosoftBackgroundLogin(true, minecraftAccount.msaRefreshToken)
                        .performLogin(mProgressListener, mDoneListener, mErrorListener);
            }
            return;
        }
        // Ely.by refresh/validation is performed when Play is pressed so a selection change
        // never causes a network request or races the launch process.
        if (minecraftAccount.isElyBy() && !minecraftAccount.hasElyBySession()) {
            Toast.makeText(getContext(), R.string.auth_elyby_session_expired, Toast.LENGTH_LONG).show();
        }
    }

    /** Pick the selected account, the one in settings if 0 is passed */
    private void pickAccount(int position){
        MinecraftAccount selectedAccount;
        if(position != -1){
            PojavProfile.setCurrentProfile(getContext(), mAccountList.get(position));
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), mAccountList.get(position));


            // WORKAROUND
            // Account file corrupted due to previous versions having improper encoding
            if (selectedAccount == null){
                // Remove the account that was actually tapped. removeCurrentAccount() would use
                // getSelectedItemPosition(), which is still the PREVIOUS selection here (the popup
                // flow calls pickAccount() before setSelection()) and would delete a healthy account.
                removeAccount(position);
                pickAccount(-1);
                setSelection(0);
                return;
            }
            setSelection(position);
        }else {
            // Restore the active profile; if it was removed or corrupted, select the first saved account.
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), null);
            int spinnerPosition = selectedAccount == null ? -1 : mAccountList.indexOf(selectedAccount.username);
            if (spinnerPosition < 1 && mAccountList.size() > 1) {
                spinnerPosition = 1;
                String fallbackName = mAccountList.get(spinnerPosition);
                PojavProfile.setCurrentProfile(getContext(), fallbackName);
                selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), fallbackName);
            }
            if (spinnerPosition < 0) spinnerPosition = 0;
            setSelection(spinnerPosition, false);
        }

        mSelectecAccount = selectedAccount;
        setImageFromSelectedAccount();
    }

    private void showRenameOfflineDialog(Context context, String oldName) {
        MinecraftAccount account = MinecraftAccount.load(oldName);
        if (account == null || !account.isOffline()) {
            Toast.makeText(context, "Only offline profiles can be renamed here.", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText nameInput = new EditText(context);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nameInput.setHint(R.string.auth_rename_offline_hint);
        nameInput.setText(oldName);
        nameInput.setSelection(nameInput.getText().length());
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding / 2, padding, padding / 2);

        new AlertDialog.Builder(context)
                .setTitle(R.string.auth_rename_offline)
                .setView(nameInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    if (!MinecraftAccount.isValidMinecraftUsername(newName)
                            || (!oldName.equals(newName) && new File(Tools.DIR_ACCOUNT_NEW, newName + ".json").exists())) {
                        Toast.makeText(context, R.string.auth_rename_offline_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (oldName.equals(newName)) return;

                    boolean wasActive = oldName.equals(PojavProfile.getCurrentProfileName(context));
                    account.username = newName;
                    account.authType = MinecraftAccount.TYPE_OFFLINE;
                    account.profileId = MinecraftAccount.offlineUuid(newName);
                    account.accessToken = "0";
                    try {
                        account.save();
                        File oldFile = new File(Tools.DIR_ACCOUNT_NEW, oldName + ".json");
                        if (oldFile.exists()) oldFile.delete();
                        if (wasActive) PojavProfile.setCurrentProfile(context, newName);
                        reloadAccounts(true, 0);
                    } catch (IOException exception) {
                        Tools.showError(context, "Could not rename the offline profile.", exception);
                    }
                })
                .show();
    }

    @Deprecated()
    /* Legacy behavior, update the head image manually for the selected account */
    private void setImageFromSelectedAccount(){
        BitmapDrawable oldBitmapDrawable = mHeadDrawable;

        if(mSelectecAccount != null){
            View layout = getSelectedView();
            if(layout != null){
                ExtendedTextView view = layout.findViewById(R.id.account_item);
                Bitmap bitmap = mSelectecAccount.getSkinFace();
                if(bitmap != null) {
                    mHeadDrawable = new BitmapDrawable(getResources(), bitmap);
                    view.setCompoundDrawables(mHeadDrawable, null, null, null);
                }else{
                    view.setCompoundDrawables(null, null, null, null);
                }
                view.postProcessDrawables();
            }
        }

        if(oldBitmapDrawable != null){
            oldBitmapDrawable.getBitmap().recycle();
        }
    }

    private class AccountAdapter extends ArrayAdapter<String> {

        private final HashMap<String, Drawable> mImageCache = new HashMap<>();
        public AccountAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if(convertView == null){
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_minecraft_account, parent, false);
            }

            ExtendedTextView textview = convertView.findViewById(R.id.account_item);
            ImageView deleteButton = convertView.findViewById(R.id.delete_account_button);
            String username = super.getItem(position);

            // Handle the "Add account section"
            if(position == 0) {
                textview.setText(username);
                textview.setCompoundDrawables(ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_add, null), null, null, null);
                textview.setOnLongClickListener(null);
                deleteButton.setVisibility(View.GONE);
            }
            else {
                MinecraftAccount account = MinecraftAccount.load(username);
                String typeLabel = account == null ? "Account data unavailable" : account.getAuthTypeLabel();
                String activeMark = position == getSelectedItemPosition() ? "✓ " : "";
                textview.setText(activeMark + username + " · " + typeLabel);
                textview.setOnLongClickListener(v -> {
                    showRenameOfflineDialog(getContext(), username);
                    return true;
                });
                Drawable accountHead = mImageCache.get(username);
                if (accountHead == null){
                    accountHead = new BitmapDrawable(parent.getResources(), MinecraftAccount.getSkinFace(username));
                    mImageCache.put(username, accountHead);
                }
                textview.setCompoundDrawables(accountHead, null, null, null);

                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    dismissAccountPopup();
                    showDeleteDialog(getContext(), position);
                });
            }
            return convertView;
        }



        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = getDropDownView(position, convertView, parent);
            view.findViewById(R.id.delete_account_button).setVisibility(View.GONE);
            return view;
        }

        private void showDeleteDialog(Context context, int position) {
            new AlertDialog.Builder(context)
                    .setMessage(R.string.warning_remove_account)
                    .setPositiveButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.global_delete, (dialog, which) -> {
                        dismissAccountPopup();
                        removeAccount(position);
                    })
                    .show();
        }
    }



}
