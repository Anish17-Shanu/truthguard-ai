# Architecture

## Services

| Service | Runtime | Responsibility |
| --- | --- | --- |
| `frontend` | Next.js 14 (App Router) | UI and BFF. Serves pages, holds the session cookie, proxies API calls server-side. |
| `backend` | Spring Boot 3 / Java 21 | Auth, sessions, RBAC, quotas, analysis persistence, feedback, admin analytics. |
| `ai-service` | FastAPI / Python 3.11 | Ingestion, English gate, ONNX inference, deterministic signals and explanations. |
| MongoDB | Atlas M0 or local | Shared database: `users`, `sessions`, `analyses`, `feedback`, `models`, `audit_logs`. |

There is no Redis or job queue. Rate limits are per instance and state is in
MongoDB. The AI service is stateless apart from its model and feedback writes.

## Request flow

```
Browser
  │  fetch /api/* (httpOnly tg_session cookie)
  ▼
Next.js route handler (BFF)
  │  Authorization: Bearer <jwt>   (server-side only)
  ▼
Spring Boot ──► MongoDB
  │  X-Internal-Token + X-Request-ID
  ▼
FastAPI
  │  ingestion (text / URL / file) → language gate → classifier
  ▼
Analysis response → backend persists → BFF returns JSON
```

- The browser never sees the JWT; only an httpOnly, SameSite=Lax cookie.
- Logout deletes the server-side session (`jti`), not just the cookie.
- Every state-changing BFF route checks the request Origin.

## Analysis pipeline

1. **Ingestion** — exactly one source: raw text, a URL fetched under SSRF
   protections (public hosts only, redirect revalidation, robots.txt, size/time
   caps), or a `.txt`/`.pdf` file validated by magic bytes (10 MB, 200 pages).
2. **Normalization and limits** — whitespace collapsed, 20,000-character cap,
   minimum length check.
3. **Language gate** — English only at launch; non-English input is rejected.
4. **Classification** — int8 ONNX encoder. The manifest carries labels, max
   length, temperature, uncertainty threshold, metrics, and the model SHA-256.
   Confidence below the threshold returns `uncertain`.
5. **Deterministic explanation** — lexicon hits (propaganda, sensational, bias),
   emotion hint, and token-occlusion attribution. No generated text.
6. **Persistence** — backend stores the label, confidence, signals, snippet,
   `content_hash`, and model version; the AI feedback collection is updated when
   the user submits a correction.

## Degraded mode

If the ONNX artifacts are missing, the AI service starts in explicit
`heuristic` mode (lexicon scoring) and every response says
`classifier_mode: heuristic`. In production the service refuses to start in
that state unless `ALLOW_HEURISTIC_FALLBACK=true`; the UI labels heuristic
results as not being model predictions.

Backend resilience: timeouts, a circuit breaker around AI calls, and stable
error mapping (`503` when the AI service is unavailable). Results are never
silently fabricated.

## Data model

| Collection | Contents | Notes |
| --- | --- | --- |
| `users` | profile, BCrypt hash, role | unique email index |
| `sessions` | JWT `jti`, user, expiry | TTL index; logout removes the record |
| `analyses` | label, signals, snippet, content hash, model version | user+created index; retention purge |
| `feedback` | analysis id, predicted/corrected label, comment | unique per analysis/user in AI store |
| `models` | version, metrics, trained_at | populated by training tooling |
| `audit_logs` | actor, action, route, status | retention-bound |

## Security controls

- HMAC JWT validated on every backend request; session revocation via `jti`.
- BCrypt password hashing; generic login errors; 10+ character policy.
- RBAC (`USER`, `ADMIN`) on `/admin/**`.
- Per-IP request limit plus per-user analysis quotas.
- AI service authenticated with a constant-time shared-token check.
- SSRF-safe fetching and magic-byte upload validation.
- CSP, HSTS, `frame-ancestors 'none'`, `nosniff`, restrictive Permissions-Policy.
- Startup validation rejects weak/missing secrets, wildcard CORS, and missing
  production configuration.
- Account export and deletion; bounded retention; no content in logs.

## Deployment shape

Frontend on Vercel; backend and AI service on Render free Docker services;
MongoDB Atlas M0; model artifact on a public HTTPS release URL, verified by
SHA-256 at startup. See [DEPLOYMENT.md](DEPLOYMENT.md).
