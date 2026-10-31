# DIVlauncher change log

What changed in the redesign and the features added after it, newest steps last. Older one-off fix reports were removed; the git history keeps them.

Follows the approved preview (labeled icon rail; top bar with profile switcher, Play and account).

## Done in this step
- **Shell (`activity_div_launcher.xml`)**
  - Floating, rounded navigation rail with icon + label per item; the current page is highlighted.
  - Top bar on every screen: page title, profile switcher (`mc_version_spinner`), Play (`play_button`) and the account control.
  - Rail sized so five items fit on a 360 dp tall landscape screen.
- **Home (`fragment_div_home.xml`, `MainMenuFragment`)**
  - Hero card shows the selected profile name and version and stays in sync when the profile is changed in the top bar.
  - Four shortcut tiles: New profile, Browse mods, Edit controls, Settings.
  - All previous tools kept under "More": Wiki, Discord, Execute a .jar, Share log file, Open game directory.
- **`LauncherActivity`**: owns the profile switcher and Play button now (previously inside the Home fragment), reloads
  the profile list whenever a top-level screen resumes, and sets the page title per screen.
- **Styles**: larger radii (nav items 16 dp, hero 20 dp, buttons and account/profile pills 14 dp), new tile and outline
  button styles, new drawables (`rail_background`, `tile_background`, `tile_icon_background`, `outline_button_background`,
  `ic_plus`, `ic_play`) and tile accent colors.

## Step 2: Profiles screen
- Filter chips (All N plus one chip per loader found: Fabric, Forge, NeoForge, Quilt, OptiFine, Vanilla) and a "New profile" outline button; the page title now lives in the top bar.
- Cards: profile icon, name, version and downloaded state, a loader tag tinted per loader, an explicit **Select / Selected** button, edit and download buttons. The selected card has an accent outline.
- Grid column count accounts for the navigation rail.
- **Bug fix from step 1:** the profile switcher in the top bar is no longer rebuilt per screen, so it could show a stale profile after creating, editing, deleting or selecting a profile. `mcVersionSpinner.syncSelection()` now reloads the list and applies the saved/requested profile; it is called whenever a top-level screen resumes and when a card is selected. The cards in turn follow changes made from the top bar.

## Step 3: Mods screen
- Rounded search field with a search icon, and a square filter button tinted with the accent.
- Control row: type and "Installing to" pickers as rounded pills with a chevron (a small caption names the target profile), and the Installed button as an outline button.
- Mod/modpack cards: larger corners, bold title, smaller secondary text, 64 dp thumbnail.
- All view ids used by `SearchModFragment` and `ModItemAdapter` are unchanged; no Java changes were needed.
- Not restyled yet: the filter dialog, the mod version list screen and the expanded card panel (`view_mod_extended`).

## Step 4: Settings screen
- The page title now lives in the top bar; the category list (General, Video, Controls, Java, Miscellaneous, Experimental) has 48 dp rows with larger icons and bold labels, and the current page uses the rounded accent highlight.
- The version number (`BuildConfig.VERSION_NAME`, for example "Version 1.0.0-local") is shown under the category list.
- The settings panel on the right has larger rounded corners; preference lists have no divider lines and extra padding (`LauncherPreferenceFragment`).
- The individual preference rows keep the standard AndroidX look (accent-coloured category headers); no preference keys or behaviour changed.

## Step 5: Add Account chooser and account popup
- **Chooser:** a two-part panel. The title and hint are on the left; on the right are one large card per sign-in method (Microsoft, Ely.by, Offline), each with an icon chip, a title and a one-line description. It scrolls on short screens. View ids and behaviour (including the offline warning dialog) are unchanged.
- **Account popup:** rounded popup, 56 dp rows with a 32 dp avatar, a bold name and a second line with the sign-in type and an accent "Active" mark on the current account. "Add account" is accent-coloured. The closed control in the top bar shows just the avatar and name.
- **Bug fix:** the popup list calls `getView()`, which hid the delete button, so accounts could not be deleted from the popup since the move to `ListPopupWindow`. The popup now has its own adapter mode that shows the delete button; the closed spinner keeps it hidden.

