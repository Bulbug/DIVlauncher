# DIVlauncher Offline Account Full-Access Fix Report

## Changes
- Explicit offline account type is now authoritative in `MinecraftAccount.isLocal()`, even if stale token fields exist in an old JSON profile.
- Offline accounts are explicitly exempted from demo-only classification in `Tools.isDemoProfile()`.
- Offline usernames are passed through unchanged; only a genuine demo account has its legacy `Demo.` prefix removed.
- Removed demo-only UI gates from Mods navigation, profile-type installer choices (Fabric, Forge, Quilt, OptiFine, modpacks), custom JAR/mod installation, and opening the game directory.
- Removed the download-task restriction that blocked public Minecraft files for local/offline profile contexts. When there is no Activity context, downloads now use the normal shared game directory rather than the demo directory.
- Kept `--demo` for a genuine demo account. Offline login remains unverified; this change does not grant Minecraft ownership or authenticate offline accounts to online-mode servers.

## Checks run
- All Android resource XML files parsed successfully.
- Static assertions passed for offline account classification, username preservation, removal of feature-level demo gates, and public game-file download behavior.
- Full Android/Gradle build could not be verified in this environment because Gradle 8.13 is not cached and the wrapper distribution download is unavailable here.
