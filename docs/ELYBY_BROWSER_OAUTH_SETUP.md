# DIVlauncher — Ely.by browser sign-in setup

The Ely.by option now uses the provider's official OAuth website flow. It does not ask the player to type an Ely.by password into DIVlauncher. After authorization, Ely.by redirects the browser to a loopback callback on the same device; the launcher validates the OAuth `state`, exchanges the code, reads the Ely.by profile, installs the Ely.by authlib-injector, and saves the account session in Android Keystore-encrypted storage.

## Required Ely.by registration

1. Register an OAuth application in the Ely.by account settings.
2. Register this exact redirect URI:

   `http://127.0.0.1:18763/elyby-callback`

3. Ensure the app registration allows these scopes: `account_info minecraft_server_session offline_access`. The OAuth flow needs `minecraft_server_session` to use the returned access token as a Minecraft session and `offline_access` to get a refresh token.
4. Supply the real client ID and client secret at build time. For a private test build, set environment variables `ELYBY_OAUTH_CLIENT_ID` and `ELYBY_OAUTH_CLIENT_SECRET`, or Gradle properties `elybyOAuthClientId` and `elybyOAuthClientSecret`. Do not commit values to source control.

Example Linux/macOS shell invocation:

```sh
export ELYBY_OAUTH_CLIENT_ID='your-real-client-id'
export ELYBY_OAUTH_CLIENT_SECRET='your-real-client-secret'
./gradlew :app_pojavlauncher:assembleDebug --no-daemon
```

The client secret is compiled into the APK by this private-build implementation and can be extracted from a distributed APK. **Do not use this approach for a public release**: a public launcher should exchange OAuth codes through a backend that keeps the client secret server-side. This repository does not contain registered Ely.by credentials or such a backend, so browser sign-in will show a setup message until credentials are supplied.

**Build guard:** `app_pojavlauncher/build.gradle` now refuses to build a release/bundle variant while `ELYBY_OAUTH_CLIENT_SECRET` is set, so the secret cannot ship in a release APK by accident. Debug builds are unaffected. Override only knowingly with `-PelybyAllowSecretInRelease=true`.

## Build status

The source was updated, but an APK could not be compiled in the current environment: Gradle Wrapper tried to download `https://services.gradle.org/distributions/gradle-8.13-bin.zip` and failed with `UnknownHostException`. The source update has therefore not been verified by an Android build or real Ely.by sign-in.