## Step 6: Login screens
- **Offline and Ely.by:** same two-part panel as the chooser: a Back link, title and description on the left, the input on the right (username field and button; or status, progress and the browser sign-in button). Larger 48 dp inputs and buttons; input fields now have 14 dp corners everywhere (`field_background`).
- **Back link:** new `login_back` view calls the activity's back handler, so it behaves exactly like the system Back button.
- **Microsoft:** unchanged on purpose. It is only a full-screen WebView showing Microsoft's own sign-in page.
- No sign-in logic changed.

## Step 7: New-profile type screen and profile editor
- **New profile (type) screen:** one large card per profile type (Vanilla, OptiFine, Fabric, Quilt, Forge, Modpack, BTA), two per row, each with a loader-coloured letter chip. Section headings are quieter and the same view ids are kept.
- **Profile editor:**
  - Name, game directory, JVM arguments and the version/controls fields now use the rounded input style (48 dp, 14 dp corners) instead of underlines.
  - The Java runtime and renderer pickers use the rounded pill with a built-in chevron (the two hand-placed arrow images were removed to avoid a double arrow).
  - "Select" buttons are outline pills, field labels are small bold secondary text, Save is a 48 dp primary button and Delete is a red outline button.
  - All view ids and the Java code are unchanged.

## Step 8: Leftover mod screens
- **Filter dialog:** labels in small bold secondary text, the Minecraft version field as a rounded 48 dp box with an outline "Select" button, loader and provider pickers as rounded pills with a chevron, 48 dp Apply button.
- **Mod version list:** left-aligned bold title (no centred title and thick divider), no divider lines in the list, outline Retry button. The version rows (`item_version_profile_layout`, shared with other version lists) are 52 dp tall with 14 sp primary text.
- **Expanded mod card:** the version picker is a rounded pill with a chevron, smaller error text and a 44 dp Install button.
- All view ids are unchanged; no Java changes.

