#!/usr/bin/env python3
"""Capture a supervisor RAG comparison and aggregate independent reviews. No automatic retries."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import statistics
import urllib.error
import urllib.parse
import urllib.request
import uuid

MODES = ("LEXICAL", "SEMANTIC", "HYBRID")
READY_RETRIEVAL = ("AVAILABLE", "EMPTY_CORPUS")
READY_GENERATION = ("ANSWERED", "INSUFFICIENT_INFORMATION", "NO_CONTEXT")


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Keep the supervisor token on the explicitly selected API origin.


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def save(path, value):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def independent(label):
    return isinstance(label, dict) and label.get("independent") is True and bool(label.get("reviewer")) and bool(label.get("reviewDate"))


def validate_manifest(manifest, corpus=None):
    if not manifest.get("datasetVersion") or not isinstance(manifest.get("cases"), list) or not manifest["cases"]:
        raise ValueError("A datasetVersion and nonempty cases list are required")
    ids, groups = set(), {}
    sources = None if corpus is None else {c["source"]["chunkId"]: c["source"]["snippet"] for c in corpus["chunks"]}
    for case in manifest["cases"]:
        if not case.get("id") or case["id"] in ids or not case.get("split") or not isinstance(case.get("question"), str) or not 1 <= len(case["question"].strip()) <= 2000:
            raise ValueError("Every case needs a unique id, explicit split and a 1–2000 character question")
        ids.add(case["id"])
        group = case.get("groupId", case["id"])
        if group in groups and groups[group] != case["split"]:
            raise ValueError("Related questions/history must stay in one split")
        groups[group] = case["split"]
        history = case.get("history", [])
        if not isinstance(history, list) or len(history) > 6 or any(
            turn.get("sender") not in ("USER", "ASSISTANT") or not isinstance(turn.get("content"), str)
            or not 1 <= len(turn["content"].strip()) <= 12000 for turn in history
        ):
            raise ValueError("History requires at most six USER/ASSISTANT turns with nonempty content")
        labels = case.get("labels") or {}
        if labels.get("answerable") is not None and type(labels["answerable"]) is not bool:
            raise ValueError("answerable must be true, false or null")
        relevant = labels.get("relevantChunkIds")
        if relevant is not None:
            if not isinstance(relevant, list) or any(not isinstance(x, str) for x in relevant) or len(set(relevant)) != len(relevant):
                raise ValueError("relevantChunkIds must be a distinct list or null")
            if labels.get("answerable") is False and relevant:
                raise ValueError("An unanswerable case cannot identify answering passages")
            if independent(labels) and sources is not None and any(chunk not in sources for chunk in relevant):
                raise ValueError("Reviewed relevant chunk IDs are absent from this approved corpus; update labels explicitly")
        for passage in labels.get("expectedPassages", []):
            if not isinstance(passage.get("quote"), str) or not passage["quote"].strip() or not passage.get("chunkId"):
                raise ValueError("Expected passages require a chunk ID and literal quote")
            if independent(labels) and sources is not None and passage["quote"] not in sources.get(passage["chunkId"], ""):
                raise ValueError("A reviewed passage is not a literal substring of its approved source")


def api(base):
    token = os.environ.get("GESTI_EVAL_TOKEN")
    if not token:
        raise ValueError("Set GESTI_EVAL_TOKEN to a supervisor token for the intended demonstration backend")
    url = urllib.parse.urlsplit(base)
    if url.scheme not in ("http", "https") or not url.netloc or url.username or url.password or url.query or url.fragment:
        raise ValueError("Use a plain HTTP(S) API base URL without credentials, query or fragment")
    opener = urllib.request.build_opener(NoRedirects())

    def request(path, body=None):
        payload = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base.rstrip("/") + path, data=payload, headers={
            "Authorization": "Bearer " + token, "Content-Type": "application/json",
        }, method="GET" if body is None else "POST")
        # One embedding and up to three sequential bounded generations can occur in one comparison.
        with opener.open(req, timeout=300) as response:
            return json.load(response)
    return request


def export_corpus(args):
    snapshot = api(args.base_url)("/ai/rag/evaluation/corpus")
    save(args.output, snapshot)
    print(json.dumps({"output": args.output, "corpus": snapshot["corpus"]}))


def capture(args):
    manifest = load(args.manifest)
    validate_manifest(manifest)
    request = api(args.base_url)
    corpus = request("/ai/rag/evaluation/corpus")
    validate_manifest(manifest, corpus)
    report = {"schemaVersion": "rag-evaluation-v1", "runId": str(uuid.uuid4()),
              "capturedAt": datetime.now(timezone.utc).isoformat(), "datasetVersion": manifest["datasetVersion"],
              "manifestFingerprint": fingerprint(manifest), "corpus": corpus,
              "generationRequested": args.generate, "modes": args.modes, "cases": []}
    for case in manifest["cases"]:
        row = {"id": case["id"], "split": case["split"], "groupId": case.get("groupId", case["id"]),
               "question": case["question"], "history": case.get("history", []), "labels": case.get("labels")}
        try:
            result = request("/ai/rag/evaluation", {"question": case["question"], "history": row["history"],
                "modes": args.modes, "generateAnswer": args.generate,
                "expectedCorpusFingerprint": corpus["corpus"]["fingerprint"]})
            if result["corpus"] != corpus["corpus"] or result["settings"] != corpus["settings"]:
                raise ValueError("Corpus or configuration changed during this run; keep this partial run separate and restart")
            if [mode["requestedMode"] for mode in result["results"]] != args.modes:
                raise ValueError("Backend did not return the requested comparison modes")
            for mode in result["results"]:
                mode["responseFingerprint"] = fingerprint({"question": row["question"], "history": row["history"], "result": mode})
            row["response"] = result
        except urllib.error.HTTPError as error:
            row["error"] = "HTTP " + str(error.code)
            report["cases"].append(row)
            save(args.output, report)
            if error.code in (401, 403, 409):
                raise ValueError(f"Evaluation stopped with HTTP {error.code}; partial results saved, no automatic retry") from None
            continue
        except (OSError, ValueError, KeyError) as error:
            row["error"] = type(error).__name__
            report["cases"].append(row)
            save(args.output, report)
            raise ValueError("Evaluation interrupted; partial results saved, no automatic retry") from None
        report["cases"].append(row)
        save(args.output, report)
    print(json.dumps({"output": args.output, "runId": report["runId"], "cases": len(report["cases"]),
                      "requestFailures": sum("error" in row for row in report["cases"])}))


def review_template(report):
    reviews = []
    for case in report["cases"]:
        for result in case.get("response", {}).get("results", []):
            if result.get("status") != "ANSWERED":
                continue
            reviews.append({"caseId": case["id"], "mode": result["requestedMode"],
                            "responseFingerprint": result["responseFingerprint"],
                            "reviewer": None, "reviewDate": None, "independent": False,
                            "answerCorrect": None, "claimsComplete": False, "claims": [],
                            "citations": [{"citationIndex": index, "supportsAnswer": None}
                                          for index, _ in enumerate(result.get("citations", []))]})
    return {"runId": report["runId"], "reviewVersion": "UNREVIEWED_TEMPLATE", "reviews": reviews}


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def latency(values):
    values = sorted(value for value in values if isinstance(value, (int, float)) and math.isfinite(value) and value >= 0)
    return {"count": len(values), "meanMs": statistics.mean(values) if values else None,
            "medianMs": statistics.median(values) if values else None,
            "p95Ms": values[max(0, math.ceil(.95 * len(values)) - 1)] if values else None}


def validate_reviews(report, review_file):
    if review_file is None:
        return {}
    if review_file.get("runId") != report["runId"]:
        raise ValueError("Reviews belong to a different run")
    results = {(case["id"], result["requestedMode"]): result for case in report["cases"]
               for result in case.get("response", {}).get("results", [])}
    indexed = {}
    for review in review_file.get("reviews", []):
        key = (review.get("caseId"), review.get("mode"))
        if key in indexed or key not in results or results[key].get("status") != "ANSWERED":
            raise ValueError("Reviews require distinct existing ANSWERED case/mode pairs")
        result = results[key]
        if review.get("responseFingerprint") != result.get("responseFingerprint"):
            raise ValueError("Review fingerprint does not match the captured answer")
        if review.get("answerCorrect") is not None and type(review["answerCorrect"]) is not bool:
            raise ValueError("answerCorrect must be true, false or null")
        for claim in review.get("claims", []):
            if not isinstance(claim.get("text"), str) or not claim["text"].strip() or claim.get("supported") is not None and type(claim["supported"]) is not bool:
                raise ValueError("Each claim requires text and a boolean/null support label")
        indexes = set()
        for citation in review.get("citations", []):
            index = citation.get("citationIndex")
            if type(index) is not int or index < 0 or index >= len(result.get("citations", [])) or index in indexes:
                raise ValueError("Citation labels must identify distinct captured citation indexes")
            if citation.get("supportsAnswer") is not None and type(citation["supportsAnswer"]) is not bool:
                raise ValueError("Citation support must be true, false or null")
            indexes.add(index)
        indexed[key] = review
    return indexed


def summarize(report, review_file=None):
    validate_manifest({"datasetVersion": report["datasetVersion"], "cases": report["cases"]})
    reviews = validate_reviews(report, review_file)
    groups = defaultdict(list)
    for case in report["cases"]:
        for result in case.get("response", {}).get("results", []):
            groups[(case["split"], result["requestedMode"])].append((case, result))
    output = []
    for (split, mode), rows in sorted(groups.items()):
        recalls, ranks, passage_recalls = [], [], []
        unanswerable_retrieval = empty_unanswerable = retrieval_unreviewed = retrieval_unavailable = 0
        answerable = abstained_answerable = unanswerable = abstained_unanswerable = answered_unanswerable = 0
        missing_answerability = generation_unavailable = 0
        answers = reviewed_answers = fully_reviewed = correct_count = correctness_labels = 0
        claim_labels = unsupported = supported = citations_total = citation_labels = citation_support = 0
        claims_missing_review = citations_missing_review = 0
        for case, result in rows:
            labels = case.get("labels") or {}
            reviewed = independent(labels)
            ready = result["retrievalStatus"] in READY_RETRIEVAL
            relevant = labels.get("relevantChunkIds") if reviewed else None
            retrieved = [source["chunkId"] for source in result.get("retrievedSources", [])]
            if not ready:
                retrieval_unavailable += 1
            if relevant is None or labels.get("answerable") is True and not relevant:
                retrieval_unreviewed += 1
            elif ready and relevant:
                recalls.append(len(set(retrieved) & set(relevant)) / len(relevant))
                ranks.append(next((1 / (i + 1) for i, chunk in enumerate(retrieved) if chunk in relevant), 0))
                passages = labels.get("expectedPassages", [])
                if passages:
                    passage_recalls.append(sum(any(source["chunkId"] == passage["chunkId"] and passage["quote"] in source["snippet"]
                        for source in result.get("retrievedSources", [])) for passage in passages) / len(passages))
            elif ready and labels.get("answerable") is False:
                unanswerable_retrieval += 1
                empty_unanswerable += not retrieved
            answerable_label = labels.get("answerable") if reviewed else None
            if answerable_label is None:
                missing_answerability += 1
            generation_ready = ready and result.get("generationStatus") in READY_GENERATION
            if result.get("generationStatus") != "NOT_REQUESTED" and (result.get("generationStatus") not in READY_GENERATION or not ready):
                generation_unavailable += 1
            if generation_ready and answerable_label is True:
                answerable += 1
                abstained_answerable += result.get("status") == "INSUFFICIENT_INFORMATION"
            if generation_ready and answerable_label is False:
                unanswerable += 1
                abstained_unanswerable += result.get("status") == "INSUFFICIENT_INFORMATION"
                answered_unanswerable += result.get("status") == "ANSWERED"
            if result.get("status") != "ANSWERED":
                continue
            answers += 1
            citations_total += len(result.get("citations", []))
            review = reviews.get((case["id"], mode), {})
            if not independent(review):
                claims_missing_review += 1
                citations_missing_review += len(result.get("citations", []))
                continue
            reviewed_answers += 1
            claims = review.get("claims", [])
            complete = review.get("claimsComplete") is True and bool(claims) and all(type(claim.get("supported")) is bool for claim in claims)
            fully_reviewed += complete
            claims_missing_review += not complete
            if type(review.get("answerCorrect")) is bool:
                correctness_labels += 1
                correct_count += review["answerCorrect"]
            for claim in claims:
                if type(claim.get("supported")) is bool:
                    claim_labels += 1
                    supported += claim["supported"]
                    unsupported += not claim["supported"]
            labelled_citations = [citation for citation in review.get("citations", []) if type(citation.get("supportsAnswer")) is bool]
            citation_labels += len(labelled_citations)
            citation_support += sum(citation["supportsAnswer"] for citation in labelled_citations)
            citations_missing_review += len(result.get("citations", [])) - len(labelled_citations)
        output.append({"split": split, "mode": mode, "caseCount": len(rows),
            "retrieval": {"k": report["corpus"]["settings"]["topK"], "reviewedAnswerableAvailableCount": len(recalls),
                "recallAtK": statistics.mean(recalls) if recalls else None, "mrrAtK": statistics.mean(ranks) if ranks else None,
                "literalSupportingPassageRecall": statistics.mean(passage_recalls) if passage_recalls else None,
                "reviewedUnanswerableAvailableCount": unanswerable_retrieval, "emptyRetrievalOnUnanswerableCount": empty_unanswerable,
                "missingOrUnreviewedLabelsCount": retrieval_unreviewed, "unavailableOrIncompleteIndexCount": retrieval_unavailable},
            "answers": {"answerableEligibleCount": answerable, "answerableAbstentionRate": ratio(abstained_answerable, answerable),
                "unanswerableEligibleCount": unanswerable, "correctAbstentionRate": ratio(abstained_unanswerable, unanswerable),
                "answeredUnanswerableRate": ratio(answered_unanswerable, unanswerable), "missingOrUnreviewedAnswerabilityCount": missing_answerability,
                "unavailableOrInvalidGenerationCount": generation_unavailable, "answeredCount": answers,
                "independentlyReviewedAnswerCount": reviewed_answers, "completeClaimReviewCount": fully_reviewed,
                "missingOrIncompleteClaimReviewCount": claims_missing_review,
                "reviewedClaimCount": claim_labels, "groundedClaimRate": ratio(supported, claim_labels),
                "unsupportedClaimRate": ratio(unsupported, claim_labels), "citationCount": citations_total,
                "reviewedCitationCount": citation_labels, "citationSupportRate": ratio(citation_support, citation_labels),
                "missingCitationReviewCount": citations_missing_review, "answerCorrectnessReviewedCount": correctness_labels,
                "answerCorrectnessRate": ratio(correct_count, correctness_labels)},
            "responseStates": dict(Counter(result.get("status") or "NOT_REQUESTED" for _, result in rows)),
            "retrievalStates": dict(Counter(result["retrievalStatus"] for _, result in rows)),
            "generationStates": dict(Counter(result["generationStatus"] for _, result in rows)),
            "latency": {name: latency([result.get(field) for _, result in rows]) for name, field in
                (("retrieval", "retrievalLatencyMs"), ("generation", "generationLatencyMs"), ("perModeTotal", "totalLatencyMs"))}})
    return {"schemaVersion": "rag-metrics-v1", "runId": report["runId"], "datasetVersion": report["datasetVersion"],
            "corpusFingerprint": report["corpus"]["corpus"]["fingerprint"], "settings": report["corpus"]["settings"],
            "reviewVersion": None if review_file is None else review_file.get("reviewVersion"),
            "requestFailures": [{"caseId": case["id"], "split": case["split"], "error": case["error"]} for case in report["cases"] if "error" in case],
            "denominators": {"retrieval": "Independent relevant-passage labels and complete available requested mode only",
                "abstention": "Independent answerability labels, available retrieval and valid generation/no-context result; provider/invalid/index failures excluded",
                "claimsAndCitations": "Explicit independent support labels only; missing labels and incomplete claim inventories reported separately",
                "latency": "Observed milliseconds; per-mode total includes shared query embedding for semantic/hybrid, so totals must not be summed as API wall time"},
            "groups": output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    corpus = commands.add_parser("corpus", help="Export approved passages and index metadata for independent labelling")
    corpus.add_argument("--base-url", required=True)
    corpus.add_argument("--output", required=True)
    run = commands.add_parser("capture", help="Compare modes against one frozen approved corpus; generation is opt-in")
    run.add_argument("--base-url", required=True)
    run.add_argument("--manifest", required=True)
    run.add_argument("--output", required=True)
    run.add_argument("--generate", action="store_true")
    run.add_argument("--modes", choices=MODES, nargs="+", default=list(MODES))
    template = commands.add_parser("review-template", help="Create empty human labels; never auto-label model output")
    template.add_argument("--results", required=True)
    template.add_argument("--output", required=True)
    summary = commands.add_parser("summarize", help="Aggregate available independent labels by split and mode without provider calls")
    summary.add_argument("--results", required=True)
    summary.add_argument("--reviews")
    summary.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        if args.command == "corpus":
            export_corpus(args)
        elif args.command == "capture":
            if len(set(args.modes)) != len(args.modes):
                raise ValueError("Choose distinct modes")
            capture(args)
        elif args.command == "review-template":
            save(args.output, review_template(load(args.results)))
        else:
            save(args.output, summarize(load(args.results), load(args.reviews) if args.reviews else None))
    except (ValueError, KeyError, OSError, urllib.error.HTTPError) as error:
        # No raw HTTP body, headers, token, CV/document URL or credential-bearing traceback.
        if isinstance(error, urllib.error.HTTPError):
            parser.exit(1, f"Evaluation API returned HTTP {error.code}\n")
        if isinstance(error, ValueError):
            parser.exit(1, str(error) + "\n")
        parser.exit(1, type(error).__name__ + ": could not complete evaluation\n")


if __name__ == "__main__":
    main()
