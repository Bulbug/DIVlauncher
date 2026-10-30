# DIVlauncher in-app mod downloader

This implementation extends the existing Android Java/Gradle application. It is not a separate launcher.

## Current capabilities in source

- Browse four Modrinth project types: mods, modpacks, resource packs, and shaders.
- Filter by project type, Minecraft version, loader, and provider.
- Select a launcher profile as the install target. The target profile's actual game version/loader are kept separate from user-edited search filters so search filters cannot make an incompatible file look compatible with that profile.
- Install individual mods, resource packs, or shader packs into the selected profile's game directory.
- Automatically resolve and install required dependencies reported by Modrinth or CurseForge, with a maximum recursion depth to stop loops.
- Verify returned hashes when available, use a temporary download file before replacing a managed installation, retain the previous file while an update is downloaded, and refuse to overwrite a different local file.
- Manage known installed files: update provider-managed files, disable/enable through a `.disabled` extension, and uninstall. The inventory is written per game directory to `.divlauncher/installed-content.json`.
- Discover local files already in `mods`, `resourcepacks`, and `shaderpacks`; these are labeled as unmanaged and are not offered an API update.
- When an old profile shares the default `.minecraft` directory, the installer gives that profile its own directory under `custom_instances/` and copies mutable data (mods, resource packs, shaders, configs, saves, screenshots, logs, options and server lists) rather than moving/deleting it.
- Modpacks create a separate new launcher profile and unique `custom_instances` directory.
- HTTP requests have timeouts and errors are logged without query strings or response bodies. Modpack URLs must be HTTPS; archive paths are checked to prevent ZIP Slip path traversal.

## Modrinth

The Android client calls the public Modrinth v2 API. The search facet types include `project_type`, `categories`, and `versions`; project types include `mod`, `modpack`, `resourcepack`, and `shader`. Project versions are requested against the selected target profile's actual compatibility metadata. See <https://docs.modrinth.com/api/> and <https://docs.modrinth.com/api/operations/searchprojects/>.

## CurseForge: requires API approval and a server proxy

The APK does **not** contain a CurseForge API key. The app only enables CurseForge when built with a public HTTPS proxy URL ending in `/v1`. CurseForge API access is gated by an application/approval process, and project distribution preferences must be respected. See <https://support.curseforge.com/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key> and <https://support.curseforge.com/support/solutions/articles/9000207877>.

The read-only Node.js proxy lives in `services/curseforge-proxy/`. Set `CURSEFORGE_API_KEY` only as a server-side environment secret, deploy the service behind HTTPS, configure access and rate limits at the edge, and then build the Android app with the proxy URL:

```bash
./gradlew :app_pojavlauncher:assembleDebug -PdivAbi=arm64-v8a -PcurseforgeProxyBaseUrl=https://YOUR_HOST/v1
```

Alternatively set the public proxy URL in `CURSEFORGE_PROXY_BASE_URL` in the build environment. This environment variable is the public URL, **not the API key**. If no valid URL is provided, the app uses Modrinth and shows a message that CurseForge is not configured.

Local proxy smoke check (no CurseForge key required):

```bash
cd services/curseforge-proxy
node server.js
```

Then visit `http://127.0.0.1:8080/health`. API routes return 503 until the approved server key is configured.

## Instance isolation

`SearchModFragment` offers an explicit target-instance selector. The install handler re-reads the selected profile from `LauncherProfiles`, resolves its game directory, and installs there. If multiple profiles resolve to the same directory, `InstanceDirectoryResolver` creates an isolated folder and updates `launcher_profiles.json`; it copies supported mutable folders/files to preserve the old shared data.

The default installation folders are:

- mods -> `<instance>/mods`
- resource packs -> `<instance>/resourcepacks`
- shaders -> `<instance>/shaderpacks`

Modpacks are separate instances rather than entries in an existing instance's content inventory.

## Known limitations

- The full Gradle/Android build and live provider downloads must still be tested on a configured build machine and device. The build wrapper in the current execution environment could not download Gradle 8.13 because `services.gradle.org` could not be resolved.
- CurseForge is unavailable until an application is approved, a server-side key is configured, the proxy is deployed with HTTPS/rate limiting, and the app is rebuilt with that proxy URL.
- The existing loader-installation architecture supports Forge, Fabric and Quilt for automatic modpack setup. NeoForge modpack setup is not implemented in the upstream loader installer; modpack archives requiring unsupported loaders are rejected rather than silently installed incorrectly.
- Mods require a compatible loader/version in the selected instance. Vanilla or unknown profile versions are not treated as a valid mod-loader target.
- Installed-mod tracking is per instance. Modpack management is via the separate profile the modpack creates; it is not treated as an ordinary mod file.
