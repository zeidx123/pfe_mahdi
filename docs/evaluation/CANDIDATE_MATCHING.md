# Candidate matching — implementation and evaluation

The supervisor's web screen uses one backend rubric. The initial profile gives 40 points for verified C/CE, up to 40 for verified driving experience with a two-year proportional target, 10 for safety training, and 10 for waste-collection experience. Only the licence criterion is mandatory initially. No automatic hiring/rejection occurs; an eligible candidate still needs a supervisor decision. Creating a driver requires verified identity/contact/licence fields and a valid licence expiry. No email is sent by CV acceptance.

Extracted fields start as `UNCERTAIN`, `MISSING`, or `INVALID`. A supervisor can correct each value and mark it `VERIFIED`. Unknown information earns no confirmed points and is explicitly shown as provisional. A verified negative boolean is different from an absent value. PDF pages and DOCX paragraph/table locations accompany source excerpts. Unreadable/scanned documents use manual review. Provider failure retains conservative, labelled local extraction; eight-digit telephone numbers are never guessed to be CIN numbers.

Each requirements edit creates a new version. Every evaluation preserves the requirements snapshot, reviewed fields and criterion scores. Existing scores do not change when requirements change; the supervisor explicitly re-evaluates. Retired age/identity-based scores are retained as legacy metadata and hidden from current comparisons until review. If experience is explicitly changed into a mandatory threshold, sub-threshold experience receives zero experience points and a separate unmet requirement, not misleading partial mandatory compatibility.

## Checks implemented

Run `mvn -f BACK/pom.xml -Dtest=CandidateScoringTest,CandidateReviewTest,DriverRequirementsTest,CvExtractionTest test` from the workspace root.

The CSV at `BACK/src/test/resources/evaluation/candidate-rubric-seed.csv` contains eight synthetic rubric cases with explicit expected scores and required-criteria states. Additional tests cover identity/age independence, version preservation, provider outage, citations, safe human correction, repeat acceptance, real PDF page extraction and DOCX tables. These checks establish software behavior; they do not establish model extraction accuracy on real CVs. CV extraction uses the configured collection provider (Groq by default); Gemini is reserved for RAG.

## Evaluation runner and authored development fixtures

Run the evaluator tests without a backend, token or provider:

```bash
python3 -m unittest discover -s scripts/evaluation -p 'test_evaluate_candidates.py' -v
```

The regression bundle at [candidate-development.json](../../evaluation/candidates/fixtures/candidate-development.json) has simulated extraction/review responses and synthetic labels. Tests create temporary DOCX containers, inject an endpoint allowlist, and forbid network requests. They check field-status confusion metrics, missing labels, separate split results, actual provider strata, identity leading zeros, rubric denominators, upload failure coverage, provenance and source-hash validation. These are software checks, not a final reviewed dataset.

Two fictional documents are ready for a real-provider or fallback **development** run:

- [Category C, one year of experience](../../evaluation/candidates/fixtures/cv-development/fictional-driver-c.docx): authored rubric expectation 70/100 and required criterion MET.
- [Category B, half a year of experience](../../evaluation/candidates/fixtures/cv-development/fictional-driver-b.docx): authored rubric expectation 10/100 and required criterion UNMET; includes an intentionally invalid telephone.
- [Runnable development manifest](../../evaluation/candidates/fixtures/cv-development/manifest.json): both documents, reference field values/statuses, optional review submissions and source hashes.

These documents describe no real people. Their expectations were authored for development; **no independent human review has occurred**. The manifest declares `datasetKind: SYNTHETIC_DEVELOPMENT`, `labelProvenance.kind: AUTHORED_SYNTHETIC`, and `independentHumanReview: false`. Reports retain those declarations and use `evidenceClass: SYNTHETIC_CONTRACT_CHECKS`. Their model results must not be described as final CV accuracy.

The command uses a supervisor token already provided through `GESTI_EVAL_TOKEN`. It uploads candidate records and may save the supplied field corrections through the review API. It never accepts candidates or sends email. HTTP redirects are rejected so the supervisor token cannot be forwarded to another origin. Use only the intended disposable/demo instance:

```bash
python3 scripts/evaluation/evaluate_candidates.py \
  --base-url http://127.0.0.1:18080/api \
  --manifest evaluation/candidates/fixtures/cv-development/manifest.json \
  --output output/cv-development-results.json
```

The example manifest freezes requirements version 1 and assumes the initial 40/40/10/10 rubric. If the demonstration has another version, check the actual requirements and update the manifest version and expected scores deliberately. A mismatch stops before any upload. Do not change expected extraction labels after viewing predictions just to improve reported performance.

## Manifest semantics

All labels/files are validated before API mutation. A manifest requires `datasetVersion`, `datasetKind`, `profileVersion`, `labelProvenance` and nonempty `cases`. Each case needs a unique id, PDF/DOCX path, explicit split and label provenance/date. Source SHA-256 is calculated and recorded; a supplied `documentSha256` must match. The same file bytes cannot appear in different splits.

