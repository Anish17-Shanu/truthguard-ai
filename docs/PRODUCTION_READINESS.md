# TruthGuard AI — Production Readiness Roadmap

**Owner:** Anish Kumar
**Last updated:** 2026-10-29
**Status:** Living document — this is the single source of truth for production work. Update checkboxes and the tracker as items land; do not create parallel planning docs.

## How to use this file

- Every item has a stable ID (`P0-01`, `D-03`, …). Reference IDs in commits and PRs.
- `[x]` = done and verified, `[ ]` = not done. Partial work stays unchecked with a `partial:` note.
- "Done when" is the acceptance test, not the implementation description. An item is only checked when its evidence exists (test, command output, screenshot, or deployed URL).
- Phases are ordered by dependency, not by perceived importance. Do not start a later phase while a `P0` blocker in an earlier one is open.
- Re-verify free-tier limits at implementation time — providers change them.

---

## 1. Locked decisions and constraints

| Decision | Value | Consequence |
|---|---|---|
| LLM policy | **No generative LLM anywhere** | Detection is a fine-tuned encoder classifier + deterministic rules/attribution. No OpenAI/Anthropic/Gemini calls. Zero marginal inference cost. |
| Hosting budget | **Free tier first** | Under ~1 GB RAM per service, cold starts, quota ceilings, no managed backups on Atlas M0. |
| Language scope | **English only at launch** | Non-English input is rejected with a clear message, not silently misclassified. |
| Training data | **Public datasets** | LIAR, ISOT, FakeNewsNet, WELFake, etc., subject to license review (see `D-01`). |
| Architecture | Next.js + Spring Boot + FastAPI (keep) | No rewrite. Harden what exists. |
| Team | Solo developer | Sequencing and free automation (GitHub Actions) matter more than parallelism. |

### Non-goals (do not build before launch gates pass)

- Fact-checking or verifying truth claims (the product estimates *style/signal-based risk*, not facts).
- generative summaries or "AI chat" over articles.
- Multilingual support, real-time social feeds, browser extensions, mobile apps.
- Scraping social platforms; only user-submitted text/URL/file.
- Billing/subscriptions (a free quota is enough for launch).

### Terminology rule

The classifier is a **fine-tuned transformer encoder**, not an LLM. Marketing copy, UI, and docs must say "transformer-based classifier" — this keeps claims accurate and avoids implying generative AI.

---

## 2. Launch gates (definition of production-ready)

The product is production-grade only when all of these are true and evidenced:

| Gate | Target (adjust only with written rationale) |
|---|---|
| Detection quality | Macro-F1 ≥ 0.85 and FPR ≤ 5% on a **domain-disjoint** English holdout; abstain/uncertain rate reported |
| Calibration | ECE ≤ 0.05; confidence shown to users is calibrated |
| Latency | p95 `POST /analyze` ≤ 2.0 s server-side on 1 vCPU; ≤ 4 s end-to-end for text inputs |
| Reliability | ≥ 99% monthly availability (free-tier realistic); < 1% 5xx over a 7-day window |
| Security | Zero critical/high findings unpatched > 7 days; zero secrets in git; authz matrix tested |
| Data safety | Tested restore from backup (RTO ≤ 4 h, RPO ≤ 24 h); account deletion works |
| Model safety | Rollback to previous model tested; evaluation gate blocks regressions |
| Accessibility | WCAG 2.1 AA on login, detection, results, admin; usable at 320 px width |
| Legal | Privacy policy, Terms, AI disclaimer, content policy published; dataset licenses cleared |
| Operations | Alerts fire for 5xx/AI-down/OOM; runbooks exist and were exercised |

---

## 3. Current state audit (2026-10-29)

Verified during implementation (2026-10-29):

- [x] Backend unit tests: `mvn verify` → 13 tests, 0 failures (Java 21). Covers auth/session revocation, JWT tampering, quotas, and feedback ownership fields.
- [x] AI service tests: `pytest` → 24 passed in a Python 3.11 venv. Covers API contracts, token enforcement, SSRF payloads, file magic bytes, and the heuristic pipeline.
- [x] Frontend: `npx tsc --noEmit` clean; `npm run build` succeeds (29 routes, including `robots.txt`, `sitemap.xml`, 404 and error boundaries).
- [x] Cookie/BFF auth end-to-end: httpOnly `tg_session`, server-side `jti` revocation, Origin checks; no active page uses `localStorage`.
- [x] Classifier mode is explicit (`transformer` | `heuristic`); production refuses to start without a model artifact unless explicitly overridden.
- [x] Fake retraining removed: `/retrain` returns 501 unless enabled; feedback is persisted and forwarded to the AI service.
- [x] Admin page consumes real `/admin/stats` and `/admin/feedback`; user pages cover history, deletion, feedback, export, and account deletion.
- [x] CI workflow runs backend tests, AI tests, frontend typecheck/build, Docker builds, and a secret scan; a daily `mongodump` workflow and Dependabot are added.
- [ ] No trained model artifact exists in the repository. The offline train/export/evaluate pipeline is implemented but must run on Python 3.11 with downloaded datasets.
- [ ] No public deployment has been performed. A local full-stack smoke test did run (below), but there is no automated Playwright E2E suite, load test, or accessibility audit.
- [ ] Email verification/password reset, domain-disjoint evaluation, observability dashboards, and restore drills remain open (see the phases below).

Local full-stack smoke test (Docker Compose: frontend, backend, AI service, MongoDB; 2026-10-29):

