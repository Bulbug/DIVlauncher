# External Integrations

## Integration inventory

| System | Purpose | Current status / notes |
|---|---|---|
| Mojang version manifest and metadata | Version discovery, libraries, assets and client metadata | Used by Windows vanilla workflow; schemas/rules must be validated |
| Minecraft services / Microsoft identity | Authenticated Minecraft profile and entitlement | Windows sign-in not confirmed implemented |
| Modrinth API | Mod search and mod-file download | Windows search/download implementation present; test rate limits and compatibility |
| CurseForge API/proxy | CurseForge project and pack metadata/files | Optional proxy service exists; requires approved API access and configured secrets |
| Eclipse Adoptium | Managed Java runtime distributions | Windows managed runtime feature present for selected majors; verify architecture/checksums and provider terms |
| GitHub Actions | CI, artifacts and releases | Android and Windows workflows present; CI success is the build evidence |
| Tauri/WebView2 | Windows desktop shell and web rendering | Tauri 2 app in `desktop/` |
| MCP | Optional assistant/tool interoperability | Design only; no connection claimed |

## Integration rules

- Use official documentation and endpoints; avoid scraping or undocumented private APIs.
- Respect API terms, attribution requirements, rate limits and caching rules.
- Keep provider-specific code behind adapters with typed models.
- Set network timeouts and bounded response sizes; handle offline and provider outage states.
- Validate redirects and final download hosts. Use HTTPS.
- Store API credentials only in appropriate secret storage; never in frontend bundles or repository files.
- Do not proxy around provider access controls or distribute copyrighted game files.

## Modrinth

Filter by Minecraft version and loader where metadata supports it. Before installation, verify game version, loader compatibility, file type, URL and published hash when available. Display project/file attribution and source. A mod download is not equivalent to installing the required loader or proving the mod launches.

## CurseForge

Use the official approved API access path. The repository contains `services/curseforge-proxy/`; review its README and implementation before deployment. Keep API keys server-side and configure them through deployment secrets. Full CurseForge pack support must respect manifest references and provider restrictions; do not assume every referenced file can be downloaded without API authorization.

## Java distributions

Select runtime builds by OS, CPU architecture, Java major and distribution. Verify checksums/signatures when provided, unpack to a staging directory, validate the executable, then atomically activate. Provide clear license and vendor information.

## Integration change checklist

Record provider, purpose, endpoints, auth model, scopes, terms, rate limits, data collected, failure modes, secret handling, test fixtures and fallback behavior before release.
