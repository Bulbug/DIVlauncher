# DIVlauncher for Windows (desktop/)

A Windows 10 and 11 launcher for Minecraft: Java Edition, built with [Tauri](https://tauri.app) (a Rust core with a web-style window). It lives in the `desktop` folder of this repository and is released together with the Android app.

## Current Windows support status
- The window follows DIVlauncher's desktop design: side rail, profile switcher, Play/Install action, account switcher, Home, Profiles, Mods and Settings.
- **Offline accounts:** add, switch, rename and remove, with the correct Minecraft offline UUID. These do not authenticate with Microsoft or online-mode servers.
- **Profiles:** create, select, rename and delete using Mojang's version manifest, including latest release and optional snapshots/old versions.
- **Vanilla install/launch:** downloads version metadata, client JAR, Windows libraries/natives and assets; launches using an existing compatible Java runtime and writes a per-version log.
- **Settings:** maximum Java heap size, persisted with account/profile state.
- **Modrinth file management:** search by Minecraft version and loader, download mod JARs, and list/remove files in `.minecraft/mods`. Loader installation and launching are not implemented yet.
- Accounts, profiles and settings are saved to `%APPDATA%\com.divlauncher.desktop\state.json`.

## Layout
| Folder | What it is |
|--------|------------|
| `core/` | `divcore`, plain Rust logic with unit tests (version list parsing, usernames, offline UUID). No network, no files. |
| `src-tauri/` | the Tauri app: commands the window can call (`app_info`, `list_versions`, `offline_uuid`, `load_state`, `save_state`), settings and installer configuration |
| `ui/` | the window itself (HTML, CSS, JavaScript, bundled Manrope and Sora fonts under the SIL Open Font License) |

## Build on GitHub
The `windows` job in `.github/workflows/android.yml` runs on `windows-latest`: it runs the `divcore` tests, builds the app and an NSIS installer with the same version number as the Android release, and uploads
- `DIVlauncher-<version>-windows-x64-setup.exe` (installs for the current user, no administrator needed; downloads the WebView2 runtime if Windows does not have it)
- `DIVlauncher-<version>-windows-x64-portable.exe` (single file, needs the WebView2 runtime that Windows 11 includes)
- a `.sha256` file for each

The release job attaches them to the same release as the APKs. See `VERSIONING.md` for how the version is chosen.

## Build on your own PC (optional)
Install [Rust](https://rustup.rs) and Node.js, then:
```
npm install --global @tauri-apps/cli@2
cd desktop/src-tauri
tauri dev        # opens the window
tauri build --bundles nsis
```
To check the window in a normal browser, open `desktop/ui/index.html`; it uses stand-ins for the Rust commands.

## Known limits
- The installer is not code-signed, so Windows SmartScreen may show an "unknown publisher" warning (More info, then Run anyway). Some antivirus programs are cautious with new, unsigned launchers.
- Windows 10 and 11 are supported. Microsoft ended Windows 10 support in October 2025, so Windows 11 is the main target.
- Microsoft account sign-in needs an app registration and is planned for a later phase; offline accounts work now.

## Next phases
1. Package/manage a compatible Java runtime automatically and add granular download progress.
2. Verify Windows runtime behavior across supported vanilla versions; improve process tracking and crash diagnostics.
3. Microsoft sign-in and secure session storage.
4. Install and launch Fabric, Forge, NeoForge and Quilt profiles; then expand modpack support beyond the initial Modrinth file browser.
5. Microsoft sign-in and secure session storage.
6. Auto-update, uninstaller polish, and release hardening.

## Windows support update: vanilla installer and initial launch

The Windows UI's primary action switches between **Install** and **Play**. It resolves `Latest release` to Mojang's current release and invokes `install_version` in the Tauri backend. The installer downloads the selected version JSON, client JAR, applicable Windows library artifacts/native classifiers, and asset index/object files into the launcher's app-data `.minecraft` directory. Existing files are reused when their hashes match; new downloads are checked against published SHA-1 values when available, written to a temporary file, and then renamed into place. Download URLs must use HTTPS and library artifact paths are checked for traversal.

The **Play** action is an initial vanilla launcher implementation. It uses a Java runtime already installed on the PC (`JAVA_HOME` or `PATH`), checks the Java major version against the version metadata, extracts native libraries, constructs metadata-driven JVM/game arguments, and launches Minecraft with output redirected to a per-version log under `.minecraft/logs/`. If no compatible Java runtime is available, the launcher reports an actionable error instead of installing Java automatically.

**Known limits:** automatic Java runtime acquisition, live download progress, Microsoft authentication, inherited/mod-loader profiles, advanced process lifecycle controls, and some legacy metadata edge cases remain future work. Offline accounts are suitable only for offline/local play and do not authenticate ownership or permit access to servers requiring authenticated accounts. A successful file download alone is not proof that a game launches correctly.

### Verifying this update
From `desktop`, run `cargo test -p divcore`. Then from `desktop/src-tauri`, run `cargo tauri dev`. Select or create a vanilla profile and press **Install**. After installation, add/select an offline account and press **Play**. For `Latest release`, the app resolves Mojang's current release. The first install can download a substantial amount of data. Check `%APPDATA%\\com.divlauncher.desktop\\.minecraft` for the resulting `versions`, `libraries`, `assets`, and `logs` folders. Build and runtime verification must be performed on Windows; static inspection in another environment is not a substitute.


## Windows support update 3: launcher settings and Modrinth file management

This update adds maximum heap configuration (saved with launcher state and passed as `-Xmx`), profile and offline-account renaming, a Modrinth search interface filtered by Minecraft version and loader, mod JAR download into `.minecraft/mods`, and a local list/remove view for downloaded JAR files. The UI clearly warns that mod-loader profiles are not yet installed/launched by the Windows backend. Search/download requests use HTTPS and downloads verify SHA-1 when Modrinth supplies one.

Validation performed for this update: `node --check desktop/ui/app.js` passes, and the Tauri configuration/capabilities JSON parse successfully. Rust/Cargo is not installed in the editing environment, so backend compilation and Windows runtime behavior have not been verified here. The Windows GitHub Actions build must pass before treating this update as build-verified.

### Backup and restore

The Profiles screen supports exporting and importing a versioned JSON backup of launcher state. This backup includes profile/account metadata and settings, not installed game files, mods, Java runtimes, or credentials. Validate the file source before importing because import replaces the current saved state.

### Library manager (Update 7)
The Library screen provides a safe, category-scoped view of worlds, screenshots, resource packs, shader packs, and logs. Users can open the selected folder in Explorer and delete individual entries after confirmation. This is file management only; it does not install shader/resource packs or browse inside world directories.

### Backup restore validation

The JSON launcher backup importer validates profile entries, account metadata, and settings structure before applying a backup. Backups are not encrypted and do not include game files or authentication credentials. See `WINDOWS-UPDATE-8.md` for validation and Windows test commands.

### Advanced JVM options

Windows Settings supports optional additional JVM arguments, one per line. Launcher-managed heap/classpath and Java agent flags are rejected. See `WINDOWS-UPDATE-9.md` for validation and test steps.

### Managed Java runtimes (Update 10)

Settings can install Eclipse Temurin JRE 8, 17 or 21 for Windows x64. The launcher stores managed runtimes in its application data directory and prefers a suitable managed runtime when launching vanilla Minecraft. This feature requires an internet connection and needs Windows runtime testing. ARM64 and automatic selection/download of every possible Java major are not yet supported.

### Modpack and world ZIP import/export (Update 11)

The Mods screen can import/export a generic DIVlauncher ZIP containing `mods`, `config`, `resourcepacks`, and `shaderpacks`. The Library screen can import/export Minecraft world ZIPs; imports require `level.dat` and refuse to overwrite an existing world. Archive paths, symbolic links, entry counts, and expanded size are checked. This is not yet full Modrinth `.mrpack` or CurseForge pack installation and does not install a mod loader. See `WINDOWS-UPDATE-11.md`.