- [x] All four containers healthy; backend `/ready` reported MongoDB up, AI service up, classifier mode `heuristic` (explicit dev fallback).
- [x] Auth: register/login set `tg_session` with `HttpOnly`, `SameSite=Lax`, `Secure`; `/api/auth/me` 200. A token for a deleted account now returns 401 — this smoke test found a 500 NPE on `/auth/me` because all of `/auth/**` was public; `SecurityConfig` now permits only `register`, `login`, and `logout`.
- [x] Analyze: pasted text 200, `.txt` upload 200, public-URL ingestion 200, non-English 400, too-short text 400, fake `.pdf` rejected on magic bytes 400, 20,390-character input 413.
- [x] Defenses: unauthenticated analyze 401, cross-origin analyze 403, normal user hitting `/admin/stats` 403.
- [x] Data lifecycle: history pagination, feedback persisted with `userId`, analysis delete 204 then 404, account export downloads JSON with `Content-Disposition`, account deletion 204 removes the user, analyses, feedback, sessions, and audit rows and invalidates the token.
- [x] Admin: login, `/admin/stats`, `/admin/feedback`, and `/admin/retrain` returning the documented 503 envelope while retraining is disabled.
- [x] AI service: `/health` 200; `/models/current` 401 without the internal token and 200 with it; `/feedback` 401 without it.
- [x] Security headers on pages and exports: CSP, HSTS, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`.
- [x] Mobile: true 390 px viewport on landing, dashboard, and detection pages shows no horizontal document overflow; the account nav scrolls inside its own strip. The landing header now hides the redundant Login button below `sm` so the title no longer wraps.

---

## 4. Phase 0 — Stabilize the scaffold (P0 blockers)

Do these first; several later phases depend on them.

- [x] **P0-01** Fix the frontend Docker image: remove `COPY /app/public` or add `frontend/public/.gitkeep`; switch `npm install` → `npm ci`; add `.dockerignore`; run as non-root. *Done when:* `docker build frontend` succeeds from a clean clone and `docker compose up --build` boots all services.
- [x] **P0-02** Make model failures explicit: log the exception, expose `classifier_mode` (`transformer` | `fallback`) and `model_version` in `/analyze` responses. *Done when:* killing the model file produces a logged error and a documented degraded response, never a silent claim of AI confidence.
- [ ] **P0-03** Replace the random-head model with a real fine-tuned artifact (blocked by Phase 6–7). *Done when:* a versioned ONNX artifact with recorded metrics is what the service loads, and a golden-set test proves it — **Partial:** offline train/export/eval pipeline exists, but no artifact has been trained here.
- [x] **P0-04** Stop publishing fake retraining metrics: remove or disable the hardcoded F1/version bump until the real pipeline exists. *Done when:* no code path can register a model that was never evaluated.
- [x] **P0-05** Remove committed default secrets (JWT secret, bootstrap admin password); fail startup in `prod` if secrets are missing/default. *Done when:* `APP_ENV=production` with the example secret refuses to start.
- [x] **P0-06** Upgrade Next.js to the latest patched 14.2.x (or 15.x) and re-run `npm audit`. *Done when:* no known high/critical advisories remain for the lockfile.
- [x] **P0-07** Make the admin page honest: either consume `/admin/stats` or mark it "Coming soon" and hide it from non-admins. *Done when:* no page presents static text as operational data.
- [ ] **P0-08** Pin the runtime: document Python 3.11 for the AI service, add a lockfile (`uv lock` / `pip-compile`), and verify `pip install` in a clean 3.11 container. *Done when:* README instructions work on a clean machine — **Partial:** requirements are pinned and Python 3.11 is documented; no hash lockfile.
- [x] **P0-09** Add distinct `/health` (process alive) and `/ready` (model loaded, Mongo reachable) endpoints in the AI service; same split in the backend. *Done when:* orchestrators can gate traffic on readiness.
- [x] **P0-10** Add a minimal CI pipeline that runs backend tests, AI tests, and frontend lint/typecheck/build on every push (see `O-07`). *Done when:* a red build blocks merge.

---

## 5. Phase 1 — Data acquisition and evaluation assets

- [ ] **D-01** Dataset inventory and license review: LIAR, ISOT, FakeNewsNet, WELFake, PolitiFact/GossipCop splits. Record source URL, license/ToS, allowed use (research vs commercial), redistribution rules, and date accessed. Do not commit restricted data to git — **Partial:** sources and licence caveats documented; no per-dataset licence matrix.
- [ ] **D-02** Write a data card: provenance, label definitions, collection period, known biases, PII handling, and exclusions — **Partial:** model card covers provenance and bias; collection windows and PII handling are not filled in.
- [ ] **D-03** Deduplicate near-identical articles (MinHash/SimHash) before splitting; ISOT and WELFake contain syndicated copies that leak across splits — **Partial:** exact-duplicate removal only; no near-duplicate (MinHash) pass.
- [ ] **D-04** Define the label taxonomy. Recommend `real` / `fake` / `propaganda` / `uncertain`, with an explicit mapping table from each dataset's labels and a written rule for what counts as propaganda. If mapping is unreliable, ship binary `real` vs `misleading` and keep propaganda as a separate signal — **Partial:** taxonomy and mapping documented; the propaganda label remains subjective.
- [ ] **D-05** Build a **domain-disjoint, time-aware** test set: no publisher, URL, or near-duplicate overlap with training. This is the only set used for launch claims.
- [ ] **D-06** Write annotation guidelines and double-label a 300–500 item gold subset; compute inter-annotator agreement (Cohen's κ ≥ 0.7) before trusting labels.
- [ ] **D-07** Balance sources and topics; cap any single publisher/domain to avoid outlet-style shortcuts ("Reuters" ⇒ real) — **Partial:** per-class cap only; no per-publisher cap.
- [ ] **D-08** Store data snapshots outside git (R2 or release asset) with SHA-256 hashes and a manifest; record snapshot hash in every model's registry entry.
- [x] **D-09** English-only gate: add fast language identification (e.g., `lingua`/`fasttext` lid.176 small) and return a clear `unsupported_language` error. No LLM required.
- [ ] **D-10** Scrub obvious PII (emails, phone numbers, account handles) from training and evaluation text; document what remains.
- [ ] **D-11** Create a small, fixed golden set (200–500 items) committed as *labels + hashes only* (text fetched from storage) for CI regression tests.

---

## 6. Phase 2 — Model training, evaluation, packaging

- [ ] **M-01** Reproducible baseline: TF-IDF + logistic regression on the same splits. Record macro-F1/FPR. This is the number any transformer must beat.
- [ ] **M-02** Fine-tune `distilbert-base-uncased` (English, 66M params) and, if time permits, `MiniLM-L6` for a smaller/faster fallback. Training runs offline only.
- [ ] **M-03** Fix seeds, deterministic data order, pinned training environment, and log the git SHA + dataset hash per run — **Partial:** seed and pinned env; git SHA and dataset hash are not recorded per run.
- [x] **M-04** Calibrate probabilities (temperature scaling on a held-out calibration split). Do not ship raw softmax as "confidence".
- [x] **M-05** Define the decision policy: per-class thresholds, an `uncertain` band (abstain), and what the API/UI returns when abstaining. Never force a confident label on weak evidence.
- [ ] **M-06** Evaluation suite: macro-F1, per-class precision/recall, FPR on real news, ECE, confusion matrix, and subgroup slices (topic, outlet, length). Emit a machine-readable `metrics.json` per model — **Partial:** accuracy/F1/per-class/fake recall; no FPR, ECE, or topic/length slices.
- [ ] **M-07** Robustness suite: paraphrase, typos, headline-only, truncated body, ALL CAPS, satire, translated-looking text. Report degradation.
- [ ] **M-08** Shortcut audit: train with publisher names/URLs stripped and compare; inspect top features/attribution for leakage. If performance collapses, fix the data before shipping.
- [x] **M-09** Export to ONNX with int8 dynamic quantization; verify output parity against PyTorch (max absolute logit/probability delta documented).
- [ ] **M-10** Benchmark artifact size, cold-load time, RAM, and p95 latency on the target host class (1 vCPU, ≤ 1 GB). Budget: ONNX artifact ≤ 70 MB, resident memory ≤ 350 MB, load ≤ 5 s.
- [x] **M-11** Write a model card: intended use, out-of-scope use, training data, metrics on the domain-disjoint set, known failure modes, bias findings, and the calibration method.
- [ ] **M-12** Model registry record per version: version, git SHA, dataset hash, metrics, ONNX checksum, calibration params, created_at, active flag — **Partial:** manifest and Mongo registry exist; git SHA and dataset hash are not stored.
- [x] **M-13** Host artifacts outside the repo (GitHub Release or R2) with checksums; the runtime verifies the checksum at startup and refuses to serve a corrupt/missing artifact.
- [ ] **M-14** Champion/challenger rule: a new model replaces production only if it beats the current one on the golden set and does not regress FPR by more than 1 point — **Partial:** promotion is a documented manual process; no automated challenger comparison.

---

## 7. Phase 3 — Inference service and content ingestion

- [x] **I-01** Production runtime uses **ONNX Runtime only** (no PyTorch/`transformers` import in the serving image) to fit free-tier RAM. Keep PyTorch in a training-only dependency group.
- [ ] **I-02** Load and warm the tokenizer/model at startup; readiness flips only after a successful warmup inference — **Partial:** model loads during startup; no explicit warmup inference before readiness.
- [x] **I-03** Deterministic explanations: token attribution (integrated gradients/occlusion or attention rollout), the propaganda/bias lexicons, and sentence templates. No generative text anywhere.
- [x] **I-04** Enforce input limits: 20–20,000 characters for text, 256 tokens to the model, explicit 413/422 errors with codes.
- [x] **I-05** Language gate from `D-09`, applied before inference.
- [x] **I-06** SSRF-safe URL fetch: allow only `http`/`https`; resolve DNS and reject private/link-local/loopback/metadata ranges (including after redirects); cap redirects at 3; 5 MB and 15 s limits; validate `Content-Type`; set a descriptive User-Agent; honor `robots.txt`; block known paywall/login redirects. *Done when:* a test suite of SSRF payloads (localhost, 169.254.169.254, DNS rebinding, IPv6-mapped IPv4) is rejected.
- [ ] **I-07** File ingestion: verify magic bytes (not just extension), cap at 10 MB and 200 PDF pages, guard decompression bombs, decode with errors handled, and delete temp buffers immediately — **Partial:** magic bytes, size and page caps; no decompression-bomb test.
- [ ] **I-08** Concurrency config for 1 vCPU: one worker, bounded thread pool, `OMP_NUM_THREADS=1`, request timeout; document max concurrency — **Partial:** single uvicorn worker; OMP/thread tuning and load test are pending.
- [ ] **I-09** Latency budget documented and tested: extraction ≤ 1 s for typical URL/file, inference ≤ 1 s, total p95 ≤ 2 s server-side — **Partial:** no latency measurement has been run.
- [ ] **I-10** Error taxonomy with stable codes (`unsupported_language`, `content_too_short`, `fetch_blocked`, `extraction_failed`, `model_unavailable`); no stack traces or upstream bodies leaked to clients — **Partial:** backend has stable error codes; AI errors use plain messages.
- [x] **I-11** Authenticate backend→AI calls (shared secret/HMAC header) and keep the AI service off the public internet where the host allows it.
- [x] **I-12** Remove public `/retrain` or require an admin-signed request; scheduled retraining must not be triggerable by anonymous users.
- [ ] **I-13** Readiness includes model version and checksum; version is returned on every analysis — **Partial:** version is exposed; checksum is verified at load but not returned by readiness.
- [x] **I-14** Propagate a request ID from backend to AI service and include it in both logs.
- [ ] **I-15** (Optional) Cache results by content SHA-256 for repeated identical submissions; never cache across users in a way that leaks history.

---

## 8. Phase 4 — Backend and API hardening

- [ ] **B-01** Publish an OpenAPI spec (springdoc) and generate `docs/API.md` from it; keep the spec in CI — **Partial:** springdoc is present and docs/API.md is written by hand; spec is not generated or checked in CI.
- [x] **B-02** One error envelope for every failure: `{code, message, requestId, timestamp}`; no internal details.
- [x] **B-03** Validate all inputs: multipart size/type, text length, URL length/scheme, enum values, pagination bounds.
- [x] **B-04** AI client resilience: connect/read timeouts, bounded retries only for idempotent calls, circuit breaker, and a clear `503 model_unavailable` (no silent heuristic fallback in the backend).
- [ ] **B-05** Add request-level deduplication/idempotency key for `/analyze` to prevent double charges/duplicate records on retry.
- [ ] **B-06** Paginate `/history` (cursor or page/size, max 50) and add filters (label, date range) — **Partial:** pagination done; label/date filters are not implemented.
- [x] **B-07** Create Mongo indexes: `{userId, createdAt:-1}`, `{label, createdAt}`, `{createdAt}` for TTL-bound audit logs, and `{contentHash}` if dedup is added.
- [x] **B-08** Retention and deletion: configurable retention for analyses/feedback; `DELETE /me` and per-analysis delete; cascade to audit logs where required.
- [x] **B-09** Align feedback contracts: backend `analysisId` ↔ AI `analysis_id`; forward feedback to the AI service (or a shared collection) with `correctedLabel`, `comment`, `contentHash`, `modelVersion`, `createdAt`.
- [x] **B-10** Replace `findAll()`-based admin aggregation with Mongo aggregation pipelines; cap and index trend queries.
- [x] **B-11** Expose active model version and last retrain time on admin/health endpoints.
- [x] **B-12** Audit every admin action (retrain trigger, stats access, user role changes) with actor, IP, and result.
- [ ] **B-13** Rate limiting: per-user and per-IP, shared store (Upstash Redis free) or documented per-instance behavior, and **eviction** for stale IP entries (current map grows unbounded) — **Partial:** per-instance in-memory limits; no shared store or eviction audit.
- [x] **B-14** Graceful shutdown: drain in-flight analyses, close Mongo/HTTP pools.
- [x] **B-15** Health/readiness split (Mongo, AI reachability, model version).
- [x] **B-16** Remove unused `REDIS_URL` from docs or implement it; no dead configuration.
- [ ] **B-17** Structured JSON logs with request ID, user ID (hashed/pseudonymous), route, status, latency; never log tokens, passwords, or full content — **Partial:** request ID in logs; logs are not fully structured JSON.
- [x] **B-18** Startup config validation: refuse production startup on default JWT secret, wildcard CORS, missing Mongo URI, or missing admin bootstrap.
- [x] **B-19** CORS: exact origins, no wildcard with credentials; include `Vary: Origin`.
- [ ] **B-20** Version the API (`/v1/...`) before external clients exist.

---

## 9. Phase 5 — Authentication, security, privacy, legal

- [x] **A-01** Move tokens out of `localStorage`: httpOnly, `Secure`, `SameSite=Lax` cookies (or a BFF session), short-lived access + rotating refresh tokens.
- [x] **A-02** Add logout that clears cookies and revokes the refresh token (server-side session/denylist).
- [ ] **A-03** Add password reset and email verification via a free transactional provider (e.g., Resend/Brevo free tier); no password reset over unauthenticated email enumeration.
- [ ] **A-04** Password policy (min 10 chars, breached-password check via HIBP k-anonymity optional) and generic login errors to prevent enumeration — **Partial:** 10+ letters/numbers enforced; no breached-password check.
- [ ] **A-05** Login throttling/lockout (per account + IP), independent of the global rate limit — **Partial:** analysis quotas and per-IP limiting; no per-account login lockout.
- [ ] **A-06** Bootstrap admin only from env; force password change on first login; no default credentials in any environment — **Partial:** env bootstrap admin with validation; no forced first-login password change.
- [ ] **A-07** JWT secret: ≥ 256-bit random, rotated with a documented dual-key overlap; never committed — **Partial:** secret strength validated; rotation overlap is documented but not implemented.
- [x] **A-08** CSRF protection if cookie auth is used: `SameSite` + origin check or double-submit token on state-changing routes.
- [x] **A-09** Security headers: CSP (nonce/hash for Next.js), HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'`.
- [ ] **A-10** Authorization matrix tests for every route (anonymous, USER, ADMIN; cross-user access to `/history/{id}` must 403) — **Partial:** service-level ownership and token tests; no full route authz matrix.
- [ ] **A-11** Upload hardening validated with malicious payload tests (polyglot PDF, zip bomb, huge dimensions) — **Partial:** magic-byte rejection tests; no polyglot or archive-bomb suite.
- [ ] **A-12** Dependency hygiene: Dependabot/renovate, `npm audit`, `pip-audit`, OWASP dependency-check for Maven, monthly patch cadence — **Partial:** Dependabot added; pip-audit/OWASP checks are not in CI.
- [x] **A-13** Secret scanning in CI (`gitleaks`) and pre-commit hooks.
- [ ] **A-14** SAST (CodeQL free for public repos) and a baseline DAST scan (OWASP ZAP) against staging.
- [ ] **A-15** Mongo: dedicated least-privilege user per service, TLS, strong unique passwords; document the Atlas network-allowlist tradeoff on free hosts (dynamic egress may force `0.0.0.0/0` — compensate with credentials, TLS, and monitoring) — **Partial:** documented Atlas guidance; dedicated least-privilege users not provisioned here.
- [ ] **A-16** Redact PII in logs and error reports (email, tokens, content snippets); send only pseudonymous IDs to Sentry — **Partial:** no content or token logging; Sentry is not integrated.
- [x] **A-17** Publish Privacy Policy, Terms of Service, AI disclaimer ("estimates risk, not truth; not legal advice"), and content policy.
- [x] **A-18** Data lifecycle: disclose what is stored (snippet, signals, feedback), retention periods, export, and deletion; implement account deletion end-to-end.
- [ ] **A-19** Define abuse and defamation safeguards: never publish verdicts about named people/companies automatically; provide a correction/appeal path; log analyst overrides — **Partial:** disclaimers and human-review language; no appeal/correction workflow.
- [ ] **A-20** Threat model document (STRIDE) covering auth, analysis, ingestion, admin, and model supply chain.
- [x] **A-21** Per-user quotas and anomaly alerts (scan spikes, file-upload abuse) to protect free-tier limits.
- [ ] **A-22** Legal review of scraping and dataset use (copyright, robots.txt, database rights, dataset licenses). Store only derived snippets/hashes where possible.
- [ ] **A-23** Incident response plan: contacts, severity levels, disclosure template, 72-hour breach notification consideration.

