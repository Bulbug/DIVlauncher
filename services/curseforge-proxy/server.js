'use strict';

// Minimal read-only proxy for DIVlauncher. Keep CURSEFORGE_API_KEY only in the server
// environment; never put it in the Android app, Git, or public build logs.
const http = require('node:http');
const https = require('node:https');
const { URL } = require('node:url');

const PORT = Number(process.env.PORT || 8080);
const API_KEY = process.env.CURSEFORGE_API_KEY || '';
const HOST = process.env.HOST || '127.0.0.1';
const MAX_PATH_LENGTH = 2048;

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET') return json(res, 405, { error: 'Only read-only GET requests are supported.' });
  if (req.url === '/health') return json(res, 200, { ok: true, service: 'DIVlauncher CurseForge proxy' });
  if (!API_KEY) return json(res, 503, { error: 'CurseForge API is not configured on the server.' });

  let parsed;
  try { parsed = new URL(req.url, 'http://proxy.local'); }
  catch (_) { return json(res, 400, { error: 'Invalid request path.' }); }

  if (!parsed.pathname.startsWith('/v1/')) return json(res, 404, { error: 'Unknown route.' });
  const path = parsed.pathname.slice(3);
  if (path.length > MAX_PATH_LENGTH || path.includes('..') || path.includes('\\') || path.includes('\0')) {
    return json(res, 400, { error: 'Invalid API path.' });
  }
  // Restrict this service to CurseForge's documented read-only API routes; do not make it an open proxy.
  if (!/^\/(mods(?:\/|$)|categories(?:\/|$)|minecraft(?:\/|$)|games(?:\/|$))/.test(path)) {
    return json(res, 403, { error: 'This endpoint is not permitted.' });
  }

  const upstream = new URL('https://api.curseforge.com/v1' + path + parsed.search);
  const upstreamReq = https.request(upstream, {
    method: 'GET',
    headers: {
      'x-api-key': API_KEY,
      'Accept': 'application/json',
      'User-Agent': 'DIVlauncher-CurseForge-Proxy/1.0'
    },
    timeout: 20000
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode || 502, {
      'content-type': upstreamRes.headers['content-type'] || 'application/json',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff'
    });
    upstreamRes.pipe(res);
  });
  upstreamReq.on('timeout', () => upstreamReq.destroy(new Error('Upstream timeout')));
  upstreamReq.on('error', () => { if (!res.headersSent) json(res, 502, { error: 'CurseForge API request failed.' }); else res.destroy(); });
  upstreamReq.end();
});

server.listen(PORT, HOST, () => {
  console.log(`DIVlauncher CurseForge proxy listening on ${HOST}:${PORT}`);
  if (!API_KEY) console.warn('CURSEFORGE_API_KEY is missing; API requests will return 503.');
});
