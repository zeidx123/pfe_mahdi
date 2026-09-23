#!/usr/bin/env python3
"""Small, explicit live-provider probe. Never emits credentials or uses fallbacks."""
import argparse
import base64
import datetime
import json
import math
import os
from pathlib import Path
import re
import struct
import time
import urllib.error
import urllib.request
import zlib

ROOT = Path(__file__).resolve().parents[1]
GEMINI = "https://generativelanguage.googleapis.com/v1beta"
ORS = "https://api.openrouteservice.org"
OPENROUTER = "https://openrouter.ai/api/v1"
GROQ = "https://api.groq.com/openai/v1"
GROQ_MODELS = {"qwen/qwen3.8-27b"}
VISION_STATES = ("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE")
VISION_PROMPT = ("Assess only visible evidence. Return JSON with status CLEANED, NOT_CLEANED, "
                 "OBSTRUCTED or CANNOT_DETERMINE. If no collection point is visible, use "
                 "CANNOT_DETERMINE. Include a brief description; do not invent confidence.")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, new_url):
        # Provider credentials belong only to the explicitly selected origin.
        return None


def load_settings(path):
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                name, value = line.split("=", 1)
                values[name.strip()] = value.strip()
    values.update(os.environ)
    return values


def configured(value):
    return bool(value) and not any(marker in value.upper() for marker in ("VOTRE_CLE", "REPLACE", "YOUR_KEY"))


def post(url, headers, payload, timeout=30):
    started = time.monotonic()
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json", **headers}, method="POST")
    body = None
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=timeout) as response:
            result = {"status": "RESPONDED", "httpStatus": response.status}
            try:
                body = json.loads(response.read())
                if not isinstance(body, dict):
                    result["status"], body = "INVALID_RESPONSE", None
                elif body.get("error") is not None:
                    result.update(status="PROVIDER_UNAVAILABLE", failureCategory="PROVIDER_RESPONSE_ERROR")
                    body = None
            except ValueError:
                result["status"] = "INVALID_RESPONSE"
    except urllib.error.HTTPError as error:
        # Provider bodies/URLs can contain request details. Keep only the HTTP code.
        result = {"status": "PROVIDER_UNAVAILABLE", "httpStatus": error.code}
    except (OSError, ValueError):
        result = {"status": "REQUEST_FAILED"}
    result["latencyMs"] = round((time.monotonic() - started) * 1000)
    return result, body


def extract_json(body):
    parts = (body or {}).get("candidates", [{}])[0].get("content", {}).get("parts", [])
    text = "".join(part.get("text", "") for part in parts if not part.get("thought"))
    return json.loads(text)


def abstract_png():
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data) & 0xffffffff)
    pixels = b"".join(b"\0" + bytes([128, 128, 128] * 32) for _ in range(32))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!IIBBBBB", 32, 32, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


def extract_chat_json(body):
    choice = body["choices"][0]
    if choice.get("finish_reason") not in (None, "", "stop") or choice.get("error") is not None:
        raise ValueError("Incomplete or failed provider response")
    message = choice["message"]
    if message.get("refusal"):
        raise ValueError("Provider refusal")
    content = message.get("content")
    if isinstance(content, list):
        content = "".join(part["text"] for part in content if isinstance(part, dict)
                          and part.get("type") == "text" and isinstance(part.get("text"), str))
    return json.loads(content)


def openrouter_completion(settings, model_setting, content, schema_name, schema):
    model = settings.get(model_setting) or "qwen/qwen3.8-27b:free"
    key = settings.get("OPENROUTER_API_KEY", "")
    metadata = {"provider": "OPENROUTER", "model": model}
    if not configured(key):
        return {**metadata, "status": "NOT_CONFIGURED"}, None
    if (not re.fullmatch(r"[A-Za-z0-9._-]+/[A-Za-z0-9._-]+:free", model) or model.startswith("openrouter/")):
        return {**metadata, "status": "INVALID_MODEL_CONFIGURATION"}, None
    try:
        timeout = max(1, min(120, int(settings.get("OPENROUTER_TIMEOUT_SECONDS") or "60")))
    except (ValueError, TypeError):
        return {**metadata, "status": "INVALID_PROVIDER_CONFIGURATION"}, None
    result, body = post(f"{OPENROUTER}/chat/completions", {
        "Authorization": f"Bearer {key}", "Accept": "application/json"}, {
        "model": model, "messages": [{"role": "user", "content": content}],
        "stream": False, "temperature": 0, "reasoning": {"enabled": False},
        "max_tokens": 1024 if model_setting == "OPENROUTER_MODEL_VISION" else 4096,
        "response_format": {"type": "json_schema", "json_schema": {
            "name": schema_name, "strict": True, "schema": schema}},
        "provider": {"allow_fallbacks": False, "require_parameters": True,
                     "max_price": {"prompt": 0, "completion": 0, "request": 0, "image": 0}}}, timeout=timeout)
    result.update(metadata)
    if body is not None:
        if isinstance(body, dict) and body.get("error") is not None:
            result.update(status="PROVIDER_UNAVAILABLE", failureCategory="PROVIDER_RESPONSE_ERROR")
            return result, None
        try:
            parsed = extract_chat_json(body)
            if isinstance(parsed, dict):
                return result, parsed
            result["status"] = "INVALID_RESPONSE"
        except (ValueError, TypeError, AttributeError, IndexError, KeyError):
            result["status"] = "INVALID_RESPONSE"
    return result, None


