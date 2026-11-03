# DIVlauncher Documentation Hub

This directory is the engineering documentation source of truth for DIVlauncher. Start here, then follow the topic-specific documents.

## Document map

| Document | Purpose |
|---|---|
| [Project requirements](PROJECT_REQUIREMENTS.md) | Scope, users, functional and non-functional requirements, acceptance criteria |
| [Design](DESIGN.md) | UX principles, navigation, interaction and accessibility |
| [Architecture](ARCHITECTURE.md) | Android/Windows components, boundaries and data flows |
| [Database and state](DATABASE.md) | Local persistence model, schemas and migration rules |
| [API](API.md) | Tauri command contracts and external service boundaries |
| [Agents](AGENTS.md) | Guidance for human and AI contributors/automation agents |
| [Project implementation](PROJECT_IMPLEMENTATION.md) | Delivery phases, milestones and definition of done |
| [Setup and initialization](SETUP_AND_INITIALIZATION.md) | Prerequisites and local setup |
| [Feature development](FEATURE_DEVELOPMENT.md) | Workflow for implementing features consistently |
| [Authentication and security](AUTHENTICATION_AND_SECURITY.md) | Account flows, secrets, threat model and privacy |
| [MCP connection](MCP_CONNECTION.md) | Optional Model Context Protocol integration design and safety requirements |
| [Integrations](INTEGRATIONS.md) | Mojang, Modrinth, CurseForge, Java and platform integration notes |
| [Testing and QA](TESTING_AND_QA.md) | Test layers, CI and release gates |
| [Debugging](DEBUGGING.md) | Logs, common failures and diagnostic procedure |

## Project status and accuracy

DIVlauncher is an Android Minecraft: Java Edition launcher with a Windows desktop application in `desktop/` built using Tauri 2, Rust and HTML/CSS/JavaScript. Windows documentation describes an evolving implementation, not a claim of complete feature parity. Consult [DESKTOP.md](DESKTOP.md), the `WINDOWS-UPDATE-*.md` notes and the source code for current implementation details.

The Windows backend currently contains code for vanilla version installation/launching, offline accounts, profile/state handling, Modrinth mod file operations, Java runtime management, library file management, generic ZIP modpack/world import-export, and JVM options. These features still require compilation and end-to-end Windows validation. Mod-loader installation/launching, Microsoft account authentication and full Modrinth/CurseForge modpack compatibility are not confirmed as implemented.

## Documentation rules

- Label capabilities as **Implemented**, **Partial**, **Planned**, or **Not supported**. Do not turn roadmap items into claims.
- Update the relevant document and changelog in the same pull request as behavior changes.
- Prefer source-backed statements. When implementation differs from this documentation, the code and tests are the immediate truth; update docs promptly.
- Never commit credentials, OAuth client secrets, signing keys, access tokens, private user data or unredacted logs.
