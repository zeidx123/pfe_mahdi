"""Offline regression checks; no real provider credentials or HTTP requests."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch


spec = importlib.util.spec_from_file_location("check_live_providers", Path(__file__).with_name("check-live-providers.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
diagnostic_spec = importlib.util.spec_from_file_location("diagnose_vision", Path(__file__).with_name("diagnose-vision.py"))
diagnostic = importlib.util.module_from_spec(diagnostic_spec)
diagnostic_spec.loader.exec_module(diagnostic)


def response(content=None, finish_reason="stop"):
    return {"choices": [{"finish_reason": finish_reason, "message": {"content": content or json.dumps({
        "status": "CANNOT_DETERMINE", "description": "No collection point is visible."})}}]}


class LiveProviderProbeTest(unittest.TestCase):
    def setUp(self):
        self.settings = {"COLLECTION_AI_PROVIDER": "OPENROUTER", "OPENROUTER_API_KEY": "test-only-key",
                         "OPENROUTER_MODEL_VISION": "qwen/qwen3.8-27b:free"}

    def test_groq_is_default_and_uses_only_its_key_and_supported_request_parameters(self):
        settings = {"GROQ_API_KEY": "groq-test-key", "OPENROUTER_API_KEY": "router-test-key",
                    "GEMINI_API_KEY": "gemini-test-key", "GROQ_TIMEOUT_SECONDS": "75",
                    "OPENROUTER_TIMEOUT_SECONDS": "5", "OPENROUTER_MODEL_VISION": "other/router-model:free"}
        with patch.object(probe, "post", return_value=({"status": "RESPONDED"}, response())) as post:
            result = probe.probe_vision(settings)
        post.assert_called_once()
        url, headers, payload = post.call_args.args
        self.assertEqual(url, "https://api.groq.com/openai/v1/chat/completions")
        self.assertEqual(headers["Authorization"], "Bearer groq-test-key")
        self.assertNotIn("x-goog-api-key", headers)
        self.assertEqual(payload["model"], "qwen/qwen3.8-27b")
        self.assertEqual(payload["reasoning_effort"], "none")
        self.assertEqual(payload["max_completion_tokens"], 1024)
        self.assertFalse(payload["stream"])
        for forbidden in ("provider", "max_price", "reasoning", "max_tokens"):
            self.assertNotIn(forbidden, payload)
        self.assertEqual(payload["response_format"]["type"], "json_schema")
        self.assertTrue(payload["response_format"]["json_schema"]["strict"])
        self.assertEqual(post.call_args.kwargs["timeout"], 75)
        self.assertEqual(result["provider"], "GROQ")
        self.assertEqual(result["status"], "PASS")
        self.assertTrue(result["uncertaintyObserved"])
        for key in ("groq-test-key", "router-test-key", "gemini-test-key"):
            self.assertNotIn(key, json.dumps(result))

    def test_selected_provider_cannot_borrow_other_credentials(self):
        for provider, credentials in (("GROQ", {"OPENROUTER_API_KEY": "router-test-key", "GEMINI_API_KEY": "gemini-test-key"}),
                                      ("OPENROUTER", {"GROQ_API_KEY": "groq-test-key", "GEMINI_API_KEY": "gemini-test-key"})):
            for function in (probe.probe_vision, probe.probe_cv_text):
                with self.subTest(provider=provider, function=function.__name__), patch.object(probe, "post") as post:
                    result = function({**credentials, "COLLECTION_AI_PROVIDER": provider})
                    self.assertEqual(result["status"], "NOT_CONFIGURED")
                    self.assertEqual(result["provider"], provider)
                    post.assert_not_called()

    def test_groq_rejects_unreviewed_models_or_unknown_provider_without_fallback(self):
        settings = {"GROQ_API_KEY": "groq-test-key", "OPENROUTER_API_KEY": "router-test-key"}
        for model in ("qwen/qwen3.8-27b:free", "qwen/qwen3.8-14b", "llama/other", "qwen/qwen3.8-27b?extra=true"):
            with self.subTest(model=model), patch.object(probe, "post") as post:
                result = probe.probe_vision({**settings, "GROQ_MODEL_VISION": model})
                self.assertEqual(result["status"], "INVALID_MODEL_CONFIGURATION")
                post.assert_not_called()
        with patch.object(probe, "post") as post:
            self.assertEqual(probe.probe_cv_text({**settings, "COLLECTION_AI_PROVIDER": "AUTO"})["status"], "INVALID_PROVIDER_CONFIGURATION")
            self.assertEqual(probe.probe_vision({**settings, "GROQ_TIMEOUT_SECONDS": "invalid"})["status"], "INVALID_PROVIDER_CONFIGURATION")
            post.assert_not_called()

    def test_groq_cv_budget_and_failure_paths_never_switch_provider(self):
        settings = {"GROQ_API_KEY": "groq-test-key", "OPENROUTER_API_KEY": "router-test-key"}
        fields = {"drivingExperienceYears": 2, "licenceCategories": ["C"]}
        with patch.object(probe, "post", return_value=({"status": "RESPONDED"}, response(json.dumps(fields)))) as post:
            self.assertEqual(probe.probe_cv_text(settings)["status"], "PASS")
            payload = post.call_args.args[2]
            self.assertEqual(payload["max_completion_tokens"], 4096)
            self.assertEqual(payload["response_format"]["json_schema"]["name"], "cv_probe")
            self.assertEqual(payload["reasoning_effort"], "none")
            self.assertNotIn("provider", payload)
            self.assertEqual(post.call_args.kwargs["timeout"], 60)
        failures = (({"status": "PROVIDER_UNAVAILABLE", "httpStatus": 503}, None, "PROVIDER_UNAVAILABLE"),
                    ({"status": "RESPONDED"}, {"error": {"message": "private error"}}, "PROVIDER_UNAVAILABLE"),
                    ({"status": "RESPONDED"}, response(finish_reason="length"), "INVALID_RESPONSE"),
                    ({"status": "RESPONDED"}, response("not JSON"), "INVALID_RESPONSE"))
        for transport, body, expected in failures:
            with self.subTest(expected=expected, body=body), patch.object(probe, "post", return_value=(transport, body)) as post:
                result = probe.probe_vision(settings)
                self.assertEqual(result["status"], expected)
                self.assertEqual(result["provider"], "GROQ")
                self.assertNotIn("private error", json.dumps(result))
                post.assert_called_once()

    def test_openrouter_selected_without_gemini_key_and_never_paid(self):
        with patch.object(probe, "post", return_value=({"status": "RESPONDED", "httpStatus": 200}, response())) as post:
            result = probe.probe_vision(self.settings)
        post.assert_called_once()
        url, headers, payload = post.call_args.args
        self.assertEqual(url, "https://openrouter.ai/api/v1/chat/completions")
        self.assertEqual(headers["Authorization"], "Bearer test-only-key")
        self.assertNotIn("x-goog-api-key", headers)
        self.assertEqual(payload["model"], self.settings["OPENROUTER_MODEL_VISION"])
        self.assertEqual(payload["reasoning"], {"enabled": False})
        self.assertEqual(payload["max_tokens"], 1024)
        self.assertEqual(payload["provider"], {"allow_fallbacks": False, "require_parameters": True,
            "max_price": {"prompt": 0, "completion": 0, "request": 0, "image": 0}})
        self.assertTrue(payload["response_format"]["json_schema"]["strict"])
        self.assertEqual(payload["response_format"]["json_schema"]["schema"]["required"], ["status", "description"])
        self.assertTrue(payload["messages"][0]["content"][1]["image_url"]["url"].startswith("data:image/png;base64,"))
        self.assertEqual(post.call_args.kwargs["timeout"], 60)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["prediction"], "CANNOT_DETERMINE")
        self.assertEqual(result["provider"], "OPENROUTER")
        self.assertNotIn("test-only-key", json.dumps(result))

    def test_missing_or_placeholder_key_never_calls_provider(self):
        for key in ("", "VOTRE_CLE_OPENROUTER_ICI"):
            with self.subTest(key=key), patch.object(probe, "post") as post:
                result = probe.probe_vision({**self.settings, "OPENROUTER_API_KEY": key})
                self.assertEqual(result["status"], "NOT_CONFIGURED")
                post.assert_not_called()

    def test_paid_model_and_automatic_router_rejected_before_request(self):
        for model in ("qwen/qwen3.8-27b", "openrouter/free", "openrouter/auto:free", "qwen/model:free?paid=true"):
            with self.subTest(model=model), patch.object(probe, "post") as post:
                result = probe.probe_vision({**self.settings, "OPENROUTER_MODEL_VISION": model})
                self.assertEqual(result["status"], "INVALID_MODEL_CONFIGURATION")
                post.assert_not_called()

    def test_invalid_and_incomplete_assessments_cannot_pass(self):
        for body in ({}, {"choices": []}, response("not json"), response(finish_reason="length"),
                     response(json.dumps({"status": "CLEANED"})), response(json.dumps({"status": "NORMAL", "description": "Fine"})),
                     {"choices": [{"message": {"refusal": "Cannot analyze", "content": "{}"}}]}):
            with self.subTest(body=body), patch.object(probe, "post", return_value=({"status": "RESPONDED"}, body)):
                self.assertEqual(probe.probe_vision(self.settings)["status"], "INVALID_RESPONSE")

    def test_http_failure_and_error_envelope_never_retry_or_fallback(self):
        for transport, body in (({"status": "PROVIDER_UNAVAILABLE", "httpStatus": 429}, None),
                                ({"status": "RESPONDED", "httpStatus": 200}, {"error": {"message": "private request details"}})):
            with self.subTest(body=body), patch.object(probe, "post", return_value=(transport, body)) as post:
                result = probe.probe_vision(self.settings)
                self.assertEqual(result["status"], "PROVIDER_UNAVAILABLE")
                self.assertNotIn("private request details", json.dumps(result))
                post.assert_called_once()

    def test_malformed_transport_json_and_error_envelope_are_not_success(self):
        for raw, expected in ((b"not json", "INVALID_RESPONSE"), (b"[]", "INVALID_RESPONSE"),
                              (b'{"error":{"message":"private request details"}}', "PROVIDER_UNAVAILABLE")):
            fake_response = MagicMock()
            fake_response.status = 200
            fake_response.read.return_value = raw
            fake_response.__enter__.return_value = fake_response
            opener = MagicMock()
            opener.open.return_value = fake_response
            with self.subTest(raw=raw), patch.object(probe.urllib.request, "build_opener", return_value=opener):
                result, body = probe.post("https://openrouter.ai/api/v1/chat/completions", {}, {})
                self.assertEqual(result["status"], expected)
                self.assertIsNone(body)
                self.assertNotIn("private request details", json.dumps(result))

    def test_gemini_key_cannot_enable_cv_or_vision(self):
        with patch.object(probe, "post") as post:
            for function in (probe.probe_vision, probe.probe_cv_text):
                result = function({"GEMINI_API_KEY": "test-gemini-key"})
                self.assertEqual(result["status"], "NOT_CONFIGURED")
                self.assertEqual(result["provider"], "GROQ")
            post.assert_not_called()

    def test_cv_text_uses_own_free_model_and_validates_extracted_fields(self):
        for fields, expected in (({"drivingExperienceYears": 2, "licenceCategories": ["C"]}, "PASS"),
                                 ({"drivingExperienceYears": "2", "licenceCategories": ["C"]}, "INVALID_RESPONSE"),
                                 ({"drivingExperienceYears": 2, "licenceCategories": ["CE"]}, "INVALID_RESPONSE"),
                                 ({"status": "READY"}, "INVALID_RESPONSE")):
            with self.subTest(fields=fields), patch.object(probe, "post", return_value=({"status": "RESPONDED"}, response(json.dumps(fields)))) as post:
                result = probe.probe_cv_text({**self.settings, "OPENROUTER_MODEL_TEXT": "qwen/cv-test:free"})
                self.assertEqual(result["status"], expected)
                self.assertEqual(post.call_args.args[2]["model"], "qwen/cv-test:free")
                self.assertEqual(post.call_args.args[2]["reasoning"], {"enabled": False})
                self.assertEqual(post.call_args.args[2]["max_tokens"], 4096)
                self.assertEqual(len(post.call_args.args[2]["messages"][0]["content"]), 1)
                self.assertFalse(post.call_args.args[2]["provider"]["allow_fallbacks"])
                self.assertTrue(post.call_args.args[2]["provider"]["require_parameters"])
                output = post.call_args.args[2]["response_format"]
                self.assertEqual(output["type"], "json_schema")
                self.assertTrue(output["json_schema"]["strict"])
                self.assertEqual(output["json_schema"]["name"], "cv_probe")
                schema = output["json_schema"]["schema"]
                self.assertEqual(schema["required"], ["drivingExperienceYears", "licenceCategories"])
                self.assertFalse(schema["additionalProperties"])
        with patch.object(probe, "post") as post:
            self.assertEqual(probe.probe_cv_text({**self.settings, "OPENROUTER_MODEL_TEXT": "qwen/paid"})["status"], "INVALID_MODEL_CONFIGURATION")
            post.assert_not_called()

    def test_complete_probe_keeps_chat_and_embeddings_on_gemini(self):
        bodies = [
            {"candidates": [{"content": {"parts": [{"text": '{"status":"READY"}'}]}}]},
            {"embedding": {"values": [0.1] * 768}},
            response('{"drivingExperienceYears":2,"licenceCategories":["C"]}'), response()]
        with patch.object(probe, "post", side_effect=[({"status": "RESPONDED"}, body) for body in bodies]) as post:
            report = probe.probe({**self.settings, "GEMINI_API_KEY": "test-gemini-key"}, vision=True)
        self.assertEqual(post.call_count, 4)
        urls = [call.args[0] for call in post.call_args_list]
        self.assertTrue(urls[0].startswith(probe.GEMINI) and urls[1].startswith(probe.GEMINI))
        self.assertTrue(urls[2].startswith(probe.OPENROUTER) and urls[3].startswith(probe.OPENROUTER))
        for name in ("text", "embedding", "cvText", "vision"):
            self.assertEqual(report["checks"][name]["status"], "PASS")
        self.assertEqual(report["providers"], {"text": "GEMINI", "embedding": "GEMINI", "cvText": "OPENROUTER", "vision": "OPENROUTER"})

    def test_complete_default_groq_probe_preserves_gemini_rag(self):
        bodies = [{"candidates": [{"content": {"parts": [{"text": '{"status":"READY"}'}]}}]},
                  {"embedding": {"values": [.1] * 768}},
                  response('{"drivingExperienceYears":2,"licenceCategories":["C"]}'), response()]
        with patch.object(probe, "post", side_effect=[({"status": "RESPONDED"}, body) for body in bodies]) as post:
            report = probe.probe({"GROQ_API_KEY": "groq-test-key", "GEMINI_API_KEY": "gemini-test-key",
                                  "OPENROUTER_API_KEY": "router-test-key"}, vision=True)
        self.assertEqual(post.call_count, 4)
        self.assertEqual(report["providers"], {"text": "GEMINI", "embedding": "GEMINI", "cvText": "GROQ", "vision": "GROQ"})
        for index, call in enumerate(post.call_args_list):
            self.assertTrue(call.args[0].startswith(probe.GEMINI if index < 2 else probe.GROQ))
            self.assertNotIn("router-test-key", json.dumps(call.args))
        self.assertTrue(all(report["checks"][name]["status"] == "PASS" for name in ("text", "embedding", "cvText", "vision")))


class DirectVisionDiagnosticTest(unittest.TestCase):
    def test_groq_selection_and_configuration_are_isolated(self):
        provider, model, key, timeout = diagnostic.select_configuration({"GROQ_API_KEY": "groq-test-key",
            "OPENROUTER_API_KEY": "router-test-key", "OPENROUTER_MODEL_VISION": "other/model:free", "GROQ_TIMEOUT_SECONDS": "70"})
        self.assertEqual((provider, model, key, timeout), ("GROQ", "qwen/qwen3.8-27b", "groq-test-key", 70))
        self.assertEqual(diagnostic.select_configuration({"COLLECTION_AI_PROVIDER": "OPENROUTER",
            "OPENROUTER_API_KEY": "router-test-key", "GROQ_API_KEY": "groq-test-key"})[:3],
            ("OPENROUTER", "qwen/qwen3.8-27b:free", "router-test-key"))
        for config in ({"OPENROUTER_API_KEY": "router-test-key"},
                       {"COLLECTION_AI_PROVIDER": "OPENROUTER", "GROQ_API_KEY": "groq-test-key"},
                       {"GROQ_API_KEY": "groq-test-key", "GROQ_MODEL_VISION": "qwen/unreviewed"},
                       {"GROQ_API_KEY": "groq-test-key", "GROQ_TIMEOUT_SECONDS": "NaN"},
                       {"COLLECTION_AI_PROVIDER": "AUTO", "GROQ_API_KEY": "groq-test-key"}):
            with self.subTest(config=config), self.assertRaises(ValueError):
                diagnostic.select_configuration(config)

    def test_direct_groq_request_has_strict_schema_and_no_router_parameters(self):
        image = MagicMock(name="image")
        image.name = "synthetic.png"
        image.read_bytes.return_value = probe.abstract_png()
        fake_response = MagicMock()
        fake_response.status = 200
        fake_response.read.return_value = json.dumps(response()).encode()
        fake_response.__enter__.return_value = fake_response
        opener = MagicMock()
        opener.open.return_value = fake_response
        with patch.object(diagnostic.urllib.request, "build_opener", return_value=opener):
            result = diagnostic.probe(image, "qwen/qwen3.8-27b", "groq-test-key", "Synthetic image.", 60)
        request = opener.open.call_args.args[0]
        payload = json.loads(request.data)
        self.assertEqual(request.full_url, "https://api.groq.com/openai/v1/chat/completions")
        self.assertEqual(request.get_header("Authorization"), "Bearer groq-test-key")
        self.assertEqual(payload["reasoning_effort"], "none")
        self.assertEqual(payload["max_completion_tokens"], 1024)
        self.assertTrue(payload["response_format"]["json_schema"]["strict"])
        for forbidden in ("reasoning", "provider", "max_tokens", "max_price"):
            self.assertNotIn(forbidden, payload)
        self.assertEqual(result["provider"], "GROQ")
        self.assertEqual(result["status"], "PASS")
        self.assertNotIn("groq-test-key", json.dumps(result))
        opener.open.assert_called_once()

    def test_groq_diagnostic_failure_is_sanitized_and_has_no_fallback(self):
        image = MagicMock()
        image.name = "synthetic.png"
        image.read_bytes.return_value = probe.abstract_png()
        error = diagnostic.urllib.error.HTTPError("https://api.groq.com/openai/v1/chat/completions", 429, "quota", {},
            io.BytesIO(json.dumps({"error": {"message": "quota groq-test-key gsk_synthetic_other_key", "code": "rate_limit"}}).encode()))
        opener = MagicMock()
        opener.open.side_effect = error
        try:
            with patch.object(diagnostic.urllib.request, "build_opener", return_value=opener):
                result = diagnostic.probe(image, "qwen/qwen3.8-27b", "groq-test-key", "Synthetic image.", 60)
            self.assertEqual(result["status"], "PROVIDER_UNAVAILABLE")
            self.assertEqual(result["httpStatus"], 429)
            self.assertEqual(result["provider"], "GROQ")
            self.assertNotIn("groq-test-key", json.dumps(result))
            self.assertNotIn("gsk_synthetic_other_key", json.dumps(result))
            opener.open.assert_called_once()
        finally:
            error.close()

    def test_direct_diagnostic_uses_only_free_openrouter_and_preserves_report_fields(self):
        image = MagicMock()
        image.name = "synthetic.png"
        image.read_bytes.return_value = probe.abstract_png()
        envelope = {**response(), "model": "qwen/qwen3.8-27b", "usage": {"prompt_tokens": 20, "completion_tokens": 10, "total_tokens": 30}}
        fake_response = MagicMock()
        fake_response.status = 200
        fake_response.read.return_value = json.dumps(envelope).encode()
        fake_response.__enter__.return_value = fake_response
        opener = MagicMock()
        opener.open.return_value = fake_response
        with patch.object(diagnostic.urllib.request, "build_opener", return_value=opener):
            result = diagnostic.probe(image, "qwen/qwen3.8-27b:free", "test-secret", "Assess this synthetic image.", 60, "OPENROUTER")
        request = opener.open.call_args.args[0]
        payload = json.loads(request.data)
        self.assertEqual(request.full_url, "https://openrouter.ai/api/v1/chat/completions")
        self.assertEqual(request.get_header("Authorization"), "Bearer test-secret")
        self.assertEqual(payload["reasoning"], {"enabled": False})
        self.assertEqual(payload["max_tokens"], 1024)
        self.assertFalse(payload["provider"]["allow_fallbacks"])
        self.assertTrue(all(price == 0 for price in payload["provider"]["max_price"].values()))
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["prediction"], "CANNOT_DETERMINE")
        self.assertEqual(result["inputMode"], "AFTER_ONLY")
        self.assertEqual(len(result["imageSha256"]), 64)
        self.assertEqual(result["usageMetadata"]["totalTokenCount"], 30)
        self.assertNotIn("test-secret", json.dumps(result))
        opener.open.assert_called_once()

    def test_paid_model_and_missing_key_stop_before_file_or_network(self):
        image = MagicMock()
        for model, key in (("qwen/qwen3.8-27b", "test-key"), ("openrouter/auto:free", "test-key"),
                           ("qwen/qwen3.8-27b:free", "VOTRE_CLE_OPENROUTER_ICI")):
            with self.subTest(model=model, key=key), patch.object(diagnostic.urllib.request, "build_opener") as opener:
                with self.assertRaises(ValueError):
                    diagnostic.probe(image, model, key, "Synthetic image.", 60, "OPENROUTER")
                opener.assert_not_called()
                image.read_bytes.assert_not_called()

    def test_provider_error_truncation_or_missing_evidence_never_passes(self):
        for body, expected in (({"error": {"message": "private data"}}, "PROVIDER_UNAVAILABLE"),
                               ([], "INVALID_RESPONSE"), ({"choices": []}, "INVALID_RESPONSE"),
                               (response(finish_reason="length"), "INVALID_RESPONSE"),
                               (response("not json"), "INVALID_RESPONSE"),
                               (response('{"status":"CLEANED"}'), "INVALID_RESPONSE")):
            result = {}
            with self.subTest(body=body):
                diagnostic.apply_provider_response(result, body, "test-secret")
                self.assertEqual(result["status"], expected)
                self.assertNotIn("private data", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
