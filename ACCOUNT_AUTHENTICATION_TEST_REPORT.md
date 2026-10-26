# Account authentication test report

## Checks run in the editing environment

- **Resource XML:** 174 XML files parsed successfully; 0 parse errors.
- **Static integration checks:** 16/16 passed. Checks covered offline UUID and username rules, official Ely.by HTTPS endpoint usage, profile UUID/name parsing, the documented 2FA suffix, validate/refresh/invalidate endpoints, token marker storage, Android Keystore + AES-GCM and account-bound encryption, authlib-injector build/checksum pinning, pre-launch validation/refresh, provider-specific agent arguments, offline account UUID assignment, remote invalidation on account removal, and refusal to persist tokens where secure storage is unsupported.
- **Offline UUID formula:** compiled and ran a standalone Java check using `UUID.nameUUIDFromBytes("OfflinePlayer:" + username)`; sample outputs were produced for `Notch`, `Steve`, and `Test_User`.
- **Gradle wrapper:** normalized the Unix wrapper's line endings and executable bit so the documented Linux/macOS command can be invoked.

## Checks not completed

- **Android build:** `./gradlew :app_pojavlauncher:assembleDebug --no-daemon` could not start Gradle because the wrapper had to download Gradle 8.13 and this environment could not resolve `services.gradle.org` (`UnknownHostException`). There is no system Gradle installation or configured Android SDK in this environment. Therefore, a successful APK build is **not verified**.
- **Live Ely.by authentication:** not run; no real Ely.by credentials or test account were provided, and the environment has no direct download/network access for the application runtime.
- **Device/emulator launch:** not run. The account flows and Java launch arguments were source-checked, not end-to-end validated on Android or against a live Minecraft server.

These results distinguish source/resource checks from a complete Android build or live authentication test. Do not treat the unrun checks as passed.


## Follow-up fixes in `DIVlauncher-account-fixes.zip`

- Add Account no longer silently does nothing when the current fragment is not `MainMenuFragment`; it opens the authentication-method selector from the shared account picker.
- After a login completes, the account picker reloads and activates the matching username rather than assuming the newly created account is the last filename returned by the filesystem.
- Explicit Offline/Microsoft/Ely.by account types are not inferred to be demo accounts from a stale `Demo.*` username; only explicit demo accounts or legacy untyped `Demo.*` profiles use demo handling.
- Offline/local profiles are allowed to download public game files and use profile, mod-loader, installer, mod-navigation, and game-directory tools without a demo-only UI gate. Public file downloads do not grant ownership or authenticate the offline player to online-mode servers.
- Follow-up static checks passed for the changed source paths and XML parsing. A full Gradle compile remains unverified because the wrapper must fetch Gradle 8.13 from `services.gradle.org`, which is not resolvable in this environment.
