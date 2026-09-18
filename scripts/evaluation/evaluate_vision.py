#!/usr/bin/env python3
"""Run a staged vision benchmark through the supervisor API. No direct model calls.

Images/analysis records are saved to the selected backend; tours are not modified.
The token is read only from GESTI_EVAL_TOKEN. Uses Python's standard library.
"""
import argparse
import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timezone
from pathlib import Path

LABELS = {"CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE"}
MODES = {"AFTER_ONLY", "BEFORE_AFTER"}
CONFIG_KEYS = ("provider", "model", "promptVersion")
MAX_IMAGE_BYTES = 10 * 1024 * 1024


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def image_data(path):
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_IMAGE_BYTES:
        raise ValueError("Each image must exist and contain at most 10 MiB")
    data = path.read_bytes()
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return data, "image/png"
    if data.startswith(b"\xff\xd8\xff"):
        return data, "image/jpeg"
    raise ValueError("Only JPEG/PNG images are supported; the API also validates decoding")


def configuration(value):
    if not isinstance(value, dict) or any(not isinstance(value.get(k), str) or not value[k].strip() for k in CONFIG_KEYS):
        raise ValueError("Configuration requires nonempty provider, model and promptVersion")
    return {key: value[key] for key in CONFIG_KEYS}


def load_dataset(manifest_path, labels_path=None):
    path = Path(manifest_path).resolve()
    raw = path.read_bytes()
    manifest = json.loads(raw)
    if not isinstance(manifest, dict):
        raise ValueError("Manifest must be a JSON object")
    if not isinstance(manifest.get("datasetVersion"), str) or not manifest["datasetVersion"].strip():
        raise ValueError("datasetVersion is required")
    kind = manifest.get("dataKind", "STAGED_PHOTOS")
    if kind not in {"STAGED_PHOTOS", "ABSTRACT_SYNTHETIC_TRANSPORT_ONLY"}:
        raise ValueError("Unknown dataKind")
    scenes = manifest.get("scenes")
    if not isinstance(scenes, list) or not scenes:
        raise ValueError("At least one scene is required")
    if manifest.get("expectedConfiguration") is not None:
        configuration(manifest["expectedConfiguration"])
    seen, photo_splits, plans = set(), {}, []
    for scene in scenes:
        if not isinstance(scene, dict):
            raise ValueError("Each scene must be a JSON object")
        scene_id, split = scene.get("sceneId"), scene.get("split")
        if not isinstance(scene_id, str) or not scene_id.strip() or scene_id in seen:
            raise ValueError("Scene IDs must be nonempty and unique; a scene cannot cross splits")
        if split not in {"development", "validation", "test"}:
            raise ValueError("split must be development, validation or test")
        seen.add(scene_id)
        photos = {}
        for field in ("afterPhoto", "beforePhoto"):
            reference = scene.get(field)
            if field == "beforePhoto" and reference is None:
                continue
            if not isinstance(reference, str) or not reference.strip():
                raise ValueError("Every scene requires an AFTER image and valid optional BEFORE path")
            data, mime = image_data(path.parent / reference)
            sha = digest(data)
            if sha in photo_splits and photo_splits[sha] != split:
                raise ValueError("Split leakage: identical photo bytes occur in different splits")
            photo_splits[sha] = split
            photos[field] = {"reference": reference, "data": data, "mime": mime, "sha256": sha}
        for mode in (["AFTER_ONLY", "BEFORE_AFTER"] if "beforePhoto" in photos else ["AFTER_ONLY"]):
            plans.append({"sceneId": scene_id, "split": split, "inputMode": mode,
                          "photos": {key: value for key, value in photos.items()
                                     if key == "afterPhoto" or mode == "BEFORE_AFTER"}})
    labels, labels_sha = {}, None
    if labels_path:
        labels_raw = Path(labels_path).read_bytes()
        labels_sha = digest(labels_raw)
        reviewed = json.loads(labels_raw)
        if not isinstance(reviewed, dict) or reviewed.get("datasetVersion") != manifest["datasetVersion"]:
            raise ValueError("Labels datasetVersion differs from manifest")
        if not isinstance(reviewed.get("labels"), list):
            raise ValueError("Labels requires a labels array")
        keys = {(p["sceneId"], p["inputMode"]) for p in plans}
        for label in reviewed.get("labels", []):
            if not isinstance(label, dict):
                raise ValueError("Each review label must be a JSON object")
            key = (label.get("sceneId"), label.get("inputMode"))
            if key not in keys or key in labels:
                raise ValueError("Label scene/mode is unknown or duplicated")
            if label.get("referenceLabel") not in LABELS or label.get("independentReview") is not True:
                raise ValueError("Labels require a valid referenceLabel and independentReview: true")
            if not isinstance(label.get("reviewer"), str) or not label["reviewer"].strip():
                raise ValueError("Each human label needs an identified reviewer")
            if not isinstance(label.get("reviewDate"), str):
                raise ValueError("Each review label requires an ISO reviewDate")
            date.fromisoformat(label["reviewDate"])
            labels[key] = {k: label[k] for k in ("referenceLabel", "reviewer", "reviewDate", "independentReview")}
        if labels and kind == "ABSTRACT_SYNTHETIC_TRANSPORT_ONLY":
            raise ValueError("Abstract transport fixtures cannot produce cleanliness accuracy metrics")
    return manifest, plans, labels, {"manifestSha256": digest(raw), "labelsSha256": labels_sha, "dataKind": kind}


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward a supervisor token to a redirected origin.


