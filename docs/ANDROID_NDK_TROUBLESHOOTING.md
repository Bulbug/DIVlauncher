# Android NDK troubleshooting

## NDK archive is not a ZIP archive

If GitHub Actions reports:

```text
An error occurred while preparing SDK package NDK (Side by side) 25.2.9519653:
Archive is not a ZIP archive.
```

the SDK Manager received a corrupt, incomplete, or unexpected response instead of the NDK archive. This is generally a download/cache problem, not an `arm64-v8a` compiler error.

The Android workflow now:

1. Sets up the Android SDK command-line tools.
2. Accepts SDK licenses.
3. Installs NDK `25.2.9519653`, Android 34 platform tools, and Build Tools 34.
4. Retries the package installation up to three times.
5. Clears the partial NDK directory and SDK Manager download cache between attempts.
6. Verifies `source.properties` and the LLVM toolchain before Gradle starts.
7. Exposes `ANDROID_NDK_HOME` and `ANDROID_NDK_ROOT` to later build steps.

## ARM64 target

The Android ABI `arm64-v8a` corresponds to the Rust target triple `aarch64-linux-android`. The workflow's ABI matrix selects `arm64-v8a`; the Android NDK provides the matching Clang cross-toolchain.

## If the workflow still fails

- Open the failed `Install Android NDK and SDK packages (with recovery)` step.
- Check whether all three attempts failed or whether package metadata/download access was blocked.
- Re-run the workflow once; transient GitHub/Google download failures can clear.
- Do not commit an NDK installation or SDK cache into the repository.
- Keep `ndkVersion = "25.2.9519653"` in `app_pojavlauncher/build.gradle` aligned with `ANDROID_NDK_VERSION` in `.github/workflows/android.yml`.

## Local verification

On a machine with Android SDK command-line tools installed:

```bash
sdkmanager --sdk_root="$ANDROID_HOME" --install "ndk;25.2.9519653"
test -f "$ANDROID_HOME/ndk/25.2.9519653/source.properties"
grep "Pkg.Revision" "$ANDROID_HOME/ndk/25.2.9519653/source.properties"
```

The workflow must still complete the Gradle build to establish that the complete application compiles. A successful NDK installation alone does not prove the APK builds.
