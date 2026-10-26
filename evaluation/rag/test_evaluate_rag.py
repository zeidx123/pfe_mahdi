"""Synthetic result arithmetic and runner tests; never call a model or assert AI accuracy."""
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import urllib.response
from unittest.mock import patch
from types import SimpleNamespace

SPEC = importlib.util.spec_from_file_location("evaluate_rag", Path(__file__).resolve().parents[2] / "scripts/evaluation/evaluate_rag.py")
evaluation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evaluation)


def labelled(answerable, ids):
    return {"independent": True, "reviewer": "unit-test-only", "reviewDate": "2026-09-17",
            "answerable": answerable, "relevantChunkIds": ids}


def source(chunk="c1"):
    return {"chunkId": chunk, "snippet": "Fictional source text", "documentId": "d1"}


def result(mode="HYBRID", status="ANSWERED", generation="ANSWERED", retrieval="AVAILABLE", sources=None):
    return {"requestedMode": mode, "effectiveMode": mode, "retrievalStatus": retrieval,
            "retrievedSources": [source()] if sources is None else sources, "generationStatus": generation,
            "status": status, "answer": "Fixture response", "citations": [source()] if status == "ANSWERED" else [],
            "retrievalLatencyMs": 5, "generationLatencyMs": 20, "totalLatencyMs": 30,
            "responseFingerprint": "fixture-response-hash"}


def row(case_id="q1", split="evaluation", labels=None, mode_result=None):
    return {"id": case_id, "split": split, "question": "Fixture question?", "labels": labels,
            "response": {"results": [mode_result or result()]}}


def report(rows):
    return {"runId": "unit-test-run", "datasetVersion": "synthetic-metric-test", "cases": rows,
            "corpus": {"corpus": {"fingerprint": "fixture-corpus-hash"}, "settings": {"topK": 3}}}


