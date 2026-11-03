# DIVlauncher — Project Requirements Document

**Status:** Living draft · **Product:** DIVlauncher · **Platforms:** Android and Windows 10/11 (Windows 11 primary target)

## 1. Purpose

Provide a user-friendly launcher for Minecraft: Java Edition on supported Android devices and Windows PCs. The desktop application should reuse product concepts where appropriate while respecting platform-specific input, filesystem, graphics and security constraints.

## 2. Goals

- Discover, install and launch supported Minecraft Java versions.
- Manage profiles, game files, Java runtimes and supported modifications.
- Provide clear progress, actionable errors and useful diagnostics.
- Protect account credentials and user data.
- Produce reproducible Android and Windows builds through CI.
- Maintain transparent licensing, attribution and compatibility information.

## 3. Out of scope / constraints

- DIVlauncher is not a Minecraft account provider and must not bypass Microsoft authentication or ownership requirements.
- Offline/local accounts do not authenticate users for online-mode servers.
- Feature parity does not mean identical UI or identical platform internals.
- Third-party modpack formats and services must be integrated under their terms and API policies.
- No guarantee of compatibility with every Minecraft version, mod, graphics driver or device.

## 4. User classes

- **Player:** installs and launches vanilla or supported modded profiles.
- **Power user:** manages Java versions, JVM arguments, mods, packs and game directories.
- **Maintainer:** configures integrations, reviews issues and publishes releases.
- **Contributor:** develops, tests and documents changes.

## 5. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-01 | Discover supported Minecraft versions and show release metadata. | P0 |
| FR-02 | Create, select, rename and delete launcher profiles safely. | P0 |
| FR-03 | Download and verify required version metadata, client, libraries and assets. | P0 |
| FR-04 | Launch a supported installed version with a compatible Java runtime. | P0 |
| FR-05 | Show install/launch failures and retain useful logs. | P0 |
| FR-06 | Support secure Microsoft sign-in for online play. | P0 for online accounts; Windows implementation pending |
| FR-07 | Manage compatible Java runtimes and select the required major version. | P1; Windows partial |
| FR-08 | Install and launch supported Fabric/Forge/NeoForge/Quilt profiles. | P1; pending Windows validation/implementation |
| FR-09 | Browse, install and remove mods from supported catalogs. | P1; Windows partial |
| FR-10 | Import/export supported modpacks and worlds. | P1; generic ZIP partial, standard formats pending |
| FR-11 | Manage resource packs, shaders, screenshots, worlds and logs. | P1; Windows file management partial |
| FR-12 | Configure memory, resolution, Java arguments and game directory. | P1; Windows partial |
| FR-13 | Back up and restore launcher metadata with validation. | P2 |
| FR-14 | Provide accessibility, localization and platform-appropriate input. | P2 |

## 6. Non-functional requirements

- **Security:** HTTPS for network requests; validate remote metadata and archive paths; avoid shell interpolation; keep tokens in OS-protected storage.
- **Reliability:** atomic writes, resumable or retryable downloads where practical, integrity checks, bounded extraction and recoverable errors.
- **Performance:** avoid blocking the UI thread; report long-running work; reuse verified downloads.
- **Compatibility:** document supported OS, CPU architecture, Java versions and known limitations.
- **Maintainability:** separate UI, domain logic, platform adapters and external-service clients.
- **Privacy:** collect only necessary data; disclose diagnostics/telemetry; provide account-data removal guidance.
- **Accessibility:** keyboard navigation on desktop, touch-friendly controls on mobile, readable contrast and scalable text.

## 7. Acceptance criteria

A release candidate must build in CI for each advertised platform, pass automated tests, install cleanly, launch at least one explicitly supported vanilla version on a clean test environment, and pass account/security checks. Mod-loader and catalog claims require separate compatibility tests. Release notes must identify known limitations.

## 8. Open decisions

Supported Windows minimum build, ARM64 support, exact OAuth provider/application registration, token storage implementation, modpack format support, telemetry policy and formal supported Minecraft-version matrix require maintainer approval.
