# Project Implementation Plan

## Delivery strategy

Deliver small, testable milestones. Stabilize the Windows vanilla path and build pipeline before layering mod loaders, identity providers and third-party pack formats. Keep Android regressions out of desktop-only work by running both relevant CI paths.

## Workstreams and status

| Workstream | Current assessment | Exit criteria |
|---|---|---|
| Android launcher | Existing app and CI workflow | Android build and device smoke tests pass |
| Windows shell/UI | Tauri 2 UI and Rust host present | Clean Windows build, navigation and state tests |
| Vanilla install/launch | Initial implementation present | Clean machine installs and launches supported versions |
| Java management | Managed Java installer code present | Correct runtime selection across supported versions/architectures |
| Mod catalog/files | Modrinth search/download/file management present | Compatibility checks, integrity, attribution and end-to-end tests |
| Mod loaders | Not confirmed complete | Install, update, launch and repair tests for each supported loader |
| Microsoft identity | Not confirmed implemented for Windows | OAuth flow, token vault, refresh, sign-out and ownership verification |
| Pack formats | Generic ZIP import/export present | Standard `.mrpack`/CurseForge support with dependency resolution and safe extraction |
| Library management | Basic file operations present | Import/export and safe management per category |
| Diagnostics | Logs exist; recovery evolving | Actionable crash report, process lifecycle and redaction tests |

## Recommended phases

### Phase 0 — Establish a trustworthy baseline

- Build Android and Windows from a clean checkout.
- Resolve compiler warnings/errors and document supported toolchains.
- Add CI artifacts and retain test logs.
- Create a feature matrix with verified status.

### Phase 1 — Stabilize vanilla desktop

- Validate version metadata/rules, libraries, natives, assets and logging.
- Test Java major selection and process lifecycle.
- Add download progress, cancellation and retry behavior.

### Phase 2 — Java and mod-loader lifecycle

- Runtime manifests, platform/architecture selection, checksums and safe updates.
- Loader-specific installers/metadata and profile inheritance.
- Test install, launch, update, removal and repair for each loader/version pair.

### Phase 3 — Identity and account security

- Register supported OAuth application(s) and document redirect configuration.
- Implement authorization-code flow with PKCE where supported, secure token storage and refresh/revoke.
- Verify game entitlement/profile through official supported endpoints.

### Phase 4 — Content ecosystem

- Implement Modrinth `.mrpack` parsing and dependency resolution.
- Add CurseForge only through approved API access and configured credentials/proxy.
- Add pack preview, conflict resolution, rollback and export.

### Phase 5 — Polish and release readiness

- Resource/shader pack workflows, world management, diagnostics and accessibility.
- Security review, regression suite, installer upgrade/uninstall tests and release checklist.

## Definition of done

A feature is done only when code, tests, UI states, security validation, documentation and known limitations are updated. A feature requiring a live provider account or Windows device must have a recorded manual test. A successful static check is not an end-to-end pass.
