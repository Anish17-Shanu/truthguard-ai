# TruthGuard AI - Fake News & Cyber Propaganda Detection System
## Creator
This project was created, written, and maintained by **Anish Kumar**.


Production-oriented SaaS platform with microservices:
- `frontend/` - Next.js (TypeScript, Tailwind, componentized UI)
- `backend/` - Java Spring Boot (JWT auth, RBAC, orchestration, analytics APIs)
- `ai-service/` - Python FastAPI (transformer NLP pipeline, explainability, retraining hooks)

## Folder Structure

```text
truthguard-ai/
  frontend/
    app/
    components/
    lib/
    middleware.ts
  backend/
    src/main/java/com/truthguard/backend/
    src/main/resources/application.yml
  ai-service/
    app/api
    app/services
    tests/
    data/sample
  docs/
    ARCHITECTURE.md
    API.md
    DEPLOYMENT.md
  docker-compose.yml
```

## Environment Variables

### Frontend (`frontend/.env.local`)
```env
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
```

### Backend (`backend/.env`)
```env
PORT=8080
MONGODB_URI=mongodb://localhost:27017/truthguard
JWT_SECRET=VGhpc0lzQVNlY3VyZUJhc2U2NEpXVFNlY3JldEtleUZvclRydXRoR3VhcmRBSTEyMw==
JWT_EXPIRATION_SECONDS=7200
AI_SERVICE_BASE_URL=http://localhost:8000
AI_SERVICE_TIMEOUT_SECONDS=90
CORS_ALLOWED_ORIGINS=http://localhost:3000
RATE_LIMIT_MAX_REQUESTS_PER_MINUTE=120
BOOTSTRAP_ADMIN_EMAIL=admin@truthguard.ai
BOOTSTRAP_ADMIN_PASSWORD=Admin@12345
BOOTSTRAP_ADMIN_NAME=TruthGuard Admin
```

### AI Service (`ai-service/.env`)
```env
APP_ENV=development
MONGODB_URI=mongodb://localhost:27017
MONGODB_DB=truthguard
PRIMARY_MODEL_NAME=distilbert-base-multilingual-cased
EMOTION_MODEL_NAME=j-hartmann/emotion-english-distilroberta-base
MODEL_VERSION=v1.0.0
```

## Local Run (Manual)

### 1. AI Service
```bash
cd ai-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 2. Backend
```bash
cd backend
mvn spring-boot:run
```

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
```

## Docker Run
```bash
docker compose up --build
```

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- AI Service: `http://localhost:8000`

## Testing

### Backend unit tests
```bash
cd backend
mvn test
```

### AI service tests
```bash
cd ai-service
pytest
```

## Sample Dataset Usage

Sample CSV is available at:
- `ai-service/data/sample/fake_news_sample.csv`

Use it as seed data for classifier tuning during retraining jobs or offline experiments.

## Additional Docs
- Architecture: `docs/ARCHITECTURE.md`
- API: `docs/API.md`
- Deployment: `docs/DEPLOYMENT.md`
