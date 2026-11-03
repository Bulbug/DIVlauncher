# Contributor and Automation Agent Instructions

These instructions apply to human contributors and AI coding agents working in this repository.

## First steps

1. Read the root `README.md`, this `docs/README.md`, `PROJECT_REQUIREMENTS.md` and `ARCHITECTURE.md`.
2. Inspect the actual implementation and current Git diff before editing. Do not assume a previous update description matches the checked-out branch.
3. Identify platform scope: Android, Windows desktop, shared Rust core or service.
4. Check existing issues, workflows and tests before creating duplicate systems.

## Engineering rules

- Do not claim a feature is implemented, compiled or tested unless evidence exists.
- Keep changes scoped; preserve unrelated user changes and existing licensing notices.
- Never replace the entire repository with a generated scaffold.
- Never commit credentials, private keys, tokens, personal data or unredacted diagnostic logs.
- Validate all user input again in Rust/backend code.
- Treat remote metadata, archives, mod files and imported backups as untrusted.
- Use safe path joins/canonicalization, archive limits and integrity verification.
- Launch processes with argument arrays; do not build shell command strings from user input.
- Do not bypass Microsoft authentication, game ownership checks, provider terms or API rate limits.
- Prefer reusable domain logic and tests over duplicating logic in the UI.
- Update docs and changelog with behavior changes.

## Required workflow

1. State the goal and acceptance criteria.
2. Inspect relevant code and identify risks.
3. Implement the smallest coherent change.
4. Add/update unit, integration or UI tests.
5. Run formatting, lint, tests and builds available in the environment.
6. Record commands run and exact results; distinguish not-run from passed.
7. Update documentation and known limitations.
8. Review the diff for accidental files, secrets and unrelated changes.

## Agent boundaries

Agents may inspect, propose, edit and run tests within the authorized workspace. Do not publish releases, alter repository secrets, change OAuth registrations, deploy services, delete user data or push commits unless explicitly authorized. Ask before destructive migrations or changes that affect account/session storage.

## Completion report template

- Changed files:
- Behavior delivered:
- Tests run and results:
- Tests not run:
- Security/privacy considerations:
- Known limitations:
- Manual verification steps:
