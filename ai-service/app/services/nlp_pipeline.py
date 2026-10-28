import hashlib
import json
import logging
import re
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from app.core.config import settings
from app.services.language import detect_language

logger = logging.getLogger(__name__)

PROPAGANDA_KEYWORDS = {
    "traitor", "enemy", "purge", "exposed", "conspiracy", "cover-up", "brainwash",
    "agenda", "hoax", "plandemic", "deep state", "false flag", "mainstream media",
    "wake up", "sheeple", "regime", "propaganda", "disinformation",
}

SENSATIONAL_MARKERS = {
    "breaking", "shocking", "secret", "bombshell", "miracle", "insane",
    "you won't believe", "they don't want you to know", "must see", "urgent",
    "exclusive", "unbelievable", "destroyed", "slams", "outrage",
}

BIAS_KEYWORDS = {
    "always", "never", "obviously", "everyone knows", "undeniably", "clearly",
    "without a doubt", "no one can deny", "radical", "corrupt", "liar",
}

EMOTION_HINTS = {
    "fear": {"threat", "panic", "danger", "attack", "terror", "crisis", "collapse"},
    "anger": {"enemy", "corrupt", "outrage", "betray", "furious", "scandal"},
    "joy": {"success", "win", "hope", "progress", "celebrate", "breakthrough"},
}


@dataclass
class PipelineOutput:
    label: str
    confidence: float
    bias_score: float
    emotion: str
    language: str
    highlighted_text: list[str]
    suspicious_phrases: list[str]
    classifier_mode: str
    content_hash: str
    model_version: str


class _OnnxClassifier:
    def __init__(self) -> None:
        from onnxruntime import InferenceSession
        from tokenizers import Tokenizer

        manifest = json.loads(Path(settings.manifest_path).read_text(encoding="utf-8"))
        model_path = Path(settings.model_path)
        tokenizer_path = Path(settings.tokenizer_path)
        if not model_path.exists() or not tokenizer_path.exists():
            raise FileNotFoundError("ONNX model or tokenizer artifact is missing")

        expected = manifest.get("model_sha256")
        if expected:
            digest = hashlib.sha256(model_path.read_bytes()).hexdigest()
            if digest != expected:
                raise ValueError("ONNX model checksum does not match the manifest")

        self.session = InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        self.tokenizer = Tokenizer.from_file(str(tokenizer_path))
        self.max_length = int(manifest.get("max_length", 256))
        self.tokenizer.enable_truncation(max_length=self.max_length)
        self.labels: list[str] = manifest.get("labels", ["real", "fake", "propaganda"])
        self.temperature = max(float(manifest.get("temperature", 1.0)), 0.05)
        self.uncertain_threshold = float(manifest.get("uncertain_threshold", 0.55))
        self.version = str(manifest.get("version", settings.model_version))
        self.metrics: dict[str, float] = manifest.get("metrics", {})
        self.trained_at = manifest.get("trained_at")
        self._input_names = {item.name for item in self.session.get_inputs()}
        if "input_ids" not in self._input_names:
            raise ValueError("ONNX model must accept input_ids")

    def predict(self, text: str) -> tuple[str, float, np.ndarray]:
        encoding = self.tokenizer.encode(text)
        ids = np.array([encoding.ids], dtype=np.int64)
        feed: dict[str, np.ndarray] = {"input_ids": ids}
        if "attention_mask" in self._input_names:
            feed["attention_mask"] = np.ones_like(ids)
        if "token_type_ids" in self._input_names:
            feed["token_type_ids"] = np.zeros_like(ids)

        logits = self.session.run(None, feed)[0][0].astype(np.float64)
        scaled = logits / self.temperature
        shifted = scaled - np.max(scaled)
        exp = np.exp(shifted)
        probs = exp / np.sum(exp)
        index = int(np.argmax(probs))
        label = self.labels[index] if index < len(self.labels) else self.labels[-1]
        confidence = float(probs[index])
        if confidence < self.uncertain_threshold:
            return "uncertain", confidence, probs
        return label, confidence, probs


