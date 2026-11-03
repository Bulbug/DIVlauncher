# Windows support update 11 — modpack and world archives

This increment adds native ZIP import/export workflows to the Windows desktop UI.

## Modpack archive

The Mods screen can export/import a ZIP containing the `.minecraft/mods`, `config`, `resourcepacks`, and `shaderpacks` directory contents. The export includes a small `divlauncher-manifest.json` marker. Import stages archive contents before copying them into the game directory. The importer rejects absolute/traversal paths, symbolic links, archives with more than 30,000 entries, and expanded content over 2 GiB. Matching destination filenames can be replaced; the UI asks for confirmation first.

This is a generic file-based pack format, not full Modrinth `.mrpack` or CurseForge manifest resolution. It does not download dependencies or install a loader.

## World archives

The Library screen supports importing a world ZIP and exporting a selected world folder. Imports require a `level.dat` at the world root (or in a matching top-level folder), reject path traversal and symbolic links, enforce the archive entry/expanded-size limits, and refuse to overwrite an existing world folder.

## Validation

`node --check desktop/ui/app.js` and ZIP integrity checks were run while preparing this update. Rust compilation and Windows GUI/file-dialog runtime tests were not available in the editing environment. The `rfd` native file-dialog dependency must be compiled by the Windows GitHub Actions workflow before merging this update into a release branch.

## Known limits

- Mod-loader installation/launching (Fabric, Forge, NeoForge, Quilt) is not included in this increment.
- Microsoft authentication is not included.
- Modpack archive import copies supported folders; it does not resolve metadata/dependencies or guarantee that the pack's Minecraft/loader versions match the selected profile.
- Back up existing worlds and mod folders before importing archives.
