"""Separate availability from independently reviewed classification; no provider calls."""
import argparse
import json
from collections import Counter, defaultdict

LABELS = ("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE")


def summarize(rows, include_paired=True):
    splits = {}
    seen = set()
    groups = defaultdict(list)
    for original in rows:
        row = dict(original)
        row["prediction"] = row.get("prediction") or row.get("status")
        row["reviewedLabel"] = row.get("referenceLabel") or row.get("reviewedLabel")
        required = ("sceneId", "split", "inputMode", "prediction", "model", "promptVersion")
        if any(not row.get(key) for key in required):
            raise ValueError("Each row requires scene, split, mode, prediction and model/prompt metadata")
        if row["inputMode"] not in ("AFTER_ONLY", "BEFORE_AFTER") or row["prediction"] not in (*LABELS, "ANALYSIS_UNAVAILABLE"):
            raise ValueError("Invalid mode or label")
        if row.get("reviewStatus") == "UNREVIEWED":
            if any(row.get(key) for key in ("reviewedLabel", "reviewer", "reviewDate", "independentReview")):
                raise ValueError("UNREVIEWED results cannot contain human label metadata")
            row["isReviewed"] = False
        else:
            if row.get("reviewStatus") not in (None, "REVIEWED"):
                raise ValueError("Invalid reviewStatus")
            if row["reviewedLabel"] not in LABELS or not row.get("reviewer") or not row.get("reviewDate"):
                raise ValueError("Reviewed results require a valid human label and reviewer/date")
            if row.get("independentReview") is not True:
                raise ValueError("All reviewed results must declare independentReview: true")
            if row.get("dataKind") == "ABSTRACT_SYNTHETIC_TRANSPORT_ONLY":
                raise ValueError("Abstract transport fixtures cannot be used for cleanliness accuracy")
            row["isReviewed"] = True
        scene = row["sceneId"]
        if scene in splits and splits[scene] != row["split"]:
            raise ValueError("Photos of the same scene must remain in the same split")
        splits[scene] = row["split"]
        key = (scene, row["inputMode"], row["model"], row["promptVersion"])
        if key in seen:
            raise ValueError("Duplicate scene/mode/model/prompt result")
        seen.add(key)
        groups[(row["split"], row["inputMode"], row["model"], row["promptVersion"])].append(row)
    reports = []
    for (split, mode, model, prompt), group in sorted(groups.items()):
        reviewed = [row for row in group if row["isReviewed"]]
        matrix = {truth: {prediction: 0 for prediction in (*LABELS, "ANALYSIS_UNAVAILABLE")} for truth in LABELS}
        for row in reviewed:
            matrix[row["reviewedLabel"]][row["prediction"]] += 1
        classification_count = sum(row["prediction"] != "ANALYSIS_UNAVAILABLE" for row in reviewed)
        classes = {}
        for label in LABELS:
            tp = matrix[label][label]
            predicted = sum(matrix[truth][label] for truth in LABELS)
            actual = sum(matrix[label][prediction] for prediction in LABELS)
            precision = tp / predicted if predicted else (0.0 if classification_count else None)
            recall = tp / actual if actual else None
            f1 = 2 * precision * recall / (precision + recall) if actual and precision + recall else (0.0 if actual else None)
            classes[label] = {"support": actual, "precision": precision, "recall": recall, "f1": f1}
        represented = [values["f1"] for values in classes.values() if values["support"]]
        uncleaned = sum(matrix["NOT_CLEANED"][prediction] for prediction in LABELS)
        available_count = sum(row["prediction"] != "ANALYSIS_UNAVAILABLE" for row in group)
        counterpart = "BEFORE_AFTER" if mode == "AFTER_ONLY" else "AFTER_ONLY"
        other_group = groups.get((split, counterpart, model, prompt), [])
        paired_ids = {row["sceneId"] for row in group} & {row["sceneId"] for row in other_group}
        reviewed_paired_ids = {row["sceneId"] for row in reviewed} & {row["sceneId"] for row in other_group if row["isReviewed"]}
        reports.append({"split": split, "inputMode": mode, "model": model, "promptVersion": prompt, "count": len(group),
                        "reviewedCount": len(reviewed), "unreviewedCount": len(group) - len(reviewed),
                        "reviewCoverage": len(reviewed) / len(group), "accuracyAvailable": classification_count > 0,
                        "classificationAvailableCount": classification_count,
                        "confusionMatrix": matrix, "classes": classes,
                        "macroF1RepresentedClasses": sum(represented) / len(represented) if represented else None,
                        "classificationDenominator": "Independently reviewed available analyses only; unreviewed and unavailable outcomes excluded",
                        "availableCount": available_count, "analysisCoverage": available_count / len(group),
                        "pairedSceneCount": len(paired_ids), "pairedSceneIds": sorted(paired_ids),
                        "reviewedPairedSceneCount": len(reviewed_paired_ids), "reviewedPairedSceneIds": sorted(reviewed_paired_ids),
                        "notCleanedPredictedCleanedRate": matrix["NOT_CLEANED"]["CLEANED"] / uncleaned if uncleaned else None,
                        "unavailableCount": sum(row["prediction"] == "ANALYSIS_UNAVAILABLE" for row in group),
                        "failureKindCounts": dict(Counter(row.get("failureKind") or "ANALYSIS_UNAVAILABLE" for row in group if row["prediction"] == "ANALYSIS_UNAVAILABLE")),
                        "uncertainCount": sum(row["prediction"] == "CANNOT_DETERMINE" for row in group),
                        "uncertainOnDeterminateCount": sum(row["prediction"] == "CANNOT_DETERMINE" and row["reviewedLabel"] != "CANNOT_DETERMINE" for row in reviewed)})
        if include_paired and reviewed_paired_ids:
            matched = summarize([row for row in reviewed if row["sceneId"] in reviewed_paired_ids], include_paired=False)[0]
            reports[-1]["matchedSceneMetrics"] = {key: matched[key] for key in
                ("count", "reviewedCount", "accuracyAvailable", "classificationAvailableCount", "analysisCoverage", "classes", "macroF1RepresentedClasses", "notCleanedPredictedCleanedRate", "unavailableCount")}
        elif include_paired:
            reports[-1]["matchedSceneMetrics"] = None
    return reports


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results", help="JSON Lines predictions with explicit review status")
    args = parser.parse_args()
    with open(args.results, encoding="utf-8") as source:
        rows = [json.loads(line) for line in source if line.strip()]
    if not rows:
        raise SystemExit("No results supplied; no metrics can be calculated.")
    print(json.dumps(summarize(rows), indent=2, ensure_ascii=False))
