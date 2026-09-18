# PROJECT_CONTEXT — implemented GestiCollecte PFE

Implementation snapshot: **17 September 2026**. This file describes the current application source and demonstrated software behavior. The original pre-implementation context is preserved unchanged in [PROJECT_CONTEXT_AUDIT_BASELINE.md](PROJECT_CONTEXT_AUDIT_BASELINE.md). Documents `00`–`15` and `AUDIT_REPORT.md` remain historical audit records, not the current implementation status.

Read [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) for verification gates and [DEMO_SETUP.md](DEMO_SETUP.md) for setup. Test success is distinct from physical-device verification and empirical AI accuracy.

## Product, users and boundaries

GestiCollecte is an academic municipal waste-collection PFE. Its central demonstration is: supervisor creates a tour → road optimization → assigned driver starts/resumes → navigates to each point → collects and captures evidence → AI assessment and human observation → remaining stops → return to depot → completion and supervisor review.

| Platform | User | Implemented responsibilities |
| --- | --- | --- |
| React web | `SUPERVISEUR` | Manage drivers, trucks and points; create/optimize tours; inspect live/stale tracking, execution, evidence and duration; review/correct evidence; review candidate matching and decide recruitment; approve RAG documents. |
| Flutter mobile | `CHAUFFEUR` | Log in, receive assigned tours, start/resume, navigate, arrive, submit AFTER evidence and optional BEFORE evidence, report issues, consult AI assessment, record human observation, return/finish, submit complaints and use the procedural RAG assistant. |

Candidates do not have an application account. There is no citizen portal or autonomous hiring process. The real role enum contains `SUPERVISEUR` and `CHAUFFEUR`; some older annotations still mention other names without corresponding role records.

The implementation keeps a Spring monolith, MongoDB, local files, React/Vite and Flutter. It does not introduce microservices, generalized distributed transactions, a full offline queue, trained custom models, a custom navigation engine or a full UI redesign. Optional P2 summaries and P3 polish have not been implemented.

## Architecture and integration

- `BACK`: Java 17, Spring Boot 3.2.5 MVC/Security/Validation, Spring Data MongoDB, BCrypt/JJWT, OkHttp, PDFBox/POI, OpenPDF and STOMP/SockJS. The main package is `com.municipalite.gestiontournees`.
- `FRONT`: React 19/Vite 8, React Router, Axios, existing custom CSS, Leaflet/Recharts and STOMP. `src/main.jsx` mounts the actual AuthProvider/AppRoutes application. The unused starter files remain outside the demonstrated workflow.
- `MOBILE`: Flutter/Dart with Dio, SharedPreferences, Geolocator, image_picker, flutter_map and external URL navigation. Server configuration is selectable at login or supplied with `API_BASE_URL` at build time.
- MongoDB stores users, resources, tours, embedded visits, document chunks, conversations, evidence analyses, candidates, requirement versions and evaluation snapshots. Uploaded files remain on local disk; authenticated endpoints serve them.
- Gemini is used through configurable text/vision and embedding settings. The current checked-in Flash default is `gemini-3.8-flash`; this is a replaceable configuration value, not an architectural dependency on that generation. Embeddings default to configurable `gemini-embedding-2`, 768 dimensions and version metadata.
- OpenRouteService supplies directed road matrices and route geometry. The configured depot is latitude **36.722857**, longitude **10.283535**. Default profile: `driving-car`; default maximum: 20 collection points; default service: 300 seconds per stop.

Normal application routes remain `/login`, `/dashboard`, `/agents`, `/camions`, `/points`, `/tournees`, `/tracking`, `/reclamations`, `/candidatures`, `/documents-ia`. `/optimisation` redirects to `/tournees`. Tour evidence review is integrated into tour details. Older standalone assistant/vision/report page files are not a separate completed product surface.

## Core workflows and data

### Authentication, ownership and files

The login response is flat: token/type plus id, email, name, role and photo. Flutter now reads that contract and restores the session through `/api/auth/me`. Inactive users cannot authenticate; disabling an account also makes its previously issued token invalid on subsequent authenticated HTTP requests.

`AccessService` centralizes authenticated user, supervisor and tour-assignment checks. Drivers access assigned tours and their evidence; supervisors manage/review the workflow. RAG conversation IDs are scoped to the authenticated owner. Photo/file requests carry a Bearer header instead of a token in the URL.

The former anonymous bulk password reset is removed. Startup does not reset passwords or reactivate drivers. First-supervisor bootstrap is explicitly opt-in and requires configured credentials; an existing account is never reset. The existing driver onboarding credential convention remains CIN-based; this is not a completed password-management redesign.

