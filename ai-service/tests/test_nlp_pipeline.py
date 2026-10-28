from app.services.nlp_pipeline import NlpPipeline


def test_pipeline_output_shape():
    pipeline = NlpPipeline()
    result = pipeline.analyze("Government sources claim a conspiracy and cover-up across national media.")
    assert result.label in {"fake", "real", "propaganda", "uncertain"}
    assert 0 <= result.confidence <= 1
    assert 0 <= result.bias_score <= 1
    assert result.classifier_mode in {"transformer", "heuristic"}
    assert len(result.content_hash) == 64
    assert isinstance(result.highlighted_text, list)


def test_pipeline_flags_propaganda_lexicon():
    pipeline = NlpPipeline()
    result = pipeline.analyze(
        "The deep state conspiracy is a hoax and the regime wants to brainwash the sheeple."
    )
    assert "conspiracy" in result.suspicious_phrases
    assert result.label in {"propaganda", "fake", "uncertain"}


def test_pipeline_detects_english():
    pipeline = NlpPipeline()
    result = pipeline.analyze(
        "The parliament voted on the budget after a long debate about public spending and taxes."
    )
    assert result.language in {"en", "unknown"}
