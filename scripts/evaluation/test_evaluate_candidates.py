"""Synthetic evaluator regression checks. No HTTP/provider calls or real CVs."""
import copy
import io
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
from xml.sax.saxutils import escape
import urllib.error
import zipfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
FIXTURES = HERE.parents[1] / "evaluation/candidates/fixtures"
SPEC = importlib.util.spec_from_file_location("cv_evaluator", HERE / "evaluate_candidates.py")
cv = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cv)


class FixtureApi:
    def __init__(self, fixture):
        self.fixture = fixture
        self.calls = []
        self.upload_index = 0
        self.failed_upload = None
        self.profile_version = 1

    def __call__(self, path, data=None, method="GET", content_type="application/json"):
        self.calls.append((method, path, data))
        if (method, path) == ("GET", "/ai/cv/requirements"):
            return {"version": self.profile_version, "title": "Synthetic rubric"}
        if (method, path) == ("POST", "/ai/cv/upload"):
            index = self.upload_index
            self.upload_index += 1
            if index == self.failed_upload:
                raise urllib.error.HTTPError("fixture", 503, "simulated failure", {}, None)
            return copy.deepcopy(self.fixture["uploads"][index])
        if method == "PATCH" and path.endswith("/review"):
            return copy.deepcopy(self.fixture["reviews"][path.split("/")[-2]])
        raise AssertionError(f"Unexpected mutation or endpoint: {method} {path}")


