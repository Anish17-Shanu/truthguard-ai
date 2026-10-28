# TruthGuard AI

Fake-news and propaganda **screening** for English text, built as three
services. Created and maintained by **Anish Kumar**.

TruthGuard estimates whether a piece of news-like text shows patterns
associated with misinformation or propaganda. It is a screening aid, **not a
fact-checker**: it cannot verify events, quotes, or sources, and a `real` label
is not proof of authenticity.

## How it works

| Service | Stack | Responsibility |
| --- | --- | --- |
| `frontend/` | Next.js 14 App Router | UI + BFF. httpOnly cookie session; server route handlers proxy the API. |
| `backend/` | Spring Boot 3 / Java 21 | Auth, sessions, RBAC, quotas, analysis storage, admin analytics. |
| `ai-service/` | FastAPI / Python 3.11 | Ingestion, English gate, classifier inference, deterministic explanations. |

Detection uses a **fine-tuned transformer encoder** (`distilbert-base-uncased`)
exported to int8 ONNX, plus deterministic lexicons and token-occlusion
attribution. There is **no generative LLM** in the request path and no
third-party AI API: inference is local and has zero marginal cost.

Labels: `real`, `fake`, `propaganda`, plus `uncertain` when calibrated
confidence falls below the manifest threshold.

## Important: model artifact required for production

This repository does **not** ship trained weights. Without an artifact the AI
service:

- in development: serves clearly-labelled **heuristic** results
  (`classifier_mode: heuristic`), or
- in production: **refuses to start**, unless `ALLOW_HEURISTIC_FALLBACK=true`
  is set explicitly.

To train, evaluate, and publish a model, follow
[`ai-service/training/README.md`](ai-service/training/README.md) and read the
[model card](ai-service/MODEL_CARD.md). The artifact is a checksummed
`model.zip` downloaded at startup via `MODEL_ARTIFACT_URL` +
`MODEL_ARTIFACT_SHA256`.

## Quick start with Docker

```bash
cp .env.example .env          # local dev values; change secrets for anything public
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- AI service: http://localhost:8000 (docs at `/docs` in development)

Compose runs the AI service in explicit heuristic mode so the stack boots
without weights. The UI labels those results as heuristic.

Register the first account, then log in. An admin account is created only when
`BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are set.

## Local development

Prerequisites: Java 21, Maven 3.9+, Node 20+, Python 3.11, MongoDB 7 (or the
Compose `mongo` service).

```bash
# 1. Database
docker compose up -d mongo

# 2. AI service (serving deps only; no torch)
cd ai-service
python -m venv .venv
.venv/Scripts/activate            # Windows; source .venv/bin/activate elsewhere
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# 3. Backend
cd backend
mvn spring-boot:run

# 4. Frontend
cd frontend
npm ci
npm run dev
```

Environment files: [`frontend/.env.example`](frontend/.env.example),
[`backend/.env.example`](backend/.env.example),
[`ai-service/.env.example`](ai-service/.env.example). Copy to `.env` (or
`.env.local` for Next.js) and fill in values. Never commit real secrets.

### Key configuration

| Variable | Service | Notes |
| --- | --- | --- |
| `MONGODB_URI` | backend, AI | Atlas M0 works; snippets only, no full articles |
| `JWT_SECRET` | backend | Base64, decodes to 32+ bytes: `openssl rand -base64 48` |
| `AI_INTERNAL_TOKEN` | backend + AI | Same value on both, 24+ characters |
| `AI_SERVICE_BASE_URL` | backend | e.g. `http://ai-service:8000` |
| `BACKEND_URL` | frontend | Server-side URL of the backend BFF target |
| `SITE_URL` | frontend | Public origin, used for origin checks and canonical URLs |
| `MODEL_ARTIFACT_URL` / `MODEL_ARTIFACT_SHA256` | AI | Optional in dev, required in production |
| `ALLOW_HEURISTIC_FALLBACK` | AI | Keep `false` in production |

Production startup validates secrets and CORS, requires a model artifact, and
disables API docs.

## Testing

```bash
cd backend && mvn test                 # 13 tests
cd ai-service && pytest                # 24 tests
cd frontend && npx tsc --noEmit && npm run build
```

CI (`.github/workflows/ci.yml`) runs all three suites, builds every Docker
image, and scans for committed secrets.

## Deployment

A root [`render.yaml`](render.yaml) blueprint provisions the three free-tier
web services and a shared internal-token env group. See
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for the ordered setup, free-tier
limits, Atlas configuration, and the Vercel alternative for the frontend.

## Security and privacy

- httpOnly `tg_session` cookie; the browser never holds a bearer token.
- Server-side sessions with `jti` revocation on logout.
- SameSite=Lax cookies plus Origin checks on state-changing API routes.
- SSRF-safe URL ingestion (private/loopback/link-local rejection, redirect
  revalidation, robots.txt, size/time limits) and magic-byte file validation.
- Per-user analysis quotas and per-IP rate limits.
- Account data export and deletion; configurable analysis retention.
- Uploaded text is sent only between your own services. No AI vendor receives
  user content.

Details: [architecture](docs/ARCHITECTURE.md), [API](docs/API.md),
[production readiness](docs/PRODUCTION_READINESS.md).

## Status

Implemented and verified in this repository: cookie/BFF auth, analysis for
text/URL/PDF/TXT, history and deletion, feedback, admin analytics, quotas,
legal pages, tests, CI, Docker, and the offline training/evaluation/export
pipeline.

Not included: trained weights, dataset files (licences vary), email
verification/password reset, and managed backups on Atlas M0. See the readiness
document for the remaining operational gates.
