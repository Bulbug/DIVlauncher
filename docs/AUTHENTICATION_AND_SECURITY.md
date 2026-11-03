# Authentication and Security

## Account types

Keep these identities distinct in UI and storage:

- **Offline/local profile:** local username and offline UUID only. It does not prove account ownership and is not valid for authenticated online-mode access.
- **Microsoft/Minecraft account:** provider-authenticated identity with verified Minecraft profile/entitlement where required.
- **Third-party identity provider:** only if deliberately supported and clearly labelled; do not imply it is Microsoft authentication.

## Microsoft sign-in requirements

Use Microsoft's supported OAuth authorization flow and the appropriate Minecraft services flow under current provider documentation. Prefer authorization code with PKCE for public desktop clients when supported. Validate state/nonce and redirect URI; do not embed a client secret in a distributed desktop binary. Verify the Minecraft profile/entitlement using official supported services. Handle expired/revoked tokens, refresh, sign-out and account removal.

No Windows Microsoft-authentication implementation is claimed complete by this document. Registration IDs, redirect URIs and tenant/application settings must be configured by the maintainer; never invent credentials.

## Token storage

- Store refresh/access tokens only in an OS-protected credential store (Windows Credential Manager/DPAPI-backed solution; Android Keystore-backed storage).
- Never store tokens in `state.json`, backups, logs, crash reports, screenshots or source control.
- Minimize token lifetime in memory and clear on sign-out where practical.
- Ensure backup/export excludes secrets.

## Threat model

Assets: account tokens, game files, profile state, user worlds/mods, local filesystem and release signing keys.

Threats: malicious mod/pack archives, path traversal, poisoned metadata, compromised download endpoints, token theft, command injection, unsafe plugin/agent arguments, corrupted state, dependency compromise and accidental log disclosure.

Mitigations: HTTPS and host allowlists where appropriate; integrity checks; safe archive extraction; canonicalized path boundaries; argument-vector process spawning; strict validation; least privilege; OS secret storage; dependency pinning/checksums; signed releases where feasible; redacted diagnostics; backup validation and recovery.

## Download and archive safety

Treat remote content as untrusted. Validate URLs/schemes, metadata sizes, hashes/signatures when provided, archive paths, symlinks, entry count and expanded size. Avoid extracting outside a designated staging directory. Never overwrite user data without preview and confirmation; use backups/rollback for imports.

## Privacy

Collect the minimum data necessary. Explain any telemetry and make it opt-in where appropriate. Before sharing logs, redact usernames, local paths, session identifiers, access tokens and server addresses. Provide a clear account-data removal process.

## Incident response

If a credential leak is suspected: revoke sessions/tokens, rotate affected secrets, remove leaked artifacts from releases and Git history as appropriate, notify affected users when warranted, and document the incident. Deleting a secret from the latest commit alone does not remove it from history.
