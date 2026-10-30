# DIVlauncher account authentication

This project keeps the existing PojavLauncher-based Java/Gradle architecture and extends its account picker with Offline, Microsoft, and Ely.by profiles.

## How it works

- **Offline:** accepts only Java Edition usernames matching `^[A-Za-z0-9_]{3,16}$`. It creates the standard Java offline UUID from `OfflinePlayer:<exact username>`, saves the profile beside existing accounts, and explicitly activates the new profile after login. A saved offline profile can download the public client/assets/libraries needed for local play (an internet connection is still needed for files that are not cached). Offline mode does not verify account ownership and cannot authenticate with online-mode servers.
- **Ely.by:** uses Ely.by's documented HTTPS Yggdrasil-compatible `/auth/authenticate`, `/auth/validate`, `/auth/refresh`, and `/auth/invalidate` endpoints at `https://authserver.ely.by`. Two-factor authentication follows the documented `password:token` retry. A returned profile UUID/name—not user-entered data—is used for the account.
- **Storage:** Ely.by access tokens are encrypted with AES-GCM using an Android Keystore key (Android 6.0/API 23+). The account JSON contains a marker, not the token. Passwords and TOTP codes are not saved. On devices without supported secure storage, Ely.by login is refused rather than storing the token in plain text.
- **Launch:** before Play, Ely.by sessions are validated and refreshed. The active profile's returned UUID, username and access token populate the existing Minecraft launch arguments; `-javaagent:<private-app-path>/authlib-injector-1.2.8.jar=ely.by` is added only for Ely.by accounts. The existing Minecraft JAR is not modified. Offline and Microsoft accounts do not receive the Ely.by agent.
- **Injector verification:** the launcher downloads the pinned v1.2.8/build 56 JAR from the official authlib-injector distribution site and verifies its SHA-256 (`9c7f4343e6c82034958ffb48c14a2cb0c85928be7283103ce17da00c6d5a7b10`) before installing it. The hash is published by the official authlib-injector download page.
- **Account naming:** this project's legacy account storage uses the Minecraft profile name as its JSON filename. Therefore a saved account with the same Minecraft name must be renamed/removed before a different provider account using that name is added; the app rejects that collision instead of overwriting an unrelated profile.

## Adding accounts

1. Open the account picker and choose **Add an account**.
2. Choose **Offline account**, enter a valid 3–16 character username, and confirm the offline-mode warning; or choose **Ely.by account** and sign in at the official Ely.by authentication endpoint.
3. For Ely.by 2FA, enter the current verification code when prompted. The session is saved only after both authentication and the injector integrity check succeed.
4. The launcher automatically activates the account that just completed login. Select a different saved account in the account picker to switch accounts. The check mark identifies the active entry. Long-press an offline account in the dropdown to rename it; removal requires confirmation.

The Add Account menu opens from the shared account picker even if another launcher screen is currently visible. Explicit `offline` accounts are treated as local identities even if stale token fields are present, are not treated as legacy `Demo.*` profiles, and do not receive the Minecraft `--demo` launch argument. Offline accounts can use the launcher's profile, mod, installer, and game-directory tools. Public client files can be downloaded without a signed-in account. Offline identities remain unverified and cannot authenticate with online-mode servers or grant a Minecraft license.

## Build

Use JDK 21, Android SDK platform 34, NDK 25.2.9519653, and a working internet connection for Gradle dependency downloads as described in the project README.

Linux/macOS:

```bash
./gradlew :app_pojavlauncher:assembleDebug
```

Windows:

```bat
gradlew.bat :app_pojavlauncher:assembleDebug
```

## Validation notes

The account flow was source-checked, resource XML was parsed, and the offline UUID algorithm was checked against Java's `UUID.nameUUIDFromBytes` behavior. Full Android compilation and device-side Ely.by/Minecraft testing require the Gradle distribution, Android SDK/NDK, a device/emulator, network access, and a real Ely.by test account; those live checks are not claimed by this source package.

Official references:

- https://docs.ely.by/en/minecraft-auth.html
- https://docs.ely.by/en/authlib-injector.html
- https://github.com/yushijinhun/authlib-injector/releases/tag/v1.2.8
- https://authlib-injector.yushi.moe/artifact/56/authlib-injector-1.2.8.jar
- https://authlib-injector.yushi.moe/