class ApiClient:
    def __init__(self, base_url, token, timeout=90):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("Use a plain HTTP(S) backend URL without credentials, query or fragment")
        self.base_url = base_url.rstrip("/")
        if timeout <= 0:
            raise ValueError("Timeout must be positive")
        self.token, self.timeout = token, timeout
        self.opener = urllib.request.build_opener(NoRedirects())

    def request(self, path, data=None, content_type=None):
        headers = {"Authorization": "Bearer " + self.token}
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers)
        with self.opener.open(request, timeout=self.timeout) as response:
            return json.load(response)

    def config(self):
        return configuration(self.request("/ai/vision/evaluation-config"))

    def evaluate(self, plan, config):
        boundary = "gesti-vision-" + uuid.uuid4().hex
        parts = []
        for key, value in {"expectedModel": config["model"], "expectedPromptVersion": config["promptVersion"]}.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        for key, photo in plan["photos"].items():
            extension = "png" if photo["mime"] == "image/png" else "jpg"
            parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"; filename="{key}.{extension}"\r\n'
                          f'Content-Type: {photo["mime"]}\r\n\r\n').encode() + photo["data"] + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        return self.request("/ai/vision/evaluate", b"".join(parts), "multipart/form-data; boundary=" + boundary)


def evaluate_plan(client, plan, config, common, label):
    row = {**common, "sceneId": plan["sceneId"], "split": plan["split"], "inputMode": plan["inputMode"],
           **config, "configurationConfirmed": False, "evaluatedAt": utc_now(),
           "reviewStatus": "REVIEWED" if label else "UNREVIEWED",
           "referenceLabel": None, "reviewer": None, "reviewDate": None, "independentReview": False,
           "prediction": "ANALYSIS_UNAVAILABLE", "failureKind": None, "analysisId": None,
           "afterPhoto": plan["photos"]["afterPhoto"]["reference"], "beforePhoto": None,
           "afterSha256": plan["photos"]["afterPhoto"]["sha256"], "beforeSha256": None}
    if label:
        row.update(label)
    if "beforePhoto" in plan["photos"]:
        row.update(beforePhoto=plan["photos"]["beforePhoto"]["reference"], beforeSha256=plan["photos"]["beforePhoto"]["sha256"])
    started, fatal = time.monotonic(), False
    try:
        result = client.evaluate(plan, config)
        actual = configuration(result)
        if actual != config or result.get("inputMode") != plan["inputMode"]:
            row.update(failureKind="CONFIGURATION_OR_MODE_MISMATCH", returnedConfiguration=actual)
            fatal = True
        elif result.get("status") not in LABELS | {"ANALYSIS_UNAVAILABLE"}:
            row["failureKind"] = "INVALID_RESPONSE"
        else:
            row.update(prediction=result["status"], configurationConfirmed=True, analysisId=result.get("id"),
                       afterEvidenceRef=result.get("afterPhotoUrl"), beforeEvidenceRef=result.get("beforePhotoUrl"),
                       analysisCreatedAt=result.get("createdAt"),
                       failureKind="ANALYSIS_UNAVAILABLE" if result["status"] == "ANALYSIS_UNAVAILABLE" else None)
    except urllib.error.HTTPError as error:
        row.update(httpStatus=error.code, failureKind="CONFIGURATION_CHANGED" if error.code == 409 else "HTTP_ERROR")
        fatal = error.code in {401, 403, 409}
        error.close()
    except (urllib.error.URLError, TimeoutError, OSError):
        row["failureKind"] = "TRANSPORT_ERROR"
    except (ValueError, TypeError, KeyError):
        row["failureKind"] = "INVALID_RESPONSE"
    row["latencyMs"] = round((time.monotonic() - started) * 1000)
    return row, fatal


