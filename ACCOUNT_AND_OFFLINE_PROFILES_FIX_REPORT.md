# Add Account and offline profile fixes

## Changes made

- Added a dedicated plus button beside the account picker in `activity_div_launcher.xml`. It opens the existing authentication-method screen directly, avoiding reliance on Android Spinner selection callbacks.
- Kept the Add Account row in the dropdown and made it restore the active account selection before navigation.
- Removed demo-only UI gates from Mods navigation, profile-type installer choices (Fabric/Forge/Quilt/OptiFine/modpacks), game-directory access, and custom JAR/mod installer actions. Offline accounts can use those launcher tools. A genuine demo profile still receives Minecraft's demo launch argument; this does not grant license ownership.
- Preserved the earlier `java.io.File` import fix in `ElyByLoginFragment.java`.

## Checks performed

- All 174 Android resource XML files parsed successfully.
- Static assertions passed for the header button, direct navigation handler, and offline profile/mod-install access paths.
- Full Gradle build was attempted with `:app_pojavlauncher:assembleDebug -PdivAbi=arm64-v8a --offline`, but the Gradle wrapper still attempted to fetch Gradle 8.13 and failed because this environment cannot resolve `services.gradle.org`.

## Not yet verified

A successful APK build and hands-on testing on an Android device have not been completed. These changes should be built and tested in the repository's CI environment before release.
