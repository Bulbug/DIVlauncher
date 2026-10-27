# DIVlauncher UI redesign v2 - step 1: shell and Home

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

## Not done yet (next steps)
The Controls screen (separate activity) and the in-game screens are not restyled; side dialogs used by the controls editor (`dialog_side_dialog` and friends) are untouched.

## Validation
Static only: all XML parses and referenced colors, drawables and styles exist (the AppCompat parents are library styles). No Gradle build and no on-device test were possible; check the next CI build and the screens on a phone (landscape).