WebSocket transport uses `/ws` with a Bearer token in the STOMP CONNECT `Authorization` header. Only supervisors subscribe to `/topic/positions`; client STOMP publication is rejected. GPS writes use the authenticated REST endpoint, a matching tour/truck and an active tour. Latest positions are memory-only and marked stale after 60 seconds or when the tour ends. They are not a historical telemetry database.

### Tour execution and evidence

A `Tournee` embeds ordered `TourneeVisit` records: point/order, planned service, arrival/completion timestamps, before/after references, AI analysis reference/status, driver outcome/assessment/notes and supervisor review. Tour states are `PLANIFIEE`, `EN_COURS`, `TERMINEE`, `ANNULEE`. Visit states are `PENDING`, `ARRIVED`, `COMPLETED_REPORTED`, `ISSUE_REPORTED`.

Normal completion requires arrival and an AFTER image. BEFORE is optional. An unsafe/obstructed stop can be handled as an issue with an explanation, without forcing dangerous photography. Finish requires all stops handled and explicit return-to-depot confirmation; final-tour photo is optional. The legacy generic status endpoint cannot bypass finish validation. Truck availability follows active execution.

For historical tours without visit records, `legacyProgressMissing` marks unknown progress and `legacyPhoto` preserves any generic photograph whose BEFORE/AFTER stage was not recorded. No completion timestamp, AFTER evidence or AI label is inferred from those photos.

The mobile app resumes confirmed server state. It preserves one unsent private photo draft per user on the device, with explicit retry/discard. There is no automatic offline action replay. Tracking uses one active Geolocator stream with an Android foreground notification while navigating; force-stop may interrupt tracking and must be followed by resume.

Evidence assessments are `CLEANED`, `NOT_CLEANED`, `OBSTRUCTED`, `CANNOT_DETERMINE`, `ANALYSIS_UNAVAILABLE`. They describe the visible evidence, not proof of who performed the task. Original prediction, driver observation/correction and supervisor label remain separate. There are no invented confidence percentages. Provider failure never means cleaned. Replacing evidence clears stale reviews; delayed analysis is checked against the evidence it actually used.

### Routing and duration

`RouteAlgorithms` compares manual order, nearest neighbour and nearest neighbour + directed 2-opt on the same road matrix. Cost prioritizes travel seconds and then distance. 2-opt recomputes the complete directed cost. Held–Karp provides an exact benchmark for up to ten collection points; exact results are a benchmark, while the app selects the best manual/heuristic candidate.

`RoadRoute` stores depot, profile/provider, ordered points, input coordinates, matrix, legs, geometry, costs, comparisons and algorithm version. The selected route returns to the depot. Invalid/unreachable input and provider failure remain explicit; there is no hidden Haversine fallback. Coordinate snapshots are checked before start and retained for route navigation so a later point edit cannot silently change the estimated route.

Google Maps opens the next destination, then the depot. It may choose different roads; GestiCollecte controls visit order. This is not a truck-restriction/traffic-aware CVRP solver.

At start, estimated total/service duration is frozen. Total observed duration uses real start/finish timestamps; service observations use arrival/completion pairs. Missing intervals remain missing, with a measured-stop count. The timing evaluator only compares full-tour service estimates when all service intervals exist. Elapsed time may include waiting/breaks and is not claimed to be pure driving time or observed distance.

### RAG

Approved documents feed page/section-aware extraction, BM25 lexical retrieval, compatible semantic retrieval and hybrid reciprocal-rank fusion. Approval, readable text and semantic-index readiness are distinct. The current small-corpus implementation remains Java/MongoDB; no vector database is required.

Document/chunk records carry embedding model, dimensions and version. Synthetic hash embeddings are removed; incompatible historical vectors require reindexing. A bounded existing document worker handles extraction/indexing; there is no general distributed job platform.

After a restart, interrupted `PROCESSING` records become `TEXT_READY` if usable chunks exist or `UPLOADED` otherwise. Their interruption message requests manual reindex; no provider job is automatically replayed. Retrieval history expands explicit follow-ups rather than unrelated short new questions.

Responses expose `ANSWERED`, `SOURCE_EXCERPTS`, `INSUFFICIENT_INFORMATION`, or `PROVIDER_UNAVAILABLE`, with structured citations, literal snippets and authenticated source links. If generation or semantic infrastructure fails but lexical sources remain usable, the assistant returns labelled excerpts. It does not invent a procedural answer. Generated citation identifiers/supporting quotes are validated, but real answer grounding still requires empirical review.

### Candidate matching

PDF/DOCX extraction proposes fields with `MISSING`, `INVALID` or `UNCERTAIN` status, excerpts and page/paragraph/table location. Human review can correct and mark values `VERIFIED`. Unreadable/scanned files require manual review. Local fallback is conservative and does not infer a CIN from an arbitrary telephone number.

