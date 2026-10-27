# DIVlauncher CurseForge API proxy

DIVlauncher must not embed the CurseForge API key in its APK. This small Node.js service keeps the key on a server and forwards only read-only CurseForge API requests.

## Requirements

- Node.js 18 or newer
- An approved CurseForge API key provided by CurseForge
- A publicly reachable HTTPS reverse proxy/load balancer in production

## Run locally

```bash
export CURSEFORGE_API_KEY='YOUR_APPROVED_KEY'
export HOST='127.0.0.1'
export PORT='8080'
node server.js
```

`/health` is a simple health check. API routes are exposed below `/v1/`, for example `/v1/mods/search?...`. The service forwards to `https://api.curseforge.com/v1` and never accepts arbitrary upstream URLs or non-GET requests.

## Production

Run behind an HTTPS reverse proxy (Caddy, nginx, managed load balancer, etc.). Bind the Node process to localhost or a private network. Restrict inbound access with your hosting firewall and rate-limit requests at the edge; if public access is unavoidable, add application-level quotas and abuse controls appropriate to your deployment. Keep the API key in the host's secret manager/environment, rotate it if exposed, and never log it.

Then build Android with the public HTTPS proxy base URL:

```bash
./gradlew :app_pojavlauncher:assembleDebug -PdivAbi=arm64-v8a -PcurseforgeProxyBaseUrl=https://YOUR_HOST/v1
```

or set `CURSEFORGE_PROXY_BASE_URL` in the build environment. **This is the public proxy URL, not the API key.** The app disables CurseForge when the value is missing or is not HTTPS.

The service forwards only documented read-only API routes (`mods`, `categories`, `minecraft`, `games`). Project/file distribution rules still apply. The Android app only uses HTTPS URLs returned by the official API/download URL endpoint and deliberately does not synthesize CurseForge CDN URLs.