def groq_completion(settings, model_setting, content, schema_name, schema):
    model = settings.get(model_setting) or "qwen/qwen3.8-27b"
    key = settings.get("GROQ_API_KEY", "")
    metadata = {"provider": "GROQ", "model": model}
    if not configured(key):
        return {**metadata, "status": "NOT_CONFIGURED"}, None
    if model not in GROQ_MODELS:
        return {**metadata, "status": "INVALID_MODEL_CONFIGURATION"}, None
    try:
        timeout = max(1, min(120, int(settings.get("GROQ_TIMEOUT_SECONDS") or "60")))
    except (ValueError, TypeError):
        return {**metadata, "status": "INVALID_PROVIDER_CONFIGURATION"}, None
    result, body = post(f"{GROQ}/chat/completions", {
        "Authorization": f"Bearer {key}", "Accept": "application/json"}, {
        "model": model, "messages": [{"role": "user", "content": content}],
        "stream": False, "temperature": 0, "reasoning_effort": "none",
        "max_completion_tokens": 1024 if model_setting == "GROQ_MODEL_VISION" else 4096,
        "response_format": {"type": "json_schema", "json_schema": {
            "name": schema_name, "strict": True, "schema": schema}}}, timeout=timeout)
    result.update(metadata)
    if body is not None:
        if isinstance(body, dict) and body.get("error") is not None:
            result.update(status="PROVIDER_UNAVAILABLE", failureCategory="PROVIDER_RESPONSE_ERROR")
            return result, None
        try:
            parsed = extract_chat_json(body)
            if isinstance(parsed, dict):
                return result, parsed
            result["status"] = "INVALID_RESPONSE"
        except (ValueError, TypeError, AttributeError, IndexError, KeyError):
            result["status"] = "INVALID_RESPONSE"
    return result, None


def collection_provider(settings):
    return (settings.get("COLLECTION_AI_PROVIDER") or "GROQ").strip().upper()


def collection_model(settings, kind):
    provider = collection_provider(settings)
    if provider not in ("GROQ", "OPENROUTER"):
        return None
    default = "qwen/qwen3.8-27b" if provider == "GROQ" else "qwen/qwen3.8-27b:free"
    return settings.get(f"{provider}_MODEL_{kind}") or default


def collection_completion(settings, kind, content, schema_name, schema):
    provider = collection_provider(settings)
    if provider == "GROQ":
        return groq_completion(settings, f"GROQ_MODEL_{kind}", content, schema_name, schema)
    if provider == "OPENROUTER":
        return openrouter_completion(settings, f"OPENROUTER_MODEL_{kind}", content, schema_name, schema)
    return {"provider": provider, "model": None, "status": "INVALID_PROVIDER_CONFIGURATION"}, None


