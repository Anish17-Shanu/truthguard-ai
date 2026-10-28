"""Fine-tune a small encoder classifier and calibrate its confidence.

This is a discriminative encoder (DistilBERT/MiniLM), not a generative LLM.
Run from the ai-service directory after installing requirements-train.txt:

    python training/train.py --model distilbert-base-uncased --epochs 3
"""

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import accuracy_score, f1_score, precision_recall_fscore_support
from sklearn.utils.class_weight import compute_class_weight
from torch.utils.data import Dataset
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
)

LABEL_ORDER = ["real", "fake", "propaganda"]


def read_jsonl(path: Path) -> list[dict[str, str]]:
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


class EncodedDataset(Dataset):
    def __init__(self, rows: list[dict[str, str]], tokenizer, label_to_id: dict[str, int], max_length: int):
        self.encodings = tokenizer(
            [row["text"] for row in rows],
            truncation=True,
            max_length=max_length,
            padding=False,
        )
        self.labels = [label_to_id[row["label"]] for row in rows]

    def __len__(self) -> int:
        return len(self.labels)

    def __getitem__(self, index: int) -> dict[str, torch.Tensor]:
        item = {key: torch.tensor(values[index]) for key, values in self.encodings.items()}
        item["labels"] = torch.tensor(self.labels[index])
        return item


class WeightedTrainer(Trainer):
    def __init__(self, class_weights: torch.Tensor | None = None, **kwargs):
        super().__init__(**kwargs)
        self.class_weights = class_weights

    def compute_loss(self, model, inputs, return_outputs=False, num_items_in_batch=None, **kwargs):
        labels = inputs.pop("labels")
        outputs = model(**inputs)
        weight = self.class_weights
        if weight is not None:
            weight = weight.to(outputs.logits.device)
        loss = torch.nn.CrossEntropyLoss(weight=weight)(
            outputs.logits.view(-1, model.config.num_labels),
            labels.view(-1),
        )
        return (loss, outputs) if return_outputs else loss


def softmax(logits: np.ndarray) -> np.ndarray:
    shifted = logits - np.max(logits, axis=1, keepdims=True)
    exponentiated = np.exp(shifted)
    return exponentiated / np.sum(exponentiated, axis=1, keepdims=True)


def negative_log_likelihood(logits: np.ndarray, truth: np.ndarray, temperature: float) -> float:
    probabilities = softmax(logits / max(temperature, 0.05))
    picked = probabilities[np.arange(len(truth)), truth]
    return float(-np.mean(np.log(np.clip(picked, 1e-12, 1.0))))


def calibrate(logits: np.ndarray, truth: np.ndarray) -> float:
    best_temperature = 1.0
    best_loss = float("inf")
    for temperature in np.arange(0.5, 3.01, 0.05):
        loss = negative_log_likelihood(logits, truth, float(temperature))
        if loss < best_loss:
            best_loss = loss
            best_temperature = float(temperature)
    return round(best_temperature, 2)


