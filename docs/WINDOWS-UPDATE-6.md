# Windows Support Update 6

## Launcher backup and restore

The Profiles screen now offers JSON backup export/import for launcher state. A backup includes the current saved launcher state (profiles, account names/UUIDs and settings), but does not include Minecraft game files, downloaded mods, Microsoft credentials, or Java runtimes.

- Export creates a versioned `divlauncher-backup` JSON file.
- Import validates the backup format and requires confirmation before replacing the current saved state.
- Backups contain local/offline account profile metadata only; do not treat them as a secure credential store.

## Validation

`node --check desktop/ui/app.js` passes. Rust compilation and interactive Windows testing remain required before release.
