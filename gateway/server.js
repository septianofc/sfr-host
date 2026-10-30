/**
 * SFR Host — Public Tunnel Gateway & Custom Domain Router
 * Author: Septian Fathur Rohman
 * 
 * This server runs on a public IP / VPS with ports 80/443 (or behind Cloudflare/Nginx reverse proxy).
 * It receives public visitor traffic for custom domains (e.g., tokoku.com),
 * routes requests over a persistent secure WebSocket tunnel to the owner's Android smartphone,
 * and streams the phone's HTTP responses back to the public visitors.
 */

const http = require('http');
const https = require('https');
const url = require('url');
const crypto = require('crypto');
const { WebSocketServer, WebSocket } = require('ws');

const HTTP_PORT = process.env.PORT || 80;
const GATEWAY_SECRET = process.env.GATEWAY_SECRET || 'sfrhost_default_secret_2026';

// Tunnel Registry:
// Map<tunnelId, { ws: WebSocket, domains: Set<string>, authToken: string, connectedAt: number, ip: string }>
const activeTunnels = new Map();

// Domain Routing Table:
// Map<hostname, tunnelId>
const domainToTunnelMap = new Map();

// In-flight HTTP Requests waiting for responses from Android phones:
// Map<requestId, { res: http.ServerResponse, timer: NodeJS.Timeout }>
const pendingRequests = new Map();

// HTTP Reverse Proxy Server
const server = http.createServer((req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const hostHeader = (req.headers['host'] || '').split(':')[0].toLowerCase();

    // 1. Healthcheck / Gateway Status Endpoint
    if (parsedUrl.pathname === '/_sfr/health' || parsedUrl.pathname === '/_sfr/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            status: 'ONLINE',
            service: 'SFR Host Public Gateway',
            version: 'SFRHOST-R-001',
            activeTunnelsCount: activeTunnels.size,
            registeredDomainsCount: domainToTunnelMap.size,
            uptimeSeconds: Math.floor(process.uptime()),
            timestamp: new Date().toISOString()
        }));
    }

    // 2. DNS Verification Endpoint for Custom Domains
    if (parsedUrl.pathname === '/.well-known/sfrhost-verify') {
        const domain = parsedUrl.query.domain || hostHeader;
        const isVerified = domainToTunnelMap.has(domain);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            domain: domain,
            status: isVerified ? 'ROUTED' : 'AWAITING_TUNNEL',
            gateway: 'SFR Host Edge'
        }));
    }

    // 3. Resolve which Android Phone owns this Custom Domain
    const tunnelId = domainToTunnelMap.get(hostHeader);
    const tunnel = tunnelId ? activeTunnels.get(tunnelId) : null;

    if (!tunnel || tunnel.ws.readyState !== WebSocket.OPEN) {
        res.writeHead(502, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(`
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <title>502 Bad Gateway - SFR Host</title>
                <style>
                    body { font-family: -apple-system, sans-serif; background: #101418; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 20px; }
                    .card { background: #181C20; border: 1px solid #242A30; border-radius: 16px; padding: 32px; max-width: 500px; text-align: center; }
                    h1 { color: #FF9800; font-size: 48px; margin: 0 0 12px; }
                    h2 { color: #42A5F5; font-size: 20px; margin: 0 0 16px; }
                    p { color: #8C9199; line-height: 1.6; font-size: 14px; margin-bottom: 20px; }
                    .box { background: rgba(0,0,0,0.3); padding: 12px; border-radius: 8px; font-family: monospace; font-size: 12px; color: #E1E2E5; margin-bottom: 20px; text-align: left; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>502</h1>
                    <h2>Server HP Android Sedang Offline</h2>
                    <p>Domain <strong>${escapeHtml(hostHeader)}</strong> berhasil diarahkan ke SFR Host Public Gateway, namun server HP Android pemilik website belum terhubung atau tunnel terputus.</p>
                    <div class="box">
                        Host: ${escapeHtml(hostHeader)}<br>
                        Status: Tunnel Disconnected<br>
                        Penyebab: HP mati, aplikasi ditutup, atau koneksi seluler terputus.
                    </div>
                    <p style="font-size: 12px; color: #666;">SFR Host • Your Android Phone, Your Own Public Web Server</p>
                </div>
            </body>
            </html>
        `);
    }

    // 4. Read request body if present
    const bodyChunks = [];
    req.on('data', chunk => bodyChunks.push(chunk));
    req.on('end', () => {
        const bodyBuffer = Buffer.concat(bodyChunks);
        const requestId = crypto.randomUUID();

        // 5. Serialize HTTP request for Android Tunnel
        const requestPayload = {
            type: 'HTTP_REQUEST',
            requestId: requestId,
            domain: hostHeader,
            method: req.method,
            path: req.url,
            headers: req.headers,
            clientIp: req.headers['cf-connecting-ip'] || req.headers['x-forwarded-for'] || req.socket.remoteAddress,
            bodyBase64: bodyBuffer.length > 0 ? bodyBuffer.toString('base64') : null
        };

        // Timeout handler (15 seconds)
        const timeoutTimer = setTimeout(() => {
            if (pendingRequests.has(requestId)) {
                pendingRequests.delete(requestId);
                res.writeHead(504, { 'Content-Type': 'text/plain' });
                res.end('504 Gateway Timeout: Server HP Android tidak merespons dalam 15 detik.');
            }
        }, 15000);

        pendingRequests.set(requestId, { res, timer: timeoutTimer });

        // Transmit over secure WebSocket tunnel to the Android phone
        try {
            tunnel.ws.send(JSON.stringify(requestPayload));
        } catch (err) {
            clearTimeout(timeoutTimer);
            pendingRequests.delete(requestId);
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end('500 Internal Gateway Error: ' + err.message);
        }
    });
});

