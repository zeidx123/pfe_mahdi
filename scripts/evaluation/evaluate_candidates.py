#!/usr/bin/env python3
"""Evaluate consented/staged CVs using explicit reference labels on a demo API.

Imports candidates and optional human reviews. Never hires or sends email.
No pooled accuracy across splits; output contains match flags, not CV field values.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import math
import os
import tempfile
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from pathlib import Path

RUNNER_VERSION = "cv-evaluation-v2"
STATUSES = ("MISSING", "INVALID", "UNCERTAIN", "VERIFIED")
SPLITS = {"development", "validation", "final", "evaluation"}
KINDS = {"SYNTHETIC_DEVELOPMENT", "REVIEWED"}


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # The supervisor token is authorized only for the selected demo API.
        return None


def normalized(value, field=None):
    if value is None:
        return None
    text = " ".join(unicodedata.normalize("NFKC", str(value)).casefold().split())
    if not text:
        return None
    # Numeric identity strings keep leading zeros. Only experience is a quantity.
    if field == "anneesExperience":
        try:
            number = float(text)
            if math.isfinite(number):
                return str(number)
        except ValueError:
            pass
    return text


def _rate(numerator, denominator):
    return numerator / denominator if denominator else None


def _status_metrics(fields):
    labelled = [field for field in fields if field.get("expectedStatus") is not None]
    predictions = (*STATUSES, "UNREPORTED", "UNRECOGNIZED")
    confusion = {expected: {actual: 0 for actual in predictions} for expected in STATUSES}
    for field in labelled:
        confusion[field["expectedStatus"]][field["actualStatus"]] += 1
    per_class = {}
    for status in STATUSES:
        support = sum(confusion[status].values())
        predicted = sum(row[status] for row in confusion.values())
        tp = confusion[status][status]
        per_class[status] = {
            "support": support, "predicted": predicted,
            "precision": _rate(tp, predicted), "recall": _rate(tp, support),
            "f1": _rate(2 * tp, support + predicted),
        }
    represented = [item["f1"] for item in per_class.values() if item["support"]]
    return {"labelledFields": len(labelled),
            "accuracy": _rate(sum(confusion[s][s] for s in STATUSES), len(labelled)),
            "macroF1": _rate(sum(represented), len(represented)),
            "macroF1Scope": "classes with at least one independent expected label",
            "unreportedPredictions": sum(row["UNREPORTED"] for row in confusion.values()),
            "unrecognizedPredictions": sum(row["UNRECOGNIZED"] for row in confusion.values()),
            "confusionMatrix": confusion, "perClass": per_class}


def metrics(rows):
    fields = [field for row in rows for field in row.get("fields", [])]
    values = [field for field in fields if field.get("valueLabelled")]
    expected = sum(field["expectedPresent"] for field in values)
    actual = sum(field["actualPresent"] for field in values)
    both = sum(field["expectedPresent"] and field["actualPresent"] for field in values)
    scored = [row for row in rows if row.get("scoreMatches") is not None]
    required = [row for row in rows if row.get("requiredMatches") is not None]
    latencies = [row["latencyMs"] for row in rows if "latencyMs" in row]
    return {
        "cases": len(rows), "failures": sum("error" in row for row in rows),
        "uploadedCases": sum("candidateId" in row for row in rows),
        "expectedValueFields": sum(row.get("valueLabelCount", 0) for row in rows),
        "expectedStatusFields": sum(row.get("statusLabelCount", 0) for row in rows),
        "labelledValueFields": len(values),
        "exactFieldAccuracy": _rate(sum(field["matches"] for field in values), len(values)),
        "fieldPresencePrecision": _rate(both, actual),
        "fieldPresenceRecall": _rate(both, expected),
        "fieldStatuses": _status_metrics(fields),
        "rubricExpectedCases": sum(row.get("scoreLabelled", False) for row in rows),
        "rubricCases": len(scored), "rubricAgreement": _rate(sum(row["scoreMatches"] for row in scored), len(scored)),
        "requiredCriteriaExpectedCases": sum(row.get("requiredLabelled", False) for row in rows),
        "requiredCriteriaCases": len(required),
        "requiredCriteriaAgreement": _rate(sum(row["requiredMatches"] for row in required), len(required)),
        "meanUploadLatencyMs": _rate(sum(latencies), len(latencies)),
        "latencyCases": len(latencies),
    }


def _mapping(case, key):
    value = case.get(key, {})
    if not isinstance(value, dict):
        raise ValueError(f"{case['id']}: {key} must be an object; omit it when unlabelled")
    return value


def label_identity(manifest, case):
    reviewed = manifest["datasetKind"] == "REVIEWED"
    return (case.get("reviewedBy" if reviewed else "authoredBy"),
            case.get("reviewedAt" if reviewed else "authoredAt"), reviewed)


def validate_manifest(manifest, directory):
    """Validate all labels/files before any API mutation; never infer missing labels."""
    if not manifest.get("datasetVersion") or manifest.get("datasetKind") not in KINDS:
        raise ValueError("datasetVersion and datasetKind (SYNTHETIC_DEVELOPMENT or REVIEWED) are required")
    if type(manifest.get("profileVersion")) is not int or manifest["profileVersion"] < 1:
        raise ValueError("profileVersion must be a positive integer")
    provenance = manifest.get("labelProvenance", {})
    if not provenance.get("description") or provenance.get("independentOfPredictions") is not True:
        raise ValueError("labelProvenance must describe labels established independently of predictions")
    reviewed = manifest["datasetKind"] == "REVIEWED"
    if provenance.get("kind") != ("INDEPENDENT_REVIEW" if reviewed else "AUTHORED_SYNTHETIC") or provenance.get("independentHumanReview") is not reviewed:
        raise ValueError("Provenance must distinguish authored synthetic expectations from independent human review")
    if not isinstance(manifest.get("cases"), list) or not manifest["cases"]:
        raise ValueError("cases must be a nonempty list")
    seen, hashes, prepared = set(), {}, []
    for case in manifest["cases"]:
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id.strip() or case_id in seen:
            raise ValueError("Each case requires a unique nonempty id")
        seen.add(case_id)
        split = case.get("split")
        if split not in SPLITS:
            raise ValueError(f"{case_id}: explicit development/validation/final/evaluation split required")
        if manifest["datasetKind"] == "SYNTHETIC_DEVELOPMENT" and split != "development":
            raise ValueError(f"{case_id}: synthetic development fixtures cannot claim a final/reviewed split")
        author, date, _ = label_identity(manifest, case)
        if not author or not date:
            raise ValueError(f"{case_id}: explicit label author/reviewer and ISO date are required")
        datetime.fromisoformat(date.replace("Z", "+00:00"))
        values, statuses = _mapping(case, "expectedFields"), _mapping(case, "expectedFieldStatuses")
        for field, value in values.items():
            if isinstance(value, (list, dict)) or isinstance(value, float) and not math.isfinite(value):
                raise ValueError(f"{case_id}: expected field values must be finite scalars or null")
        for field, status in statuses.items():
            if status is not None and status not in STATUSES:
                raise ValueError(f"{case_id}: invalid expected status for {field}")
            if field in values and status == "MISSING" and normalized(values[field], field) is not None:
                raise ValueError(f"{case_id}: MISSING cannot have an expected present value")
            if field in values and status == "VERIFIED" and normalized(values[field], field) is None:
                raise ValueError(f"{case_id}: VERIFIED cannot have an expected absent value")
        for value in _mapping(case, "reviewedFields").values():
            if isinstance(value, (list, dict)) or isinstance(value, float) and not math.isfinite(value):
                raise ValueError(f"{case_id}: reviewed values must be finite scalars or null")
        score = case.get("expectedScore")
        if score is not None and (type(score) not in (int, float) or not math.isfinite(score) or not 0 <= score <= 100):
            raise ValueError(f"{case_id}: expectedScore must be 0–100 or omitted/null")
        if case.get("expectedRequiredCriteriaStatus") not in (None, "MET", "UNMET", "NEEDS_REVIEW"):
            raise ValueError(f"{case_id}: invalid required-criteria label")
        if not values and not any(statuses.values()) and score is None and case.get("expectedRequiredCriteriaStatus") is None:
            raise ValueError(f"{case_id}: at least one independent label is required")
        file = (directory / case["file"]).resolve()
        if file.suffix.lower() not in (".pdf", ".docx"):
            raise ValueError(f"{case_id}: only PDF/DOCX fixtures are supported")
        data = file.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if case.get("documentSha256") not in (None, digest):
            raise ValueError(f"{case_id}: document hash differs from reviewed source")
        if digest in hashes and hashes[digest] != split:
            raise ValueError(f"{case_id}: identical source bytes occur in multiple splits")
        hashes[digest] = split
        prepared.append((case, file, data, digest))
    return prepared


def field_results(case, candidate):
    values, statuses = case.get("expectedFields", {}), case.get("expectedFieldStatuses", {})
    evidence = candidate.get("fieldEvidence") or {}
    result = []
    for field in sorted(set(values) | set(statuses)):
        actual = evidence.get(field) or {}
        status = actual.get("status")
        item = {"field": field, "valueLabelled": field in values,
                "expectedStatus": statuses.get(field),
                "actualStatus": status if status in STATUSES else "UNREPORTED" if status is None else "UNRECOGNIZED"}
        if field in values:
            expected_value = normalized(values[field], field)
            actual_value = normalized(actual.get("value"), field)
            item.update(matches=actual_value == expected_value,
                        expectedPresent=expected_value is not None, actualPresent=actual_value is not None)
        result.append(item)
    return result


def provider_state(candidate):
    # The current API has extractionStatus, not a separate provider-call status.
    if candidate.get("providerStatus"):
        return candidate["providerStatus"], "API_PROVIDER_STATUS"
    inferred = {"EXTRACTED": "RESPONSE_RECEIVED", "PROVIDER_UNAVAILABLE": "UNAVAILABLE",
                "MANUAL_REVIEW_REQUIRED": "NOT_CALLED"}.get(candidate.get("extractionStatus"), "UNREPORTED")
    return inferred, "DERIVED_FROM_API_EXTRACTION_STATUS"


def evaluate(manifest_path, request, on_progress=None):
    manifest_path = Path(manifest_path).resolve()
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes)
    prepared = validate_manifest(manifest, manifest_path.parent)
    profile = request("/ai/cv/requirements")
    if profile["version"] != manifest["profileVersion"]:
        raise ValueError("Manifest profileVersion differs from the demo API; review the rubric first")
    results = []
    if on_progress:
        on_progress(build_report(manifest, manifest_bytes, profile, results, "RUNNING"))
    for case, file, file_bytes, digest in prepared:
        author, date, reviewed_labels = label_identity(manifest, case)
        row = {"id": case["id"], "split": case["split"], "labelAuthor": author,
               "labelDate": date, "independentHumanReview": reviewed_labels, "documentSha256": digest,
               "valueLabelCount": len(case.get("expectedFields", {})),
               "statusLabelCount": sum(status is not None for status in case.get("expectedFieldStatuses", {}).values()),
               "scoreLabelled": case.get("expectedScore") is not None,
               "requiredLabelled": case.get("expectedRequiredCriteriaStatus") is not None,
               "extractionStatus": "UPLOAD_FAILED", "providerStatus": "UNREPORTED"}
        started = time.monotonic()
        try:
            boundary = "gesti-eval-" + uuid.uuid4().hex
            mime = "application/pdf" if file.suffix.lower() == ".pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="cv"; filename="staged{file.suffix}"\r\n'
                      f"Content-Type: {mime}\r\n\r\n").encode()
            body = prefix + file_bytes + f"\r\n--{boundary}--\r\n".encode()
            candidate = request("/ai/cv/upload", body, "POST", "multipart/form-data; boundary=" + boundary)
            provider, source = provider_state(candidate)
            row.update(candidateId=candidate["id"], extractionStatus=candidate.get("extractionStatus") or "UNREPORTED",
                       providerStatus=provider, providerStatusSource=source,
                       model=candidate.get("extractionModel"), promptVersion=candidate.get("extractionPromptVersion"),
                       latencyMs=round((time.monotonic() - started) * 1000),
                       fields=field_results(case, candidate))
            reviewed = case.get("reviewedFields", {})
            row["rubricStage"] = "POST_REVIEW" if reviewed else "INITIAL_EXTRACTION"
            if reviewed:
                corrected = {field: {"value": None if value is None else str(value).lower() if isinstance(value, bool) else str(value),
                                     "status": "MISSING" if value is None else "VERIFIED",
                                     "reviewNote": ("Benchmark review: " if reviewed_labels else "Authored synthetic fixture: ") + author}
                             for field, value in reviewed.items()}
                candidate = request("/ai/candidates/" + candidate["id"] + "/review", {"fields": corrected}, "PATCH")
            if case.get("expectedScore") is not None:
                row.update(score=candidate.get("scoreCompatibilite"),
                           scoreMatches=candidate.get("scoreCompatibilite") == case["expectedScore"])
            if case.get("expectedRequiredCriteriaStatus") is not None:
                row.update(requiredCriteriaStatus=candidate.get("requiredCriteriaStatus"),
                           requiredMatches=candidate.get("requiredCriteriaStatus") == case["expectedRequiredCriteriaStatus"])
        except (urllib.error.HTTPError, OSError, KeyError, ValueError, TypeError) as error:
            row["error"] = f"HTTP {error.code}" if isinstance(error, urllib.error.HTTPError) else type(error).__name__
            if isinstance(error, urllib.error.HTTPError):
                error.close()
        results.append(row)
        if on_progress:
            on_progress(build_report(manifest, manifest_bytes, profile, results, "RUNNING"))
    return build_report(manifest, manifest_bytes, profile, results, "COMPLETED")


def build_report(manifest, manifest_bytes, profile, results, status):
    by_split = {}
    for split in sorted({row["split"] for row in results}):
        rows = [row for row in results if row["split"] == split]
        by_split[split] = {
            "metrics": metrics(rows),
            "byExtractionStatus": {status: metrics([row for row in rows if row["extractionStatus"] == status])
                                   for status in sorted({row["extractionStatus"] for row in rows})},
            "byProviderStatus": {status: metrics([row for row in rows if row["providerStatus"] == status])
                                 for status in sorted({row["providerStatus"] for row in rows})},
            "byModelAndPrompt": [{"model": model, "promptVersion": prompt,
                                  "metrics": metrics([row for row in rows if (row.get("model"), row.get("promptVersion")) == (model, prompt)])}
                                 for model, prompt in sorted({(row.get("model"), row.get("promptVersion")) for row in rows}, key=str)],
        }
    return {"runnerVersion": RUNNER_VERSION, "generatedAt": datetime.now(timezone.utc).isoformat(),
            "runStatus": status, "plannedCases": len(manifest["cases"]),
            "datasetVersion": manifest["datasetVersion"], "datasetKind": manifest["datasetKind"],
            "evidenceClass": "INDEPENDENTLY_REVIEWED_BENCHMARK" if manifest["datasetKind"] == "REVIEWED" else "SYNTHETIC_CONTRACT_CHECKS",
            "labelProvenance": manifest["labelProvenance"], "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
            "profile": profile, "counts": {"cases": len(results), "failures": sum("error" in row for row in results),
                                         "bySplit": dict(Counter(row["split"] for row in results))},
            "bySplit": by_split, "cases": list(results),
            "limitations": ["No pooled accuracy across splits; metrics use only explicit reference labels.",
                            "Upload failures have no predictions and are reported separately, not credited as correct missing fields.",
                            "Provider status is derived from actual API extractionStatus unless the API supplies it directly.",
                            "Synthetic development fixtures verify the evaluator, not empirical model accuracy."]}


def write_report(output, report):
    """Replace one JSON report only after the new complete JSON has been written."""
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent,
                                         prefix="." + output.name + ".", delete=False) as target:
            temporary = Path(target.name)
            json.dump(report, target, ensure_ascii=False, indent=2, allow_nan=False)
            target.write("\n")
        os.replace(temporary, output)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def prepare_output(path):
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    initial = {"runnerVersion": RUNNER_VERSION, "runStatus": "PREPARING", "cases": [],
               "counts": {"cases": 0, "failures": 0, "bySplit": {}}}
    # Test the actual target before any HTTP request, and preserve earlier runs.
    with output.open("x", encoding="utf-8") as target:
        json.dump(initial, target)
        target.write("\n")
    return output, initial


def run(args):
    token = os.environ.get("GESTI_EVAL_TOKEN")
    if not token:
        raise SystemExit("Set GESTI_EVAL_TOKEN to a supervisor token for the demonstration instance.")
    output, latest = prepare_output(args.output)
    base = args.base_url.rstrip("/")
    opener = urllib.request.build_opener(NoRedirects())

    def request(path, data=None, method="GET", content_type="application/json"):
        if isinstance(data, dict):
            data = json.dumps(data).encode()
        req = urllib.request.Request(base + path, data=data, method=method, headers={
            "Authorization": "Bearer " + token, "Content-Type": content_type,
        })
        with opener.open(req, timeout=120) as response:
            return json.load(response)

    def persist(report):
        nonlocal latest
        write_report(output, report)
        latest = report

    try:
        report = evaluate(args.manifest, request, on_progress=persist)
        persist(report)
    except BaseException as error:
        try:
            persist({**latest, "runStatus": "INTERRUPTED", "interruptionType": type(error).__name__})
        except OSError:
            pass  # Keep the last successfully written checkpoint if storage fails.
        raise
    print(json.dumps({"output": str(output), "runStatus": report["runStatus"], **report["counts"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="Dedicated demo backend, including /api")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--output", required=True)
    run(parser.parse_args())
