# Prioritized improvement roadmap

## Current state

These are recommendations after the audit, **not implemented changes**. Preserve Spring/React/Flutter/Mongo. Complexity is relative: low = localized change, medium = coordinated flow/schema/test work, high = dataset/experiment plus integration. “Before PFE” indicates presentation importance, not a promise about calendar time.

## P0 — Broken fundamentals

| ID / problem | Evidence | Proposed improvement and completion condition | Expected value | Complexity / components | Before PFE |
| --- | --- | --- | --- | --- | --- |
| P0-1 Photo uploads block collection | V13; TourneeService relative transferTo | Central absolute storage root; validate upload/ownership; demonstrate point and final photo from device through persisted tour and web display | Complete field workflow | Medium; B tour/storage, M active page, W photo URLs | Yes |
| P0-2 Identity and authorization fail | V02/V04/V05/V07/V20/V25; SecurityConfig/User/startup/AuthService | Correct mobile flat login mapping; enforce actif; remove anonymous reset access; avoid startup password resets/reactivation; scope tour/chat/WS/file access. Two drivers cannot access/mutate each other's protected objects | Correct role workflow and demo data integrity | Medium; B auth/security/services, M auth | Yes |
| P0-3 Tour lifecycle can contradict itself | V08–V12; TourneeService/PointCollecteService | Validate resources, assignment/availability and ranges; enforce transitions/evidence policy, sync truck status; clear derived route fields; reject or correctly represent empty tours | Trustworthy planning/execution | Medium; B DTO/services, W forms, M tour | Yes |
| P0-4 Mobile execution cannot reliably resume | detail/active tracking/TrackingService source | Re-fetch tour; resume EN_COURS; rebuild photo/visit state; stop tracking on logout/completion, recover finalization errors; verify permissions and backend URL on intended device | Avoid demo reset and lost progress | Medium; M pages/services, B persisted state | Yes |
| P0-5 Provider failure masquerades as success | V14/V24; vision/RagService | Explicit unavailable/uncertain DTO/UI; never default unanalysed photo to normal; offline RAG only supported quotes or abstention; verify malformed/timeout paths | Honest AI behavior | Medium; B AI, M assistant/vision, W relevant UI | Yes |
| P0-6 Invalid complaint crashes web | V18; ReclamationController/Page | Validated request DTO, server-owned ID/identity, null-safe list and error boundary; `{}` rejected400 and normal reply path remains valid | Reliable incident handling | Low; B reclamation, W page | Yes |
| P0-7 Environment/test path unreliable | Original FRONT failure; mobile/Java stale tests; hardcoded URLs | Document clean install; configurable host/storage; externalize literal secrets; repair obsolete tests without faking coverage; run packaged backend + intended mobile target | Reproducible demo | Medium; config/manifests/tests/docs | Yes |

## P1 — High-value PFE improvements

| ID / problem | Evidence | Proposed improvement and completion condition | Expected value | Complexity / components | Before PFE |
| --- | --- | --- | --- | --- | --- |
| P1-1 RAG lacks trustworthy grounding/evaluation | V22–V25; embedding/retrieval/history code | Approved corpus/version metadata; separate processing invocation; embedding provenance/reindex policy; scoped history + returned ID; 50–100 labeled questions and lexical/model/hybrid comparison with unsupported-answer/abstention metrics | Strongest feasible AI contribution | Medium; B rag, M assistant, W documents, evaluation artifacts | Yes if RAG showcased |
| P1-2 Duplicate route engines/false guarantees | V11/V29; both optimization services | One objective/depot/return/service-time contract; NN and 2-opt baseline comparison; capacity feasibility if claimed; exact small-instance benchmark; UI says heuristic/estimate | Measurable algorithmic contribution | Medium; B routes/tours, W/M route display | Yes |
| P1-3 Fixed values presented as facts | Dashboard, fuel75, seeded alerts | Replace tonnes with actual persisted measurements if available, otherwise clear demo/unavailable state; label simulated fuel; isolate demo alert seeding; no false “online” for stale positions | Academic honesty and coherence | Low/medium; W dashboard/tracking, M tracking, B alerts | Yes |
| P1-4 CV extraction/rubric inconsistent | V27/V28; conflicting score methods | Evidence spans, missing-field handling, no fabricated identity/license; one justified rubric/criterion response; explicit candidate→driver success/link; labeled extraction regression set | Defensible language processing and explainability | Medium; B cv/agent, W candidatures | Yes if showcased |
| P1-5 Vision labels/incidents inconsistent | V15; problem before analysis save; correction path | Save provenance and original prediction separately; link/reconcile incident after saved analysis; honor auto-create flag; give human normal/anomaly/cannot-tell options; small labeled benchmark | Real human-in-the-loop pipeline | Medium; B vision, M dialog, W review if enabled | Yes if showcased |
| P1-6 No complete feature regression evidence | Existing tests only contexts/starters | Add focused behavior checks for login/ownership, resource/tour states, uploads, complaint reply, AI outage/grounding and browser smoke; record results in demo checklist | Prevent regressions and support report claims | Medium; all apps/test fixtures | Yes |

