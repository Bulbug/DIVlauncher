# Debugging and Diagnostics

## First-response procedure

1. Record app version, OS version/build, CPU architecture and selected Minecraft version/profile.
2. Record the exact action and whether the failure is repeatable.
3. Check launcher logs and the per-version Minecraft log directory.
4. Confirm Java executable path and major version for the selected Minecraft version.
5. Check network connectivity, provider status, free disk space and filesystem permissions.
6. Reproduce with a clean vanilla profile before blaming a mod or loader.
7. Share only redacted logs and the smallest reproducible case.

## Log locations

The Windows launcher state is documented at `%APPDATA%\com.divlauncher.desktop\state.json`. The desktop backend writes Minecraft launch output under the game directory's `logs` folder using a per-version launcher log name. Exact paths can vary with configuration; use the app's Open Folder action rather than assuming a fixed custom game directory.

## Common failure classes

| Symptom | Checks |
|---|---|
| App does not start | WebView2, Windows event logs, installer integrity and antivirus quarantine |
| Install fails | Network/TLS, disk space, permissions, metadata response, URL and checksum |
| Java not found | `JAVA_HOME`, PATH, managed runtime directory, executable permissions and required major version |
| Game exits immediately | Correct Java major, native libraries, JVM args, game log and graphics driver |
| Modded profile fails | Loader actually installed, exact game/loader/mod compatibility and dependency versions |
| Import rejected | Archive format, required root structure, path traversal, symlinks, limits and duplicate names |
| Account cannot join server | Confirm whether account is authenticated; offline/local profiles do not authenticate online-mode access |
| Provider API errors | Rate limit, API key configuration, endpoint changes, connectivity and provider terms |

## Safe diagnostic collection

Before sharing logs, remove access/refresh tokens, authorization codes, usernames if not needed, local home-directory names, private server addresses and other personal data. Never ask users to upload credential stores or complete state files containing sensitive metadata.

## Crash report quality

A useful report includes app/build version, platform, selected Minecraft version, Java major, loader (if any), concise failure summary, relevant sanitized log excerpt and reproduction steps. Avoid claiming a root cause until supported by evidence.

## Recovery

Prefer non-destructive steps first: retry metadata fetch, verify files, switch to a clean profile, restore a known-good backup and preserve logs. Do not automatically delete worlds, mods or user data as a repair action. Ask for confirmation before destructive cleanup.
