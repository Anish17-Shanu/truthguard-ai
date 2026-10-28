"""Download the public LIAR dataset and print instructions for the rest.

ISOT, WELFake, and FakeNewsNet are distributed under terms that require you to
obtain them from their publishers. This script does not bypass those terms.

Run from the ai-service directory:
    python training/download_data.py --liar
"""

import argparse
import sys
import urllib.request
from pathlib import Path

LIAR_BASE = "https://raw.githubusercontent.com/thiagorainmaker77/liar_dataset/master"
LIAR_FILES = ("train.tsv", "valid.tsv", "test.tsv")

SOURCES = {
    "ISOT": "https://www.uvic.ca/ecs/ece/isot/datasets/fake-news/index.php",
    "WELFake": "https://zenodo.org/records/4561253",
    "FakeNewsNet": "https://github.com/KaiDMML/FakeNewsNet",
    "Propaganda": "https://propaganda.math.unipd.it/fakenews/",
}


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "TruthGuardAI-training/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as handle:
        while True:
            chunk = response.read(65536)
            if not chunk:
                break
            handle.write(chunk)
    print(f"wrote {destination} ({destination.stat().st_size} bytes)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", default="training/data/raw")
    parser.add_argument("--liar", action="store_true", help="download the LIAR TSV splits")
    args = parser.parse_args()

    if args.liar:
        for name in LIAR_FILES:
            target = Path(args.raw_dir) / "liar" / name
            if target.exists():
                print(f"skip {target} (already present)")
                continue
            try:
                download(f"{LIAR_BASE}/{name}", target)
            except Exception as exc:
                print(f"failed to download {name}: {exc}", file=sys.stderr)
                return 1

        sample = Path(args.raw_dir) / "liar" / "train.tsv"
        if sample.exists() and sum(1 for _ in sample.open(encoding="utf-8", errors="replace")) < 100:
            print("LIAR train.tsv looks unexpectedly small", file=sys.stderr)
            return 1

    print("\nManual datasets (download from the publisher, then place them as shown):")
    print("  ISOT:      training/data/raw/isot/Fake.csv and True.csv")
    print("  WELFake:   training/data/raw/welfake/WELFake_Dataset.csv")
    print("  Propaganda training/data/raw/propaganda/*.txt or propaganda.csv")
    for name, url in SOURCES.items():
        print(f"  {name}: {url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
