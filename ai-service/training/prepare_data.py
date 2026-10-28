"""Normalize public fake-news datasets into stratified JSONL splits.

Expected raw layout (all optional, whichever are present are used):

    training/data/raw/liar/train.tsv, valid.tsv, test.tsv
    training/data/raw/isot/Fake.csv, True.csv
    training/data/raw/welfake/WELFake_Dataset.csv
    training/data/raw/propaganda/*.txt  or  propaganda.csv with text,label

Run from the ai-service directory:
    python training/prepare_data.py --max-per-class 60000
"""

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

LABEL_ORDER = ["real", "fake", "propaganda"]
LIAR_LABELS = {
    "true": "real",
    "mostly-true": "real",
    "false": "fake",
    "pants-fire": "fake",
}


def normalize(text: str) -> str:
    text = re.sub(r"\s+", " ", str(text or "")).strip()
    return text


def add_row(rows: list[dict[str, str]], text: str, label: str, source: str) -> None:
    normalized = normalize(text)
    if len(normalized) < 40 or label not in LABEL_ORDER:
        return
    if len(normalized) > 20_000:
        normalized = normalized[:20_000]
    rows.append({"text": normalized, "label": label, "source": source})


def load_liar(raw: Path, rows: list[dict[str, str]]) -> int:
    added = 0
    for name in ("train.tsv", "valid.tsv", "test.tsv"):
        path = raw / "liar" / name
        if not path.exists():
            continue
        with path.open(encoding="utf-8", errors="replace") as handle:
            for line in handle:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                label = LIAR_LABELS.get(parts[1].strip().lower())
                if not label:
                    continue
                add_row(rows, parts[2], label, "liar")
                added += 1
    return added


def load_isot(raw: Path, rows: list[dict[str, str]]) -> int:
    added = 0
    for filename, label in (("Fake.csv", "fake"), ("True.csv", "real")):
        path = raw / "isot" / filename
        if not path.exists():
            continue
        frame = pd.read_csv(path, on_bad_lines="skip")
        for record in frame.to_dict("records"):
            title = record.get("title", "")
            body = record.get("text", "")
            add_row(rows, f"{title}. {body}" if title else body, label, "isot")
            added += 1
    return added


def load_welfake(raw: Path, rows: list[dict[str, str]], real_label: int) -> int:
    path = raw / "welfake" / "WELFake_Dataset.csv"
    if not path.exists():
        return 0
    frame = pd.read_csv(path, on_bad_lines="skip")
    if "label" not in frame.columns:
        raise SystemExit("WELFake dataset must contain a 'label' column")
    added = 0
    for record in frame.to_dict("records"):
        value = record.get("label")
        if pd.isna(value):
            continue
        label = "real" if int(value) == real_label else "fake"
        title = record.get("title", "")
        body = record.get("text", "")
        add_row(rows, f"{title}. {body}" if title else body, label, "welfake")
        added += 1
    return added


def load_propaganda(raw: Path, rows: list[dict[str, str]]) -> int:
    folder = raw / "propaganda"
    if not folder.exists():
        return 0
    added = 0
    for path in folder.glob("*.txt"):
        add_row(rows, path.read_text(encoding="utf-8", errors="replace"), "propaganda", "propaganda")
        added += 1
    csv_path = folder / "propaganda.csv"
    if csv_path.exists():
        frame = pd.read_csv(csv_path, on_bad_lines="skip")
        for record in frame.to_dict("records"):
            add_row(rows, record.get("text", ""), "propaganda", "propaganda")
            added += 1
    return added


def deduplicate(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    seen: dict[str, str] = {}
    conflicts: set[str] = set()
    for row in rows:
        digest = hashlib.sha256(row["text"].lower().encode("utf-8")).hexdigest()
        if digest in seen and seen[digest] != row["label"]:
            conflicts.add(digest)
        seen.setdefault(digest, row["label"])
    unique: list[dict[str, str]] = []
    kept: set[str] = set()
    for row in rows:
        digest = hashlib.sha256(row["text"].lower().encode("utf-8")).hexdigest()
        if digest in conflicts or digest in kept:
            continue
        kept.add(digest)
        unique.append(row)
    return unique


def cap_per_class(rows: list[dict[str, str]], limit: int, seed: int) -> list[dict[str, str]]:
    frame = pd.DataFrame(rows)
    capped = []
    for label, group in frame.groupby("label"):
        capped.append(group.sample(n=min(len(group), limit), random_state=seed))
    return pd.concat(capped).sample(frac=1.0, random_state=seed).to_dict("records")


def write_jsonl(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps({"text": row["text"], "label": row["label"]}, ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", default="training/data/raw")
    parser.add_argument("--out-dir", default="training/data/processed")
    parser.add_argument("--max-per-class", type=int, default=60_000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--welfake-real-label", type=int, default=1)
    args = parser.parse_args()

    raw = Path(args.raw_dir)
    rows: list[dict[str, str]] = []
    counts = {
        "liar": load_liar(raw, rows),
        "isot": load_isot(raw, rows),
        "welfake": load_welfake(raw, rows, args.welfake_real_label),
        "propaganda": load_propaganda(raw, rows),
    }
    if not rows:
        print("No dataset rows found. Run training/download_data.py and see training/README.md.", file=sys.stderr)
        return 1

    rows = cap_per_class(deduplicate(rows), args.max_per_class, args.seed)
    labels = [label for label in LABEL_ORDER if any(row["label"] == label for row in rows)]
    if len(labels) < 2:
        print("At least two labels are required to train a classifier.", file=sys.stderr)
        return 1

    frame = pd.DataFrame(rows)
    train, remainder = train_test_split(
        frame, test_size=0.2, random_state=args.seed, stratify=frame["label"]
    )
    validation, test = train_test_split(
        remainder, test_size=0.5, random_state=args.seed, stratify=remainder["label"]
    )

    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    write_jsonl(out / "train.jsonl", train.to_dict("records"))
    write_jsonl(out / "validation.jsonl", validation.to_dict("records"))
    write_jsonl(out / "test.jsonl", test.to_dict("records"))

    stats = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "labels": labels,
        "total": len(rows),
        "splits": {
            "train": len(train),
            "validation": len(validation),
            "test": len(test),
        },
        "label_distribution": dict(Counter(row["label"] for row in rows)),
        "source_rows_read": counts,
        "max_per_class": args.max_per_class,
        "seed": args.seed,
        "note": "LiAR half-true/barely-true rows are excluded as ambiguous.",
    }
    (out / "dataset_stats.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
    print(json.dumps(stats, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
