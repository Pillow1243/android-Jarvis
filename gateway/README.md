# Managed inference gateway (optional)

The Android app works without this service using personal-key mode. This is a deployable **single-instance reference gateway**, not a public shared proxy or a deployed commercial service. It uses only Node built-ins; use **Node 22.18+**. `node --test gateway/security.test.mjs` exercises JWT verification, request limits and content bounds without a provider secret.

## Deployment contract

1. Put a TLS reverse proxy with request-size/time limits and ingress/IP throttling in front of the process. The service binds to `127.0.0.1` by default. Use a private container network if you set `BIND_HOST`. Do not expose the plain HTTP listener publicly. Do not log request bodies, cookies, Authorization headers or provider responses.
2. Configure a trusted OIDC issuer to issue **RS256 signed access JWTs**, at most one hour long, with a dedicated audience and the `nexus:plan` scope. Use your own sign-in client (authorization-code/PKCE for mobile). This distribution supplies a manual short-lived token session for managed testing, not an identity provider or consumer login UI. Never paste a refresh token or provider key into this field.
3. Mount the provider key as a read-only secret file. Set the following non-APK environment configuration:

| Variable | Purpose |
|---|---|
| `OIDC_ISSUER`, `OIDC_AUDIENCE`, `OIDC_JWKS_URL` | Exact issuer and audience; operator-pinned TLS JWKS endpoint. No token-controlled URLs. |
| `GEMINI_KEY_FILE` | Path to a protected Gemini key. Read per request; rotate by atomically replacing the mounted secret. |
| `RATE_DB` | Private persistent SQLite file. WAL and atomic transactions enforce per-principal and global budgets across restarts. |
| `RATE_HASH_SECRET` | High-entropy server-side pseudonymization secret (at least 32 characters). |
| `ALLOWED_MODELS` | Comma-separated list; defaults to the same Flash-Lite model as the app. |
| `PORT`, `BIND_HOST` | Optional internal listener configuration. |

4. Run `node gateway/server.mjs` as a non-root user with permission only for the key and rate database. Put provider-side project limits, billing alerts, secret rotation and operational monitoring in place. A multi-replica deployment needs a shared transactional rate store, not independent local databases.
5. In **Settings → Gemini provider → Managed gateway session**, enter the TLS origin (without path or trailing slash), scoped access token and allowed model. Review the destination and test the function call. The token remains in process memory. A personal key is never sent to this origin.

## What is enforced

Only model metadata GET, `:generateContent` and `:streamGenerateContent?alt=sse` POST for an allowlisted model are forwarded, always to Google's fixed Developer API HTTPS origin. No forwarding of custom URLs or arbitrary headers. JWT signature, issuer, audience, scope, subject and validity window are required. Unknown keys fail closed. JWKS caching is bounded. Closed text/function content types, named tool allowlist, 180 KB request cap, 260 KB response cap, 1,600 output tokens, upstream cancellation, per-user concurrency 2 and server concurrency 24. Budgets: 8 requests/minute and 60/day per authenticated principal; 1,000/day globally. The proxy must separately limit abusive inbound connections and distributed IP traffic. No prompts, transcripts, token strings or provider keys enter the budget DB.

For public distribution, commission a security and privacy review, operate your own TLS/identity service and secrets manager, test live provider traffic, and sign a non-debuggable mobile release with your protected distribution key. No production signing or hosting credentials are included here.
