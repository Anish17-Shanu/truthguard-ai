# Deployment Guide (Free Tier)

## MongoDB Atlas
1. Create free M0 cluster.
2. Create database user and network allowlist.
3. Copy connection URI to `MONGODB_URI`.

## Frontend on Vercel
1. Import `frontend/` directory as project.
2. Set env var:
   - `NEXT_PUBLIC_BACKEND_URL=https://<backend-service-url>`
3. Deploy.

## Backend on Render/Railway
1. Create new web service from `backend/`.
2. Use Docker build.
3. Set env vars from `backend/.env.example`.
4. Set `MONGODB_URI` to Atlas URI.
5. Set `AI_SERVICE_BASE_URL` to AI service URL.

## AI Service on Render
1. Create web service from `ai-service/`.
2. Use Docker build.
3. Set env vars from `ai-service/.env.example`.
4. Set `MONGODB_URI` to Atlas URI.

## Optional Redis (Upstash free)
1. Create Redis database.
2. Add `REDIS_URL` to backend environment.

