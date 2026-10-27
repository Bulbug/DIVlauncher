# DIVlauncher Profile Dropdown UI Fix

## Source package
Based on the latest project ZIP available in this conversation: `DIVlauncher-add-account-row-click-fix.zip`.

## Changes made
- `app_pojavlauncher/src/main/java/com/kdt/mcgui/mcVersionSpinner.java`
  - Replaced full-display `MATCH_PARENT` popup width with the width of the profile selector, so the popup is anchored under that control instead of covering the whole launcher.
  - Measures the actual adapter rows to calculate content height.
  - Caps the popup height at the smaller of 240 sdp or 55% of display height; long lists remain scrollable.
  - Enables window clipping to keep the popup within screen bounds.
- `app_pojavlauncher/src/main/res/layout/spinner_mc_version.xml`
  - Reduced vertical list padding from 14 sdp to 6 sdp.
- `app_pojavlauncher/src/main/res/layout/item_version_profile_layout.xml`
  - Reduced row height from 52 sdp to 44 sdp.
  - Reduced icon size, start padding, and icon/text spacing for a more compact landscape UI.

## Validation
- ZIP integrity: verified after packaging.
- Android XML parsing: validated all XML files in the project with Python's XML parser.
- Targeted source checks: confirmed popup width is based on `getWidth()`, measured row heights are used, the old `MATCH_PARENT` popup constructor is gone, and profile item click handling remains present.
- Full Gradle build: not verified in this environment. The previous build environment could not resolve `services.gradle.org`; no claim of a successful APK build is made.
- Device test: not performed. Install and test on the target phone to confirm visual positioning on its Android version and display scaling.
