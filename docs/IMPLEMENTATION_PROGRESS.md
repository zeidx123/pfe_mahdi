# Implementation progress — GestiCollecte PFE

Snapshot: **17 September 2026**. This records implementation and verification separately. The roadmap is implemented through core P0/P1 code and evaluation tooling; final P0/P1 demonstration and empirical evaluation gates are still open. Optional P2/P3 work is not implemented.

## Milestones

| Milestone | Implemented | Verification and remaining gate |
| --- | --- | --- |
| P0.1 — Foundations | Flat mobile login/session restore; inactive-user rejection; scoped tours/files/conversations/tracking; no startup resets or anonymous bulk reset; resource/input validation; upload fixes; explicit provider failures. | Backend regression suite and isolated HTTP workflow pass. Current check totals are recorded below. |
| P0.2 — Tour execution | Embedded visit progress; required AFTER/optional BEFORE; unsafe obstruction with reason; resume; manual photo retry; truck lifecycle; return/finish; supervisor evidence review. | Service, Flutter and staged HTTP checks pass. Physical camera/GPS/restart/network-retry validation remains open. |
| P0.3 — Connected demonstration | Depot, directed road matrix, route geometry, ordered navigation links, location stream and integrated vision. | Simulated-provider end-to-end workflow passes. Live ORS matrix/directions and a three-stop browser tour now pass. Live embeddings, structured text transport and an integrated CANNOT_DETERMINE image result succeeded; generation availability remains intermittent. Grounded RAG/CV generation and a complete physical Android tour remain required. |
| P1.1 — Routing and duration | Manual/NN/NN+2-opt comparison; exact small benchmark; saved matrix/coordinate snapshot; service time; timing export/evaluator. | Algorithms checked against independent permutation search; synthetic fixture runner exists. Reviewed road scenarios and real elapsed/service measurements remain open. |
| P1.2 — RAG | Approved-source retrieval and grounding, plus a supervisor-only corpus/mode comparison API, batch capture, independent review templates and split/mode metrics. | Three development questions ran in all three modes with real compatible embeddings. Generation failures stayed excerpts; unreviewed accuracy stays null. Independent questions, successful grounded generation and final comparison remain open. |
| P1.3 — Matching | Evidence-aware CV review; editable versioned scoring; human acceptance; evaluator with field-state metrics, split/provider strata and saved case progress. | Two fictional CVs ran against the live provider; both were unavailable, and authored corrections gave expected rubric results. Independent CV labels and successful live extraction remain open. |
| P1.4 — Vision | AFTER-only/paired operational inputs and supervisor-only batch evaluation, frozen model/prompt, image hashes, independent labels and availability-aware metrics. | Real abstract AFTER-only input returned CANNOT_DETERMINE; paired request was unavailable. No human labels or cleanliness accuracy claimed. Staged field scenes, independent labels and final comparison remain open. |
| P1.5 — Final integration | Unified supervisor/driver workflow, focused screens, protected sources, API verification tooling and setup docs. | Automated staged integration passes. Physical walkthrough and all P1 empirical outputs are required before declaring the milestone complete. |
| P2/P3 | Not implemented. | Defer AI tour summaries and optional polish until all core/P1 gates pass. |

## Verification snapshot

These are the completed checks for this implementation handoff. They do not establish a finished physical Android demonstration or real AI accuracy.

| Check | Latest completed result | Interpretation |
| --- | --- | --- |
| Java backend tests | **111 tests passing**, zero failures/errors/skips, in the final package run. | Includes the original regressions, supervisor-only evaluation endpoints, mode comparison and seven explicit-label CV fallback regressions. |
| Isolated HTTP workflow | **71 asserted HTTP requests passing**, with deliberately simulated road/model responses. | [Latest P1 evidence](evidence/p1-staged-workflow.json); includes a JVM restart/resume against the same disposable database, protected PDF export, CV review/scoring/version history and repeated acceptance. |
| Flutter | **17 VM tests and 20 Chromium tests passing; analyze clean**. | Session, API/workflow, pending-stop issue reporting, image format validation, a 6 MB IndexedDB draft and successful-upload/local-cleanup failure handling; no physical GPS/camera proof. |
| Flutter browser walkthrough | Completed through login, start, forced upload failure, refresh/resume, retry, AFTER-only, optional pair, no-photo issue, Google Maps depot handoff and finish. | Chrome at 430×932 and 1280×900; ten final backend state assertions pass. Real Gemini outage was handled; roads and GPS were simulated. |
| React | Production build and lint pass; existing lint/bundle warnings remain. | Warnings are not evidence of AI quality or field reliability. |
| Android APK | **Latest debug APK rebuilt successfully**, including the browser-era workflow fixes: `MOBILE/build/app/outputs/flutter-apk/app-debug.apk`. | Emulator API default is 10.0.2.2:8080; configure the server address for a physical phone. No Android device was connected; build success does not establish physical GPS/camera behavior. |
| Python evaluation checks | **53 tests passing** across RAG (8), CV (20), vision runner (12), vision metrics (10) and timing (3). | Checks access transport, labels/provenance, metrics, partial output and missing/unavailable handling, not empirical model accuracy. |
| Live provider calls | Gemini embeddings and a structured text probe succeeded. Integrated vision returned CANNOT_DETERMINE on an abstract image. Paired vision, RAG generation and CV generation also returned unavailable states. ORS matrix/directions now succeed with the configured key. | See [P1 development results](evaluation/P1_RUN_RESULTS.md) and [live ORS checks](evidence/live-ors-workflow.json). The first three-stop road case improves estimated driving time by 19.63% over its chosen manual order; it is not a reviewed municipal benchmark or a measured physical tour. |