// WebSocket Tunnel Server
const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (request, socket, head) => {
    const parsed = url.parse(request.url, true);
    if (parsed.pathname === '/tunnel/connect') {
        wss.handleUpgrade(request, socket, head, (ws) => {
            wss.emit('connection', ws, request);
        });
    } else {
        socket.destroy();
    }
});

wss.on('connection', (ws, request) => {
    const parsed = url.parse(request.url, true);
    const tunnelId = parsed.query.tunnelId;
    const authToken = parsed.query.token;
    const domainsParam = parsed.query.domains || '';
    const clientIp = request.headers['x-forwarded-for'] || request.socket.remoteAddress;

    if (!tunnelId || !authToken) {
        ws.close(4001, 'Autentikasi Tunnel Diperlukan');
        return;
    }

    const domainList = domainsParam.split(',').map(d => d.trim().toLowerCase()).filter(Boolean);
    const domainsSet = new Set(domainList);

    console.log(`[TUNNEL CONNECTED] ID: ${tunnelId} | IP: ${clientIp} | Domains: ${domainList.join(', ')}`);

    // Register active tunnel
    activeTunnels.set(tunnelId, {
        ws,
        domains: domainsSet,
        authToken,
        connectedAt: Date.now(),
        ip: clientIp
    });

    // Update routing map
    for (const d of domainsSet) {
        domainToTunnelMap.set(d, tunnelId);
    }

    // Send ACK to Android Phone
    ws.send(JSON.stringify({
        type: 'AUTH_SUCCESS',
        tunnelId: tunnelId,
        message: 'Tunnel terhubung dengan sukses ke SFR Host Public Gateway',
        registeredDomains: Array.from(domainsSet),
        gatewayHost: request.headers.host || 'sfrhost.gateway'
    }));

    // Handle incoming messages from Android phone
    ws.on('message', (data) => {
        try {
            const msg = JSON.parse(data.toString());

            // Android Phone sends HTTP Response back for a pending visitor request
            if (msg.type === 'HTTP_RESPONSE') {
                const pending = pendingRequests.get(msg.requestId);
                if (pending) {
                    clearTimeout(pending.timer);
                    pendingRequests.delete(msg.requestId);

                    const clientRes = pending.res;
                    const statusCode = msg.statusCode || 200;
                    const headers = msg.headers || {};

                    // Add gateway diagnostic headers
                    headers['X-Powered-By'] = 'SFR-Host-Android-Origin';
                    headers['X-Origin-Device'] = 'Android-Phone';

                    clientRes.writeHead(statusCode, headers);

                    if (msg.bodyBase64) {
                        const bodyBuf = Buffer.from(msg.bodyBase64, 'base64');
                        clientRes.end(bodyBuf);
                    } else {
                        clientRes.end();
                    }
                }
            } else if (msg.type === 'HEARTBEAT') {
                ws.send(JSON.stringify({ type: 'HEARTBEAT_ACK', timestamp: Date.now() }));
            } else if (msg.type === 'UPDATE_DOMAINS') {
                const newDomains = (msg.domains || []).map(d => d.trim().toLowerCase());
                const tunnelInfo = activeTunnels.get(tunnelId);
                if (tunnelInfo) {
                    // Remove old domain mappings for this tunnel
                    for (const oldD of tunnelInfo.domains) {
                        if (domainToTunnelMap.get(oldD) === tunnelId) {
                            domainToTunnelMap.delete(oldD);
                        }
                    }
                    tunnelInfo.domains = new Set(newDomains);
                    for (const newD of newDomains) {
                        domainToTunnelMap.set(newD, tunnelId);
                    }
                    console.log(`[DOMAINS UPDATED] Tunnel ${tunnelId} now routes: ${newDomains.join(', ')}`);
                }
            }
        } catch (err) {
            console.error('[MSG ERROR]', err.message);
        }
    });

    ws.on('close', () => {
        console.log(`[TUNNEL DISCONNECTED] ID: ${tunnelId}`);
        const info = activeTunnels.get(tunnelId);
        if (info) {
            for (const d of info.domains) {
                if (domainToTunnelMap.get(d) === tunnelId) {
                    domainToTunnelMap.delete(d);
                }
            }
        }
        activeTunnels.delete(tunnelId);
    });

    ws.on('error', (err) => {
        console.error(`[WS ERROR ${tunnelId}]`, err.message);
    });
});

function escapeHtml(str) {
    return (str || '').replace(/[&<>'"]/g, tag => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        "'": '&#39;',
        '"': '&quot;'
    }[tag] || tag));
}

server.listen(HTTP_PORT, () => {
    console.log(`=======================================================`);
    console.log(`🚀 SFR Host Public Gateway running on port ${HTTP_PORT}`);
    console.log(`📡 WebSocket endpoint: ws://0.0.0.0:${HTTP_PORT}/tunnel/connect`);
    console.log(`🌐 Ready to route custom domains to Android origin servers`);
    console.log(`=======================================================`);
});
