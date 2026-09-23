# GestiCollecte project context

Current implementation, **24 September 2026**. See [delivery checks](DELIVERY_CHECKS.md) for verified behavior and remaining gates, and [demo setup](DEMO_SETUP.md) to run the workflow.

## Product and platforms

GestiCollecte is a municipal waste-collection PFE. Its central workflow is: supervisor creates a tour → road optimization → driver starts/resumes → navigates and records visits → supplies evidence → requests AI assessment and records human observations → returns to the depot → supervisor reviews the execution.

| Platform | User | Responsibilities |
| --- | --- | --- |
| React/Vite web | `SUPERVISEUR` | Manage drivers, trucks and points; plan/optimize tours; review execution, tracking, evidence and duration; manage approved RAG documents; review candidates and decide recruitment. |
| Flutter Android/web | `CHAUFFEUR` | Receive assigned tours; start/resume, navigate, arrive, capture/upload evidence, report issues, finish and use complaints and the procedural assistant. |

Candidates do not have application accounts. Hiring and evidence corrections remain human decisions. Browser operation helps test the workflow; it does not establish Android camera or background GPS behavior.

## Architecture

- `BACK`: Java 17, Spring Boot 3.2.5, MongoDB, Spring Security, BCrypt/JWT, OkHttp, PDFBox/POI and STOMP/SockJS. The main package is `com.municipalite.gestiontournees`.
- `FRONT`: React 19/Vite 8, React Router, Axios, Leaflet/Recharts and existing CSS. `src/main.jsx` mounts the supervisor application.
- `MOBILE`: Flutter/Dart, Dio, SharedPreferences, Geolocator, image_picker, flutter_map and external navigation links. The API address is configurable at build time or on the login screen.
- MongoDB stores users/resources, tours with embedded visits, document chunks/conversations, analyses, candidates and immutable requirements versions. Files stay on backend disk and are served through authenticated endpoints.
- Gemini supplies RAG generation and compatible embeddings. Both models are configurable; changing embedding model, dimensions or version requires reindexing.
- `CollectionAiClient` selects Groq by default for Qwen photo analysis and CV extraction. OpenRouter is an explicit alternative; neither selection automatically falls back to another provider. Keys stay in `BACK/.env`.
- OpenRouteService supplies road matrices and geometry. The demonstration depot is **36.722857, 10.283535**, with `driving-car`, up to 20 stops and 300 seconds of service per stop by default.

The project keeps one backend, local uploads and the existing apps. It does not introduce custom model training, a custom navigation engine, distributed job infrastructure or a general offline synchronization system.

## Implemented behavior

### Access and persistence

Flutter reads the flat login response and restores sessions through `/api/auth/me`. Inactive accounts are rejected. Drivers are scoped to assigned tours, their evidence, their own conversations and active-tour tracking. Uploaded files require authentication; tokens are not placed in file URLs.

First-supervisor creation is explicitly enabled by bootstrap configuration. Startup never resets existing passwords or reactivates drivers. Driver onboarding still uses the verified CIN as its initial-password convention; a full password-management redesign is outside this PFE.

Confirmed visits persist on the server. Flutter retains one private unsent-photo draft for manual retry. Start/resume and finish synchronize truck availability. Tracking starts with an active tour and stops on finish/logout; latest positions are held in memory and shown as stale when interrupted. Forced termination and navigation on a physical device still need verification.

### Visits and image evidence

A normal completion needs one AFTER image; BEFORE is optional. An obstructed/unsafe point can be reported with a reason without forcing unsafe photography. Every stop must be completed or reported, followed by an explicit return-to-depot confirmation. The final tour photo is optional.

Image states are `CLEANED`, `NOT_CLEANED`, `OBSTRUCTED`, `CANNOT_DETERMINE` and `ANALYSIS_UNAVAILABLE`. These describe visible evidence, not who performed the work. Prediction, driver observation and supervisor review are stored separately with evidence references and model/prompt metadata. No confidence percentage is invented.

Image analysis defaults to manual. Uploading evidence alone makes no model request. An unchanged visit can reuse its valid saved assessment; changed evidence/provider/model/prompt invalidates reuse, and failures can be retried manually. A small per-visit guard prevents overlapping calls within the single backend process. Supervisor evaluation requests remain fresh calls. Operational progress can continue when AI is uncertain or unavailable.