## P2 — Useful improvements after the core is strong

| ID / problem | Evidence | Proposed improvement / value | Complexity / components | Before PFE |
| --- | --- | --- | --- | --- |
| P2-1 No measured execution history | Tracking memory, no visit/weight labels | Persist minimal visit/tour measurements and timestamps; display actual vs estimated duration; enables later duration analysis | Medium; B data/tours/tracking, M execution, W report | Optional, useful for experiments |
| P2-2 Road distance/geography mismatch | Haversine; Casablanca/Tunis defaults | Configure one depot/geography; optionally use a road-distance matrix and compare against Haversine assumptions | Medium; B routing/config, W/M maps | Optional |
| P2-3 Dormant developed screens | AppRoutes vs pages | Choose needed assistant/vision/alert screens; connect only when backed by useful functioning flow; otherwise document/archive deliberately | Low/medium; W router/pages | Only chosen features |
| P2-4 Supervisor assistant semantics | All-complaint counts, inactive drivers, broad prompt context | Correct counts and authorized context; factual DB summary; expose only if it saves supervisor work | Medium; B assistant, W selected screen | No |
| P2-5 Upload/index consistency | No durable reindex recovery, public/static roots | Improve file/metadata cleanup and retry status within monolith; concurrent reindex/delete tests; no distributed queue required | Medium; B storage/rag | Optional |
| P2-6 Data-enabled ML | No current labeled corpus | Collect real duration/photos, apply dataset gates and experiments in document14; deploy only if baseline beaten on held-out data | High; collection/evaluation + selected existing modules | Conditional on data/time |
| P2-7 Source clarity and setup | Large services, duplicate starter app/dead dependencies | Extract cohesive scoring/extraction/route functions, centralize configuration, remove agreed dead artifacts, maintain concise setup docs | Medium; B/W/M | Optional |

## P3 — Optional polish

| Problem / evidence | Proposed improvement / value | Complexity / components | Before PFE |
| --- | --- | --- | --- |
| UI feedback placement/consistency | Global Toaster, clear empty/error states, keyboard/label polish; useful polish around fixed flows | Low; W shell/forms | Helpful, not core AI value |
| Large frontend bundle warning | Lazy-load chosen heavy map/chart/AI pages after measuring startup; avoid unneeded library churn | Low/medium; W routing/build | No |
| Basic PDF layout only verified | Improve report typography, pagination and evidence captions after photo flow works; render-check sample report | Low/medium; B report | Optional |
| Obsolete starter branding/docs | Remove confusing counter/starter material after explicit scope decision; tighten screenshots and PFE narration | Low; W/M/docs | Optional |

## Recommended implementation sequence

1. Establish reproducible environment, correct identity/ownership, validated resources and tour state.
2. Make device tour start/resume/photos/finish work; verify supervisor sees evidence and complaint replies.
3. Make AI unavailable behavior and all simulated/estimated values explicit.
4. Consolidate routing and evaluate RAG against documented baselines.
5. Repair and evaluate CV extraction; include vision only with working upload and honest reviewed outcomes.
6. Collect actual operational labels; decide whether any trained ML experiment is realistic.
7. Finish chosen screens, targeted regression checks and presentation/report polish.

Stop adding features when this coherent demo and its evidence are strong. No microservices, Kubernetes, enterprise analytics stack, external vector database or complicated MLOps platform is required by the evidence found.
