# DIVlauncher Add Account tap fix report

## Changes made

- `app_pojavlauncher/src/main/java/com/kdt/mcgui/mcAccountSpinner.java`
  - Registered an explicit click listener for the Add Account action.
  - When the account list contains only the Add Account row, `performClick()` now calls the registered click listener directly instead of relying on Spinner selection/popup behavior.
  - Kept the custom account popup for accounts that already exist; clicking the Add Account row in that popup still routes to the same action.
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java`
  - Removed the silent early return when FragmentManager state is saved.
  - Resolves the fragment container at tap time if the cached view is not set.
  - Opens `SelectAuthFragment` using a transaction that allows state loss during lifecycle transitions, and logs/displays a message if the container is missing or navigation throws.

## Validation

- Parsed all 179 XML files under `app_pojavlauncher/src/main`: passed.
- ZIP integrity: checked after packaging.
- Gradle build: not completed. The wrapper tried to download Gradle 8.13 from `services.gradle.org`, but this environment has no network access (`UnknownHostException`).
- Runtime/UI test: not performed. Therefore the tap fix is source-level and is not claimed as verified on a device.
