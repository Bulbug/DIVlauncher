# Application API and Tauri Command Reference

The Windows UI communicates with the Rust host through Tauri 2 `invoke` commands. This is an internal desktop IPC API, not a public HTTP API. The command list below is based on the current `desktop/src-tauri/src/main.rs`; confirm signatures and registered handlers in source before depending on them.

## Commands observed in the Windows backend

| Command | Purpose |
|---|---|
| `app_info` | Return application/platform information |
| `list_versions` | Fetch/return Minecraft version manifest data |
| `offline_uuid` | Compute the Minecraft offline UUID for a username |
| `load_state` | Load persisted launcher state |
| `save_state` | Validate/persist launcher state JSON |
| `install_version` | Install a vanilla Minecraft version and dependencies |
| `search_mods` | Search Modrinth with game version and loader filters |
| `install_mod` | Download a selected mod file |
| `list_installed_mods` | List installed mod files |
| `remove_mod` | Remove a selected mod file |
| `java_diagnostics` | Detect Java and report runtime details |
| `install_java_runtime` | Install a supported managed Java runtime |
| `is_version_installed` | Check local vanilla version installation state |
| `launch_version` | Launch a supported version using the selected account/runtime/options |
| `list_game_files` | List files in an approved library category |
| `delete_game_file` | Delete a scoped library item |
| `open_game_folder` | Open an approved game folder in the OS file manager |
| `export_modpack` / `import_modpack` | Export/import the app's generic ZIP modpack format |
| `export_world` / `import_world` | Export/import world archives |

## API contract guidelines

- Use typed Rust request/response structs for nontrivial payloads.
- Return actionable errors with stable error codes where possible; avoid leaking secrets or full local paths unnecessarily.
- Validate every argument in Rust; frontend validation is convenience, not a security boundary.
- Use allowlisted categories, loader IDs and Java majors.
- Do not accept arbitrary filesystem paths or arbitrary executable paths from untrusted UI state.
- For long operations, evolve to progress events with operation IDs, explicit cancellation and terminal status.
- Version public/imported JSON formats independently from internal Rust structs.

## External APIs

Mojang version metadata, Modrinth and configured CurseForge services are external dependencies, not Tauri commands exposed directly to third parties. Their API schemas, rate limits, attribution and terms can change. Isolate each in a client module and test parsing with fixtures.

## Change policy

When adding/removing/renaming a command, update the UI invoke call, Rust handler registration, this table, tests and relevant documentation in the same change. Treat command names and serialized shapes as compatibility surfaces.
