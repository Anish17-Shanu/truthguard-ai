"""Evaluate the exported ONNX artifact against a held-out split.

This runs the same preprocessing and probability path as the serving code, so
the reported numbers describe what users will actually receive.

Run from the ai-service directory after export_onnx.py:

    python training/evaluate.py --artifact-dir artifacts --split test
"""

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import onnxruntime as ort
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_recall_fscore_support
from tokenizers import Tokenizer


def read_jsonl(path: Path) -> list[dict[str, str]]:
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - np.max(logits, axis=1, keepdims=True)
    exponentiated = np.exp(shifted)
    return exponentiated / np.sum(exponentiated, axis=1, keepdims=True)


def predict_logits(
    session: ort.InferenceSession,
    tokenizer: Tokenizer,
    input_names: set[str],
    texts: list[str],
    max_length: int,
    pad_id: int,
    batch_size: int,
) -> np.ndarray:
    tokenizer.enable_truncation(max_length=max_length)
    batches: list[np.ndarray] = []
    for start in range(0, len(texts), batch_size):
        encodings = tokenizer.encode_batch(texts[start : start + batch_size])
        width = max(len(encoding.ids) for encoding in encodings)
        ids = np.full((len(encodings), width), pad_id, dtype=np.int64)
        mask = np.zeros((len(encodings), width), dtype=np.int64)
        for row, encoding in enumerate(encodings):
            length = len(encoding.ids)
            ids[row, :length] = encoding.ids
            mask[row, :length] = 1
        feed: dict[str, np.ndarray] = {"input_ids": ids}
        if "attention_mask" in input_names:
            feed["attention_mask"] = mask
        if "token_type_ids" in input_names:
            feed["token_type_ids"] = np.zeros_like(ids)
        batches.append(np.asarray(session.run(None, feed)[0], dtype=np.float64))
    return np.concatenate(batches, axis=0)


def compute_metrics(
    logits: np.ndarray,
    truth_ids: np.ndarray,
    labels: list[str],
    temperature: float,
    threshold: float,
) -> dict:
    probabilities = softmax(logits / max(temperature, 0.05))
    confidence = probabilities.max(axis=1)
    predicted_ids = probabilities.argmax(axis=1)
    predicted = np.array([labels[index] for index in predicted_ids])
    truth = np.array([labels[index] for index in truth_ids])

    covered = confidence >= threshold
    metrics: dict[str, object] = {
        "accuracy": float(accuracy_score(truth, predicted)),
        "macro_f1": float(f1_score(truth, predicted, average="macro", zero_division=0)),
        "coverage": float(covered.mean()) if len(covered) else 0.0,
        "mean_confidence": float(confidence.mean()),
    }
    if covered.any():
        metrics["accuracy_confident"] = float(accuracy_score(truth[covered], predicted[covered]))
        metrics["macro_f1_confident"] = float(
            f1_score(truth[covered], predicted[covered], average="macro", zero_division=0)
        )
    if "fake" in labels:
        fake_index = labels.index("fake")
        fake_mask = truth_ids == fake_index
        if fake_mask.any():
            metrics["fake_recall"] = float((predicted_ids[fake_mask] == fake_index).mean())

    precision, recall, f1, support = precision_recall_fscore_support(
        truth, predicted, labels=labels, zero_division=0
    )
    metrics["per_class"] = {
        label: {
            "precision": float(precision[index]),
            "recall": float(recall[index]),
            "f1": float(f1[index]),
            "support": int(support[index]),
        }
        for index, label in enumerate(labels)
    }
    metrics["confusion_matrix"] = {
        "labels": labels,
        "rows": confusion_matrix(truth, predicted, labels=labels).tolist(),
    }
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", default="artifacts")
    parser.add_argument("--data-dir", default="training/data/processed")
    parser.add_argument("--split", default="test", choices=["validation", "test"])
    parser.add_argument("--report", default=None)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--min-macro-f1", type=float, default=0.70)
    parser.add_argument("--min-fake-recall", type=float, default=0.70)
    args = parser.parse_args()

    artifact_dir = Path(args.artifact_dir)
    manifest_path = artifact_dir / "manifest.json"
    model_path = artifact_dir / "model.onnx"
    tokenizer_path = artifact_dir / "tokenizer.json"
    for path in (manifest_path, model_path, tokenizer_path):
        if not path.exists():
            print(f"Missing artifact: {path}", file=sys.stderr)
            return 1

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected = manifest.get("model_sha256")
    actual = sha256(model_path)
    if expected and expected != actual:
        print("Model checksum does not match the manifest; refusing to evaluate.", file=sys.stderr)
        return 1

    labels: list[str] = manifest["labels"]
    label_to_id = {label: index for index, label in enumerate(labels)}
    temperature = float(manifest.get("temperature", 1.0))
    threshold = float(manifest.get("uncertain_threshold", 0.55))
    max_length = int(manifest.get("max_length", 256))

    split_path = Path(args.data_dir) / f"{args.split}.jsonl"
    rows = read_jsonl(split_path)
    if not rows:
        print(f"Empty split: {split_path}", file=sys.stderr)
        return 1
    unknown = {row["label"] for row in rows} - set(labels)
    if unknown:
        print(f"Split contains labels absent from the artifact: {sorted(unknown)}", file=sys.stderr)
        return 1

    tokenizer = Tokenizer.from_file(str(tokenizer_path))
    pad_id = tokenizer.token_to_id("[PAD]")
    if pad_id is None:
        pad_id = tokenizer.token_to_id("<pad>")
    if pad_id is None:
        print("Tokenizer has no pad token; cannot batch safely.", file=sys.stderr)
        return 1

    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    input_names = {item.name for item in session.get_inputs()}
    logits = predict_logits(
        session,
        tokenizer,
        input_names,
        [row["text"] for row in rows],
        max_length,
        pad_id,
        args.batch_size,
    )
    if logits.shape[0] != len(rows):
        print("ONNX session returned an unexpected batch shape.", file=sys.stderr)
        return 1

    truth_ids = np.array([label_to_id[row["label"]] for row in rows])
    metrics = compute_metrics(logits, truth_ids, labels, temperature, threshold)

    failures: list[str] = []
    if metrics["macro_f1"] < args.min_macro_f1:
        failures.append(f"macro_f1 {metrics['macro_f1']:.4f} < {args.min_macro_f1}")
    if "fake_recall" in metrics and metrics["fake_recall"] < args.min_fake_recall:
        failures.append(f"fake_recall {metrics['fake_recall']:.4f} < {args.min_fake_recall}")

    report = {
        "evaluated_at": datetime.now(timezone.utc).isoformat(),
        "version": manifest.get("version"),
        "model_sha256": actual,
        "checksum_verified": True,
        "split": args.split,
        "split_path": str(split_path),
        "samples": len(rows),
        "temperature": temperature,
        "uncertain_threshold": threshold,
        "metrics": metrics,
        "gate": {
            "min_macro_f1": args.min_macro_f1,
            "min_fake_recall": args.min_fake_recall,
            "passed": not failures,
            "failures": failures,
        },
    }
    report_path = Path(args.report) if args.report else artifact_dir / "evaluation.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

    if failures:
        print("Evaluation gate failed: " + "; ".join(failures), file=sys.stderr)
        return 1
    print(f"Evaluation passed. Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
