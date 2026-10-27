# DIVlauncher AdapterView crash fix and follow-up fixes

## 1. The crash

`mcAccountSpinner` extends `AppCompatSpinner`, which is an `AdapterView`. `AdapterView.setOnClickListener()`
always throws a `RuntimeException`, and the previous "Add Account tap" fix called it while the view was being
inflated, so the launcher crashed on start.

**Fix:** no click listener is registered. Taps go through the overridden `performClick()` (direct open of the
sign-in chooser when there are no saved accounts, explicit `ListPopupWindow` otherwise). A search of the
whole source tree found no other `setOnClickListener` call on an `AdapterView`; `mcVersionSpinner` extends
`ExtendedTextView` and is not affected.

## 2. Follow-up fixes in this package

| # | Problem | Fix |
|---|---------|-----|
| 1 | Tapping an account whose saved file is corrupted deleted the *previously selected* (healthy) account, because `pickAccount()` called `removeCurrentAccount()` before the selection had moved. | `pickAccount()` now calls `removeAccount(position)` for the account that was actually tapped. |
| 2 | `ADD_ACCOUNT_TAP_FIX_REPORT.md` described the click-listener approach that crashes. | Rewritten to match the code; this report added. |
| 3 | After pressing delete in the account popup, the old list stayed on screen. | The popup is tracked in `mAccountPopup`, dismissed when delete is pressed/confirmed, and dismissed in `onDetachedFromWindow()`. The old manual `onDetachedFromWindow()` call was removed. |
| 4 | README said the NDK was avoided, but `ndkBuild` still compiles the launcher's own native bridge (`pojavexec`, `exithook`, `linkerhook`, `pojavexec_awt`, `awt_xawt`). | README corrected; the workflow now installs NDK 25.2.9519653, build-tools 34.0.0 and platform 34 explicitly. Third-party libs (LWJGL, OpenAL, gl4es, OSMesa...) remain prebuilt. |
| 5 | CI downloaded LTW from `releases/latest` with no verification. | Pinned to tag `7703f1c` with a SHA-256 check (`5640b2ae...322a`). |
| 6 | The Ely.by OAuth client secret is compiled into the APK. | `app_pojavlauncher/build.gradle` now fails any release/bundle build that has `ELYBY_OAUTH_CLIENT_SECRET` set (override: `-PelybyAllowSecretInRelease=true`). A server-side code exchange is still needed for a public release. |
| 7 | CI used Gradle 8.11 while the wrapper requests 8.13. | Workflow set to 8.13. |

## 3. Validation

- All XML files under `app_pojavlauncher/src/main` and the workflow YAML parse correctly.
- Resource ids, strings and `MinecraftAccount` methods used by `mcAccountSpinner` exist.
- Bracket balance checked on every edited Java/Gradle file.
- **Not verified:** a full Gradle/APK build (the authoring environment cannot download Gradle), the new
  `build.gradle` guard, the NDK install step, and on-device behaviour of the tap, delete and corrupted-file
  paths. Run the GitHub Actions build and test on a phone before relying on any of this.

## 4. Release build naming and version (follow-up)

- CI now always builds the **release** variant, so the installed app is named "DIVlauncher" (the
  "DIVlauncher Debug" label from `src/debug` only appears in local `assembleDebug` builds). APKs are named
  `DIVlauncher-<abi>-release.apk`.
- Without the `DIV_*` signing secrets the release APK is signed with the shared test key so it still installs.
  Moving to your own key later requires users to uninstall and reinstall once.
- `versionName` no longer uses the upstream `gladiolus-...` scheme or git commands:
  tag `v1.2.0` -> `1.2.0`; CI nightly -> `<divVersion>-nightly.<commit>`; local -> `<divVersion>-local`.
  The base `divVersion` is in `gradle.properties` (currently `1.0.0`).
- `build.gradle`: release assets now depend on the same helper jars as debug, and lint no longer checks
  release builds (it could not block the build, but this avoids a slow, noisy first release run).
- **Not verified:** no Gradle build was possible in the authoring environment; the first release-variant CI
  run is the real test.
