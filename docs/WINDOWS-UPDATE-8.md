# Windows Update 8 — safer launcher backup restore

## Change

The backup importer now validates the JSON structure and bounds profile/account counts and string lengths before replacing the in-memory launcher state. It checks profile identifiers/names/versions, checks account UUID metadata, and rejects malformed settings objects. The confirmation dialog remains required before replacement.

## Scope and limits

- This is validation of the existing JSON backup format (`divlauncher-backup`, version 1); it does not encrypt backups.
- Offline account metadata is not a Microsoft login token and does not authenticate with Microsoft services.
- Backups still do not contain game files, mods, worlds, Java runtimes, or credentials.
- Rust compilation and interactive Windows testing are required before release.

## Checks

Run from `desktop/`:

```powershell
node --check ui/app.js
cargo test -p divcore
cargo check -p divlauncher
cargo tauri build --bundles nsis
```

The repository's Windows GitHub Actions workflow runs the JavaScript syntax check, core tests, backend check, and NSIS package build on pushes and pull requests.
