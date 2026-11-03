# Feature Development Workflow

## 1. Define the feature

Write a short problem statement, target platforms, user journey, requirements IDs, non-goals and acceptance criteria. Identify dependencies on external APIs, credentials, platform capabilities and licensing.

## 2. Inspect existing behavior

Search the UI, Tauri commands, Rust core, Android implementation and tests. Prefer extending an existing abstraction over adding a parallel implementation. Record platform differences explicitly.

## 3. Design the contract first

Define input/output structs, error cases, progress/cancellation behavior, persistence changes and migration plan. For external APIs, create a dedicated client and fixture-based parser tests.

## 4. Implement safely

- UI handles presentation and user feedback.
- Rust validates all inputs and owns privileged filesystem/process/network actions.
- Keep pure parsing/validation in testable core modules.
- Use temporary files, checksums, atomic replacement and rollback where relevant.
- Enforce archive path, symlink, count and expanded-size limits.
- Avoid shell execution and avoid logging credentials.

## 5. Test and document

Add tests for success, invalid input, offline behavior, malformed remote data, permission errors, cancellation and recovery. Update API, architecture, security, setup and changelog docs as applicable.

## 6. Review checklist

- [ ] Requirements and acceptance criteria met.
- [ ] Android/Windows scope clearly stated.
- [ ] No secrets or generated build artifacts committed.
- [ ] Input and path validation enforced in backend.
- [ ] Errors are actionable and logs are redacted.
- [ ] Tests run and results recorded accurately.
- [ ] Accessibility and localization considered.
- [ ] Docs and feature matrix updated.
