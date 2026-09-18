#!/usr/bin/env python3
"""Small, explicit live-provider probe. Never emits credentials or uses fallbacks."""
import argparse
import base64
import datetime
import json
import math
import os
from pathlib import Path
import struct
import time
import urllib.error
import urllib.request
import zlib

ROOT = Path(__file__).resolve().parents[1]
GEMINI = "https://generativelanguage.googleapis.com/v1beta"
ORS = "https://api.openrouteservice.org"


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


def post(url, headers, payload):
    started = time.monotonic()
    request = urllib.request.Request(url, data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json", **headers}, method="POST")
    body = None
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=30) as response:
            result = {"status": "RESPONDED", "httpStatus": response.status}
            body = json.loads(response.read())
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


def probe(settings, vision=False):
    report = {"checkedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "purpose": "Live transport/response check; synthetic inputs, no accuracy claim", "checks": {}}
    checks = report["checks"]
    key = settings.get("GEMINI_API_KEY", "")
    text_model = settings.get("GEMINI_MODEL_TEXT") or "gemini-3.8-flash"
    embedding_model = settings.get("GEMINI_EMBEDDING_MODEL") or "gemini-embedding-2"
    vision_model = settings.get("GEMINI_MODEL_VISION") or text_model
    report["models"] = {"text": text_model, "embedding": embedding_model, "vision": vision_model}
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
        if vision:
            checks["vision"], body = post(f"{GEMINI}/models/{vision_model}:generateContent", headers, {
                "contents": [{"parts": [{"text": 'Assess only visible evidence. Return JSON with status CLEANED, NOT_CLEANED, OBSTRUCTED or CANNOT_DETERMINE. If no collection point is visible, use CANNOT_DETERMINE. Include a brief description; do not invent confidence.'},
                    {"inline_data": {"mime_type": "image/png", "data": base64.b64encode(abstract_png()).decode()}}]}],
                "generationConfig": {"responseMimeType": "application/json", "temperature": 0, "maxOutputTokens": 512}})
            if body is not None:
                try:
                    label = extract_json(body).get("status")
                    valid = label in ("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE")
                    checks["vision"].update(status="PASS" if valid else "INVALID_RESPONSE", prediction=label if valid else None,
                                             uncertaintyObserved=label == "CANNOT_DETERMINE")
                except (ValueError, TypeError, AttributeError, IndexError):
                    checks["vision"]["status"] = "INVALID_RESPONSE"
    else:
        checks["gemini"] = {"status": "NOT_CONFIGURED"}

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
    parser.add_argument("--vision", action="store_true", help="Also send one abstract synthetic PNG (no scene accuracy claim).")
    args = parser.parse_args()
    report = probe(load_settings(args.env_file), args.vision)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if all(item["status"] == "PASS" for item in report["checks"].values()) else 2


if __name__ == "__main__":
    raise SystemExit(main())
