# Versioning and releases

Every push to the default branch publishes its own release, with the next version number worked out automatically.

## The rules
| Change | What goes in the commit message | Example |
|--------|---------------------------------|---------|
| Small (default) | nothing special | 1.0.1 -> 1.0.2 |
| Big | `[big]` anywhere in a commit message of the push | 1.0.1 -> 2.0.0 (the other numbers reset to 0) |

- The next number is based on the newest existing `DIVlauncher-v<number>` release (the highest by version order).
- If there is no release yet, the first one is `divVersion` from `gradle.properties` (1.0.0).
- `divVersion` is also a floor: raise it (for example to `3.0.0`) and the next push becomes at least that number.
- If a push contains several commits, `[big]` in any of them makes the whole push big.

## What gets created
- A git tag and a GitHub release named `DIVlauncher-v1.0.2` (title "DIVlauncher v1.0.2"), marked **Latest**, with the Android APKs, the Windows setup and portable `.exe` files (see `DESKTOP.md`) and the commit message as the notes. Older releases stay as history.
- The Android app and the Windows app are both built with that number: it appears in Settings (bottom of the category list) and in Android's app info.
- The tag is reserved at the start of the run so two quick pushes can never get the same number. If publishing fails, the reserved tag is deleted again.

## Other builds
- Pushes to other branches and pull requests build the app but publish nothing; their version is `<divVersion>-dev.<commit>`.
- Pushing a tag yourself (`DIVlauncher-v2.5.0` or `v2.5.0`) publishes exactly that version.
- Local builds are `<divVersion>-local`.

## Typical commit messages
- Small: `git commit -m "fix profile card spacing"`
- Big: `git commit -m "new redesign [big]"`

## Files
`.github/workflows/android.yml` (jobs `version`, `build`, `windows`, `release`, `cleanup-tag`), `app_pojavlauncher/build.gradle` (`getVersionName`, reads `DIV_VERSION`), `gradle.properties` (`divVersion`).