class RagMetricsTest(unittest.TestCase):
    def test_api_rejects_redirects_without_forwarding_the_supervisor_token(self):
        real_builder = evaluation.urllib.request.build_opener

        class RedirectTransport(evaluation.urllib.request.HTTPHandler):
            def __init__(self, code):
                super().__init__()
                self.code, self.requests = code, []

            def http_open(self, request):
                self.requests.append(request)
                if len(self.requests) > 1:
                    raise AssertionError("A redirect must not make a second request")
                response = urllib.response.addinfourl(io.BytesIO(b""),
                    {"Location": "http://other.invalid/token-capture"}, request.full_url, self.code)
                response.msg = "Redirect"
                return response

        for code in (301, 302, 303, 307, 308):
            with self.subTest(code=code):
                transport = RedirectTransport(code)
                with patch.dict(evaluation.os.environ, {"GESTI_EVAL_TOKEN": "synthetic-test-token"}), \
                        patch.object(evaluation.urllib.request, "build_opener",
                                     side_effect=lambda *handlers: real_builder(*handlers, transport)):
                    request = evaluation.api("http://selected.invalid/api")
                    with self.assertRaises(evaluation.urllib.error.HTTPError) as error:
                        request("/ai/rag/evaluation/corpus")
                self.assertEqual(error.exception.code, code)
                error.exception.close()
                self.assertEqual(len(transport.requests), 1)
                self.assertEqual(transport.requests[0].get_header("Authorization"), "Bearer synthetic-test-token")

    def test_missing_reviews_are_unknown_not_zero_or_perfect_accuracy(self):
        data = report([row()])
        group = evaluation.summarize(data)["groups"][0]
        self.assertIsNone(group["retrieval"]["recallAtK"])
        self.assertEqual(group["retrieval"]["missingOrUnreviewedLabelsCount"], 1)
        self.assertIsNone(group["answers"]["unsupportedClaimRate"])
        self.assertIsNone(group["answers"]["citationSupportRate"])
        self.assertEqual(group["answers"]["missingOrIncompleteClaimReviewCount"], 1)
        self.assertEqual(group["answers"]["missingCitationReviewCount"], 1)
        template = evaluation.review_template(data)
        self.assertFalse(template["reviews"][0]["independent"])
        self.assertIsNone(template["reviews"][0]["citations"][0]["supportsAnswer"])

    def test_retrieval_and_independent_support_metrics_use_explicit_denominators(self):
        data = report([row(labels=labelled(True, ["c1", "c2"]))])
        reviews = evaluation.review_template(data)
        review = reviews["reviews"][0]
        review.update(independent=True, reviewer="unit-test-only", reviewDate="2026-09-17", claimsComplete=True,
                      claims=[{"text": "Supported fixture claim", "supported": True}, {"text": "Unsupported fixture claim", "supported": False}],
                      answerCorrect=False, citations=[{"citationIndex": 0, "supportsAnswer": False}])
        group = evaluation.summarize(data, reviews)["groups"][0]
        self.assertEqual(group["retrieval"]["recallAtK"], .5)
        self.assertEqual(group["retrieval"]["mrrAtK"], 1)
        self.assertEqual(group["answers"]["unsupportedClaimRate"], .5)
        self.assertEqual(group["answers"]["citationSupportRate"], 0)
        self.assertEqual(group["answers"]["completeClaimReviewCount"], 1)
        self.assertEqual(group["answers"]["answerCorrectnessRate"], 0)

    def test_outages_and_incomplete_index_do_not_count_as_correct_abstention(self):
        data = report([
            row("abstain", labels=labelled(False, []), mode_result=result(status="INSUFFICIENT_INFORMATION", generation="NO_CONTEXT", sources=[])),
            row("outage", labels=labelled(False, []), mode_result=result(status="PROVIDER_UNAVAILABLE", generation="SKIPPED_RETRIEVAL_UNAVAILABLE", retrieval="PROVIDER_UNAVAILABLE", sources=[])),
            row("partial-index", labels=labelled(True, ["c1"]), mode_result=result(status="SOURCE_EXCERPTS", generation="SKIPPED_RETRIEVAL_UNAVAILABLE", retrieval="INDEX_INCOMPLETE")),
            row("false-abstain", labels=labelled(True, ["c1"]), mode_result=result(status="INSUFFICIENT_INFORMATION", generation="NO_CONTEXT", sources=[])),
        ])
        group = evaluation.summarize(data)["groups"][0]
        self.assertEqual(group["answers"]["unanswerableEligibleCount"], 1)
        self.assertEqual(group["answers"]["correctAbstentionRate"], 1)
        self.assertEqual(group["answers"]["answerableAbstentionRate"], 1)
        self.assertEqual(group["answers"]["unavailableOrInvalidGenerationCount"], 2)
        self.assertEqual(group["retrieval"]["reviewedAnswerableAvailableCount"], 1)
        self.assertEqual(group["retrieval"]["unavailableOrIncompleteIndexCount"], 2)

    def test_modes_and_splits_are_separate_and_related_questions_cannot_leak(self):
        rows = [row("dev", "development", labelled(True, ["c1"]), result("LEXICAL")),
                row("test", "evaluation", labelled(True, ["c1"]), result("LEXICAL"))]
        self.assertEqual(len(evaluation.summarize(report(rows))["groups"]), 2)
        rows[0]["groupId"] = rows[1]["groupId"] = "same-conversation"
        with self.assertRaisesRegex(ValueError, "one split"):
            evaluation.summarize(report(rows))

    def test_stale_review_and_invalid_citation_index_are_rejected(self):
        data = report([row(labels=labelled(True, ["c1"]))])
        reviews = evaluation.review_template(data)
        reviews["reviews"][0]["responseFingerprint"] = "old-answer"
        with self.assertRaisesRegex(ValueError, "fingerprint"):
            evaluation.summarize(data, reviews)
        reviews = evaluation.review_template(data)
        reviews["reviews"][0]["citations"][0]["citationIndex"] = 99
        with self.assertRaisesRegex(ValueError, "indexes"):
            evaluation.summarize(data, reviews)

    def test_reviewed_source_references_must_exist_in_the_approved_snapshot(self):
        manifest = {"datasetVersion": "test", "cases": [row(labels=labelled(True, ["c2"]))]}
        corpus = {"chunks": [{"source": source()}]}
        with self.assertRaisesRegex(ValueError, "absent"):
            evaluation.validate_manifest(manifest, corpus)
        manifest["cases"][0]["labels"] = labelled(True, ["c1"])
        manifest["cases"][0]["labels"]["expectedPassages"] = [{"chunkId": "c1", "quote": "invented passage"}]
        with self.assertRaisesRegex(ValueError, "literal"):
            evaluation.validate_manifest(manifest, corpus)

    def test_capture_binds_corpus_and_keeps_generation_opt_in(self):
        corpus = {"corpus": {"fingerprint": "same", "chunkCount": 1}, "settings": {"topK": 3}, "chunks": [{"source": source()}]}
        calls = []

        def request(path, body=None):
            calls.append((path, body))
            if body is None:
                return corpus
            return {"corpus": corpus["corpus"], "settings": corpus["settings"], "results": [result("LEXICAL", None, "NOT_REQUESTED")]}

        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "manifest.json"
            output = Path(directory) / "result.json"
            evaluation.save(manifest, {"datasetVersion": "test", "cases": [{"id": "dev", "split": "development", "question": "Fixture?"}]})
            args = SimpleNamespace(manifest=manifest, output=str(output), base_url="http://127.0.0.1/api", modes=["LEXICAL"], generate=False)
            with patch.object(evaluation, "api", return_value=request):
                evaluation.capture(args)
            self.assertFalse(calls[1][1]["generateAnswer"])
            self.assertEqual(calls[1][1]["expectedCorpusFingerprint"], "same")
            saved = evaluation.load(output)
            self.assertEqual(len(saved["cases"]), 1)
            self.assertTrue(saved["cases"][0]["response"]["results"][0]["responseFingerprint"])


if __name__ == "__main__":
    unittest.main()