## Step 9: Dialogs and download progress
- **All dialogs at once:** the app theme (portrait and landscape) now uses a custom `alertDialogTheme` (`DivAlertDialog`): rounded 20 dp panel with a subtle border, accent confirm/neutral buttons and quieter grey cancel buttons. This covers every `AlertDialog` in the app (delete account, offline-account warning, installer confirmations, the mod filter dialog and more).
- **Download progress bar:** the bar at the bottom of the launcher that shows loader, modpack and game downloads is now a floating rounded card with primary-coloured bold text; it still expands to show the individual tasks.
- **Version selector dialog:** no divider lines in its list.
- No Java logic changed (only the progress bar's background drawable in `ProgressLayout`).

## Step 10: Whole account block clickable, rename button
- **Root cause of the unresponsive Add Account tap:** the account name view (`account_item`) was marked long-clickable (`setOnLongClickListener`, used for renaming; even `setOnLongClickListener(null)` makes a view long-clickable). A clickable child consumes the touch, so the closed account control and the popup rows never received the tap. The earlier fixes (explicit popup, direct fragment commit) worked around the symptom, not this cause.
- **Fix (`mcAccountSpinner`):** the name view is explicitly non-clickable/non-focusable in every row, so the whole account control and every popup row (including the whole "Add account" row) receive taps.
- **Rename is now visible:** offline accounts show a pencil button in the popup (the old long-press rename was removed because it caused the bug). Microsoft and Ely.by accounts cannot be renamed here because their names come from the service.
- Multiple accounts were already supported (one saved file per account; offline, Microsoft and Ely.by can be mixed) and each popup row switches the active account.

## Step 11: One style for every dropdown
- **Theme default:** `spinnerStyle` (`DivSpinner`) in the app theme (portrait and landscape): every Spinner is now a rounded pill with a chevron, with a rounded popup list, unless a layout sets its own look. This covers the per-version settings, the controller remapper mode and any other plain spinner.
- **Rows:** new shared layouts `item_spinner_selected` (value in the closed pill) and `item_spinner_dropdown` (48 dp rows, 14 sp, primary colour) replace the platform spinner layouts in the mod search/filter pickers, Fabric/Quilt installer, gamepad mapper, profile editor renderer, the Java runtime list (`RTSpinnerAdapter`) and the expanded mod card version list (`SimpleArrayAdapter`).
- **Profile switcher popup (top bar):** rounded popup background with clipped corners; its rows are the restyled version rows.
- **Fabric/Quilt installer:** underline fields replaced by the pill style.
- **Deliberately kept compact:** the in-game control editor spinners and the controller key-mapping rows keep their old padding (explicitly pinned) so those narrow panels do not change layout; they still get the rounded popup list.

## Step 12: Game profile per account
- **New `AccountProfiles`** (`net.kdt.pojavlaunch.prefs`): remembers, per launcher account, the last game profile used (stored in the launcher preferences as `accountProfile:<account>`).
- **Remember:** every profile selection (top-bar switcher, Profiles screen Select, re-sync) goes through `mcVersionSpinner.setProfileSelection()`, which now saves the choice for the current account.
- **Restore:** when the user picks another account in the account popup, `mcAccountSpinner.applyAccountProfile()` switches the current profile to the one that account used last (if it still exists) and updates the top-bar switcher. An account with nothing remembered keeps the current profile and remembers it from then on.
- **Clean-up:** deleting an account forgets its profile; renaming an offline account moves it; a deleted profile is simply ignored.
- **Popup rows:** the second line now shows the sign-in type and the remembered profile, e.g. "Offline - Survival - Active".
- Startup and list reloads do not restore anything, so the last session's account and profile stay as they were.

## Step 13: "Latest" release named after the app and version
- The rolling "nightly" pre-release is gone. A push to the default branch now publishes the newest build as the **Latest** release (not a pre-release), tagged `DIVlauncher-v<divVersion>` and titled `DIVlauncher v<divVersion>` (currently `DIVlauncher-v1.0.0`); the same release is replaced on each push until `divVersion` in `gradle.properties` is raised. The old `nightly` release and tag are deleted automatically.
- Pushing a git tag `DIVlauncher-v1.2.0` (or `v1.2.0`) publishes that version as its own Latest release.
- App version names: published builds use the clean version (`1.0.0`); other CI builds `1.0.0-dev.<commit>`; local builds `1.0.0-local`.

## Step 14: Full screen and responsive sizing
- **Real full screen:** `LauncherActivity` now hides the status and navigation bars (swipe from an edge to show them briefly) and draws behind the notch/cutout (`setFullscreen()` and `shouldIgnoreNotch()`), reusing the existing `Tools.setFullscreen` and `Tools.ignoreNotch`. Split-screen/multi-window still ignores the fullscreen request, as before.
- **Safe areas:** `setupEdgeToEdge()` pads the root view by the notch, any system bars that are showing and the on-screen keyboard (measured from the visible frame, because immersive mode does not always resize the window), so the background fills the whole screen while buttons stay clear of the notch and the keyboard never covers the username field.
- **Responsive sizes:** the shell uses dimension resources instead of fixed numbers. Defaults are for small phones; overrides exist for landscape width 640 dp and 900 dp (`values-w640dp`, `values-w900dp`: rail, profile/account pills, Play button, title size, settings category column) and for height 380 dp and 480 dp (`values-h380dp`, `values-h480dp`: top bar, nav item height, logo, tile height). The Profiles card grid now subtracts the actual rail width.
- Not covered: a maximum content width on very large tablets/unfolded foldables (content simply stretches), and portrait (the app is landscape only).

## Step 15: Controls editor
- **Menu drawer:** the right-hand menu (add button, drawer, joystick, load, save, set as default, share) is now a rounded 240 dp panel with 52 dp rows (`item_drawer_row`, no divider lines); width is the `drawer_width` dimension.
- **Hint:** the "tap to map" text became a small rounded chip; the pull-down gear handle is slightly larger (48 x 24 dp).
- **Editor panels:** `background_control_editor` (used by the side dialog, quick settings and the colour selector) is a rounded `surface_raised` panel with a border; the side dialog title is bold and its Cancel/Confirm buttons use the new dialog button styles.
- The control buttons themselves (user content), the per-button settings form fields and the colour picker internals are not changed, and no editor logic was touched.

## Step 16: Side margins in full screen (from a phone screenshot)
- The first real screenshot showed a wide empty strip at the left edge (right edge almost none). Cause: the safe-area padding used the full camera-cutout / "stable" system-bar insets.
- `setupEdgeToEdge()` now (a) ignores the stable system-bar insets before Android 11 and uses the display cutout only, and (b) caps the left/right safe inset at 8 dp. The background still fills the whole screen; a camera hole may slightly overlap the rail's outer edge but never its icons.
- If the strip is still there on a given phone, send its model: a toggle "Avoid camera cutout" in Settings is the next step.

## Step 17: Account and profile pills (from the same screenshot)
- **Account control:** `mcAccountSpinner.init()` no longer replaces the layout's rounded pill with a flat colour. The old full-width accent underline (the login progress bar) is now only drawn while a login is running or after an error, as a thin rounded 3 dp line inside the pill. The closed state is single-line with an ellipsis.
- **Profile pill:** the arrow is now the same down chevron used by the other dropdowns (the old image pointed up).
- **Borders:** pills and dropdown fields use a clearer 1.5 dp border (`pill_stroke`, #343B4D) instead of a nearly invisible one.

## Seen in the same screenshot, not fixed yet
The system font differs from the preview (fonts are not bundled yet); the "More" buttons on Home keep the old look.

## Step 18: Performance measurement (step 1 of the performance plan)
- **Settings > Experimental > Performance log** (off by default, `benchmark_log`). When on, the game is started with `-Ddiv.benchmark=<game dir>/benchmark/frames.csv`.
- **`FrameStats`** (new, in `jre_lwjgl3glfw`, called from `GLFW.glfwSwapBuffers`): counts frames and appends `epoch_ms,avg_fps,low1_fps,max_frame_ms,frames` every 5 seconds. It does nothing at all unless the system property is present.
- **`DeviceStatsLogger`** (new, started from `MainActivity`): appends `epoch_ms,battery_temp_c,battery_percent,thermal_status` to `benchmark/device.csv` every 5 seconds, so FPS drops can be matched with heat.
- **`PERFORMANCE_BASELINE.md`**: the exact test procedure (same world, same settings, 3 runs) and a results table.
- Nothing about rendering or settings is changed yet; this step only observes.

## Step 19: Performance toggle
Built as previewed, all switches off by default. See `PERFORMANCE_TOGGLE.md` for the behaviour, the mod lists and the limits.

## Step 20: One README
`UPSTREAM_PojavLauncher_README.md` was removed. Its credits and third-party license list now live in the "Credits and third-party components" section of `README.md`, and the LICENSE file is unchanged. `services/curseforge-proxy/README.md` is kept: it documents the optional CurseForge proxy service.

## Step 21: Tidy the repository
Fix reports and duplicate READMEs were removed from the project root. The guides moved to `docs/` (account authentication, Ely.by setup, mod downloader, performance baseline, Performance toggle, account data removal) and this file is the single log of what changed.

## Step 22: Build fixes found by the first real CI run
- `RTSpinnerAdapter` had two `getDropDownView` methods after the dropdown restyle (the original one was left in by mistake); the old one was removed.
- `.github/workflows/android.yml` had two `env:` blocks in the build job (release flag and CurseForge proxy URL); they are now one block.
- A GitHub AI "refactor" had also replaced the build and release steps with a plain debug build; the release flow (release variant named "DIVlauncher", signing, Latest release `DIVlauncher-v<version>`) was restored on top of those two fixes.

## Not done yet (next steps)
The in-game HUD/menus and the inner forms of the control-button settings dialog keep their old look. The Performance toggle is planned but not started.

## Validation
Static only: XML parses, referenced drawables/colors/styles/dimens exist, brackets balance. No Gradle build and no on-device test were possible; open the editor on a phone, pull the menu out and open a button's settings.
