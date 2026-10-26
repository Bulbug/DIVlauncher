# DIVlauncher

DIVlauncher is a Minecraft: Java Edition launcher for Android.

It is based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
(by Tran Khanh Duy and the PojavLauncher team), which is in turn based on
[Boardwalk](https://github.com/zhuowei/Boardwalk). PojavLauncher is discontinued;
its original README is kept in `UPSTREAM_PojavLauncher_README.md`.

## Building

1. Debug builds are produced by GitHub Actions (`.github/workflows/android.yml`),
   one APK per CPU architecture. The workflow downloads LTW and the Java 8, 17 and 21 runtimes
   (from the AngelAuraMC OpenJDK builds) and repacks them with `scripts/repack_jre.py`;
   none of that is stored in this repo.
2. Locally: `./gradlew :app_pojavlauncher:assembleDebug` (needs JDK 21, Android SDK 34, NDK 25.2.9519653,
   and the JREs/LTW placed as the workflow does).

## Releases

Which APK do I download? Almost every modern phone needs **arm64-v8a**. Older 32-bit phones need
`armeabi-v7a`; `x86_64` and `x86` are for emulators and some Chromebooks.

- Every push to the default branch builds the APK and updates a rolling **nightly** pre-release on the Releases page.
- Pushing a tag such as `v1.0.0` (`git tag v1.0.0 && git push origin v1.0.0`) publishes a normal versioned release.
- Builds are signed with your own release key when the signing secrets exist, and debug-signed otherwise.

### Release signing (one-time setup)

1. Create a key. **Keep the file and passwords safe and never commit them; if you lose the key, existing users can no longer update the app.**
   ```
   keytool -genkeypair -v -keystore divlauncher-release.jks -alias divlauncher -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Turn the key file into text:
   - Linux/macOS: `base64 -w0 divlauncher-release.jks`
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("divlauncher-release.jks"))`
3. In the GitHub repo open Settings, Secrets and variables, Actions, and add these four secrets:
   `DIV_KEYSTORE_BASE64` (the text from step 2), `DIV_KEYSTORE_PASSWORD`, `DIV_KEY_ALIAS` (`divlauncher` above) and `DIV_KEY_PASSWORD`.
4. Push. APKs are now named `DIVlauncher-<abi>-release.apk`. Release builds install next to debug builds as a separate app.

## License

DIVlauncher is licensed under the [GNU LGPLv3](LICENSE), the same license as PojavLauncher.
The `LICENSE` file and the upstream copyright notices must stay in the repo.
Third-party components and their licenses are listed in `UPSTREAM_PojavLauncher_README.md`.
