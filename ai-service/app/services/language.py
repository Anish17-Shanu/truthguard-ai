import re

from langdetect import DetectorFactory, LangDetectException, detect_langs

DetectorFactory.seed = 0

_ENGLISH_STOPWORDS = {
    "the", "and", "of", "to", "in", "is", "that", "for", "it", "as", "was", "with",
    "be", "by", "on", "not", "he", "this", "are", "or", "his", "from", "at", "which",
    "but", "have", "has", "had", "were", "they", "you", "we", "their", "there", "been",
    "would", "could", "should", "will", "can", "about", "into", "than", "them", "these",
}


def _word_ratio(text: str) -> float:
    words = re.findall(r"[A-Za-z']+", text.lower())
    if not words:
        return 0.0
    return sum(1 for word in words if word in _ENGLISH_STOPWORDS) / len(words)


def detect_language(text: str) -> str:
    stripped = text.strip()
    if len(stripped) < 40:
        return "en" if _word_ratio(stripped) >= 0.05 else "unknown"
    try:
        candidates = detect_langs(stripped)
    except LangDetectException:
        return "en" if _word_ratio(stripped) >= 0.05 else "unknown"
    if not candidates:
        return "unknown"
    top = candidates[0]
    if top.lang == "en" and top.prob >= 0.5:
        return "en"
    if top.prob < 0.5:
        return "en" if _word_ratio(stripped) >= 0.08 else "unknown"
    return top.lang


def is_english(text: str) -> bool:
    return detect_language(text) == "en"