One editable driver profile is stored as immutable versions in `driver_requirement_versions`. Initial weights: C/CE licence 40, driving experience 40, safety training 10, collection experience 10. Only licence is initially mandatory; two years of driving experience is a proportional scoring target, with partial points below two years and no automatic exclusion. A later explicit mandatory-experience setting instead gives zero experience points below its hard threshold. Age, CIN, telephone and CV completeness do not award suitability points.

Each evaluation saves the profile and reviewed values with criterion scores, required-criteria status and date. Unknown fields make the score provisional. Legacy scores remain identifiable as historical; changing a profile does not silently reinterpret previous evaluations. `AUCUN` is valid only as the sole licence value.

Hiring remains a supervisor decision. Driver creation requires verified identity/contact/licence data, a valid expiry and satisfied mandatory criteria under the current profile. The saved candidate–driver link protects repeated acceptance from duplicate creation. CV acceptance sends no email. The older direct Agents CRUD still has its existing optional SMTP behavior and is not an audited email-delivery feature.

## Current API additions and changes

All paths below use the existing `/api` base except `/uploads` and `/ws`. Existing resource CRUD remains available with role validation.

| API | Contract |
| --- | --- |
| `POST /auth/login`, `GET /auth/me` | Flat authenticated identity response; inactive accounts rejected. |
| `GET /tournees`, `GET /tournees/mes-tournees`, `GET /tournees/{id}` | Driver-scoped results with visits, route and timing. |
| `POST /tournees/{id}/optimiser`, `GET /tournees/{id}/route-export` | Supervisor road optimization and reproducible matrix/route export. |
| `POST /tournees/{id}/start` | Validated start or resume; frozen estimate and truck state. |
| `POST /tournees/{id}/points/{pointId}/arrive` | Arrival at the next unhandled stop. |
| `POST /tournees/{id}/points/{pointId}/photo?stage=AFTER` | Multipart `photo`; stage defaults to AFTER, optional BEFORE. |
| `POST /tournees/{id}/points/{pointId}/analyse` | Analyze already stored visit evidence. |
| `POST /tournees/{id}/points/{pointId}/complete` | `outcome: COMPLETED|ISSUE`, optional `driverAssessment`, notes; issue requires notes. |
| `PATCH /tournees/{id}/points/{pointId}/review` | Supervisor `{label, notes}`. |
| `POST /tournees/{id}/finish` | `{returnedToDepot:true}` and all-stop validation. |
| `POST /tracking/position`, `GET /tracking/positions` | Matching active tour/truck GPS submission; supervisor current/stale positions. |
| `GET /uploads/{category}/{filename}` | Authenticated files; driver tour-evidence ownership. |
| `POST /ai/documents`, `POST /ai/documents/{id}/reindex`, `PATCH /ai/documents/{id}/approval` | Multipart `file`; separate processing/approval; `{approved:true|false}`. |
| `GET /ai/documents/{id}/source` | Authenticated source; driver requires approved document. |
| `POST /ai/rag/query`, `GET /ai/rag/conversations` | `{question, conversationId?}` and owner-scoped conversations. |
| `POST /ai/cv/upload`, `GET /ai/candidates` | Multipart `cv`; candidates with evidence and canonical scoring. |
| `GET/PUT /ai/cv/requirements` | Read current profile / save new version. |
| `PATCH /ai/candidates/{id}/review` | `{fields:{fieldName:{value,status,reviewNote?}}}`. |
| `POST /ai/candidates/{id}/evaluate` | Explicit evaluation against current profile. |
| `PATCH /ai/candidates/{id}/decision` | `{statut,notesSuperviseur}`; acceptance validates onboarding. |
| `GET /rapports/tournee/{id}/pdf` | Supervisor execution report. |

Standalone historical `/ai/routes/optimize` and proposal status mutation return `410 Gone`; historical route proposals remain readable. The active route path is the persisted tour API. Legacy vision endpoints remain for compatible callers, but the demonstrated flow analyzes the visit's stored photos through the tour API.

## Evidence and remaining gates

See [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md) for current check counts and [the staged HTTP evidence](evidence/implementation-staged-workflow.json). Simulated providers prove contracts and failure behavior only. Final gates still include a physical Android tour, live Gemini/ORS configuration and success/failure verification, independent CV/RAG/image labels, reviewed road scenarios, and actual field duration measurements. Dataset sizes are flexible targets, not rigid product requirements.

Evaluation entrypoints exist under `evaluation/routing`, `evaluation/rag`, `evaluation/vision`, `evaluation/timing`, and `scripts/evaluation/evaluate_candidates.py`. No empirical model-accuracy, real-road improvement or field-duration claim is established merely by their presence. P2/P3 remain deferred until the complete workflow and P1 evaluations pass.
