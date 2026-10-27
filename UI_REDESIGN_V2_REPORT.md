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

## Not done yet (next steps)
Controls screen (separate activity), Add Account chooser, account popup, mod filter dialog, mod version list, expanded mod card panel and the profile editor/type screens are not restyled.

## Validation
Static only: all XML parses, referenced ids/strings exist, brackets balance. No Gradle build and no on-device test were possible; check the next CI build and the screens on a phone (landscape).