| Field | Interpretation |
| --- | --- |
| `expectedFields` | Reference values for the initial upload response. An omitted field is unlabelled; an explicit `null` means the field should be absent. False and zero are present values. |
| `expectedFieldStatuses` | Independently specified expected initial statuses: MISSING, INVALID, UNCERTAIN or VERIFIED. Omission/null means status is unlabelled, even if a value label exists. The evaluator never derives these labels from value presence or review submissions. |
| `reviewedFields` | Optional values to submit to the supervisor review endpoint after measuring initial extraction. Null submits MISSING; other scalar values submit VERIFIED. This does not create extraction reference labels or establish independent human review. Synthetic submissions are explicitly marked as authored fixture values. |
| `expectedScore` | Optional 0–100 reference for the resulting candidate, after review when provided, otherwise immediately after extraction. Null/omission means no rubric score label. |
| `expectedRequiredCriteriaStatus` | Optional MET, UNMET or NEEDS_REVIEW reference. Its denominator is independent of score labels. Null/omission is not counted as a failed match. |
| `split` | development, validation, final, or the legacy name evaluation. Reports keep each name separate; prefer final for the final benchmark. Synthetic development fixtures are restricted to development. |
| `authoredBy`, `authoredAt` | Required for SYNTHETIC_DEVELOPMENT; identify the fixture author and ISO date, without claiming review. |
| `reviewedBy`, `reviewedAt` | Required for REVIEWED; identify the independent reviewer and ISO review date. |

The extractor normally returns UNCERTAIN, INVALID or MISSING. VERIFIED is generally reached through supervisor review; it must not be inferred merely because extracted text looks plausible. All four states are supported in the evaluator's confusion matrix, so inappropriate automatic verification is visible. Missing API status is reported as UNREPORTED, and an unknown status token as UNRECOGNIZED; neither is silently changed to MISSING.

Field-value comparison normalizes case/Unicode/whitespace and driving-experience numbers. Identity strings preserve leading zeros. An absent value reference must be explicit; fields omitted from the manifest do not silently become expected missing fields.

## Reviewed PFE benchmark

Aim for approximately 30–40 staged or consented CV examples as time allows. Include C versus CE versus B, zero/partial/full driving experience, omitted/ambiguous experience, overlapping jobs, unrelated training, explicit absence versus missing evidence, scanned PDFs, invalid fields and duplicated telephone/CIN-like numbers. Keep development and final evaluation cases separate, including multiple versions of the same person's CV. The hash guard catches identical files across splits, not every possible semantic duplication.

A reviewed manifest must declare this provenance and supply real reviewer/date values for every case:

```json
{
  "datasetVersion": "cv-reviewed-v1",
  "datasetKind": "REVIEWED",
  "profileVersion": 1,
  "labelProvenance": {
    "kind": "INDEPENDENT_REVIEW",
    "description": "Explain who reviewed the source CVs and the labelling procedure.",
    "independentOfPredictions": true,
    "independentHumanReview": true
  },
  "cases": [{
    "id": "cv-001", "file": "fixtures/cv-001.pdf", "split": "final",
    "reviewedBy": "actual-reviewer-id", "reviewedAt": "2026-09-17",
    "expectedFields": {"typePermis": "C", "anneesExperience": 0.5, "cin": null},
    "expectedFieldStatuses": {"typePermis": "UNCERTAIN", "anneesExperience": "UNCERTAIN", "cin": "MISSING"},
    "reviewedFields": {"typePermis": "C", "anneesExperience": 0.5, "formationSecurite": false, "experienceCollecteConfirmee": false},
    "expectedScore": 50, "expectedRequiredCriteriaStatus": "MET"
  }]
}
```

This is a schema example, not a claim that such a reviewed case exists. Declaring REVIEWED without explicit independent-human provenance is rejected. The software cannot establish that a declaration is truthful; the PFE must retain the actual review record. A reviewer should label source CVs before seeing model output and freeze the requirements version used for the comparison.

## Report interpretation

Every accuracy table is inside `bySplit`; there is no pooled development/final accuracy. Each split also has `byExtractionStatus`, `byProviderStatus` and `byModelAndPrompt` metrics. These groups use the **actual initial API response**, preserved before human corrections; they never use expected status labels as the grouping variable.

The current backend supplies `extractionStatus` rather than a separate provider status. The report explicitly records this derivation: EXTRACTED → RESPONSE_RECEIVED, PROVIDER_UNAVAILABLE → UNAVAILABLE, MANUAL_REVIEW_REQUIRED → NOT_CALLED, anything unknown → UNREPORTED. If an API response supplies `providerStatus`, that actual value is used and its source recorded. RESPONSE_RECEIVED describes availability, not correctness. Upload failures have a separate UPLOAD_FAILED group; unavailable-provider local extraction is evaluated in its own group, not presented as successful model extraction.

Metrics include exact labelled-value accuracy, field-presence precision/recall, status confusion matrix, class support/precision/recall/F1, status accuracy and macro F1 over classes with reference support. Missing/unrecognized status predictions remain visible errors in labelled cases. Rubric score agreement and required-criteria agreement have separate labelled denominators. Expected label counts, returned prediction counts, upload failures and mean successful upload latency expose coverage; a failed request cannot earn credit as a correct missing field.

The report saves runner/dataset version, UTC run time, manifest/source hashes, label provenance, frozen API requirements, initial extraction model/prompt version and case match flags. It omits CV text and extracted/reviewed field values. Dataset files themselves can contain personal data and should remain confined to the intended evaluation environment.

No live model accuracy result is claimed by these regression fixtures. Independent CV labels, reviewed corrections, live provider runs and final results remain separate PFE evidence.
