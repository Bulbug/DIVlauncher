# DIVlauncher Windows Update 2

This source update extends the initial vanilla file installer with a first-pass launch workflow.

## Included
- The main action switches between **Install** and **Play** based on whether the selected vanilla version's metadata and client JAR are present.
- Installer fetches Windows native library classifiers in addition to ordinary library artifacts.
- Asset indexes are saved under their metadata-provided IDs.
- Client logging configuration is downloaded when Mojang metadata provides it.
- Launching uses an existing Java installation from `JAVA_HOME` or `PATH`.
- Java's major version is checked against Mojang's `javaVersion` metadata.
- Native libraries are extracted from the selected version's native archives.
- JVM and game arguments are built from Mojang version metadata and common placeholders.
- Minecraft is started as a child process; stdout/stderr are redirected to `.minecraft/logs/launcher-<version>.log`.
- Invalid previously downloaded files can be replaced on Windows after a verified replacement has been downloaded.
- The frontend and backend command list is kept in sync.

## Not included yet
- Automatic Java runtime download/selection.
- Microsoft authentication or online-mode access.
- Fabric/Forge/NeoForge/Quilt installation and inherited version metadata.
- Detailed live download progress, cancellation, or full process lifecycle UI.
- Comprehensive compatibility testing across all historical Minecraft versions.

## Verify on Windows
From the `desktop` folder:

```powershell
cargo test -p divcore
```

Then from `desktop/src-tauri`:

```powershell
cargo tauri dev
```

Create/select a vanilla profile and click **Install**. After it completes, add an offline account and click **Play**. Check the launcher toast for errors and inspect `%APPDATA%\com.divlauncher.desktop\.minecraft\logs` for the per-version launcher log.

This update has passed JavaScript syntax checks and static frontend/backend command-reference checks in the available environment. Rust compilation and runtime launching still require verification on a Windows development machine.
