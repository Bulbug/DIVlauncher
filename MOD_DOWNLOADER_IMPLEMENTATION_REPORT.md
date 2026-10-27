# Mod downloader implementation and validation report

## Source updates

- Added provider-neutral filtering, target profile/version/loader metadata, and four project types (mod, modpack, resourcepack, shader).
- Added the Modrinth API provider for search, compatible version lookup, downloads, required-dependency recursion, and Modrinth `.mrpack` installation through the existing modpack pipeline.
- Added a CurseForge provider that only uses an HTTPS server-side proxy; it checks the official distribution toggle, fetches download URLs from the documented API endpoint when absent, and resolves required dependencies.
- Added the CurseForge Node.js read-only proxy under `services/curseforge-proxy/`; private API credentials remain in the server environment, not the Android app.
- Added per-instance installed-content inventory and install/update/enable/disable/remove controls for mods, resource packs, and shaders.
- Added per-instance directory isolation when launcher profiles would otherwise share the default `.minecraft` directory.
- Added safe temporary-file updates, checksum verification where available, HTTPS-only modpack downloads, safe archive destination checks, and Zip Slip protection in archive extraction.
- Updated the mod browser UI to select project type and target instance and to filter the provider and loader.

## Validation actually run

- Android XML parse: all 174 files parsed successfully.
- Default Android resource reference scan: all referenced strings resolved except `application_package`, which is intentionally generated with Gradle `resValue`.
- Java source brace-balance check: no imbalances found.
- Node.js syntax validation: `node --check services/curseforge-proxy/server.js` passed.
- Archive integrity: ZIP validation is run during packaging.
- Gradle build attempt: failed before project compilation because the wrapper could not resolve `services.gradle.org` to download Gradle 8.13 (`UnknownHostException`). No APK build success is claimed.
- No live Modrinth or CurseForge installation was run; CurseForge requires approved API access and a deployed proxy.

## Known limitations

- Automatic modpack loader setup uses the launcher's existing Forge/Fabric/Quilt support. NeoForge modpack installation is not available in the existing loader installer and is rejected.
- A compatible loader must already be installed in a target instance before individual mods can be installed.
- CurseForge support requires external approval, a privately configured server API key and public HTTPS proxy URL. Those deployment credentials are not included.

## Main files changed

- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/SearchModFragment.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/ProfileTypeSelectFragment.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/ModItemAdapter.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ApiHandler.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/CommonApi.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/CurseforgeApi.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/InstalledContentStore.java` (new)
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/InstanceDirectoryResolver.java` (new)
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModDownloader.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModpackApi.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModpackInstaller.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModrinthApi.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ProjectFileInstaller.java` (new)
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/models/ModDetail.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/models/ModItem.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/models/ModSource.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/models/SearchFilters.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/ZipUtils.java`
- `app_pojavlauncher/src/main/res/layout/dialog_mod_filters.xml`
- `app_pojavlauncher/src/main/res/layout/fragment_mod_search.xml`
- `app_pojavlauncher/src/main/res/values/strings.xml`
- `app_pojavlauncher/build.gradle`
- `.github/workflows/android.yml`
- `services/curseforge-proxy/server.js` and `services/curseforge-proxy/README.md` (new)

Additional validation: the proxy was started locally with no API key; `/health` returned HTTP 200 and `/v1/mods/search` returned HTTP 503 as designed, without an embedded key. `node --check` passed.
