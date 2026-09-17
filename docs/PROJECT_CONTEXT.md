# PROJECT_CONTEXT — GestiCollecte

**Source-of-truth snapshot: 17 September 2026.** Read this first when continuing the project in ChatGPT/Codex. It describes audited implementation, not a proposed redesign. Full evidence and detailed source mapping are in this docs directory; start at [README](README.md). Reverify findings after code changes. No git commit identifier was available.

## Product and scope

Academic PFE municipal waste-collection system. Supervisors manage drivers, trucks, collection points, tours, incidents, CV candidates and a document corpus. Drivers use Flutter for assigned tours, GPS/photo collection, complaints and procedural RAG chat. Goal: a coherent working demo and defensible AI/algorithm evaluation, not commercial SaaS or enterprise infrastructure.

Actual roles: `SUPERVISEUR`, `CHAUFFEUR`. “Agent” means driver in CRUD. `ADMIN`/`AGENT` occur in annotations but are absent from the role enum. Candidates have no login/portal. No citizen app, multi-tenancy, actual sensor firmware, trained predictive model or autonomous AI agent exists.

## Applications and architecture

- `BACK`: Java17/Spring Boot3.2.5 modular monolith. Controllers/services/Spring Data Mongo repositories under `src/main/java/com/municipalite/gestiontournees`. REST JSON/multipart, BCrypt + jjwt0.12.5 bearer authentication, STOMP/SockJS broadcaster, local uploads, OpenPDF reports, SMTP welcome email.
- `FRONT`: React19/Vite8 JavaScript JSX, React Router7, Axios, Context/hooks, custom CSS, Leaflet, Recharts, STOMP/SockJS. Entry `src/main.jsx` mounts AuthProvider/AppRoutes, **not** starter App.jsx.
- `MOBILE`: Flutter/Dart, Dio, SharedPreferences, Geolocator, image_picker, flutter_map. REST GPS timer every10seconds; no background service/offline queue. SDK used in audit Flutter3.24.5/Dart3.5.4.
- MongoDB default `gestion_tournees` on localhost27017; backend port8080. Tracking latestPositions is **memory only**. Uploads are local files with references in Mongo.
- Google Gemini REST via OkHttp4.12, PDFBox3, POI5 for AI/document processing. No Spring AI, LangChain, Python service, notebook, trained model artifact, external vector DB, Docker/Compose or complex infrastructure was found.

Web routes: `/login`, `/dashboard`, `/agents`, `/camions`, `/points`, `/tournees`, `/tracking`, `/reclamations`, `/candidatures`, `/documents-ia`; `/optimisation` redirects to tours. AssistantPage, AnalysePhotosPage, AlertePage and RapportPage exist but are **unrouted**. Tour PDF is still reachable from details. Nested `FRONT/gestion-tournees-front` and top-level App.jsx are unused starters.

## Data and APIs

Collections: `users` (drivers and supervisors), `camions`, `points_collecte`, `tournees`, `reclamations`, `alertes`, `candidates`, `route_optimizations`, `image_analyses`, `ai_problems`, `ai_documents`, `document_chunks`, `rag_conversations`, `chat_conversations`. Conversations embed messages. String-ID relationships are not DB-enforced foreign keys. User email/matricule and resource references have selected unique indexes. No visit/weight/telemetry time series exists.

Core `/api` families: auth login/me; agents/camions/points/tournees CRUD; tour mes-tournees/status/photo/optimiser; tracking position(s); reclamations/repondre; dashboard/stats; alertes; rapports/tournee/{id}/pdf. AI families: `/api/ai/cv/upload`, candidates/decision, routes/optimize/history/status, vision/analyze/confirm/analyses, problems, documents/reindex, rag/query/conversations, assistant/chat/conversations, statistics/summary. See [06](06-api-inventory.md) for all methods/contracts/access.

AuthResponse is flat (`token`, user fields), while mobile reads `response['user']`: token succeeds but stored user becomes null. Tour status is a query parameter. CV upload field is `cv`; document field `file`; image field `photo`. Tracking uses `camionMatricule` and `niveauCarburant`. RAG and supervisor chat have different response types/storage.

## Actual AI systems

