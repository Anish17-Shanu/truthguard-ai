"""Export a fine-tuned encoder to an int8 ONNX artifact with a checksum manifest.

Run from the ai-service directory after training:

    python training/export_onnx.py \
        --model-dir artifacts/model \
        --calibration training/runs/<version>/calibration.json
"""

import argparse
import hashlib
import json
import shutil
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


class LogitsWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        return self.model(input_ids=input_ids, attention_mask=attention_mask).logits


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def export_fp32(wrapper: LogitsWrapper, tokenizer, destination: Path, max_length: int) -> None:
    sample = tokenizer(
        "A city council approved the transit budget after a public hearing.",
        return_tensors="pt",
        truncation=True,
        max_length=min(max_length, 32),
    )
    input_ids = sample["input_ids"]
    attention_mask = sample["attention_mask"]
    with torch.no_grad():
        torch.onnx.export(
            wrapper,
            (input_ids, attention_mask),
            str(destination),
            input_names=["input_ids", "attention_mask"],
            output_names=["logits"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "sequence"},
                "attention_mask": {0: "batch", 1: "sequence"},
                "logits": {0: "batch"},
            },
            opset_version=17,
            do_constant_folding=True,
        )


def quantize(source: Path, destination: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantize_dynamic(str(source), str(destination), weight_type=QuantType.QInt8)


def verify(model_path: Path, wrapper: LogitsWrapper, tokenizer, max_length: int) -> None:
    import onnxruntime as ort

    sample = tokenizer(
        "The parliament voted on the budget after a long debate about public spending.",
        return_tensors="pt",
        truncation=True,
        max_length=min(max_length, 32),
    )
    ids = sample["input_ids"].numpy()
    mask = sample["attention_mask"].numpy()
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    onnx_logits = session.run(None, {"input_ids": ids, "attention_mask": mask})[0]
    with torch.no_grad():
        torch_logits = wrapper(sample["input_ids"], sample["attention_mask"]).numpy()
    if int(np.argmax(onnx_logits, axis=1)[0]) != int(np.argmax(torch_logits, axis=1)[0]):
        raise RuntimeError("ONNX and PyTorch predictions disagree on the smoke input")
    print(f"ONNX smoke check passed for {model_path.name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", default="artifacts/model")
    parser.add_argument("--out-dir", default="artifacts")
    parser.add_argument("--calibration", default=None)
    parser.add_argument("--version", default=None)
    parser.add_argument("--max-length", type=int, default=256)
    parser.add_argument("--temperature", type=float, default=1.0)
    parser.add_argument("--uncertain-threshold", type=float, default=0.55)
    parser.add_argument("--no-quantize", action="store_true")
    args = parser.parse_args()

    model_dir = Path(args.model_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    calibration = {}
    if args.calibration:
        calibration = json.loads(Path(args.calibration).read_text(encoding="utf-8"))
    version = args.version or calibration.get("version") or datetime.now(timezone.utc).strftime("v%Y.%m.%d.%H%M")
    temperature = float(calibration.get("temperature", args.temperature))
    threshold = float(calibration.get("uncertain_threshold", args.uncertain_threshold))
    max_length = int(calibration.get("max_length", args.max_length))
    metrics = calibration.get("metrics", {})

    model = AutoModelForSequenceClassification.from_pretrained(str(model_dir))
    model.eval()
    tokenizer = AutoTokenizer.from_pretrained(str(model_dir), use_fast=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token or "[PAD]"

    labels = [str(model.config.id2label[index]) for index in range(model.config.num_labels)]
    wrapper = LogitsWrapper(model)

    fp32_path = out_dir / "model-fp32.onnx"
    final_path = out_dir / "model.onnx"
    with tempfile.TemporaryDirectory() as temporary:
        export_fp32(wrapper, tokenizer, Path(temporary) / "model-fp32.onnx", max_length)
        shutil.copy(Path(temporary) / "model-fp32.onnx", fp32_path)

    if args.no_quantize:
        shutil.copy(fp32_path, final_path)
    else:
        with tempfile.TemporaryDirectory() as temporary:
            quantized = Path(temporary) / "model-int8.onnx"
            quantize(fp32_path, quantized)
            shutil.copy(quantized, final_path)

    tokenizer.save_pretrained(str(out_dir))
    tokenizer_file = out_dir / "tokenizer.json"
    if not tokenizer_file.exists():
        raise SystemExit("Fast tokenizer did not produce tokenizer.json")

    verify(final_path, wrapper, tokenizer, max_length)

    manifest = {
        "version": version,
        "labels": labels,
        "max_length": max_length,
        "temperature": temperature,
        "uncertain_threshold": threshold,
        "model_sha256": sha256(final_path),
        "trained_at": calibration.get("created_at") or datetime.now(timezone.utc).isoformat(),
        "base_model": calibration.get("base_model"),
        "metrics": metrics,
        "quantized": not args.no_quantize,
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    bundle = out_dir / "model.zip"
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(final_path, "model.onnx")
        archive.write(tokenizer_file, "tokenizer.json")
        archive.write(out_dir / "manifest.json", "manifest.json")

    print(json.dumps(manifest, indent=2))
    print(f"model.zip: {bundle}")
    print(f"MODEL_ARTIFACT_SHA256={sha256(bundle)}")
    print("Upload model.zip to a release, then set MODEL_ARTIFACT_URL and MODEL_ARTIFACT_SHA256.")
    fp32_path.unlink(missing_ok=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
