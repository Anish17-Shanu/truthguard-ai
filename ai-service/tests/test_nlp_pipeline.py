from app.services.nlp_pipeline import NlpPipeline


def test_pipeline_output_shape():
    pipeline = NlpPipeline()
    result = pipeline.analyze("Government sources claim a conspiracy and cover-up across national media.")
    assert result.label in {"fake", "real", "propaganda"}
    assert 0 <= result.confidence <= 1
    assert isinstance(result.highlighted_text, list)

