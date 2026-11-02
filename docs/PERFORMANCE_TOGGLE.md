# Performance toggle (per profile)

A Fabric profile can switch on optimisation mods. Everything is **off by default**; each profile has its own switches.

## Switches (profile editor, plus a quick "Performance" switch on each profile card)
1. **Performance** (safe pack): Lithium, FerriteCore, ModernFix, Entity Culling, MoreCulling, ScalableLux, Fast Noise, Language Reload, Alternate Current.
2. **Experimental rendering**: Sodium, ImmediatelyFast (may not work with the phone's graphics layers).
3. **Advanced**: C2ME, Dynamic FPS (may crash or heat the phone).

The card's quick switch controls switch 1 only. Profiles that are not Fabric show the switches greyed out.

## What happens at launch
`LauncherActivity` calls `PerformancePack.syncThenRun()` before the game download/launch when the profile has a switch on, or still has mods from an earlier run to remove:
- Mods added earlier by the pack that are no longer wanted are removed (only entries whose inventory `origin` is `performance:<slug>`).
- For each wanted mod: already added -> kept; a jar with the mod's usual file name already in `mods/` -> "using your copy", nothing downloaded; otherwise the newest release (a beta if there is no release, never an alpha) for the profile's Minecraft version and the Fabric loader is downloaded from Modrinth with its required dependencies, hash-checked, and recorded in `.divlauncher/installed-content.json`.
- Mods with no matching release are skipped ("not available for <version>"). Fast Noise, for example, currently lists only recent Minecraft versions.
- Progress shows in the bottom bar ("Adding performance mods - 3 of 9"); a short summary toast follows.
- No internet and mods missing: a dialog offers "Play anyway" (with the mods you already have) or Cancel.

## Rules
- Your own mods are never touched. Dependencies the pack pulled in (for example Fabric API) are marked `performance-dep` and are kept when you switch off.
- Mods are not auto-updated once added (only added/removed).
- Only Fabric profiles (`fabric-loader-<loader>-<minecraft version>`) are supported for now.

## Files
`performance/PerformancePack.java`, `ModrinthApi.installLatestRelease()`, `InstalledContentStore.Entry.origin`, `MinecraftProfile.perfMods/perfExperimental/perfAdvanced`, editor section in `fragment_profile_editor.xml`, quick switch in `item_profile_card.xml`.

## Not verified
No Gradle build, no Modrinth calls and no on-device test were possible where this was written. Test with a Fabric profile: switch on, launch (with and without internet), check the `mods` folder, then switch off and launch again.