class NlpPipeline:
    def __init__(self) -> None:
        self.classifier: _OnnxClassifier | None = None
        self.mode = "heuristic"
        self.version = "heuristic-v1"
        try:
            self.classifier = _OnnxClassifier()
            self.mode = "transformer"
            self.version = self.classifier.version
            logger.info("Loaded ONNX classifier version=%s", self.version)
        except Exception as exc:
            logger.error(
                "ONNX classifier unavailable (%s); serving explicit heuristic mode. "
                "Results are not model predictions and must be labelled as such.",
                exc,
            )

    def metadata(self) -> dict[str, object]:
        return {
            "version": self.version,
            "classifier_mode": self.mode,
            "metrics": getattr(self.classifier, "metrics", {}),
            "trained_at": getattr(self.classifier, "trained_at", None),
        }

    def analyze(self, text: str) -> PipelineOutput:
        normalized = self._normalize(text)
        language = detect_language(normalized)
        suspicious = self._suspicious_phrases(normalized)
        bias_score = self._bias_score(normalized, suspicious)
        emotion = self._emotion_score(normalized)
        content_hash = hashlib.sha256(normalized.encode("utf-8")).hexdigest()

        if self.classifier is not None:
            label, confidence = self.classifier.predict(normalized)[:2]
            highlighted = self._occlusion_highlights(normalized, suspicious)
        else:
            label, confidence = self._heuristic_label(normalized, suspicious)
            highlighted = self._heuristic_highlights(normalized, suspicious)

        return PipelineOutput(
            label=label,
            confidence=round(float(confidence), 4),
            bias_score=bias_score,
            emotion=emotion,
            language=language,
            highlighted_text=highlighted,
            suspicious_phrases=suspicious,
            classifier_mode=self.mode,
            content_hash=content_hash,
            model_version=self.version,
        )

    def _normalize(self, text: str) -> str:
        return re.sub(r"\s+", " ", text.strip())

    def _suspicious_phrases(self, text: str) -> list[str]:
        lowered = text.lower()
        hits = [phrase for phrase in PROPAGANDA_KEYWORDS if phrase in lowered]
        return sorted(set(hits))

    def _bias_score(self, text: str, suspicious: list[str]) -> float:
        lowered = text.lower()
        count = sum(1 for phrase in BIAS_KEYWORDS if phrase in lowered) + len(suspicious)
        return float(min(1.0, count / 8.0))

    def _emotion_score(self, text: str) -> str:
        lowered = text.lower()
        for label, words in EMOTION_HINTS.items():
            if any(word in lowered for word in words):
                return label
        return "neutral"

    def _heuristic_label(self, text: str, suspicious: list[str]) -> tuple[str, float]:
        lowered = text.lower()
        sensational = [marker for marker in SENSATIONAL_MARKERS if marker in lowered]
        bias = sum(1 for phrase in BIAS_KEYWORDS if phrase in lowered)
        letters = [char for char in text if char.isalpha()]
        caps_ratio = (sum(1 for char in letters if char.isupper()) / len(letters)) if letters else 0.0
        exclamations = text.count("!")

        score = min(
            1.0,
            0.18 * len(suspicious)
            + 0.12 * len(sensational)
            + 0.05 * bias
            + 0.9 * caps_ratio
            + 0.04 * min(exclamations, 5),
        )
        if suspicious:
            label = "propaganda"
        elif sensational:
            label = "fake"
        else:
            label = "real"
        confidence = min(0.85, 0.55 + 0.3 * score)
        return label, confidence

    def _heuristic_highlights(self, text: str, suspicious: list[str]) -> list[str]:
        lowered = text.lower()
        phrases = list(suspicious)
        phrases += [marker for marker in SENSATIONAL_MARKERS if marker in lowered]
        words = [word for word in re.findall(r"[A-Z][A-Z]{2,}", text)]
        combined = list(dict.fromkeys(phrases + words + self._long_words(text)))
        return combined[:12]

    def _long_words(self, text: str) -> list[str]:
        words = re.findall(r"[A-Za-z][A-Za-z'-]{6,}", text)
        return list(dict.fromkeys(words[:12]))

    def _occlusion_highlights(self, text: str, suspicious: list[str]) -> list[str]:
        if self.classifier is None:
            return self._heuristic_highlights(text, suspicious)

        candidates = list(dict.fromkeys(suspicious + self._heuristic_highlights(text, suspicious)))[:8]
        if not candidates:
            return []

        base_label, _, base_probs = self.classifier.predict(text)
        if base_label == "uncertain":
            base_index = int(np.argmax(base_probs))
        else:
            base_index = self.classifier.labels.index(base_label) if base_label in self.classifier.labels else int(np.argmax(base_probs))
        base_probability = float(base_probs[base_index])

        scored: list[tuple[float, str]] = []
        for candidate in candidates:
            if candidate.lower() not in text.lower():
                continue
            occluded = re.sub(re.escape(candidate), " ", text, flags=re.IGNORECASE)
            if occluded.strip() == text.strip():
                continue
            _, _, probs = self.classifier.predict(occluded)
            delta = base_probability - float(probs[base_index])
            if delta > 0.005:
                scored.append((delta, candidate))

        scored.sort(reverse=True)
        return [term for _, term in scored[:8]]
