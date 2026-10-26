# DIVlauncher UI Redesign Report

## Goal

Reduce the overly enlarged feel and move the launcher UI toward a more modern, minimalist dark style while preserving existing account, profile, navigation, and launch behavior.

## UI changes

- Replaced responsive SDP sizing with standard `dp` dimensions on key launcher screens to prevent controls from scaling up on wide/landscape configurations.
- Reduced the navigation rail width, navigation row height, and icon/label sizes.
- Reduced launcher home card padding, hero artwork, quick-action button height, and spacing.
- Standardized the theme's default text family to sans-serif and set a consistent 13sp default; landscape theme no longer uses the larger 14ssp default.
- Changed `MineButton` to use a predictable 13sp font size instead of SSP scaling.
- Reduced profile cards and account selector rows while keeping the profile creation and account selection controls available.
- Constrained authentication panels to reasonable maximum widths on landscape displays.
- Standardized dark surfaces and corner radii, with a restrained periwinkle accent.
- Capped the account picker popup at a responsive 380dp instead of allowing it to span almost the full landscape launcher width.

## Main files modified

- `app_pojavlauncher/src/main/java/com/kdt/mcgui/MineButton.java`
- `app_pojavlauncher/src/main/java/com/kdt/mcgui/mcAccountSpinner.java`
- `app_pojavlauncher/src/main/res/values/colors.xml`
- `app_pojavlauncher/src/main/res/values/styles.xml`
- `app_pojavlauncher/src/main/res/values-land/styles.xml`
- `app_pojavlauncher/src/main/res/layout/activity_div_launcher.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_div_home.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_profiles.xml`
- `app_pojavlauncher/src/main/res/layout/item_profile_card.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_profile_type.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_select_auth_method.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_local_login.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_elyby_login.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_settings.xml`
- `app_pojavlauncher/src/main/res/layout/item_settings_category.xml`
- `app_pojavlauncher/src/main/res/layout/item_minecraft_account.xml`
- Core shape resources: `hero_background.xml`, `panel_background.xml`, `field_background.xml`, `menu_tile_background.xml`, `mine_button_background.xml`, `profile_card_background.xml`, `nav_item_background.xml`

## Checks performed

- All 174 Android resource XML files parsed successfully with Python's XML parser.
- 16 critical view IDs used by launcher navigation, play button, account screen, profile creation, and Ely.by login remain present.
- Static source checks confirmed the explicit account picker click handling and capped popup sizing remain in place.
- ZIP archive integrity was checked after packaging.

## Not verified

- No Android APK build was completed in this environment because the Gradle 8.13 distribution is not available locally and network access to the Gradle distribution host is unavailable.
- The redesigned UI has not been rendered on a device or emulator here. Screen appearance and touch target comfort still require testing on the target Android device.
- Ely.by login, Microsoft login, offline login, and Minecraft launch behavior were not re-tested as part of this visual-only redesign.

## Build command

```bash
./gradlew :app_pojavlauncher:assembleDebug -PdivAbi=arm64-v8a
```
