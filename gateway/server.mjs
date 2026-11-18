// Single-instance BYO infrastructure. Deploy behind a TLS reverse proxy, never expose HTTP to phones.
import http from "node:http";
import { readFileSync } from "node:fs";
import { authenticate, makeBudget, boundedBody } from "./security.mjs";
const env = (name) => {
  if (!process.env[name]) throw Error("Missing configuration: " + name);
  return process.env[name];
};
const cfg = { issuer: env("OIDC_ISSUER"), audience: env("OIDC_AUDIENCE"), jwks: env("OIDC_JWKS_URL") };
if (![cfg.issuer, cfg.jwks].every((u) => new URL(u).protocol === "https:"))
  throw Error("OIDC requires TLS");
const keyFile = env("GEMINI_KEY_FILE"),
  salt = env("RATE_HASH_SECRET");
if (salt.length < 32) throw Error("Use a strong rate hash secret");
const budget = makeBudget(env("RATE_DB"), salt),
  models = new Set((process.env.ALLOWED_MODELS || "gemini-3.1-flash-lite").split(","));
let cached = [],
  expires = 0,
  pending;
const keys = async () => {
  if (Date.now() < expires) return cached;
  if (!pending)
    pending = (async () => {
      const r = await fetch(cfg.jwks, { redirect: "error", signal: AbortSignal.timeout(4000) });
      if (!r.ok) throw Error("JWKS");
      const data = await r.json();
      if (!Array.isArray(data.keys) || data.keys.length > 50) throw Error("JWKS");
      cached = data.keys;
      expires = Date.now() + 300000;
      return cached;
    })().finally(() => (pending = null));
  return pending;
};
const live = new Map();
let total = 0;
const server = http.createServer(
  { maxHeaderSize: 16384, requestTimeout: 20000, headersTimeout: 10000 },
  async (req, res) => {
    const reject = (n, msg) => {
      res.writeHead(n, {
        "content-type": "application/json",
        "cache-control": "no-store",
        ...(n === 429 ? { "retry-after": "60" } : {}),
      });
      res.end(JSON.stringify({ error: { code: n, message: msg } }));
    };
    let subject,
      held = false;
    try {
      // Never trust client-supplied forwarding headers for an identity. The TLS frontend should also limit IPs.
      if (!budget("edge:" + req.socket.remoteAddress, [["ingress", 60000, 240]]))
        return reject(429, "Ingress budget exceeded");
      const route =
        /^\/v1beta\/models\/(gemini-[a-z0-9.\-]+)(:generateContent|:streamGenerateContent\?alt=sse)?$/.exec(
          req.url || "",
        );
      if (!route || !models.has(route[1]) || req.method !== (route[2] ? "POST" : "GET"))
        return reject(404, "Unsupported model or route");
      try {
        subject = await authenticate(req.headers.authorization, cfg, keys);
      } catch {
        return reject(401, "Valid scoped access token required");
      }
      if (total >= 24 || (live.get(subject) || 0) >= 2) return reject(429, "Concurrency limit");
      held = true;
      total++;
      live.set(subject, (live.get(subject) || 0) + 1);
      if (
        !budget(subject, [
          ["minute", 60000, 8],
          ["daily", 86400000, 60],
          ["global", 86400000, 1000],
        ])
      )
        return reject(429, "Request budget exceeded");
      let body;
      if (req.method === "POST") {
        if (!req.headers["content-type"]?.startsWith("application/json"))
          return reject(415, "JSON required");
        const chunks = [];
        let size = 0;
        for await (const c of req) {
          if ((size += c.length) > 180000) return reject(413, "Payload too large");
          chunks.push(c);
        }
        try {
          body = JSON.stringify(boundedBody(JSON.parse(Buffer.concat(chunks))));
        } catch {
          return reject(400, "Unsupported request");
        }
      }
      const secret = readFileSync(keyFile, "utf8").trim();
      if (!secret) throw Error("Missing provider secret");
      const controller = new AbortController(),
        timer = setTimeout(() => controller.abort(), 35000);
      res.on("close", () => controller.abort());
      try {
        const upstream = await fetch("https://generativelanguage.googleapis.com" + req.url, {
          method: req.method,
          headers: { "x-goog-api-key": secret, "content-type": "application/json" },
          body,
          signal: controller.signal,
          redirect: "error",
        });
        if (!upstream.ok) return reject(upstream.status, "Provider request rejected");
        res.writeHead(200, {
          "cache-control": "no-store",
          "content-type": route[2]?.includes("stream") ? "text/event-stream" : "application/json",
        });
        let bytes = 0;
        for await (const chunk of upstream.body) {
          if ((bytes += chunk.length) > 260000) {
            controller.abort();
            break;
          }
          res.write(chunk);
        }
        res.end();
      } finally {
        clearTimeout(timer);
        controller.abort();
      }
    } catch {
      if (!res.headersSent) reject(503, "Gateway temporarily unavailable");
      else res.destroy();
    } finally {
      if (held) {
        total--;
        const n = live.get(subject) - 1;
        if (n) live.set(subject, n);
        else live.delete(subject);
      }
    }
  },
);
server.listen(Number(process.env.PORT || 8080), process.env.BIND_HOST || "127.0.0.1");