def evaluate(logits: np.ndarray, truth_ids: np.ndarray, labels: list[str], temperature: float, threshold: float) -> dict:
    probabilities = softmax(logits / max(temperature, 0.05))
    confidence = probabilities.max(axis=1)
    predicted_ids = probabilities.argmax(axis=1)
    predicted = np.array([labels[index] for index in predicted_ids])
    truth = np.array([labels[index] for index in truth_ids])

    covered = confidence >= threshold
    coverage = float(covered.mean()) if len(covered) else 0.0
    metrics: dict[str, float] = {
        "accuracy": float(accuracy_score(truth, predicted)),
        "macro_f1": float(f1_score(truth, predicted, average="macro", zero_division=0)),
        "coverage": coverage,
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
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="distilbert-base-uncased")
    parser.add_argument("--data-dir", default="training/data/processed")
    parser.add_argument("--output-dir", default="artifacts/model")
    parser.add_argument("--runs-dir", default="training/runs")
    parser.add_argument("--version", default=None)
    parser.add_argument("--max-length", type=int, default=256)
    parser.add_argument("--epochs", type=float, default=3)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--uncertain-threshold", type=float, default=0.55)
    parser.add_argument("--min-macro-f1", type=float, default=0.70)
    parser.add_argument("--min-fake-recall", type=float, default=0.70)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

    data_dir = Path(args.data_dir)
    train_rows = read_jsonl(data_dir / "train.jsonl")
    validation_rows = read_jsonl(data_dir / "validation.jsonl")
    test_rows = read_jsonl(data_dir / "test.jsonl")
    if not train_rows or not validation_rows or not test_rows:
        raise SystemExit("Missing processed splits. Run training/prepare_data.py first.")

    present = {row["label"] for row in train_rows}
    labels = [label for label in LABEL_ORDER if label in present]
    if len(labels) < 2:
        raise SystemExit("At least two labels are required.")
    label_to_id = {label: index for index, label in enumerate(labels)}
    unknown = {row["label"] for row in train_rows + validation_rows + test_rows} - set(labels)
    if unknown:
        raise SystemExit(f"Unexpected labels in data: {sorted(unknown)}")

    version = args.version or datetime.now(timezone.utc).strftime("v%Y.%m.%d.%H%M")
    run_dir = Path(args.runs_dir) / version
    run_dir.mkdir(parents=True, exist_ok=True)

    tokenizer = AutoTokenizer.from_pretrained(args.model, use_fast=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token or "[PAD]"

    train_dataset = EncodedDataset(train_rows, tokenizer, label_to_id, args.max_length)
    validation_dataset = EncodedDataset(validation_rows, tokenizer, label_to_id, args.max_length)
    test_dataset = EncodedDataset(test_rows, tokenizer, label_to_id, args.max_length)

    model = AutoModelForSequenceClassification.from_pretrained(
        args.model,
        num_labels=len(labels),
        id2label={index: label for index, label in enumerate(labels)},
        label2id=label_to_id,
    )
    if model.config.pad_token_id is None and tokenizer.pad_token_id is not None:
        model.config.pad_token_id = tokenizer.pad_token_id

    weights = compute_class_weight(
        "balanced",
        classes=np.arange(len(labels)),
        y=np.array(train_dataset.labels),
    )
    class_weights = torch.tensor(weights, dtype=torch.float)

    def compute_metrics(eval_prediction):
        logits, truth = eval_prediction
        predicted = np.argmax(logits, axis=1)
        return {
            "accuracy": float(accuracy_score(truth, predicted)),
            "macro_f1": float(f1_score(truth, predicted, average="macro", zero_division=0)),
        }

    training_args = TrainingArguments(
        output_dir=str(run_dir / "checkpoints"),
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_steps=50,
        learning_rate=args.learning_rate,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size * 2,
        num_train_epochs=args.epochs,
        warmup_ratio=0.1,
        weight_decay=0.01,
        load_best_model_at_end=True,
        metric_for_best_model="macro_f1",
        greater_is_better=True,
        save_total_limit=2,
        seed=args.seed,
        report_to="none",
        fp16=torch.cuda.is_available(),
    )

    trainer = WeightedTrainer(
        class_weights=class_weights,
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=validation_dataset,
        data_collator=DataCollatorWithPadding(tokenizer),
        compute_metrics=compute_metrics,
        processing_class=tokenizer,
    )
    trainer.train()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    trainer.save_model(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))

    validation_logits = trainer.predict(validation_dataset).predictions
    if isinstance(validation_logits, (tuple, list)):
        validation_logits = validation_logits[0]
    test_logits = trainer.predict(test_dataset).predictions
    if isinstance(test_logits, (tuple, list)):
        test_logits = test_logits[0]

    validation_truth = np.array(validation_dataset.labels)
    test_truth = np.array(test_dataset.labels)
    temperature = calibrate(validation_logits, validation_truth)
    metrics = evaluate(test_logits, test_truth, labels, temperature, args.uncertain_threshold)

    report = {
        "version": version,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "base_model": args.model,
        "labels": labels,
        "max_length": args.max_length,
        "temperature": temperature,
        "uncertain_threshold": args.uncertain_threshold,
        "splits": {
            "train": len(train_rows),
            "validation": len(validation_rows),
            "test": len(test_rows),
        },
        "metrics": metrics,
        "gate": {
            "min_macro_f1": args.min_macro_f1,
            "min_fake_recall": args.min_fake_recall,
        },
    }
    (run_dir / "calibration.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

    failures = []
    if metrics["macro_f1"] < args.min_macro_f1:
        failures.append(f"macro_f1 {metrics['macro_f1']:.4f} < {args.min_macro_f1}")
    if "fake_recall" in metrics and metrics["fake_recall"] < args.min_fake_recall:
        failures.append(f"fake_recall {metrics['fake_recall']:.4f} < {args.min_fake_recall}")
    if failures:
        print("Evaluation gate failed: " + "; ".join(failures))
        print("The model was saved for inspection but must not be deployed.")
        return 1

    print(f"Training passed the evaluation gate. Calibration: {run_dir / 'calibration.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
