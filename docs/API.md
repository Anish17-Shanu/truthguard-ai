# API Reference

## Conventions

- **Browser clients** call the Next.js BFF at `/api/*`. The BFF stores the JWT in
  an httpOnly `tg_session` cookie and forwards it as `Authorization: Bearer` to
  the backend. No token is exposed to browser JavaScript.
- **Direct backend calls** use `Authorization: Bearer <jwt>`.
- **AI service calls** are server-to-server only and require `X-Internal-Token`.
- Errors from the backend use one envelope:

  ```json
  {
    "code": "validation_failed",
    "message": "Validation failed",
    "requestId": "f0c1…",
    "timestamp": "2026-10-29T05:00:00Z",
    "details": { "email": "must be a well-formed email address" }
  }
  ```

- Send `X-Request-ID` to correlate logs; the backend/AI echo it back.
- JSON fields are camelCase at the backend, snake_case inside the AI service.

## BFF routes (Next.js)

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register` | Register; sets the session cookie |
| POST | `/api/auth/login` | Log in; sets the session cookie |
| POST | `/api/auth/logout` | Revoke session server-side and clear the cookie |
| GET | `/api/auth/me` | Current session user |
| DELETE | `/api/account` | Delete account (password required) |
| GET | `/api/account/export` | Download account JSON |
| POST | `/api/analyze` | Analyze text, URL, or file (multipart) |
| GET | `/api/history?page=&size=` | Paginated analysis history |
| GET | `/api/history/{id}` | One analysis |
| DELETE | `/api/history/{id}` | Delete one analysis |
| POST | `/api/feedback` | Submit feedback for an analysis |
| GET | `/api/stats/me` | Personal usage stats |
| GET | `/api/admin/stats` | Admin aggregates (ADMIN only) |
| GET | `/api/admin/feedback?page=&size=` | Feedback queue (ADMIN only) |
| POST | `/api/admin/retrain` | Queue retraining (ADMIN only; 501 when disabled) |

State-changing BFF routes reject cross-origin requests using the `Origin` header
against `SITE_URL`/the forwarded host.

## Backend (Spring Boot)

### Auth

`POST /auth/register`
```json
{ "fullName": "Analyst", "email": "analyst@example.com", "password": "StrongPass123" }
```
Password: 10+ characters with at least one letter and one number. Returns
`accessToken`, `expiresIn`, `userId`, `fullName`, `email`, `role`.

`POST /auth/login`
```json
{ "email": "analyst@example.com", "password": "StrongPass123" }
```
Generic `401` on bad credentials (no account enumeration).

`POST /auth/logout` — revokes the session's `jti`. Idempotent.

`GET /auth/me` — `{ userId, fullName, email, role, createdAt }`.

`GET /auth/me/export` — JSON attachment with profile, analyses, and feedback.

`DELETE /auth/me`
```json
{ "password": "StrongPass123" }
```
Deletes the user, analyses, feedback, and AI-side feedback. `204`.

### Analysis

`POST /analyze` (`multipart/form-data`, auth required)

Exactly one of:

| Field | Type | Limits |
| --- | --- | --- |
| `text` | string | 20–20,000 characters, English |
| `url` | string | http/https, public host, robots-respecting, 5 MB / 15 s fetch |
| `file` | file | `.pdf` (≤ 10 MB, ≤ 200 pages) or `.txt` (≤ 10 MB), magic-byte checked |

Response:
```json
{
  "analysisId": "665f…",
  "sourceType": "text",
  "inputSnippet": "First 280 characters…",
  "label": "fake",
  "confidence": 0.91,
  "biasScore": 0.5,
  "emotion": "fear",
  "language": "en",
  "highlightedText": ["cover-up", "agenda"],
  "suspiciousPhrases": ["agenda"],
  "modelVersion": "v2026.10.29.1200",
  "classifierMode": "transformer",
  "createdAt": "2026-10-29T05:00:00Z"
}
```

`label` may be `real`, `fake`, `propaganda`, or `uncertain`.
`classifierMode` is `transformer` or `heuristic`; heuristic results are not
model predictions and the UI says so.

Errors: `400` malformed/unsupported, `413` too large, `422` content too short,
`429` quota exceeded, `503` model upstream unavailable.

`GET /history?page=0&size=20` → `{ items, total, page, size }` (size capped at 50).

`GET /history/{id}` → one analysis; `404` if not owned by the caller.

`DELETE /history/{id}` → `204`; `404` if not owned.

### Feedback

`POST /feedback`
```json
{
  "analysisId": "665f…",
  "correct": false,
  "correctedLabel": "real",
  "comment": "The quote is fabricated; see the primary source."
}
```
`correctedLabel` ∈ `real|fake|propaganda|uncertain`; comment ≤ 1000 chars. The
record is stored by the backend and forwarded to the AI service (best effort).

### Stats and admin

- `GET /stats/me` — personal totals.
- `GET /admin/stats` — totals, label counts, users, feedback count, scans today,
  active model version, 14-day trend. ADMIN only.
- `GET /admin/feedback?page=&size=` — feedback queue. ADMIN only.
- `POST /admin/retrain` — queues retraining; returns `501` while
  `RETRAIN_ENABLED=false` (the default). ADMIN only.

### Health

- `GET /health` — liveness.
- `GET /ready` — readiness with Mongo and AI status, plus
  `{ ready, classifierMode, modelVersion }` when the AI service responds.

## AI service (FastAPI, internal)

All routes except `/health` and `/ready` require
`X-Internal-Token: <AI_INTERNAL_TOKEN>` (constant-time comparison). In
production the token is mandatory and `/docs` is disabled.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Liveness |
| GET | `/ready` | `{ ready, classifier_mode, model_version }` |
| POST | `/analyze` | `{ text?, url?, fileBase64?, fileName? }` → analysis |
| POST | `/feedback` | Persist feedback (202) |
| POST | `/retrain` | 501 unless `RETRAIN_ENABLED=true` |
| GET | `/models/current` | Active version, mode, metrics, trained_at |
| DELETE | `/internal/users/{userId}/data` | Remove a user's feedback |

`POST /analyze` accepts exactly one source. `fileBase64` requires `fileName`
and a `.pdf`/`.txt` extension. Long text is truncated to 20,000 characters.
Non-English input is rejected when `REQUIRE_ENGLISH=true`.

Response adds `classifier_mode` and `content_hash` (SHA-256 of normalized text)
to the backend shape above (snake_case).
