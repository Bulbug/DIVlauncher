# Windows Support Update 5

## Added

- Java runtime diagnostics in Settings: reports whether Java is discoverable, the executable path, the detected major version, and a version-oriented compatibility hint.
- A manual recheck action for after Java is installed or the environment changes.
- Browser-preview fallback for the diagnostics UI.

## Still not implemented

- Automatic Java runtime download, installation, or per-version runtime selection.
- Microsoft/Minecraft account authentication.
- Installation and launch of Fabric, Forge, NeoForge, or Quilt profiles.
- End-to-end Windows runtime testing in this environment. GitHub Actions should be used to compile and test the Rust backend.

The diagnostics message is guidance, not a guarantee that every Minecraft version or mod loader will launch successfully.
