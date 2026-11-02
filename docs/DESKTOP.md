# DIVlauncher for Windows (desktop/)

A Windows 10 and 11 launcher for Minecraft: Java Edition, built with [Tauri](https://tauri.app) (a Rust core with a web-style window). It lives in the `desktop` folder of this repository and is released together with the Android app.

## What is in this build (Phase 1, foundation)
- The window in the DIVlauncher design: side rail, top bar with profile switcher, Play and account, Home, Profiles, Mods (placeholder) and Settings.
- **Offline accounts** (add, switch, remove) with the correct Minecraft offline UUID.
- **Profiles** (create, select, delete) using Mojang's real version list, with "Latest release" and an option to show snapshots and old versions.
- Accounts and profiles are saved to `%APPDATA%\com.divlauncher.desktop\state.json`.
- **Not yet:** the Play button only explains that launching comes next. Downloading the game, a bundled Java and starting Minecraft are the next step.

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
4. Fabric, Forge, NeoForge, Quilt, mod browser and modpacks.
5. Auto-update, uninstaller polish, and release hardening.

## Windows support update: vanilla installer and initial launch

The Windows UI's primary action switches between **Install** and **Play**. It resolves `Latest release` to Mojang's current release and invokes `install_version` in the Tauri backend. The installer downloads the selected version JSON, client JAR, applicable Windows library artifacts/native classifiers, and asset index/object files into the launcher's app-data `.minecraft` directory. Existing files are reused when their hashes match; new downloads are checked against published SHA-1 values when available, written to a temporary file, and then renamed into place. Download URLs must use HTTPS and library artifact paths are checked for traversal.

The **Play** action is an initial vanilla launcher implementation. It uses a Java runtime already installed on the PC (`JAVA_HOME` or `PATH`), checks the Java major version against the version metadata, extracts native libraries, constructs metadata-driven JVM/game arguments, and launches Minecraft with output redirected to a per-version log under `.minecraft/logs/`. If no compatible Java runtime is available, the launcher reports an actionable error instead of installing Java automatically.

**Known limits:** automatic Java runtime acquisition, live download progress, Microsoft authentication, inherited/mod-loader profiles, advanced process lifecycle controls, and some legacy metadata edge cases remain future work. Offline accounts are suitable only for offline/local play and do not authenticate ownership or permit access to servers requiring authenticated accounts. A successful file download alone is not proof that a game launches correctly.

### Verifying this update
From `desktop`, run `cargo test -p divcore`. Then from `desktop/src-tauri`, run `cargo tauri dev`. Select or create a vanilla profile and press **Install**. After installation, add/select an offline account and press **Play**. For `Latest release`, the app resolves Mojang's current release. The first install can download a substantial amount of data. Check `%APPDATA%\\com.divlauncher.desktop\\.minecraft` for the resulting `versions`, `libraries`, `assets`, and `logs` folders. Build and runtime verification must be performed on Windows; static inspection in another environment is not a substitute.
