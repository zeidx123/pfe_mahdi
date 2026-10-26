import argparse
import importlib.util
import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

import evaluate_vision as runner

SPEC = importlib.util.spec_from_file_location("vision_metrics", Path(__file__).resolve().parents[2] / "evaluation/vision/metrics.py")
metrics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metrics)
CONFIG = dict(provider="GEMINI", model="test-configurable-model", promptVersion="point-cleanliness-v1")


class FakeClient:
    base_url = "http://127.0.0.1:18180/api"

    def __init__(self, results=None, config=None):
        self.calls = []
        self.results = iter(results) if results is not None else None
        self.configuration = config or CONFIG

    def config(self):
        return self.configuration

    def evaluate(self, plan, config):
        self.calls.append((plan, config))
        if self.results:
            result = next(self.results)
            if isinstance(result, Exception):
                raise result
            return result
        return {**config, "inputMode": plan["inputMode"], "status": "CLEANED", "id": "analysis-test",
                "afterPhotoUrl": "/uploads/photos/after.png", "beforePhotoUrl": "/uploads/photos/before.png" if plan["inputMode"] == "BEFORE_AFTER" else None}


class VisionRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # The backend tests validate real image decoding. These bytes exercise runner MIME/size checks.
        (self.root / "after.png").write_bytes(b"\x89PNG\r\n\x1a\nAFTER-test")
        (self.root / "before.jpg").write_bytes(b"\xff\xd8\xffBEFORE-test")
        self.manifest = {"datasetVersion": "test-v1", "scenes": [
            {"sceneId": "paired", "split": "development", "afterPhoto": "after.png", "beforePhoto": "before.jpg"}]}
        self.manifest_path = self.root / "manifest.json"
        self.save_manifest()
        self.args = argparse.Namespace(manifest=str(self.manifest_path), labels=None, validate_only=False,
                                       output=str(self.root / "results.jsonl"), base_url=FakeClient.base_url, timeout=90)

    def save_manifest(self):
        self.manifest_path.write_text(json.dumps(self.manifest))

    def rows(self):
        return [json.loads(line) for line in Path(self.args.output).read_text().splitlines()]

    def human_labels(self):
        path = self.root / "labels.json"
        labels = [{"sceneId": "paired", "inputMode": mode, "referenceLabel": "NOT_CLEANED",
                   "reviewer": "independent-test-reviewer", "reviewDate": "2026-09-17", "independentReview": True}
                  for mode in ("AFTER_ONLY", "BEFORE_AFTER")]
        path.write_text(json.dumps({"datasetVersion": "test-v1", "labels": labels}))
        self.args.labels = str(path)
        return path

    def test_pair_runs_both_modes_with_same_config_and_no_invented_reference(self):
        client = FakeClient()
        result = runner.run(self.args, client)
        rows = self.rows()
        self.assertEqual(result["analyses"], 2)
        self.assertEqual([p["inputMode"] for p, _ in client.calls], ["AFTER_ONLY", "BEFORE_AFTER"])
        self.assertEqual([p["photos"].keys() for p, _ in client.calls], [{"afterPhoto"}, {"afterPhoto", "beforePhoto"}])
        self.assertTrue(all(c == CONFIG for _, c in client.calls))
        self.assertTrue(all(r["reviewStatus"] == "UNREVIEWED" and r["referenceLabel"] is None for r in rows))
        self.assertTrue(all(r["configurationConfirmed"] for r in rows))
        self.assertTrue(all(not report["accuracyAvailable"] for report in metrics.summarize(rows)))
        metadata = json.loads(Path(self.args.output + ".run.json").read_text())
        self.assertEqual(metadata["apiBaseUrl"], client.base_url)
        self.assertEqual(metadata["status"], "COMPLETED")
        self.assertEqual(len(metadata["manifestSha256"]), 64)

    def test_after_only_scene_does_not_invent_a_pair(self):
        self.manifest["scenes"][0].pop("beforePhoto")
        self.save_manifest()
        result = runner.run(self.args, FakeClient())
        self.assertEqual(result["analyses"], 1)
        self.assertIsNone(self.rows()[0]["beforePhoto"])

    def test_independent_labels_preserved_even_when_predictions_disagree(self):
        self.human_labels()
        client = FakeClient()
        runner.run(self.args, client)
        rows = self.rows()
        self.assertTrue(all(r["prediction"] == "CLEANED" and r["referenceLabel"] == "NOT_CLEANED" for r in rows))
        self.assertTrue(all("referenceLabel" not in p for p, _ in client.calls))
        self.assertEqual(metrics.summarize(rows)[0]["notCleanedPredictedCleanedRate"], 1)

    def test_missing_independent_review_is_rejected_before_network(self):
        path = self.human_labels()
        data = json.loads(path.read_text())
        data["labels"][0]["independentReview"] = False
        path.write_text(json.dumps(data))
        client = FakeClient()
        with self.assertRaisesRegex(ValueError, "independentReview"):
            runner.run(self.args, client)
        self.assertFalse(client.calls)

    def test_identical_photo_split_leakage_and_duplicate_scene_rejected(self):
        self.manifest["scenes"].append({"sceneId": "other-scene", "split": "test", "afterPhoto": "after.png"})
        self.save_manifest()
        client = FakeClient()
        with self.assertRaisesRegex(ValueError, "Split leakage"):
            runner.run(self.args, client)
        self.manifest["scenes"][1]["sceneId"] = "paired"
        self.save_manifest()
        with self.assertRaisesRegex(ValueError, "unique"):
            runner.run(self.args, client)
        self.assertFalse(client.calls)

    def test_abstract_transport_fixture_cannot_acquire_accuracy_labels(self):
        self.manifest["dataKind"] = "ABSTRACT_SYNTHETIC_TRANSPORT_ONLY"
        self.save_manifest()
        self.human_labels()
        with self.assertRaisesRegex(ValueError, "Abstract transport"):
            runner.run(self.args, FakeClient())

    def test_provider_failure_and_transport_error_remain_distinct(self):
        unavailable = {**CONFIG, "inputMode": "AFTER_ONLY", "status": "ANALYSIS_UNAVAILABLE", "id": "saved"}
        result = runner.run(self.args, FakeClient([unavailable, urllib.error.URLError("SECRET-IN-ERROR")]))
        rows = self.rows()
        self.assertEqual(result["failures"], 2)
        self.assertEqual([r["failureKind"] for r in rows], ["ANALYSIS_UNAVAILABLE", "TRANSPORT_ERROR"])
        self.assertNotIn("SECRET-IN-ERROR", Path(self.args.output).read_text())
        self.assertEqual(metrics.summarize(rows)[1]["failureKindCounts"], {"TRANSPORT_ERROR": 1})

    def test_preflight_mismatch_and_response_drift_stop_comparison(self):
        self.manifest["expectedConfiguration"] = {**CONFIG, "model": "different-model"}
        self.save_manifest()
        client = FakeClient()
        with self.assertRaisesRegex(ValueError, "Preflight"):
            runner.run(self.args, client)
        self.assertFalse(client.calls)
        self.manifest.pop("expectedConfiguration")
        self.save_manifest()
        client = FakeClient([{**CONFIG, "model": "changed", "inputMode": "AFTER_ONLY", "status": "CLEANED"}])
        result = runner.run(self.args, client)
        self.assertEqual(len(client.calls), 1)
        self.assertEqual(result["status"], "STOPPED_CONFIGURATION_OR_ACCESS")
        self.assertEqual(self.rows()[0]["prediction"], "ANALYSIS_UNAVAILABLE")

    def test_config_409_stops_without_exposing_error_body(self):
        error = urllib.error.HTTPError("http://backend", 409, "secret message", {}, io.BytesIO(b"SECRET-TOKEN"))
        client = FakeClient([error])
        result = runner.run(self.args, client)
        self.assertEqual(result["analyses"], 1)
        self.assertEqual(self.rows()[0]["failureKind"], "CONFIGURATION_CHANGED")
        self.assertNotIn("SECRET", Path(self.args.output).read_text())

    def test_validate_only_needs_no_token_or_network(self):
        self.args.validate_only = True
        with patch.object(runner, "ApiClient", side_effect=AssertionError("network not allowed")):
            result = runner.run(self.args)
        self.assertEqual(result["plannedAnalyses"], 2)
        self.assertFalse(Path(self.args.output).exists())

    def test_multipart_preserves_jpeg_png_mime_and_sends_no_labels(self):
        _, plans, _, _ = runner.load_dataset(self.manifest_path)
        client = runner.ApiClient(FakeClient.base_url, "test-token-not-real")
        response = io.BytesIO(b"{}")
        with patch.object(client.opener, "open", return_value=response) as opening:
            client.evaluate(plans[1], CONFIG)
        request = opening.call_args.args[0]
        self.assertEqual(request.headers["Authorization"], "Bearer test-token-not-real")
        self.assertIn(b'name="afterPhoto"; filename="afterPhoto.png"\r\nContent-Type: image/png', request.data)
        self.assertIn(b'name="beforePhoto"; filename="beforePhoto.jpg"\r\nContent-Type: image/jpeg', request.data)
        self.assertIn(b'name="expectedModel"', request.data)
        self.assertNotIn(b"referenceLabel", request.data)
        self.assertIsNone(runner.NoRedirects().redirect_request(None, None, 302, "", {}, "http://other"))

    def test_bad_format_and_unsafe_backend_url_rejected(self):
        (self.root / "after.png").write_bytes(b"not an image")
        with self.assertRaisesRegex(ValueError, "JPEG/PNG"):
            runner.load_dataset(self.manifest_path)
        with self.assertRaises(ValueError):
            runner.ApiClient("https://token:secret@example.com/api", "token")


if __name__ == "__main__":
    unittest.main()
