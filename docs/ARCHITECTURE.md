# System Architecture

## Current repository shape

- `app_pojavlauncher/`: Android application and native bridge.
- `desktop/`: Windows desktop application.
  - `desktop/ui/`: HTML, CSS and JavaScript presentation layer.
  - `desktop/src-tauri/`: Tauri 2 host, Rust commands, filesystem/network/process integration.
  - `desktop/core/`: `divcore`, intended for platform-independent Rust logic and unit tests.
- `forge_installer/`, `jre_lwjgl3glfw/`, `arc_dns_injector/`: supporting Android/native components; verify each component's actual role before changing it.
- `services/curseforge-proxy/`: optional proxy service; deployment and secrets are external configuration.
- `.github/workflows/`: Android and Windows CI workflows.
- `docs/`: project documentation.

## Logical layers

1. **Presentation:** platform-specific screens, state and user input.
2. **Application/domain:** profile, version, install, launch, mod and library workflows; validate inputs and orchestrate operations.
3. **Infrastructure adapters:** filesystem, HTTP, archive handling, process execution, secure storage and OS integration.
4. **External systems:** Mojang version metadata/assets, Modrinth, CurseForge (where configured), Microsoft identity services and Java runtime distribution.

Keep business rules out of UI event handlers. Keep network and filesystem operations out of pure domain/core code where possible. Tauri commands should be narrow adapters that validate arguments and call tested Rust functions.

## Windows data flow

UI invokes a Tauri command → command validates input → domain operation resolves metadata and paths → HTTP/filesystem/process adapter performs work → structured result or typed error returns to UI → UI updates state and offers logs/recovery.

## Android data flow

Android UI and platform services interact with the launcher runtime/native bridge and Android-specific filesystem, rendering and input systems. Do not assume Windows Tauri APIs are portable to Android. Share only logic that can be isolated and tested independently.

## Design constraints

- Use app-managed directories; never trust a user-provided path without canonicalization and scope checks.
- Keep game process execution as an argument vector, not a shell command string.
- Treat remote metadata as untrusted input even when fetched over TLS.
- Downloads should use temporary files and atomic replacement after validation.
- Archive extraction must reject traversal, links and unexpected roots; enforce entry and expanded-size limits.
- Long tasks must not block UI rendering and should expose progress/cancellation where implemented.
- Use explicit versioned state schemas and migrations.

## Architectural decisions to record

Use lightweight ADRs in `docs/adr/` for decisions affecting storage, authentication, modpack formats, process lifecycle, IPC, telemetry or cross-platform sharing. Each ADR records context, decision, alternatives, security impact and consequences.