### Routing and duration

Manual order, nearest neighbour and NN + 2-opt use the same directed road matrix. Cost prioritizes travel time, then distance; 2-opt recomputes the full directed route cost. The application retains the lowest-cost tested heuristic/manual route. Held–Karp is an exact benchmark only for instances with up to ten stops.

The saved route contains the depot, ordered coordinate snapshot, matrix, legs, algorithm comparisons and road geometry. Invalid/unreachable points and provider failure are explicit; there is no silent straight-line substitute. Google Maps opens the next destination, then the depot, while the app preserves visit order.

Travel time, service time and total estimates are separate. The estimate is frozen at start. Observed total duration uses start/finish timestamps; service duration uses recorded arrival/completion pairs. Missing measurements remain missing. Waiting and breaks may be included, so elapsed time is not automatically pure travel time.

### Grounded RAG

Approved documents feed page/section-aware extraction, BM25, compatible semantic retrieval and hybrid reciprocal-rank fusion. Approval, usable text and semantic readiness are separate. Failed embeddings do not disable lexical access to approved text. Synthetic hash embeddings are removed; incompatible old vectors need reindexing.

Responses are `ANSWERED`, `SOURCE_EXCERPTS`, `INSUFFICIENT_INFORMATION` or `PROVIDER_UNAVAILABLE`. Literal excerpts can be returned when generation fails and retrieval works. Unsupported procedures are never substituted. Source identifiers/supporting passages and access are validated; empirical grounding still requires independent review. Follow-up history supports retrieval without replacing document evidence.

Interrupted document processing returns to a retryable state based on stored text; startup does not replay provider jobs automatically.

### Candidate matching

PDF/DOCX extraction proposes fields with evidence and `MISSING`, `INVALID` or `UNCERTAIN` status. Supervisor validation can mark corrected values `VERIFIED`. Unreadable/scanned files need manual entry. Failed model extraction is explicit; conservative local proposals are not presented as verified model results.

The versioned initial rubric awards C/CE licence 40 points, driving experience up to 40 against a two-year target, safety training up to 10 and collection experience up to 10. Licence is initially required. Experience below two years receives proportional points without automatic exclusion. If a supervisor explicitly makes experience mandatory, sub-threshold values receive zero experience points and an unmet requirement. Identity, age, CIN, phone and document completeness award no suitability points.

Each evaluation retains the requirements version, reviewed values and criterion breakdown. Hiring needs reviewed onboarding information and required criteria, and repeated acceptance uses the saved candidate–driver link. CV acceptance does not send email. The separate direct Agents creation path retains its existing SMTP behavior.

## API entry points

Paths below use `/api`, except `/uploads` and `/ws`. Existing resource CRUD remains role-protected.

| Area | Main entry points |
| --- | --- |
| Authentication | `/auth/login`, `/auth/me` |
| Tour route | `/tournees/{id}/optimiser`, `/tournees/{id}/route-export` |
| Tour execution | `/tournees/{id}/start`, `/tournees/{id}/points/{pointId}/{arrive,photo,analyse,complete,review}`, `/tournees/{id}/finish` |
| Tracking | `/tracking/position`, `/tracking/positions`; supervisor STOMP subscription `/topic/positions` through `/ws` |
| RAG documents | `/ai/documents`, `/ai/documents/{id}/{reindex,approval,source}` |
| Chat | `/ai/rag/query`, `/ai/rag/conversations` |
| Candidates | `/ai/cv/upload`, `/ai/cv/requirements`, `/ai/candidates/{id}/{review,evaluate,decision}` |
| Reports/files | `/rapports/tournee/{id}/pdf`; authenticated `/uploads/{category}/{filename}` |

Braced alternatives abbreviate separate actions. Standalone historical route mutation returns `410 Gone`; the supported route workflow is attached to a persisted tour.

## Delivery boundaries

See [P1 results](evaluation/P1_RUN_RESULTS.md) for reproducible inputs and selected outcomes. Software tests, one successful model call and a small ORS comparison establish different facts; none alone establishes a reviewed PFE benchmark. Final independent RAG/CV/vision labels, more road scenarios, field duration measurements and the complete physical Android workflow remain pending. Optional AI tour summaries remain deferred.
