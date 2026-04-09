# TruthGuard AI Architecture

## Services
- `frontend` (Next.js + TypeScript + Tailwind + reusable UI components)
- `backend` (Spring Boot API, JWT auth, orchestration, RBAC, rate limiting)
- `ai-service` (FastAPI + transformer NLP pipeline + explainability + retraining hooks)
- MongoDB Atlas collections: `users`, `analyses`, `feedback`, `models`, `logs`
- Redis: optional queue/cache tier

## API Flow
1. User submits text/url/file on frontend.
2. Frontend sends request to Java backend (`/analyze`) with JWT.
3. Backend validates auth, applies rate limiting, forwards payload to FastAPI.
4. AI service extracts content, runs NLP pipeline, returns classification and insights.
5. Backend persists analysis and returns structured response to frontend.
6. Dashboard/history/admin views consume backend analytics endpoints.

## Security
- JWT bearer tokens signed via HMAC.
- BCrypt password hashing.
- Role-based endpoint enforcement (`USER`, `ADMIN`).
- Global exception handling and audit logging.
- Per-IP request limiting middleware.

## Continuous Learning
- Feedback endpoint stores analyst corrections.
- Retraining service schedules weekly retrain jobs.
- Model registry writes active model version + metrics into `models`.

