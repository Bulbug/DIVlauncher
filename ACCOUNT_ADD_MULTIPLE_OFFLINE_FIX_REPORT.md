# DIVlauncher Add Account / Multiple Offline Accounts Fix

## Changes

- Replaced the platform Spinner's selection-dependent popup interaction with an explicit `ListPopupWindow` anchored to the account picker. Clicking the existing **Add account** row now invokes an item-click handler directly, even when that row is the current selection. This no longer relies on Android dispatching `onItemSelected` for a row that may already be selected.
- Clicking a saved account row explicitly activates that username, updates the current profile, and displays a small confirmation toast.
- Removed the separate header plus button so the existing **Add account** row in the account picker is the primary add-account action.
- Account JSON files are listed in case-insensitive sorted order, and all saved account files are included, allowing multiple distinct offline usernames to appear reliably.
- In Offline Login, entering an already-saved offline username re-selects the saved account instead of trying to overwrite it. Entering a distinct valid username creates another offline account. An account of another authentication type is not silently overwritten.
- Added a `LauncherActivity.activateSavedAccount` bridge to activate an existing saved account from the offline login screen.

## Relevant files

- `app_pojavlauncher/src/main/java/com/kdt/mcgui/mcAccountSpinner.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java`
- `app_pojavlauncher/src/main/res/layout/activity_div_launcher.xml`
- `app_pojavlauncher/src/main/res/values/strings.xml`

## Checks performed

- Android resource XML parsing: passed.
- Source assertions for custom popup item click, active-account selection, sorted multiple-account loading, and removal of the separate header button: passed.
- ZIP integrity: passed after packaging.
- Full Gradle build: **not completed**. `./gradlew :app_pojavlauncher:assembleDebug -PdivAbi=arm64-v8a --no-daemon` could not download Gradle 8.13 because `services.gradle.org` failed DNS resolution in this environment. The source changes are not claimed as APK-build-verified.

## Account behavior note

Offline accounts are keyed by Minecraft username in the existing account-file format. Therefore, each distinct username is a separate saved offline account; entering the same username selects the existing offline account rather than creating a duplicate copy of the same identity. Different saved usernames can be switched from the account picker. Offline login does not grant premium ownership or authenticated online-mode server access.