---

## 10. Phase 6 — Frontend and product UX

- [x] **F-01** Replace `localStorage` auth with cookie-based session; middleware guard remains defense-in-depth, backend stays the source of truth.
- [ ] **F-02** Add logout and clear session-expiry handling (401 → login with return path) — **Partial:** logout is implemented; 401 redirect/refresh UX is not audited.
- [x] **F-03** Remove silent `catch(() => null)`; show actionable error states with retry.
- [ ] **F-04** Loading skeletons for dashboard/history/result; disable double submits — **Partial:** loading states exist; skeletons and double-submit coverage are not audited.
- [x] **F-05** Empty states (no history, no results, no feedback yet).
- [ ] **F-06** Accessible forms: visible labels (not placeholder-only), `aria-describedby` for errors, focus management, keyboard submit — **Partial:** visible labels and role=alert errors; aria-describedby/focus management incomplete.
- [ ] **F-07** WCAG 2.1 AA audit (axe + manual keyboard/screen-reader pass) on all flows.
- [ ] **F-08** Responsive audit at 320/375/430/768 px; verify no horizontal scroll, tap targets ≥ 44 px, readable tables/charts — **Partial:** responsive layout implemented; 320/375/430 px not browser-audited.
- [ ] **F-09** Respect `prefers-reduced-motion` for framer-motion animations.
- [x] **F-10** Admin page consumes `/admin/stats`: totals, users, label distribution, 7-day trend, active model version, feedback queue, retrain trigger (admin only, confirmed).
- [x] **F-11** Feedback UI on the results page: correct/incorrect, corrected label, comment; reflect submission state.
- [ ] **F-12** History: pagination, search/filter, per-item delete, link to result — **Partial:** pagination and delete done; search/filter missing.
- [x] **F-13** Show confidence **and** uncertainty ("abstained / low confidence") with plain-language bands; never display a bare percentage as certainty.
- [x] **F-14** Explanation panel copy: distinguish "signals we detected" from "proof"; link to methodology.
- [x] **F-15** Legal footer: Privacy, Terms, Disclaimer, Contact, status page.
- [x] **F-16** SEO/metadata for public pages (title/description/OG image, canonical), `robots.txt`, sitemap.
- [x] **F-17** Error boundary + 404/500 pages.
- [ ] **F-18** Performance budgets: LCP < 2.5 s on mid-tier mobile, CLS < 0.1, route JS budget documented; lazy-load charts.
- [ ] **F-19** Privacy-friendly analytics (Plausible/Umami self-host or free tier) with consent if required; no ad trackers.
- [x] **F-20** Export a result as JSON/PDF (client-side is fine); no server rendering cost.
- [ ] **F-21** Shareable result links only with explicit user action and expiry; no public enumeration of analysis IDs.
- [ ] **F-22** Session expiry and refresh UX (silent refresh where possible, no data loss on long forms) — **Partial:** cookie expiry is handled; no silent refresh UX.
- [x] **F-23** Mobile navigation (current sidebar collapses poorly) and admin link visibility by role.

