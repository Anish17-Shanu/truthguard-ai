# Training pipeline

TruthGuard AI ships a small **fine-tuned encoder classifier** (DistilBERT or
MiniLM). There is no generative LLM anywhere in this pipeline: the model maps
text to `real` / `fake` / `propaganda`, and the explanation layer is
deterministic (lexicons + token occlusion). No dataset text is sent to a third
party, and no external AI API is called.

Training is offline. The service only loads the exported int8 ONNX artifact.

## Requirements

- Python 3.11 (same major version as the service image)
- ~16 GB RAM for CPU training with `distilbert-base-uncased`; a CUDA GPU cuts
  training time substantially but is not required
- CPU-only export/evaluation works on a laptop

```bash
cd ai-service
python -m venv .venv-train
.venv-train/Scripts/activate        # Windows
# source .venv-train/bin/activate   # Linux/macOS
pip install -r requirements-train.txt
```

## 1. Acquire public datasets

```bash
python training/download_data.py --liar
```

LIAR is downloaded automatically. The other datasets must be obtained from
their publishers because their licences do not permit mirroring:

| Dataset | Source | Expected location | Label mapping |
| --- | --- | --- | --- |
| LIAR | [liar_dataset](https://github.com/thiagorainmaker77/liar_dataset) | `training/data/raw/liar/{train,valid,test}.tsv` | true/mostly-true → real; false/pants-fire → fake; half/barely excluded |
| ISOT | [UVic ISOT](https://www.uvic.ca/ecs/ece/isot/datasets/fake-news/index.php) | `training/data/raw/isot/{Fake,True}.csv` | file name |
| WELFake | [Zenodo 4561253](https://zenodo.org/records/4561253) | `training/data/raw/welfake/WELFake_Dataset.csv` | `--welfake-real-label` (default 1) |
| Propaganda | [Propaganda/FakeNews](https://propaganda.math.unipd.it/fakenews/) | `training/data/raw/propaganda/*.txt` or `propaganda.csv` | propaganda |

Review each dataset's terms before training or redistributing a model. Do not
commit raw data; `training/data/` is git-ignored.

## 2. Normalize and split

```bash
python training/prepare_data.py --max-per-class 60000
```

This deduplicates exact texts, drops conflicting duplicates, caps each class,
and writes stratified `train/validation/test.jsonl` plus
`dataset_stats.json` under `training/data/processed/`.

## 3. Fine-tune and calibrate

```bash
python training/train.py --model distilbert-base-uncased --epochs 3
```

Training saves the best checkpoint to `artifacts/model/` and writes
`training/runs/<version>/calibration.json` containing:

- class-weighted validation metrics
- the temperature that minimizes validation NLL
- test metrics: accuracy, macro F1, coverage, confident accuracy, per-class
  precision/recall/F1, and fake recall

The script exits non-zero when the gate fails (default: macro F1 ≥ 0.70 and
fake recall ≥ 0.70). **A model that fails the gate must not be deployed.**

## 4. Export a quantized ONNX artifact

```bash
python training/export_onnx.py \
  --model-dir artifacts/model \
  --calibration training/runs/<version>/calibration.json
```

This exports fp32, dynamically quantizes to int8, verifies ONNX against
PyTorch, writes `artifacts/manifest.json`, and bundles
`artifacts/model.zip` containing exactly:

```
model.onnx
tokenizer.json
manifest.json
```

It prints `MODEL_ARTIFACT_SHA256=<sha of model.zip>`.

## 5. Evaluate the shipped artifact

```bash
python training/evaluate.py --artifact-dir artifacts --split test
```

Evaluation runs the **same** tokenizer and probability path as the service
(tokenizers + ONNX Runtime + temperature scaling). It re-verifies the model
checksum, applies the deployment gate again, and writes
`artifacts/evaluation.json`. Publish this report with the release so users can
see the exact metrics for the model they are served.

## 6. Publish and deploy

1. Attach `model.zip` to a GitHub release (or any https host on port 443).
2. Record the printed SHA-256.
3. Set on the AI service:
   - `MODEL_ARTIFACT_URL=https://.../model.zip`
   - `MODEL_ARTIFACT_SHA256=<64-char hex>`
4. Leave `ALLOW_HEURISTIC_FALLBACK=false` in production. Without a valid
   artifact the service refuses to start rather than silently serving
   heuristic output.

The app verifies the download's https scheme, public host, size cap
(`MODEL_ARTIFACT_MAX_MB`, default 300) and SHA-256 before extracting only the
three expected files.

## Updating a model

1. Add data, retrain, run the gate and `evaluate.py`.
2. Upload a new `model.zip` and record its SHA.
3. Update `MODEL_ARTIFACT_URL` and `MODEL_ARTIFACT_SHA256`, then redeploy.
4. The new version appears in `/models/current`, `/health` readiness metadata,
   and the analysis response (`model_version`).

Feedback stored from `/feedback` is intended as a review queue for future
datasets; retraining is not automatic (`RETRAIN_ENABLED=false`).