1. **CV:** PDFBox text → Gemini JSON extraction or local regex → cleanup → fixed eligibility/point scoring → Candidate. No learned score. Web criterion breakdown differs from backend. Local fallback never sets DOB and can take phone as CIN; explicit age55 and missing-CIN synthetic CVs both scored85 recommended. Acceptance can invent missing identity/license fields and swallow driver-creation errors; no candidate.agentId.
2. **Active tour ordering:** TourneeRouteOptimizationService uses nearest neighbor, open-path Haversine,30km/h +5min/stop and fixed explanation. No depot/capacity/road routing/LLM. The UI's “guaranteed minimum” is false.
3. **Separate route API:** RouteOptimizationService uses depot→NN→2-opt→return,40km/h, estimated weight/fuel and separate records. Capacity is not enforced (240kg accepted for100kg truck); “CVRP” label overstates implementation. No active UI or conversion of accepted proposal into tour.
4. **Vision:** Gemini image JSON → analysis; anomaly confidence>=.65 may create AiProblem. Failure becomes NORMAL/confidence.7; this is a serious false-success fallback. No validated confidence/labels. Problem created before analysis ID; human correction does not reconcile incident; mobile omits driverId.
5. **RAG:** PDF/DOCX/TXT/MD → page/section chunks1000chars overlap150 → Gemini `text-embedding-004` or768dim hashed word fallback → Java scan of Mongo chunks with .4cosine+.6keyword ranking,top5/min.15 → Gemini grounded prompt or fixed keyword procedure fallback. Not BM25/Atlas search. Embedding provenance absent. Offline procedure can be unsupported by the attached source. History saved but not used in prompt; supplied conversationId ownership unchecked; empty-corpus new response ID null.
6. **Supervisor assistant:** separate DB-context prompt + last5messages, optional Gemini, intent templates, chat_conversations. Not document RAG or tool-calling agent. No active client caller. Counts can misdescribe treated complaints as pending.

Gemini text/vision is configured as `gemini-1.5-flash`, but the checked-in key is a **placeholder**. No live Gemini success/quality was established. Do not confuse fallback execution with successful model inference. No evaluation/training dataset exists;13 CV PDFs/eight reference PDFs are samples, not validation evidence.

## Verified behavior and known failures

**Observed working in isolated tests:** JWT login/basic roles; resource creation/readback; own-tour filtering; order calculation/recalculation; complaint create/reply/history; basic PDF bytes; document upload/index/retrieval/history; browser login/tour search/details; REST position broadcast updating web map.

**Observed broken or inconsistent:** both tour photo endpoints500 (relative MultipartFile transfer under Tomcat); anonymous reset-all200; inactive driver login200; driver mutates other driver's tour; invalid references/transitions accepted; truck remains available on active tour; complete with no photos; clearing point list retains old ordered IDs; point edit resets fill; empty complaint accepted then crashes web; unavailable vision reported normal; unsupported RAG fallback; cross-user conversation mutation.

**Hardcoded/demo:** weekly tonnes array, mobile fuel75%, two seeded alerts, fixed RAG procedures, vision fallback confidence/normal result. Route/score numbers are deterministic calculations, not random predictions. Explicit GPS simulator is acceptable as labeled demo tooling but fails because require is used in ESM and lacks auth.

**Source-traced remaining concerns:** mobile cannot resume active tour; local photo/timer state discarded; tracking timer lifecycle wrong;10s Dio timeout vs60s AI; all uploads publicly exposed; unrestricted STOMP channels; backend startup resets CIN passwords/reactivates drivers; post-login toast renderer absent. Hardcoded LAN/localhost and mixed Tunisia/Casablanca geography reduce portability.

## Verification boundaries

Audit used disposable Mongo27018, temporary upload storage and synthetic records. Default Mongo was inspected read-only. Gemini and SMTP were disabled; no external messages were sent. Backend package and clean frontend build pass; frontend lint returns34warnings. Backend tests:3total,2pass,1 stale configuration error. Flutter web builds; analyze/test fail on stale MyApp counter test. Source unchanged; only docs/evidence and generated tool/build artifacts created.

**Needs verification:** live provider behavior/accuracy, email delivery, physical device camera/GPS/background operation, Android/iOS release build, mobile resume/recovery, large/concurrent indexing, document reindex/delete, candidate acceptance outcomes, photo-rich PDF rendering. Existing APKs do not prove current source works.

## Priorities and constraints for the next agent

1. Reproduce setup safely; never start on a user's real DB without accounting for destructive startup password/reactivation behavior.
2. Fix auth/ownership, mobile login, photo storage, validated tour state/resources and null complaint crash; make mobile start/resume/finish reliable.
3. Make AI unavailable/uncertain and simulated/estimated measurements explicit. Do not invent successful answers to hide outages.
4. Evaluate grounded RAG and unified deterministic routing with baselines; fix CV evidence/rubric and vision correction before showcasing them.
5. Consider trained vision/duration models only after collecting adequate real labeled data. No speculative maintenance/forecast model on constants.

User requested **audit and documentation only** in this phase. No functionality was rewritten. Preserve useful existing code/patterns, avoid microservices/Kubernetes/enterprise overhead. Consult [15](15-improvement-roadmap.md) for prioritized proposals and [10](10-functional-audit.md) before making success claims. Use “Needs verification” for untested branches. Repository navigation instructions require Graphify query first when the existing graph is available; source execution outranks graph/comment labels.
