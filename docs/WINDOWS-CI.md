# Windows desktop CI

The `DIVlauncher Windows Desktop` workflow runs on pushes, pull requests, and manual dispatches.

It performs two jobs on a Windows GitHub Actions runner:

1. Checks the desktop UI JavaScript syntax, runs the `divcore` Rust tests, and checks the Tauri backend.
2. Builds the NSIS Windows installer and uploads the `.exe` as the `DIVlauncher-Windows-Installer` workflow artifact.

## Getting the installer

1. Push the changes to GitHub.
2. Open the repository's **Actions** tab and select **DIVlauncher Windows Desktop**.
3. Open the successful workflow run.
4. Download **DIVlauncher-Windows-Installer** from its Artifacts section.

Pull-request builds validate and package the app too, but artifacts are only available from the workflow run and are not automatically published as GitHub Releases.

## Notes

- This workflow is a build and test gate; a successful build does not replace testing Minecraft installation and launch on a real Windows machine.
- The build uses the Windows runner's installed Microsoft C++ build tools and WebView2 environment.
- The workflow intentionally does not publish releases or request repository write permissions.

## Java runtime diagnostics (Update 5)

Open **Settings → Java runtime** to see whether DIVlauncher can locate Java, the detected major version, and the executable path. Use **Check Java again** after installing or changing Java. This is a diagnostic only: the launcher does not yet download or manage Java runtimes automatically. Install the Java version required by the Minecraft version you intend to run (modern releases may require Java 21; many older releases use Java 17 or earlier).
