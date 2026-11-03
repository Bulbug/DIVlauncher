# Local Data and Persistence Design

DIVlauncher currently uses local launcher state rather than a server-side relational database for the Windows desktop app. The desktop documentation identifies `%APPDATA%\com.divlauncher.desktop\state.json` as the state file. Minecraft game data is stored separately under the app's game directory (currently derived by the Tauri backend).

## Data domains

| Domain | Suggested persistence | Sensitive? |
|---|---|---|
| Profiles | Versioned JSON state | Low; may reveal preferences |
| Account display metadata | Versioned JSON state | Personal data; minimize |
| Authentication tokens | OS credential vault/keychain only | Highly sensitive |
| Settings | Versioned JSON state | Low; paths may reveal usernames |
| Installed game files/assets | Minecraft game directory | No credentials expected |
| Logs/crash reports | App/game logs | May contain usernames, paths or tokens; redact before sharing |
| Modpack/world archives | User-selected files | User content; treat as untrusted |

## Conceptual schema (not a claim of an existing SQL database)

```json
{
  "schemaVersion": 1,
  "selectedProfileId": "profile-id",
  "profiles": [{
    "id": "profile-id",
    "name": "Vanilla 1.21.x",
    "gameVersion": "1.21.x",
    "loader": "vanilla",
    "gameDirectory": null
  }],
  "accounts": [{
    "id": "account-id",
    "kind": "offline-or-provider",
    "displayName": "Player"
  }],
  "settings": {
    "memoryMb": 4096,
    "javaRuntimeId": null,
    "extraJvmArgs": []
  }
}
```

This is an illustrative target shape. The implementation's exact serialized fields must be checked in `desktop/ui/app.js` and `desktop/src-tauri/src/main.rs` before changing or migrating them.

## Persistence requirements

- Include `schemaVersion` and a migration path for every incompatible schema change.
- Write to a sibling temporary file, flush where practical, then atomically replace the previous state.
- Validate the full structure and apply sensible bounds before accepting imported backups.
- Do not store passwords, refresh tokens, access tokens or client secrets in JSON backups.
- Avoid logging full account identifiers or credential-bearing URLs.
- Keep backups explicitly scoped: launcher state backups do not imply inclusion of game assets, mods or runtimes unless stated.

## Future database decision

A local SQLite database may become useful if state becomes relational or requires transactions/search. Do not introduce it merely for fashion. If adopted, define migrations, backups, corruption recovery, encryption needs and cross-platform support first. No cloud database is required by the current desktop architecture.