def run(args, client=None):
    manifest, plans, labels, provenance = load_dataset(args.manifest, args.labels)
    if args.validate_only:
        return {"scenes": len(manifest["scenes"]), "plannedAnalyses": len(plans), "reviewedInputs": len(labels), "validatedOnly": True}
    if client is None:
        token = os.environ.get("GESTI_EVAL_TOKEN")
        if not token:
            raise ValueError("Set GESTI_EVAL_TOKEN to a supervisor token for the selected demonstration backend")
        client = ApiClient(args.base_url, token, args.timeout)
    config = configuration(client.config())
    if manifest.get("expectedConfiguration") and configuration(manifest["expectedConfiguration"]) != config:
        raise ValueError("Preflight configuration differs from manifest; no image analysis was requested")
    output = Path(args.output)
    meta_path = output.with_suffix(output.suffix + ".run.json")
    if output.exists() or meta_path.exists():
        raise ValueError("Output already exists; select a new run file")
    output.parent.mkdir(parents=True, exist_ok=True)
    common = {"runId": str(uuid.uuid4()), "datasetVersion": manifest["datasetVersion"], **provenance}
    metadata = {**common, "startedAt": utc_now(), "configuration": config,
                "apiBaseUrl": client.base_url,
                "plannedAnalyses": len(plans), "completedAnalyses": 0, "failureCount": 0,
                "reviewedInputs": len(labels), "status": "RUNNING"}
    meta_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    try:
        with output.open("x", encoding="utf-8") as destination:
            for plan in plans:
                row, fatal = evaluate_plan(client, plan, config, common, labels.get((plan["sceneId"], plan["inputMode"])))
                destination.write(json.dumps(row, ensure_ascii=False) + "\n")
                destination.flush()
                metadata["completedAnalyses"] += 1
                metadata["failureCount"] += row["failureKind"] is not None
                if fatal:
                    metadata["status"] = "STOPPED_CONFIGURATION_OR_ACCESS"
                    break
            else:
                metadata["status"] = "COMPLETED"
    finally:
        metadata["finishedAt"] = utc_now()
        if metadata["status"] == "RUNNING":
            metadata["status"] = "INTERRUPTED"
        meta_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"output": str(output), "runId": common["runId"], "status": metadata["status"],
            "analyses": metadata["completedAnalyses"], "failures": metadata["failureCount"],
            "reviewedInputs": len(labels)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:18080/api")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--labels", help="Independent human labels JSON; omitted inputs remain UNREVIEWED")
    parser.add_argument("--output", default="evaluation/vision/results.jsonl")
    parser.add_argument("--timeout", type=float, default=90)
    parser.add_argument("--validate-only", action="store_true", help="Validate files, labels and splits without network or model calls")
    args = parser.parse_args()
    try:
        summary = run(args)
    except (ValueError, OSError, urllib.error.URLError) as error:
        # Never print HTTP response bodies, credentials, request headers or provider error details.
        if isinstance(error, urllib.error.HTTPError):
            error.close()
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        raise SystemExit(message) from None
    print(json.dumps(summary))
    return 0 if summary.get("status", "COMPLETED") == "COMPLETED" else 2


if __name__ == "__main__":
    raise SystemExit(main())