---

## 11. Phase 7 — Feedback loop, retraining, and MLOps (no generative LLM)

- [ ] **L-01** Feedback export job: daily/weekly export from the backend/AI collection to a versioned snapshot, with content hashes and model versions.
- [ ] **L-02** Adjudication queue in admin: review low-confidence and disagreed feedback before it becomes training data; require two human labels for gold additions — **Partial:** admin feedback list exists; no adjudication state or two-label rule.
- [ ] **L-03** Active-learning sampling: prioritize low-confidence, `uncertain`, and user-overridden items for review.
- [ ] **L-04** Scheduled retraining outside the serving process (GitHub Actions cron or Cloud Run job). The in-process APScheduler is not durable on free hosts.
- [ ] **L-05** Reproducibility: seeds, pinned environment, data snapshot hash, git SHA recorded per run.
- [ ] **L-06** Evaluation gate as code: training fails the workflow unless golden-set macro-F1/FPR/ECE thresholds pass — **Partial:** gate runs in train/evaluate scripts; no golden set in CI.
- [ ] **L-07** Registry + rollback: activate a version by checksum; keep the previous artifact; document a one-command rollback — **Partial:** rollback documented; no one-command tool.
- [ ] **L-08** Shadow evaluation: score new models against a fixed sample offline before promotion (no need for live traffic).
- [ ] **L-09** Drift monitoring: weekly report on confidence distribution, abstain rate, label mix, input length/language, and feedback disagreement rate.
- [x] **L-10** Artifact integrity: verify SHA-256 at startup and in CI.
- [ ] **L-11** Data versioning: immutable snapshots; never retrain from a mutable collection in place.
- [x] **L-12** Retraining authorization and audit: only ADMIN may trigger; each run logs actor, dataset hash, and outcome.
- [ ] **L-13** Budget the pipeline: training on GitHub Actions/Colab free minutes; cap runtime and dataset size.
- [ ] **L-14** Document the full loop in `docs/MLOPS.md`: data → train → evaluate → package → deploy → monitor → rollback.

