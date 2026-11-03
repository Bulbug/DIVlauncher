# Testing and Quality Assurance

## Test pyramid

1. **Unit tests:** pure parsing, version rules, path validation, UUIDs, schema validation and argument construction.
2. **Integration tests:** HTTP clients with fixtures/mocks, download verification, archive import/export, state persistence and process argument generation.
3. **UI tests:** navigation, empty/loading/error states, keyboard/touch interactions and confirmation flows.
4. **End-to-end tests:** clean Windows install, Java selection, vanilla install/launch, sign-in and loader workflows when implemented.
5. **Release tests:** installer upgrade/uninstall, portable package, antivirus/SmartScreen behavior, signatures and checksums.

## Commands

From `desktop`:

```bash
cargo test -p divcore
```

From `desktop/src-tauri` on a configured Windows machine:

```powershell
cargo tauri dev
cargo tauri build --bundles nsis
```

For frontend syntax:

```bash
node --check desktop/ui/app.js
```

Android build command and prerequisites are documented in the root README and `.github/workflows/android.yml`.

## Required test scenarios

- Fresh install and upgrade over an existing version.
- Missing/incompatible Java and managed-runtime selection.
- Supported vanilla version download, hash failure, interrupted download and retry.
- Invalid version metadata and unsupported library rules.
- Offline account clearly distinguished from authenticated account.
- Invalid/corrupt backup and malicious archive traversal/symlink/zip-bomb attempts.
- Duplicate files, conflict confirmation, cancellation and rollback.
- Mod compatibility mismatch and provider rate-limit/outage.
- State file corruption, migration and permission denial.
- Crash log redaction and process termination behavior.
- Keyboard navigation, screen scaling, localization and reduced motion.

## CI gates

A pull request should run formatting/lint, unit tests, frontend checks and platform build jobs applicable to changed files. A release should require clean Windows and Android builds, installer artifact verification, checksums, regression tests and documented manual smoke tests. Do not mark a workflow green based only on YAML parsing.

## Test evidence

Every report must list the exact command, environment, result and any skipped tests. Distinguish static validation, compilation, unit tests and actual runtime tests. A build passing on Linux does not prove Windows behavior.
