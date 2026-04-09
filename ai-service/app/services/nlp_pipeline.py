import re
from dataclasses import dataclass

import numpy as np
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from app.core.config import settings


PROPAGANDA_KEYWORDS = {
    "traitor",
    "enemy",
    "purge",
    "exposed",
    "conspiracy",
    "cover-up",
    "brainwash",
    "agenda",
}

BIAS_KEYWORDS = {
    "always",
    "never",
    "obviously",
    "everyone knows",
    "undeniably",
}

EMOTION_HINTS = {
    "fear": {"threat", "panic", "danger", "attack"},
    "anger": {"enemy", "corrupt", "outrage", "betray"},
    "joy": {"success", "win", "hope", "progress"},
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


class NlpPipeline:
    def __init__(self) -> None:
        self.use_transformer = True
        try:
            self.tokenizer = AutoTokenizer.from_pretrained(settings.primary_model_name)
            self.base_model = AutoModelForSequenceClassification.from_pretrained(
                settings.primary_model_name,
                num_labels=3,
                ignore_mismatched_sizes=True,
            )
        except Exception:
            self.use_transformer = False

    def analyze(self, text: str) -> PipelineOutput:
        normalized = self._normalize(text)
        if self.use_transformer:
            tokens = self.tokenizer(
                normalized,
                truncation=True,
                max_length=256,
                return_tensors="pt",
            )
            logits = self.base_model(**tokens).logits.detach().numpy()[0]
            probs = self._softmax(logits)
            label_index = int(np.argmax(probs))
            labels = ["real", "fake", "propaganda"]
            label = labels[label_index]
            confidence = float(probs[label_index])
        else:
            suspicious = self._suspicious_phrases(normalized)
            if suspicious:
                label, confidence = "propaganda", 0.72
            elif any(k in normalized.lower() for k in ["breaking", "shocking", "secret"]):
                label, confidence = "fake", 0.66
            else:
                label, confidence = "real", 0.64

        suspicious = self._suspicious_phrases(normalized)
        bias_score = self._bias_score(normalized, suspicious)
        emotion = self._emotion_score(normalized)
        language = self._detect_language(normalized)
        highlighted = self._highlight_tokens(normalized)

        if suspicious and label == "real":
            label = "propaganda"
            confidence = min(0.99, confidence + 0.1)

        return PipelineOutput(
            label=label,
            confidence=confidence,
            bias_score=bias_score,
            emotion=emotion,
            language=language,
            highlighted_text=highlighted,
            suspicious_phrases=suspicious,
        )

    def _normalize(self, text: str) -> str:
        return re.sub(r"\s+", " ", text.strip())

    def _detect_language(self, text: str) -> str:
        ascii_ratio = sum(ch.isascii() for ch in text) / max(len(text), 1)
        return "en" if ascii_ratio > 0.9 else "multilingual"

    def _suspicious_phrases(self, text: str) -> list[str]:
        lowered = text.lower()
        hits = [word for word in PROPAGANDA_KEYWORDS if word in lowered]
        return sorted(set(hits))

    def _bias_score(self, text: str, suspicious: list[str]) -> float:
        lowered = text.lower()
        count = sum(1 for phrase in BIAS_KEYWORDS if phrase in lowered) + len(suspicious)
        score = min(1.0, count / 8.0)
        return float(score)

    def _highlight_tokens(self, text: str) -> list[str]:
        words = re.findall(r"[A-Za-z][A-Za-z'-]+", text)
        weighted = [w for w in words if len(w) >= 7 or w.lower() in PROPAGANDA_KEYWORDS]
        return list(dict.fromkeys(weighted[:12]))

    def _emotion_score(self, text: str) -> str:
        lowered = text.lower()
        for label, words in EMOTION_HINTS.items():
            if any(word in lowered for word in words):
                return label
        return "neutral"

    def _softmax(self, logits: np.ndarray) -> np.ndarray:
        shifted = logits - np.max(logits)
        exp = np.exp(shifted)
        return exp / np.sum(exp)