---

## 12. Phase 8 — Infrastructure, deployment, CI/CD (free tier)

- [ ] **O-01** Harden Dockerfiles: multi-stage, non-root user, pinned base image digests, `.dockerignore`, healthchecks, `npm ci`, no dev dependencies in runtime, no build secrets baked into layers — **Partial:** multi-stage, non-root, npm ci, .dockerignore; base images are not digest-pinned.
- [x] **O-02** Choose and document the topology. Recommended free-tier split:
  - Frontend: Vercel Hobby.
  - Backend: Render free (512 MB) or Cloud Run free (scale-to-zero).
  - AI service: Cloud Run free (1 GiB, min instances 0) or Hugging Face Spaces (2 vCPU/16 GB; verify commercial ToS). Render free's 512 MB is tight for the JVM + model and its 750 instance-hours/month is consumed by a single always-on service.
  - Database: MongoDB Atlas M0.
- [ ] **O-03** Environment management per environment (dev/staging/prod), secrets in the platform's secret store, no `.env` committed.
- [ ] **O-04** Backups: Atlas M0 has no continuous backup — schedule `mongodump` via GitHub Actions to R2 (30-day retention) and run a restore drill — **Partial:** daily mongodump workflow added; restore drill not run.
- [ ] **O-05** Cold-start plan: measure JVM and model cold starts; keep-alive ping if acceptable within quota; show a loading state; document expected latency.
- [ ] **O-06** Quota dashboard: Render instance hours, Atlas storage/connections, Cloud Run vCPU-seconds, Actions minutes, email/analytics limits.
- [x] **O-07** CI pipeline: backend (`mvn verify`), AI (`pytest`, `ruff`, `mypy`), frontend (`npm ci`, lint, `tsc --noEmit`, build), secret scan, dependency scan, Docker build.
- [ ] **O-08** CD: deploy on version tag with an approval gate; deploy hooks for Render/Cloud Run; migrations run as a separate step.
- [ ] **O-09** Branch protection: required checks, no direct pushes to `main`, signed commits optional.
- [ ] **O-10** Staging environment (can be the same free services with a `staging` project) for DAST and restore drills.
- [ ] **O-11** Schema migrations/versioning strategy for Mongo (index creation scripts, data migrations with rollback).
- [ ] **O-12** Rollback plan for app, model, and data migration; tested at least once per release train — **Partial:** documented; not exercised.
- [x] **O-13** Deployment docs updated (`docs/DEPLOYMENT.md`) with exact free-tier steps and limits.
- [ ] **O-14** Uptime monitoring (UptimeRobot free) on frontend, backend readiness, and AI readiness.
- [ ] **O-15** Cost/limit alerts and a monthly quota review checklist.
- [ ] **O-16** Custom domain + TLS (managed by Vercel/Render/Cloud Run); HSTS after verification.
- [ ] **O-17** Secrets rotation calendar (JWT, internal HMAC, Mongo, email, object storage).

