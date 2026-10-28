# Deployment Guide (free tier)

This is the recommended launch topology. Every component has a usable free
tier; all limits below were checked when this document was written and should be
re-checked before launch.

| Component | Host | Free-tier notes |
| --- | --- | --- |
| Frontend (Next.js + BFF) | Vercel Hobby | No instance-hour quota; serverless route handlers |
| Backend (Spring Boot) | Render free Docker | 512 MB RAM, sleeps after ~15 min idle |
| AI service (FastAPI + ONNX) | Render free Docker | 512 MB RAM, sleeps, downloads the model at boot |
| Database | MongoDB Atlas M0 | 512 MB storage, no continuous backups |
| Model artifact | GitHub Release asset | Public https URL on port 443 |
| Uptime checks | UptimeRobot free | 5-minute interval; also keeps services warm |
| Database dumps | GitHub Actions → artifacts | Daily `mongodump`, 30-day retention |

**Do not launch on heuristic results.** The AI service refuses to start in
production without a checksummed model artifact, by design. Train and publish
weights first (see [`ai-service/training/README.md`](../ai-service/training/README.md)).

## 1. MongoDB Atlas

1. Create an M0 cluster and a database user with a strong unique password.
2. Create the database `truthguard`.
3. Network access: Render free services have dynamic egress, so a stable IP
   allowlist is not practical. `0.0.0.0/0` is the common tradeoff — compensate
   with a strong password, TLS (`mongodb+srv`), and least-privilege credentials.
4. Copy the SRV connection string and append the database name:
   `mongodb+srv://user:pass@cluster.mongodb.net/truthguard?retryWrites=true&w=majority`.

Indexes are created automatically at startup by the backend
(`MongoIndexInitializer`) and the AI service (`ModelManager.ensure_indexes`).

## 2. Train and publish the model

```bash
cd ai-service
python training/download_data.py --liar
python training/prepare_data.py
python training/train.py
python training/export_onnx.py --calibration training/runs/<version>/calibration.json
python training/evaluate.py --split test
```

The export prints `MODEL_ARTIFACT_SHA256`. Upload `artifacts/model.zip` to a
public GitHub release (or HTTPS storage) and keep both values for step 3.

## 3. Backend + AI service on Render

1. Push this repository to GitHub.
2. Render Dashboard → **New → Blueprint** → select the repository. Render reads
   [`render.yaml`](../render.yaml) and creates two web services plus the shared
   `AI_INTERNAL_TOKEN` env group.
3. Fill the prompted values:

   | Variable | Value |
   | --- | --- |
   | `AI_INTERNAL_TOKEN` | Any random string, 24+ characters; injected into both services |
   | `MONGODB_URI` | Atlas SRV URI (both services) |
   | `JWT_SECRET` | `openssl rand -base64 48` (Base64, ≥ 32 decoded bytes) |
   | `BOOTSTRAP_ADMIN_EMAIL` | Your admin login |
   | `BOOTSTRAP_ADMIN_PASSWORD` | 12+ characters, not a known weak value |
   | `MODEL_ARTIFACT_URL` | `https://github.com/<org>/<repo>/releases/download/<tag>/model.zip` |
   | `MODEL_ARTIFACT_SHA256` | Printed by `export_onnx.py` |
   | `AI_SERVICE_BASE_URL` | Public URL of the AI service, e.g. `https://truthguard-ai-service.onrender.com` |
   | `CORS_ALLOWED_ORIGINS` | Vercel URL, e.g. `https://truthguard.vercel.app` (https required in prod) |

   `AI_SERVICE_BASE_URL` and `CORS_ALLOWED_ORIGINS` only exist after the
   services are created; set placeholders, then update them from each service's
   Render page and redeploy the backend.
4. Wait for `/health` to pass on both services. The AI service downloads and
   verifies the model on first boot; this can take a minute on 0.1 CPU.
5. Verify:

   ```bash
   curl -s https://<backend>/health
   curl -s https://<backend>/ready
   curl -s https://<ai-service>/ready
   ```

   `/ready` on the backend should report `"ready":true` and
   `"classifierMode":"transformer"`.

## 4. Frontend on Vercel

