# Setup and Initialization

## Repository prerequisites

- Git.
- Android: JDK 17+, Android SDK 34, NDK 25.2.9519653 and Gradle wrapper dependencies as described in the root README/workflow.
- Windows desktop: Windows 10/11 (Windows 11 recommended), Rust stable toolchain, Node.js, Tauri 2 CLI and the Windows native build prerequisites required by Tauri/WebView2.
- Network access for dependencies and Minecraft metadata/assets.

Tool versions can change; treat CI workflow files and Gradle wrapper as the canonical pinned configuration.

## Clone and inspect

```bash
git clone <your-repository-url>
cd DIVlauncher
git status --short
```

Do not paste credentials into terminal history or commit local environment files.

## Android build

```bash
./gradlew :app_pojavlauncher:assembleDebug
```

Use the exact SDK/NDK/JDK versions specified in the current workflow and root README. The build may require generated/downloaded runtime assets that CI prepares; inspect `.github/workflows/android.yml` before attempting a clean local build.

## Windows desktop development

```powershell
rustup update stable
npm install --global @tauri-apps/cli@2
cd desktop\src-tauri
tauri dev
```

For a Windows installer build:

```powershell
tauri build --bundles nsis
```

The UI can also be opened from `desktop/ui/index.html` for limited browser preview; native Tauri commands are mocked in preview and this is not a substitute for desktop testing.

## First-run behavior

Verify the app creates its state and game directories in the expected per-user locations. Start with a test profile and non-production account. Do not use a primary account for unverified authentication work.

## Environment configuration

Document required variable names and safe example values in `.env.example` only. Never add real secrets. OAuth client IDs may be public depending on provider/application type; client secrets, signing keys and tokens are not. Service proxy deployment instructions belong in `services/curseforge-proxy/README.md` and deployment-specific secret storage.

## Troubleshooting setup

- Confirm Rust target and Windows build tools are installed.
- Check WebView2 availability for the selected installer type.
- Run `cargo test -p divcore` from `desktop` for core tests.
- Check GitHub Actions for the authoritative clean-runner result.