---

## 13. Phase 9 — Observability and operations

- [ ] **V-01** Structured JSON logs across all three services with request IDs correlated end-to-end.
- [ ] **V-02** Metrics: request rate, p50/p95/p99 latency, 5xx rate, AI failures/timeouts, model version, abstain rate, feedback rate, memory RSS.
- [ ] **V-03** Dashboard (Grafana Cloud free) for the above; one screen for "is it healthy?".
- [ ] **V-04** Error tracking (Sentry free) with PII scrubbing and release tagging.
- [ ] **V-05** Alerts: 5xx > 2% for 5 min, p95 > 4 s, AI readiness down, OOM/restart loop, Mongo connection errors, cert expiry, quota > 80%.
- [ ] **V-06** SLOs + error budget documented; review monthly.
- [ ] **V-07** Public status page (free) and incident notices.
- [ ] **V-08** Runbooks: AI down, Mongo down, model regression, OOM, abuse spike, secret leak, bad deploy, model rollback, DB restore.
- [ ] **V-09** Solo on-call playbook: triage order, rollback-first bias, communication templates.
- [ ] **V-10** Weekly audit-log and feedback review cadence.

---

## 14. Phase 10 — Testing and release quality

- [ ] **Q-01** Backend unit tests: auth, analysis orchestration, admin aggregation, rate limiter, ownership checks. Target ≥ 70% on service/security packages — **Partial:** 13 backend tests; coverage is not measured.
- [ ] **Q-02** Integration tests with Testcontainers MongoDB (repositories, indexes, cascades).
- [ ] **Q-03** AI-client contract tests with WireMock (timeouts, 4xx/5xx mapping, malformed JSON).
- [ ] **Q-04** Schema contract tests: Java DTOs ↔ FastAPI models (snake_case fields, enums) generated or asserted in CI.
- [ ] **Q-05** E2E with Playwright: signup → login → analyze (text/URL/file) → result → feedback → history → admin stats; plus unauthorized access cases.
- [ ] **Q-06** Load test with k6: expected traffic and 3× burst; record error rate and p95 on the target host.
- [ ] **Q-07** Authz matrix tests for every endpoint; SSRF and upload payload suites — **Partial:** SSRF suite and ownership tests exist; no route-by-route authz matrix.
- [ ] **Q-08** Model regression test in CI on the golden set; block on threshold breaches.
- [ ] **Q-09** Accessibility automated checks (axe) in E2E plus a manual checklist.
- [ ] **Q-10** Fixtures and factories for tests; no live network calls in unit tests — **Partial:** unit tests avoid live network; factories are minimal.
- [ ] **Q-11** Flaky-test policy: quarantine, fix within a week, no blind retries.
- [ ] **Q-12** Release checklist template (version, changelog, migration, rollback, smoke tests).
- [ ] **Q-13** Failure drills quarterly: AI down, Mongo down, model rollback, restore from backup.
- [ ] **Q-14** Patch cadence: dependencies and base images monthly; emergency path for critical CVEs.