1. Import the repository; set **Root Directory** to `frontend`.
2. Framework preset: Next.js. Build: `npm run build`.
3. Environment variables:

   | Variable | Value |
   | --- | --- |
   | `BACKEND_URL` | Public backend URL, e.g. `https://truthguard-backend.onrender.com` |
   | `SITE_URL` | `https://truthguard.vercel.app` (or your domain) |
   | `NEXT_PUBLIC_CONTACT_EMAIL` | Contact address shown in legal pages |

4. Deploy, then update `CORS_ALLOWED_ORIGINS` on the backend to the final
   Vercel domain and redeploy the backend.
5. Optional: attach a custom domain in Vercel; update `SITE_URL` and CORS to the
   custom domain, then verify TLS and HSTS behavior.

The frontend can alternatively run as the Docker image on any host; the BFF
requires server-side rendering, not a static export.

## 5. Smoke test the deployment

1. Open the site, register, and log in. Confirm the `tg_session` cookie is
   `HttpOnly`, `Secure`, and `SameSite=Lax`.
2. Analyze a text sample, a URL, and a small PDF/TXT file.
3. Check the result page shows `classifierMode: transformer` and a model
   version; heuristic warnings must not appear.
4. Submit feedback; check it appears at Admin → feedback.
5. Export and delete a test account; confirm login no longer works.
6. Confirm `POST /api/analyze` without a cookie returns `401`.
7. Confirm a non-English text is rejected with a clear message.

## 6. Backups

Atlas M0 has no continuous backups. `.github/workflows/backup.yml` runs a daily
`mongodump` inside the official `mongo:7` image and uploads a gzipped archive as
a GitHub Actions artifact (30-day retention).

Enable it by adding the repository secret `MONGODB_URI` (Settings → Secrets and
variables → Actions). Without the secret the scheduled job skips with a notice
instead of failing.

Restore drill:

```bash
# download the artifact from the workflow run, then:
docker run --rm -v "$PWD:/backup" mongo:7 \
  mongorestore --uri="$MONGODB_URI" --gzip --archive=/backup/truthguard.archive.gz
```

Run a restore into a scratch database at least once before launch.

## 7. Monitoring and operations

- Add UptimeRobot monitors for the frontend, backend `/health`, backend
  `/ready`, and AI `/ready` (5-minute interval).
- Use Render logs and `/actuator/health` (backend) for incident triage.
- Review Render instance hours weekly: two always-on free services exceed the
  account's 750 h/month. Idle sleep keeps this viable for low traffic, at the
  cost of cold starts.
- Rotate `JWT_SECRET` and `AI_INTERNAL_TOKEN` together on a schedule; rotating
  the JWT invalidates all sessions (users log in again).
- Roll back by deploying the previous Render commit and, for models, restoring
  the previous `MODEL_ARTIFACT_URL`/`SHA256` pair. Keep at least one previous
  `model.zip`.

## Free-tier limits and expectations

- **Cold starts:** the JVM may take 30–60 s to answer after sleep; the AI
  service must load/download the model. The UI shows loading states. If the
  first analysis times out, retry once the services are warm.
- **RAM:** the AI container loads an int8 DistilBERT (~70 MB file, roughly
  200–300 MB RSS). 512 MB is sufficient but leaves little headroom; do not run
  PyTorch in the serving image.
- **Storage:** Atlas M0 is 512 MB. Analyses store snippets and signals, not full
  articles; keep `ANALYSES_RETENTION_DAYS` bounded.
- **Traffic:** the backend enforces per-user quotas (`MAX_ANALYSES_PER_MINUTE`,
  `MAX_ANALYSES_PER_DAY`) to protect the free tier.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| AI service exits at boot | Missing/invalid `MODEL_ARTIFACT_URL` or SHA mismatch; check logs |
| Backend exits at boot | `JWT_SECRET` not valid Base64/32 bytes, missing `AI_INTERNAL_TOKEN`, wildcard CORS, or missing admin email in prod |
| `503 model_unavailable` | AI service asleep/unreachable or token mismatch between services |
| `401` on every request | Cookie not set (wrong `SITE_URL`/domain) or backend JWT secret changed |
| CORS error in browser console | `CORS_ALLOWED_ORIGINS` lacks the exact Vercel origin |
| Model download fails | URL not https/port 443, host private, size > `MODEL_ARTIFACT_MAX_MB`, or redirect to a private host |
