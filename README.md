# DIVlauncher

DIVlauncher is a Minecraft: Java Edition launcher for Android.

It is based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
(by Tran Khanh Duy and the PojavLauncher team), which is in turn based on
[Boardwalk](https://github.com/zhuowei/Boardwalk). PojavLauncher is discontinued.

## Building

1. Builds are produced by GitHub Actions (`.github/workflows/android.yml`),
   one APK per CPU architecture. The workflow downloads a pinned, checksum-verified LTW release and the
   Java 8, 17, 21 and 25 runtimes (from the AngelAuraMC OpenJDK builds) and repacks them with
   `scripts/repack_jre.py`; none of that is stored in this repo.
2. **The NDK is still required.** Third-party native libraries (LWJGL, OpenAL, gl4es, OSMesa, ...) are
   prebuilt in `jniLibs`, but DIVlauncher's own native bridge (`pojavexec`, `exithook`, `linkerhook`,
   `pojavexec_awt`, `awt_xawt`) is compiled from `app_pojavlauncher/src/main/jni` with `ndkBuild`.
   CI installs NDK 25.2.9519653 for you.
3. Locally: `./gradlew :app_pojavlauncher:assembleDebug` (needs JDK 17 or newer, Android SDK 34,
   NDK 25.2.9519653, and the JREs/LTW placed as the workflow does). CI uses Gradle 8.13, the same
   version as `gradle/wrapper/gradle-wrapper.properties`; keep the two in sync.
4. To upgrade LTW, change `LTW_TAG` and `LTW_SHA256` together in the workflow.

## Releases

Which APK do I download? Almost every modern phone needs **arm64-v8a**. Older 32-bit phones need
`armeabi-v7a`; `x86_64` and `x86` are for emulators and some Chromebooks.

- Every push to the default branch builds the APK and publishes it as the **Latest** release on the Releases page.
- Pushing a tag such as `v1.0.0` (`git tag v1.0.0 && git push origin v1.0.0`) publishes a normal versioned release.
- CI always builds the normal **release** app (named "DIVlauncher", no "Debug" label). It is signed with your own
  release key when the signing secrets exist, and with a shared test key otherwise. Debug builds (`assembleDebug`,
  labelled "DIVlauncher Debug") are for local development only.
- Releases and version names:
  - Every push to the default branch publishes the newest build as the **Latest** GitHub release, tagged
    `DIVlauncher-v<divVersion>` and titled `DIVlauncher v<divVersion>` (for example `DIVlauncher-v1.0.0`). The same
    release is replaced on each push until you raise `divVersion` in `gradle.properties`.
  - To publish a specific version as its own release, push a git tag such as `DIVlauncher-v1.2.0` (a plain `v1.2.0`
    also works). The app version is then `1.2.0`.
  - Builds that are not published (other branches, pull requests) are `<divVersion>-dev.<commit>`; local builds are
    `<divVersion>-local`.
  - The old rolling "nightly" pre-release is removed automatically on the next push.

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
4. Push. APKs are named `DIVlauncher-<abi>-release.apk` and are now signed with your key. Release builds install next to debug builds as a separate app. **Switching from the shared test key to your own key later means users must uninstall and reinstall once**, so set the secrets before you share the app widely.

## Documentation

Guides are in the [`docs`](docs) folder:
- [Change log](docs/CHANGELOG.md): what changed in the redesign and the features after it
- [Account authentication](docs/ACCOUNT_AUTHENTICATION.md) and [Ely.by browser sign-in setup](docs/ELYBY_BROWSER_OAUTH_SETUP.md)
- [Mod downloader](docs/MOD_DOWNLOADER.md)
- [Performance toggle](docs/PERFORMANCE_TOGGLE.md) and [performance baseline test](docs/PERFORMANCE_BASELINE.md)
- [Removing account data](docs/dataremoval.md)

## License

DIVlauncher is licensed under the [GNU LGPLv3](LICENSE), the same license as PojavLauncher.
The `LICENSE` file and the upstream copyright notices must stay in the repo.
The components below are used under their own licenses.

## Credits and third-party components

* [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM launcher): unknown license / [Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
* Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
* [GL4ES](https://github.com/PojavLauncherTeam/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
* [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
* [LWJGL3](https://github.com/PojavLauncherTeam/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
* [LWJGLX](https://github.com/PojavLauncherTeam/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
* [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
* [pro-grade](https://github.com/pro-grade/pro-grade) (Java sandboxing security manager): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
* [bhook](https://github.com/bytedance/bhook) (exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
* [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
* [virglrenderer](https://github.com/PojavLauncherTeam/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
* Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.