---

## 15. Explicit minimal-LLM policy

These are product rules, not preferences. Any proposal that violates them needs an explicit decision-log entry.

- [x] **LLM-01** No generative LLM in the request path. Classification uses the fine-tuned encoder + deterministic rules.
- [x] **LLM-02** No third-party AI APIs (OpenAI, Anthropic, Google, Cohere, etc.) at runtime or in CI for user content.
- [x] **LLM-03** Explanations are produced by token attribution, lexicon matches, and sentence templates — reproducible and testable.
- [x] **LLM-04** If a generative model is ever added, it must be local, opt-in, cached, rate-limited, disclosed in the UI/privacy policy, and never required for a core result.
- [x] **LLM-05** No LLM-generated training labels. Labels come from datasets or human adjudication; synthetic labels create circular evaluation.
- [x] **LLM-06** Zero marginal inference cost; the only model costs are training time on free CI/Colab minutes and storage.
- [x] **LLM-07** No "LLM-powered" marketing language; document the actual architecture in the model card and UI.

**Why:** the product must remain cheap, deterministic, auditable, and explainable, with no user content sent to third parties.

---

## 16. Free-tier budget and capacity

Verify all numbers at build time; treat this as a starting model.

| Resource | Free allowance (typical) | Constraint it creates |
|---|---|---|
| Vercel Hobby | 100 GB bandwidth/mo | Frontend only; no long-running API routes |
| Render free web service | 512 MB RAM, 0.1 CPU, sleeps after ~15 min, **750 instance-hours/mo per account** | One always-on service ≈ 730 h; cannot run backend + AI separately 24/7 |
| Cloud Run free | ~180k vCPU-s + 360k GiB-s/mo, scale-to-zero | Good fit for the AI service at 1 GiB; cold starts |
| Hugging Face Spaces free | 2 vCPU, 16 GB, sleeps after inactivity | Alternative AI host; verify commercial-use ToS |
| Atlas M0 | 512 MB storage, shared CPU/RAM, ~500 connections, **no continuous backups** | Store snippets + hashes, not full articles; external mongodump |
| Upstash Redis free | ~10k commands/day | Enough for shared rate limiting/session denylist at low traffic |
| Resend/Brevo free | ~3k emails/mo | Verification + password reset only |
| Sentry free | ~5k errors/mo | Sample and scrub aggressively |
| UptimeRobot free | 50 monitors, 5-min interval | Readiness checks; doubles as keep-alive |
| Grafana Cloud free | ~10k series | Metrics + dashboards |
| Cloudflare R2 free | 10 GB storage | Model artifacts + DB dumps |
| GitHub Actions | Free for public repos; ~2,000 min/mo private | CI + weekly retraining + backup jobs |

**Memory math:** Spring Boot baseline ≈ 200–300 MB; ONNX DistilBERT int8 ≈ 150–300 MB RSS. Each fits a 512 MB service separately, but not together. Keep the services split and measure with `docker run --memory=512m`.

**Quota math:** do not plan two always-on Render services on one free account. Either host the AI service elsewhere (Cloud Run/HF Spaces) or accept scale-to-zero and cold starts.

---

## 17. Milestones and sequencing

| Milestone | Scope | Exit criteria |
|---|---|---|
| **M0 — Scaffold fixed** | `P0-*` except `P0-03`, plus CI | Clean clone builds and deploys; no silent fallbacks; no committed secrets |
| **M1 — Real model** | `D-*`, `M-01…M-14` | Golden-set metrics meet gates; ONNX artifact + model card + registry entry |
| **M2 — Deployable product** | `I-*`, `B-*`, `O-01…O-09` | Three services deployed on free tier; authz tests pass; backups scheduled |
| **M3 — Usable product** | `F-*`, `L-01…L-08`, `A-17…A-19` | Feedback UI + admin stats + legal pages; retraining runs on a schedule with a gate |
| **M4 — Launch hardening** | `V-*`, `Q-*`, `A-*` remainder | All launch gates evidenced; restore and rollback drills passed |

