# Windows Support Update 7 — Minecraft Library Manager

This increment adds a native file manager for common Minecraft data folders:

- Worlds (`.minecraft/saves`)
- Screenshots (`.minecraft/screenshots`)
- Resource packs (`.minecraft/resourcepacks`)
- Shader packs (`.minecraft/shaderpacks`)
- Logs (`.minecraft/logs`)

The Library screen lists items, reports file sizes, opens the selected folder in the operating system's file manager, and offers deletion with confirmation. Rust commands create missing category directories and reject unknown categories, path separators, and symbolic links during deletion.

## Important limitations

- This manages files already present in the launcher's `.minecraft` directory; it does not download resource packs or shader packs.
- World deletion is permanent. Back up important saves before deleting them.
- Worlds are displayed as folders; nested world contents are not browsed inside the app.
- Microsoft authentication, automated Java installation, and full mod-loader installation/launch remain separate unfinished milestones.
- The Rust backend has not been compiled or runtime-tested on Windows in this environment. Run the Windows GitHub Actions workflow before distributing this update.
