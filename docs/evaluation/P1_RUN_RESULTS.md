# P1 development results

Consolidated **24 September 2026**. These are selected software and provider observations on small, explicitly labelled development inputs. They are **not a completed independently reviewed PFE accuracy benchmark**. Current build results and remaining gates are in [delivery checks](../DELIVERY_CHECKS.md).

## Routing

The algorithms compare manual order, NN and NN + directed 2-opt on the same frozen matrix. Held–Karp supplies an exact benchmark up to ten stops. The saved live ORS scenario starts and ends at **36.722857, 10.283535**:

| Method | Estimated driving seconds | Road metres |
| --- | ---: | ---: |
| Manual | 312.52 | 1,661.92 |
| NN | 251.17 | 1,266.36 |
| NN + 2-opt | 251.17 | 1,266.36 |
| Exact | 251.17 | 1,266.36 |

This is a **19.63% estimated driving-time improvement** over that chosen manual order. Service adds 900 seconds, for 1,151.17 total estimated seconds. The route contains 28 geometry vertices. ORS matrix/directions returned successfully, and an independent permutation check evaluated all six possible orders.

This particular matrix is symmetric. The separate synthetic directed fixtures check asymmetric behavior; the six-stop synthetic case gives 150 → 119 → 114 seconds for manual/NN/NN + 2-opt, against exact 102. Neither one road case nor a synthetic improvement establishes municipality-wide performance. ORS snaps coordinates to roads; matrix costs and directions totals are not claimed to be identical.

Retained inputs and results:

- [Routing method and runner](../../evaluation/routing/README.md)
- [Saved ORS matrix/geometry](../../evaluation/routing/live-development/ors-depot-three-stops.json) and [road replay CSV](../../evaluation/routing/live-development-results.csv)
- [Directed synthetic fixtures](../../evaluation/routing/fixtures/) and [synthetic results](../../evaluation/routing/results.csv)

## RAG

Approved-source retrieval, compatible real embeddings and authenticated source downloads worked on three fictional procedural documents. In the retained five-question browser run, four questions returned literal `SOURCE_EXCERPTS`; a salary question unsupported by the corpus returned `INSUFFICIENT_INFORMATION`. The follow-up retained its conversation. No question in that run returned `ANSWERED`; the observed generation outage remained explicit.

An earlier three-question, three-mode capture compared BM25, semantic and hybrid retrieval. Independent relevance/citation/claim labels remain absent, so reviewed accuracy metrics are not established. A successful provider probe alone would not demonstrate grounded procedural answers.

- [Methods](../../evaluation/rag/README.md), [fictional source documents](../../evaluation/rag/development-documents/) and [browser observations](../../evaluation/rag/runs/ui-live-development-2026-09-23-summary.md)
- [Three-mode capture](../../evaluation/rag/runs/live-development-results.json), [metrics](../../evaluation/rag/runs/live-development-metrics.json) and [review template](../../evaluation/rag/runs/live-development-review-template.json)

Historical results retain the models/prompts used when captured. Gemini remains the current RAG/embedding provider; CV/photos now use the separately selected collection provider.

## Candidate matching

Two fictional DOCX cases have authored expectations: category C with one year of experience scores 70/100 after review, while category B with half a year scores 10/100 and leaves the licence requirement unmet. Both original live extraction calls were unavailable. Applying authored corrections demonstrated deterministic rubric behavior, not successful model extraction.

A separate explicit simulated-outage replay matched 12 expected field values and 12 field-state labels and reproduced 70/10 scores. These are synthetic contract checks, with no independent human accuracy review or real applicant hiring. The initial profile treats two years as a scoring target, while C/CE licence is required.

- [Rubric, provenance and evaluation method](CANDIDATE_MATCHING.md)
- [Fictional CV fixtures and manifest](../../evaluation/candidates/fixtures/cv-development/)
- [Original development result](../../evaluation/candidates/live-development-results.json)
- [Simulated-outage replay](../../evaluation/candidates/fallback-contract-results.json) and [manifest](../../evaluation/candidates/fallback-contract-manifest.json)

Private CVs and their diagnostic import reports are not part of the tracked benchmark. Use ignored `local-data/cvs/` for local documents. Current Groq extraction still needs a separately reviewed successful live CV run before claiming model performance.

## Vision and operational review

The current Groq integration returned a real `NOT_CLEANED` assessment for one public street-litter AFTER image. The response appeared in Chrome, survived reload/resume and remained separate from supervisor review. Two uploads produced one manual analysis; re-requesting unchanged evidence returned the saved result without another model request. The tour finished with its truck available.

See [the compact workflow record](../evidence/groq-live-simulation-2026-09-24.md) and [JSON](../evidence/groq-live-simulation-2026-09-24.json). Its 6,499 ms timing includes browser/application/network overhead. The public photographs depict separate scenes and cannot form a valid before/after pair or prove a real collection.

Earlier abstract transport fixtures produced one `CANNOT_DETERMINE` and one `ANALYSIS_UNAVAILABLE`. Their [raw outcomes](../../evaluation/vision/runs/abstract-transport-001.jsonl) and [metrics](../../evaluation/vision/runs/abstract-transport-001.metrics.json) stay explicitly unreviewed. The [vision method](../../evaluation/vision/README.md) supports scene-level splitting, frozen model/prompt settings and independent labels. No confusion matrix or macro F1 on reviewed cleanliness scenes is claimed yet.

## Execution timing

The Groq browser simulation recorded 856.88 seconds estimated at start, 423 seconds elapsed and 344 seconds of observed service. These values are not physical travel or cleaning measurements. An earlier [browser timing fixture](../../evaluation/timing/development/browser-tour.json) intentionally has incomplete service measurements; its [result](../../evaluation/timing/development/results.json) withholds an unsupported full-service comparison.

The [timing evaluator](../../evaluation/timing/README.md) reports absolute/relative error and measurement completeness. Actual field comparisons remain pending; waiting and breaks can be included in elapsed intervals.

## Final experiment work

The methods and runners are delivered so experiments can be repeated. Before declaring P1 complete, collect independent labels, verify successful grounded RAG and CV responses, compare AFTER-only and same-scene paired evidence, expand reviewed road scenarios, and record physical duration observations. Aim for the roadmap's dataset sizes when feasible, without treating counts as a substitute for quality. Keep development and final sets distinct and report provider failures separately from correctness.