Ordering rule: data and evaluation (`D-*`) come before any model claims; packaging (`M-09/M-10`) comes before deployment (`O-02`); observability (`V-*`) comes before opening signups.

---

## 18. Risks and open questions

| Risk / question | Impact | Mitigation / decision needed |
|---|---|---|
| Public datasets encode outlet/style shortcuts | Inflated accuracy; embarrassing misses in production | Domain-disjoint splits, shortcut audit (`D-03`, `M-08`), report honest limits |
| Dataset licenses restrict commercial use or redistribution | Legal exposure | License review (`D-01`), no data in git, derived features only if required |
| Atlas M0 has no backups | Permanent data loss | Scheduled mongodump to R2 + restore drills (`O-04`) |
| Free-tier RAM cannot host JVM + model together | Deployment failure | Split services; ONNX-only runtime; measure under 512 MB (`M-10`, `O-02`) |
| Render `750 instance-hours` shared across services | One service suspended | AI on Cloud Run/HF Spaces; monitor quota (`O-06`) |
| Cold starts on free hosts | Poor first-request UX | Keep-alive within quota, loading states, documented expectations (`O-05`) |
| URL fetching legality/robots | Takedown/legal risk | Honor robots.txt, store snippets only, legal review (`A-22`) |
| English-only gate misclassifies code-switched text | Bad UX | Conservative language thresholds + clear rejection (`D-09`) |
| Solo maintainer bandwidth | Roadmap stalls | Ship M0–M2 before M3; automate everything in CI |
| User feedback used for training without consent | Privacy/trust breach | Disclose, pseudonymize, require human adjudication (`A-18`, `L-02`) |
| "Propaganda" label is subjective | Weak evaluation | Define and pilot the taxonomy; fall back to binary if κ is low (`D-04`, `D-06`) |

**Open decisions to confirm before M1:** final label taxonomy, dataset shortlist and licenses, whether anonymous demo scans are allowed, which AI host is acceptable under its ToS, and the product name/domain.

---

## 19. Progress tracker

Update this table when a phase changes state. Keep it to one line per phase.

| Phase | IDs | Status | Notes |
|---|---|---|---|
| 0 — Stabilize | `P0-*` | Partial | All fixed except a real model artifact (P0-03) and a hash lockfile (P0-08) |
| 1 — Data | `D-*` | Partial | English gate and label mapping done; licensing, near-dedupe, domain-disjoint/gold sets open |
| 2 — Model | `M-*` | Partial | Scripts, calibration, ONNX export, model card done; no trained artifact, robustness/shortcut/benchmarks open |
| 3 — Inference | `I-*` | Done (notes) | ONNX-only serving, SSRF-safe ingestion, limits, token auth, request IDs; latency/concurrency unmeasured |
| 4 — Backend | `B-*` | Partial | Contracts, resilience, indexes, retention, admin done; idempotency, filters, API versioning open |
| 5 — Security/legal | `A-*` | Partial | Cookies, revocation, CSRF, headers, legal pages, deletion, quotas done; reset/verification, audits, threat model open |
| 6 — Frontend | `F-*` | Partial | Core flows, admin, feedback, history, legal, SEO, 404/error done; a11y/responsive audit, analytics, share/export open |
| 7 — MLOps | `L-*` | Partial | Feedback queue, retrain gate/audit, checksum integrity done; scheduled retraining, adjudication, drift open |
| 8 — Infra/CI | `O-*` | Partial | Blueprint, CI, backup workflow, deployment docs done; not deployed, restore/rollback drills open |
| 9 — Observability | `V-*` | Not started | Request IDs and actuator exist; no dashboards, alerts, Sentry, runbooks |
| 10 — Testing | `Q-*` | Partial | Unit suites, CI, SSRF tests pass; integration/E2E/load/a11y/model regression open |

---

## Appendix A — Verification commands

```bash
# Backend
cd backend && mvn -B verify

# AI service (Python 3.11)
cd ai-service && pytest -q

# Frontend
cd frontend && npm ci && npx tsc --noEmit && npm run build

# Training, export, and artifact gate (after downloading datasets)
cd ai-service
python training/prepare_data.py
python training/train.py
python training/export_onnx.py --calibration training/runs/<version>/calibration.json
python training/evaluate.py --split test --min-macro-f1 0.70 --min-fake-recall 0.70

# Smoke the serving image on the smallest target
docker run --memory=512m --rm -p 8000:8000 truthguard-ai-service

# E2E + load (not yet implemented; see Q-05/Q-06)
cd frontend && npx playwright test
k6 run load/analyze.js
```

## Appendix B — Target repository additions

```text
ai-service/
  app/                 # serving code only (ONNX Runtime, no torch import)
  training/            # offline only, excluded from the runtime image
    download_data.py   # fetches public datasets outside git
    prepare_data.py    # dedupe, split, label mapping
    train.py
    evaluate.py        # ONNX artifact gate: metrics + thresholds
    export_onnx.py     # int8 export + manifest + model.zip
    README.md          # dataset acquisition and publishing steps
  MODEL_CARD.md
  artifacts/           # checksummed model + tokenizer manifest (not in git)
docs/
  PRODUCTION_READINESS.md   # this file
  DEPLOYMENT.md
  API.md
  ARCHITECTURE.md
.github/workflows/
  ci.yml
  backup.yml
render.yaml
```

Still missing (tracked above): `docs/MLOPS.md`, `docs/RUNBOOKS.md`,
`docs/THREAT_MODEL.md`, `docs/SECURITY.md`, and an automated retraining
workflow (L-04).
