# API Documentation

## Backend (Spring Boot)

### `POST /auth/register`
Request:
```json
{ "fullName": "Analyst", "email": "analyst@example.com", "password": "StrongPass123" }
```

### `POST /auth/login`
Request:
```json
{ "email": "analyst@example.com", "password": "StrongPass123" }
```

### `POST /analyze` (multipart/form-data, auth required)
Fields:
- `text` (optional)
- `url` (optional)
- `file` (optional `.pdf` / `.txt`)

### `GET /history` (auth required)
Returns user analysis history.

### `GET /history/{id}` (auth required)
Returns one analysis result.

### `POST /feedback` (auth required)
```json
{ "analysisId": "id", "correct": false, "correctedLabel": "real", "comment": "Context correction" }
```

### `GET /admin/stats` (admin only)
Returns totals, ratios, and trend counts.

## AI Service (FastAPI)

### `POST /analyze`
```json
{
  "text": "input text",
  "url": null,
  "fileBase64": null,
  "fileName": null
}
```

Response:
```json
{
  "label": "fake",
  "confidence": 0.95,
  "bias_score": 0.54,
  "emotion": "fear",
  "language": "en",
  "highlighted_text": ["cover-up", "agenda"],
  "suspicious_phrases": ["agenda"],
  "model_version": "v1.0.0"
}
```

### `POST /feedback`
Stores feedback for continuous learning.

### `POST /retrain`
Triggers retraining pipeline job and version bump.

### `GET /models/current`
Returns active model version.

