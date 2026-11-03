# MCP Connection Design

## Status

**No MCP server/client connection is asserted to exist in the current DIVlauncher codebase.** This document defines a safe optional integration design for future development. MCP means Model Context Protocol; it is not required for normal launcher operation.

## Potential use cases

An optional local MCP server could expose read-only diagnostics such as app version, supported feature status, sanitized launcher logs and install state to an explicitly connected assistant. A separate, carefully permissioned tool could initiate safe tasks such as checking for updates. It should not expose raw account tokens, arbitrary filesystem access or unrestricted process execution.

## Recommended architecture

- Keep MCP integration in a separate opt-in process/module, not inside the core launch path.
- Use a documented transport supported by the chosen MCP SDK; prefer local stdio for a local-only integration unless a clear remote use case exists.
- Define a narrow tool allowlist with JSON schemas and bounded inputs/outputs.
- Require explicit user confirmation for destructive or externally visible actions.
- Authenticate and encrypt remote connections; restrict origins and bind addresses if network transport is ever used.
- Version tool names and schemas; log tool invocation metadata without secrets.

## Security boundaries

Never expose passwords, OAuth tokens, refresh tokens, client secrets, signing keys or unrestricted shell/file operations. Redact paths and usernames by default. Apply rate limits, timeouts, output-size limits and cancellation. Validate every tool argument inside the server. A connected model must not be treated as trusted authorization.

## Implementation checklist

1. Approve a concrete user need and threat model.
2. Select a maintained MCP SDK and pin its version.
3. Define read-only tools first (diagnostics, version status, feature matrix).
4. Add permission prompts and audit logging before write-capable tools.
5. Add protocol conformance, malicious-input and disconnect tests.
6. Document setup, permissions, data flow and disable/uninstall steps.

## Not an integration instruction

Do not add an MCP dependency or expose tools merely because this document exists. Normal launcher install/launch functionality must work without MCP.