The [browser/mobile record](evidence/implementation-browser-mobile.md) documents route comparisons/protected photos, separate human correction, manual order/create/optimize, document approval and candidate profile version editing. Tests and staged HTTP verification use mocks or disposable Mongo/uploads. No real CV corpus was transmitted to providers for the regression suite, and no external email delivery was used. The staged runner uses separate ports and rejects already occupied ports instead of reusing existing services.

Final regression fixes keep standalone short RAG questions separate from the previous topic while retaining explicit follow-ups. Startup restores interrupted document indexing to a retryable state from actual stored text, with a manual-reindex message and no automatic provider calls. Historical tours retain generic old photographs in `legacyPhoto` and explicitly mark missing progress; these photographs do not satisfy the new AFTER requirement or create historical timestamps/AI labels. React's final historical-evidence change passed focused lint and production build; the earlier browser run is recorded separately.

The live CV development run exposed local-parser mistakes during provider outage. Explicit labels are now bounded to their own lines; category names, years/months, yes/no training/collection and absent/ambiguous values are normalized conservatively. Proposed fields remain LOCAL and UNCERTAIN/INVALID/MISSING, never automatically VERIFIED. The configured extraction model is recorded even when the provider fails. Original live output is retained as evidence rather than rewritten after the fix. A [separate post-fix replay](../evaluation/candidates/fallback-contract-results.json) against the final package matched all 12 expected values and 12 field-state labels across the two authored fixtures, with an explicitly simulated outage and zero real model calls. These are synthetic contract checks, not empirical model accuracy.

## Reproducible experiments

| Area | Current artifact | Remaining empirical work |
| --- | --- | --- |
| Routing | [Runner and interpretation](../evaluation/routing/README.md), two synthetic fixtures and a separate live ORS three-stop matrix/geometry with replay results, `/tournees/{id}/route-export`. | Expand into reviewed road scenarios (approximately 15–20 target); report travel distance/time, runtime, manual-baseline improvement and exact gap on small cases. |
| RAG | [Three-mode capture and review evaluator](../evaluation/rag/README.md), lexical seed and a real-embedding development run. | Approximately 50–80 manually reviewed questions; successful generation, citation support, unsupported claims, follow-ups and latency. |
| CV matching | [Field/rubric evaluator](evaluation/CANDIDATE_MATCHING.md), explicit synthetic fixtures and a live-outage development result. | Approximately 30–40 independently reviewed CV examples; successful extraction, field-state accuracy and rubric consistency. |
| Vision | [Batch inference and metric evaluator](../evaluation/vision/README.md), unreviewed abstract transport results. | Approximately 30–60 staged examples/pairs; independently labelled same-scene comparisons, class metrics, uncertainty and availability. |
| Timing | [Duration evaluator](../evaluation/timing/README.md). | Completed physical tours, frozen estimates and actual timestamps; total/service errors with missing-data coverage. |

All counts are flexible targets. Keep synthetic, development and final reviewed datasets distinct. A fixture assertion, matching expected rubric score or successfully rendered screen is not a claim of real model accuracy. Human labels must be independent of the prediction being evaluated.

## Scope retained and limitations

- Routing is a directed depot round trip over a hosted road matrix, not traffic-aware truck routing or capacity-constrained VRP.
- Vision judges appearance; it cannot establish who cleaned the point or prove causality.
- Matching assists a supervisor; age/identity/contact completeness do not score suitability.
- RAG may abstain or show literal source excerpts instead of a generated answer. Source grounding takes priority over always answering.
- Tracking latest positions are in memory. Mobile keeps one photo retry draft, not a generalized offline queue; forced process termination may interrupt tracking.
- UI changes are confined to the demonstrated workflow. Historical unused pages/services and audit findings outside it may remain.
- Existing direct Agents onboarding still uses CIN credentials and SMTP code; this is not a new production identity/email platform. CV acceptance itself does not send mail.

The original audit remains available in [PROJECT_CONTEXT_AUDIT_BASELINE.md](PROJECT_CONTEXT_AUDIT_BASELINE.md), `00`–`15`, and `AUDIT_REPORT.md`. Do not rewrite that historical evidence to imply the original code was already fixed.