def probe_vision(settings):
    png = base64.b64encode(abstract_png()).decode()
    schema = {"type": "object", "properties": {
        "status": {"type": "string", "enum": list(VISION_STATES)},
        "description": {"type": "string"}},
        "required": ["status", "description"], "additionalProperties": False}
    result, assessment = collection_completion(settings, "VISION", [
        {"type": "text", "text": VISION_PROMPT},
        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{png}"}}], "point_cleanliness", schema)
    if assessment is not None:
        label, description = assessment.get("status"), assessment.get("description")
        valid = label in VISION_STATES and isinstance(description, str) and bool(description.strip())
        result.update(status="PASS" if valid else "INVALID_RESPONSE", prediction=label if valid else None,
                      uncertaintyObserved=valid and label == "CANNOT_DETERMINE")
    return result


def probe_cv_text(settings):
    # Both reviewed routes use strict structured outputs rather than a loose JSON-object request.
    schema = {"type": "object", "properties": {
        "drivingExperienceYears": {"type": "number"},
        "licenceCategories": {"type": "array", "items": {"type": "string"}}},
        "required": ["drivingExperienceYears", "licenceCategories"], "additionalProperties": False}
    prompt = ("Synthetic software check, not a real candidate: fictional CV snippet says "
              "'Driving experience: 2 years. Driving licence: C.' Extract only drivingExperienceYears "
              "and licenceCategories as JSON. Do not invent any fields.")
    result, fields = collection_completion(settings, "TEXT", [
        {"type": "text", "text": prompt}], "cv_probe", schema)
    if fields is not None:
        years = fields.get("drivingExperienceYears")
        valid = isinstance(years, (int, float)) and not isinstance(years, bool) and years == 2 and fields.get("licenceCategories") == ["C"]
        result["status"] = "PASS" if valid else "INVALID_RESPONSE"
    return result


def probe(settings, vision=False):
    report = {"checkedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "purpose": "Live transport/response check; synthetic inputs, no accuracy claim", "checks": {}}
    checks = report["checks"]
    key = settings.get("GEMINI_API_KEY", "")
    text_model = settings.get("GEMINI_MODEL_TEXT") or "gemini-3.8-flash"
    embedding_model = settings.get("GEMINI_EMBEDDING_MODEL") or "gemini-embedding-2"
    vision_model = collection_model(settings, "VISION")
    cv_model = collection_model(settings, "TEXT")
    report["models"] = {"text": text_model, "embedding": embedding_model, "cvText": cv_model, "vision": vision_model}
    report["providers"] = {"text": "GEMINI", "embedding": "GEMINI",
                           "cvText": collection_provider(settings), "vision": collection_provider(settings)}
    if configured(key):
        headers = {"x-goog-api-key": key}
        checks["text"], body = post(f"{GEMINI}/models/{text_model}:generateContent", headers, {
            "contents": [{"parts": [{"text": 'Synthetic software check. Return JSON {"status":"READY"}.'}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0, "maxOutputTokens": 256}})
        if body is not None:
            try:
                checks["text"]["status"] = "PASS" if extract_json(body).get("status") == "READY" else "INVALID_RESPONSE"
            except (ValueError, TypeError, AttributeError, IndexError):
                checks["text"]["status"] = "INVALID_RESPONSE"
        checks["embedding"], body = post(f"{GEMINI}/models/{embedding_model}:embedContent", headers, {
            "model": f"models/{embedding_model}", "content": {"parts": [{"text": "Fictional collection route test."}]},
            "outputDimensionality": 768})
        if body is not None:
            vector = body.get("embedding", {}).get("values", [])
            valid = len(vector) == 768 and all(isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(x) for x in vector)
            checks["embedding"].update(status="PASS" if valid else "INVALID_RESPONSE", dimensions=len(vector))
    else:
        checks["gemini"] = {"status": "NOT_CONFIGURED"}
    checks["cvText"] = probe_cv_text(settings)
    if vision:
        checks["vision"] = probe_vision(settings)

    ors_key = settings.get("ORS_API_KEY", "")
    if configured(ors_key):
        latitude = float(settings.get("DEPOT_LATITUDE", "36.722857"))
        longitude = float(settings.get("DEPOT_LONGITUDE", "10.283535"))
        # Probe coordinates near the configured depot; not a reviewed collection scenario.
        locations = [[longitude, latitude], [longitude + .001, latitude + .001]]
        profile = settings.get("ORS_PROFILE") or "driving-car"
        checks["roadMatrix"], body = post(f"{ORS}/v2/matrix/{profile}", {"Authorization": ors_key},
            {"locations": locations, "metrics": ["duration", "distance"], "units": "m"})
        if body is not None:
            matrices = [body.get("durations"), body.get("distances")]
            valid = all(isinstance(matrix, list) and len(matrix) == 2 and
                all(isinstance(row, list) and len(row) == 2 and all(isinstance(x, (int, float)) and math.isfinite(x) and x >= 0 for x in row) for row in matrix)
                for matrix in matrices)
            checks["roadMatrix"]["status"] = "PASS" if valid else "INVALID_OR_UNREACHABLE"
    else:
        checks["roadMatrix"] = {"status": "NOT_CONFIGURED"}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / "BACK/.env")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--vision", action="store_true", help="Also send one abstract PNG to the selected collection provider (no scene accuracy claim).")
    args = parser.parse_args()
    report = probe(load_settings(args.env_file), args.vision)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if all(item["status"] == "PASS" for item in report["checks"].values()) else 2


if __name__ == "__main__":
    raise SystemExit(main())