class CandidateEvaluatorTest(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads((FIXTURES / "candidate-development.json").read_text())
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "manifest.json"
        self.manifest = self.fixture["manifest"]
        # Real minimal DOCX containers, containing only the synthetic text above.
        for case, text in zip(self.manifest["cases"], self.fixture["documentTexts"]):
            with zipfile.ZipFile(self.root / case["file"], "w") as doc:
                doc.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>')
                doc.writestr("word/document.xml", '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>' + escape(text) + '</w:t></w:r></w:p></w:body></w:document>')
        self.write_manifest()
        self.api = FixtureApi(self.fixture)
        self.network = patch("urllib.request.urlopen", side_effect=AssertionError("Network forbidden in evaluator tests"))
        self.network.start()
        self.addCleanup(self.network.stop)

    def write_manifest(self):
        self.path.write_text(json.dumps(self.manifest))

    def report(self):
        self.write_manifest()
        return cv.evaluate(self.path, self.api)

    def simulate_reviewed_manifest(self):
        self.manifest["datasetKind"] = "REVIEWED"
        self.manifest["labelProvenance"].update(kind="INDEPENDENT_REVIEW", independentHumanReview=True)
        for case in self.manifest["cases"]:
            case["reviewedBy"] = "unit-test-only-simulated-reviewer"
            case["reviewedAt"] = "2026-09-17"

    def test_synthetic_upload_review_metrics_and_redaction(self):
        report = self.report()
        self.assertEqual(report["counts"], {"cases": 3, "failures": 0, "bySplit": {"development": 3}})
        metrics = report["bySplit"]["development"]["metrics"]
        self.assertEqual(metrics["exactFieldAccuracy"], 1)
        self.assertEqual(metrics["labelledValueFields"], 7)
        self.assertEqual(metrics["expectedStatusFields"], 8)
        self.assertEqual(metrics["fieldStatuses"]["labelledFields"], 8)
        self.assertEqual(metrics["fieldStatuses"]["accuracy"], 7 / 8)
        self.assertEqual(metrics["fieldStatuses"]["unreportedPredictions"], 1)
        self.assertEqual(metrics["rubricCases"], 1)
        self.assertEqual(metrics["requiredCriteriaCases"], 2)
        self.assertEqual(metrics["rubricAgreement"], 1)
        self.assertEqual(metrics["requiredCriteriaAgreement"], 1)
        self.assertEqual(report["cases"][0]["extractionStatus"], "EXTRACTED")
        self.assertEqual(len(report["cases"][0]["documentSha256"]), 64)
        self.assertEqual(len(report["manifestSha256"]), 64)
        self.assertEqual(report["evidenceClass"], "SYNTHETIC_CONTRACT_CHECKS")
        self.assertFalse(report["cases"][0]["independentHumanReview"])
        serialized = json.dumps(report)
        self.assertNotIn("00123456", serialized)
        self.assertNotIn('"expectedFields"', serialized)
        self.assertNotIn('"reviewedFields"', serialized)
        self.assertNotIn("documentTexts", serialized)
        reviews = [call for call in self.api.calls if call[0] == "PATCH"]
        self.assertEqual(len(reviews), 1)
        self.assertEqual(reviews[0][2]["fields"]["typePermis"]["value"], "C")
        self.assertEqual(reviews[0][2]["fields"]["formationSecurite"]["value"], "false")
        self.assertTrue(all("decision" not in path for _, path, _ in self.api.calls))

    def test_actual_provider_and_extraction_status_define_strata(self):
        strata = self.report()["bySplit"]["development"]
        self.assertEqual(set(strata["byExtractionStatus"]), {"EXTRACTED", "PROVIDER_UNAVAILABLE", "MANUAL_REVIEW_REQUIRED"})
        self.assertEqual(set(strata["byProviderStatus"]), {"RESPONSE_RECEIVED", "UNAVAILABLE", "NOT_CALLED"})
        self.assertEqual(strata["byProviderStatus"]["UNAVAILABLE"]["cases"], 1)
        self.assertEqual(cv.provider_state({"providerStatus": "DECLINED", "extractionStatus": "EXTRACTED"}),
                         ("DECLINED", "API_PROVIDER_STATUS"))
        self.assertEqual(cv.provider_state({})[0], "UNREPORTED")

    def test_splits_never_share_an_accuracy_aggregate(self):
        # This unit test simulates a schema for reviewed splits; it produces no
        # published final benchmark or claim that these synthetic CVs are reviewed.
        self.simulate_reviewed_manifest()
        self.manifest["cases"][1]["split"] = "final"
        self.fixture["uploads"][1]["fieldEvidence"]["telephone"]["value"] = "wrong"
        report = self.report()
        self.assertNotIn("metrics", report)
        self.assertNotIn("byExtractionStatus", report)
        self.assertEqual(report["bySplit"]["development"]["metrics"]["exactFieldAccuracy"], 1)
        self.assertEqual(report["bySplit"]["final"]["metrics"]["exactFieldAccuracy"], 0.5)
        self.assertEqual(report["bySplit"]["final"]["byProviderStatus"]["UNAVAILABLE"]["cases"], 1)

    def test_null_value_is_absence_but_null_or_omitted_status_is_unlabelled(self):
        fields = cv.field_results({"expectedFields": {"cin": None},
                                   "expectedFieldStatuses": {"telephone": None}},
                                  {"fieldEvidence": {"cin": {"status": "MISSING"}, "telephone": {"value": "123", "status": "UNCERTAIN"}}})
        result = cv.metrics([{"fields": fields}])
        self.assertEqual(result["labelledValueFields"], 1)
        self.assertEqual(result["exactFieldAccuracy"], 1)
        self.assertEqual(result["fieldStatuses"]["labelledFields"], 0)
        self.assertIsNone(result["fieldStatuses"]["accuracy"])
        self.assertIsNone(result["requiredCriteriaAgreement"])
        self.assertIsNone(result["rubricAgreement"])

    def test_all_four_statuses_and_absent_predictions_have_explicit_denominators(self):
        fields = cv.field_results({"expectedFieldStatuses": {"a": "MISSING", "b": "INVALID", "c": "UNCERTAIN", "d": "VERIFIED", "e": "MISSING"}},
                                  {"fieldEvidence": {"a": {"status": "MISSING"}, "b": {"status": "INVALID"}, "c": {"status": "VERIFIED"}, "d": {"status": "VERIFIED"}, "e": {"status": "BOGUS"}}})
        result = cv.metrics([{"fields": fields}])["fieldStatuses"]
        self.assertEqual(result["accuracy"], 3 / 5)
        self.assertEqual(result["perClass"]["MISSING"]["recall"], 0.5)
        self.assertEqual(result["perClass"]["VERIFIED"]["precision"], 0.5)
        self.assertEqual(result["perClass"]["UNCERTAIN"]["f1"], 0)
        self.assertEqual(result["unrecognizedPredictions"], 1)
        self.assertAlmostEqual(result["macroF1"], (2 / 3 + 1 + 0 + 2 / 3) / 4)

    def test_presence_false_and_zero_are_values_and_identity_keeps_leading_zero(self):
        self.assertNotEqual(cv.normalized("00123456", "cin"), cv.normalized("123456", "cin"))
        self.assertEqual(cv.normalized("0.50", "anneesExperience"), cv.normalized(0.5, "anneesExperience"))
        fields = cv.field_results({"expectedFields": {"formationSecurite": False, "anneesExperience": 0}},
                                  {"fieldEvidence": {"formationSecurite": {"value": "false"}, "anneesExperience": {"value": "0.0"}}})
        self.assertTrue(all(field["expectedPresent"] and field["actualPresent"] and field["matches"] for field in fields))

    def test_missing_rubric_labels_are_not_failures(self):
        self.manifest["cases"][0].pop("expectedRequiredCriteriaStatus")
        self.manifest["cases"][1]["expectedScore"] = None
        result = self.report()["bySplit"]["development"]["metrics"]
        self.assertEqual(result["rubricExpectedCases"], 1)
        self.assertEqual(result["requiredCriteriaExpectedCases"], 1)
        self.assertEqual(result["requiredCriteriaCases"], 1)
        self.assertEqual(result["requiredCriteriaAgreement"], 1)

    def test_upload_failure_is_counted_without_fabricated_missing_predictions(self):
        self.api.failed_upload = 0
        report = self.report()
        result = report["bySplit"]["development"]["metrics"]
        self.assertEqual(report["counts"]["failures"], 1)
        self.assertEqual(result["uploadedCases"], 2)
        self.assertEqual(result["expectedValueFields"], 7)
        self.assertEqual(result["labelledValueFields"], 3)
        self.assertEqual(result["rubricExpectedCases"], 1)
        self.assertEqual(result["rubricCases"], 0)
        self.assertEqual(report["cases"][0]["error"], "HTTP 503")
        self.assertEqual(report["cases"][0]["providerStatus"], "UNREPORTED")

    def test_duplicate_source_across_splits_rejected_before_requests(self):
        self.simulate_reviewed_manifest()
        self.manifest["cases"][1].update(file="partial.docx", split="final")
        with self.assertRaisesRegex(ValueError, "multiple splits"):
            self.report()
        self.assertEqual(self.api.calls, [])

    def test_synthetic_final_split_rejected(self):
        self.manifest["cases"][0]["split"] = "final"
        with self.assertRaisesRegex(ValueError, "synthetic development"):
            self.report()
        self.assertEqual(self.api.calls, [])

    def test_missing_provenance_and_split_rejected_before_requests(self):
        for key in ("authoredAt", "authoredBy", "split"):
            saved = self.manifest["cases"][0].pop(key)
            with self.assertRaises(ValueError):
                self.report()
            self.manifest["cases"][0][key] = saved
        self.manifest["labelProvenance"]["independentOfPredictions"] = False
        with self.assertRaisesRegex(ValueError, "independently"):
            self.report()
        self.assertEqual(self.api.calls, [])

    def test_inconsistent_or_invalid_status_labels_rejected(self):
        case = self.manifest["cases"][0]
        for value in ("MISSING", "UNKNOWN"):
            case["expectedFieldStatuses"]["typePermis"] = value
            with self.assertRaises(ValueError):
                self.report()
        self.assertEqual(self.api.calls, [])

    def test_document_hash_mismatch_rejected_before_requests(self):
        self.manifest["cases"][0]["documentSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "document hash"):
            self.report()
        self.assertEqual(self.api.calls, [])

    def test_reviewed_metrics_require_explicit_independent_human_provenance(self):
        self.manifest["datasetKind"] = "REVIEWED"
        with self.assertRaisesRegex(ValueError, "independent human review"):
            self.report()
        self.assertEqual(self.api.calls, [])

    def test_profile_mismatch_prevents_upload(self):
        self.api.profile_version = 2
        with self.assertRaisesRegex(ValueError, "profileVersion"):
            self.report()
        self.assertEqual([(method, path) for method, path, _ in self.api.calls], [("GET", "/ai/cv/requirements")])

    def test_runnable_docx_examples_validate_as_unreviewed_development_only(self):
        folder = FIXTURES / "cv-development"
        manifest = json.loads((folder / "manifest.json").read_text())
        prepared = cv.validate_manifest(manifest, folder)
        self.assertEqual(len(prepared), 2)
        self.assertFalse(manifest["labelProvenance"]["independentHumanReview"])
        for case, path, _, _ in prepared:
            self.assertEqual(case["split"], "development")
            self.assertNotIn("reviewedBy", case)
            with zipfile.ZipFile(path) as doc:
                for entry in doc.namelist():
                    ET.fromstring(doc.read(entry))
                text = " ".join(ET.fromstring(doc.read("word/document.xml")).itertext())
                self.assertIn("CV FICTIF", text)

    def test_cross_origin_redirect_cannot_forward_supervisor_bearer(self):
        handler = cv.NoRedirects()
        handler.parent = Mock()
        request = cv.urllib.request.Request("http://selected-demo.test/api/ai/cv/requirements",
                                            headers={"Authorization": "Bearer synthetic-test-token"})
        for code in (301, 302, 303, 307, 308):
            redirected = handler.http_error_302(request, None, code, "Redirect", {"location": "https://other-origin.test/collect"})
            self.assertIsNone(redirected)
        handler.parent.open.assert_not_called()

    def runner_args(self, output):
        return SimpleNamespace(base_url="http://selected-demo.test/api", manifest=str(self.path), output=str(output))

    def fake_open(self, output, fail_after_first=False):
        def open_request(request, timeout):
            # The first HTTP call must already have a usable output destination.
            saved = json.loads(output.read_text())
            path = cv.urllib.request.urlparse(request.full_url).path.removeprefix("/api")
            if fail_after_first and self.api.upload_index == 1 and path == "/ai/cv/upload":
                self.assertEqual(saved["counts"]["cases"], 1)
                self.assertEqual(saved["cases"][0]["score"], 50)
                raise RuntimeError("Injected interruption after a completed case")
            data = request.data
            if request.get_method() == "PATCH":
                data = json.loads(data)
            result = self.api(path, data, request.get_method())
            return io.BytesIO(json.dumps(result).encode())
        return open_request

    def test_runner_creates_missing_output_parents_before_first_api_call(self):
        output = self.root / "new" / "nested" / "results.json"
        opener = Mock()
        opener.open.side_effect = self.fake_open(output)
        with patch.dict(cv.os.environ, {"GESTI_EVAL_TOKEN": "synthetic-test-token"}), \
                patch.object(cv.urllib.request, "build_opener", return_value=opener), patch("builtins.print"):
            cv.run(self.runner_args(output))
        saved = json.loads(output.read_text())
        self.assertEqual(saved["runStatus"], "COMPLETED")
        self.assertEqual(saved["plannedCases"], 3)
        self.assertEqual(saved["counts"]["cases"], 3)

    def test_invalid_or_existing_output_fails_before_any_api_call(self):
        blocked_parent = self.root / "not-a-directory"
        blocked_parent.write_text("Preserve this file")
        existing = self.root / "existing.json"
        existing.write_text("Preserve earlier results")
        opener = Mock()
        for output in (blocked_parent / "results.json", existing):
            with patch.dict(cv.os.environ, {"GESTI_EVAL_TOKEN": "synthetic-test-token"}), \
                    patch.object(cv.urllib.request, "build_opener", return_value=opener):
                with self.assertRaises(OSError):
                    cv.run(self.runner_args(output))
        opener.open.assert_not_called()
        self.assertEqual(existing.read_text(), "Preserve earlier results")
        self.assertEqual(blocked_parent.read_text(), "Preserve this file")

    def test_runner_preserves_completed_case_when_a_later_case_interrupts(self):
        output = self.root / "partial" / "results.json"
        opener = Mock()
        opener.open.side_effect = self.fake_open(output, fail_after_first=True)
        with patch.dict(cv.os.environ, {"GESTI_EVAL_TOKEN": "synthetic-test-token"}), \
                patch.object(cv.urllib.request, "build_opener", return_value=opener):
            with self.assertRaisesRegex(RuntimeError, "Injected interruption"):
                cv.run(self.runner_args(output))
        saved = json.loads(output.read_text())
        self.assertEqual(saved["runStatus"], "INTERRUPTED")
        self.assertEqual(saved["interruptionType"], "RuntimeError")
        self.assertEqual(saved["plannedCases"], 3)
        self.assertEqual(saved["counts"]["cases"], 1)
        self.assertEqual(saved["cases"][0]["candidateId"], "fixture-candidate-a")
        self.assertEqual(saved["cases"][0]["score"], 50)


if __name__ == "__main__":
    unittest.main()
