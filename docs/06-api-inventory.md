# API inventory

## Current state and conventions

Base URL is normally `http://localhost:8080`. JSON APIs use Bearer JWT; multipart names matter. Access abbreviations: **S** supervisor, **D** driver, **A** any authenticated user, **Public** no token required. Access describes code configuration, not intended authorization. Driver-owned-object checks are absent on several A/S+D endpoints.

`R` below means runtime response observed; `S-only` means source traced and **Needs verification** at runtime. Every `/api` route below is implemented by the correspondingly named `B/<domain>/controller/*Controller.java`. Feature correctness is detailed in [10](10-functional-audit.md), not implied by a 200 response.

### Core request/response contracts

- LoginRequest: `email` (also accepts matricule) and `password`; AuthResponse is **flat**: token and user fields, not `{token,user:{...}}`.
- AgentRequest: names, CIN, email, phone, address, license fields/expiry/type, hire date, actif. Bean validation includes required names/CIN/email/phone/hire date. AgentResponse omits password hash.
- CamionRequest: matricule, make/model/year/type, weight/tank capacities, mileage/service dates/status/condition/ESP32/maintenance, actif. PointCollecteRequest: reference/address/zone/coordinates/type/fill/actif. Not all numerical business ranges are constrained.
- TourneeRequest: reference/name/description, planned date/time, truck/driver IDs, point IDs, status, notes and related editable fields. TourneeResponse expands current resource labels/point details and optimization/photo fields.
- VehiculePosition: `camionId`, `camionMatricule`, `tourneeId`, `tourneeReference`, `latitude`, `longitude`, `vitesse`, `niveauCarburant`, `statut`, timestamp. Tracking code supplies timestamp on receipt.
- Most “not found”/validation errors flow through GlobalExceptionHandler; exception types and direct map casts do not form a fully consistent API error contract.

### Operational endpoints

| Method and route | Caller / purpose | Request → response | Access | Data / AI | Status |
| --- | --- | --- | --- | --- | --- |
| POST `/api/auth/login` | Both login screens | LoginRequest → flat AuthResponse | Public | users lookup, BCrypt/JWT; no AI | R 200 / bad credentials 401; mobile contract mismatch |
| GET `/api/auth/me` | Web session bootstrap | Bearer → profile | A, controller rejects anonymous | users | R 200/401 |
| GET `/api/agents` | Web driver list, tour form | → AgentResponse[] | S | users by role | R; D gets 403 |
| GET `/api/agents/{id}` | Driver detail/reset tooling | ID → AgentResponse | S | users | S-only |
| POST `/api/agents` | Agent form | AgentRequest → AgentResponse, 201 | S | users + async SMTP | R; invalid empty body 400; email delivery unverified |
| PUT `/api/agents/{id}` | Agent edit | AgentRequest → AgentResponse | S | users | S-only; CIN/password consistency issue |
| DELETE `/api/agents/{id}?permanent=false` | Agent list | → 204; optional physical delete | S | users | R soft disable; login still succeeds; permanent path S-only |
| POST `/api/agents/{id}/reset-password` | Agent list | → 204; password becomes CIN | S | users | S-only |
| POST `/api/agents/reset-all-passwords` | No normal user workflow | → message | **Public** | all drivers' passwords | R anonymous 200; critical defect |
| GET `/api/camions` | Fleet and tour forms | → CamionResponse[] | S | camions | R, includes inactive records |
| GET `/api/camions/{id}` | Detail | → CamionResponse | S | camions | R |
| POST `/api/camions` | Fleet form | CamionRequest → response, 201 | S | camions | R |
| PUT `/api/camions/{id}` | Fleet edit | CamionRequest → response | S | camions | R |
| DELETE `/api/camions/{id}` | Fleet list | → 204, soft inactive | S | camions | R |
| GET `/api/points` | Point list, tour form | → PointCollecteResponse[] | S | points_collecte | R via dependent workflows |
| GET `/api/points/{id}` | Point detail | → PointCollecteResponse | S | points_collecte | S-only |
| POST `/api/points` | Map/form | PointCollecteRequest → response, 201 | S | points_collecte | R; 999/999 coordinates and fill 200 accepted |
| PUT `/api/points/{id}` | Point edit | Request → response | S | points_collecte | R; omitted fill resets to 0 |
| DELETE `/api/points/{id}` | Point list | → 204, soft inactive | S | points_collecte | S-only |
| GET `/api/tournees` | Web list; permitted to drivers too | → TourneeResponse[] | S+D | tournees + resource lookups | R; driver sees another driver's tour |
| GET `/api/tournees/mes-tournees` | Mobile home | token identity → own tour array | S+D | users/tournees | R; two-driver isolation works here |
| GET `/api/tournees/{id}` | Tour detail | → expanded response | S+D | tournees/resources | R via probes; no ownership guard |
| POST `/api/tournees` | Tour form | TourneeRequest → response, 201 | S | tournees; deterministic optimizer | R; nonexistent resource IDs accepted |
| PUT `/api/tournees/{id}` | Tour edit | request → response | S | tournees; optimizer | R; clearing points retains stale optimized result |
| PATCH `/api/tournees/{id}/statut?statut=...` | Web/mobile lifecycle | **query parameter**, not JSON → response | S+D | tournees | R; cross-driver changes and invalid lifecycle accepted |
| DELETE `/api/tournees/{id}` | Supervisor cancel | → 204 / ANNULEE | S | tournees | S-only |
| POST `/api/tournees/{id}/points/{pointId}/photo` | Mobile evidence | multipart `photo` → tour response | S+D | disk + tournees | **R 500**, relative transfer destination |
| POST `/api/tournees/{id}/fin/photo` | Mobile completion | multipart `photo` → tour response | S+D | disk + tournees | **R 500** |
| POST `/api/tournees/{id}/optimiser` | Web Recalculer | → updated tour response | S | deterministic integrated optimizer | R 200 |
| POST `/api/tracking/position` | Mobile GPS; simulator intended | VehiculePosition → response | A | in-memory latest map; broadcast | R; browser live update verified |
| GET `/api/tracking/positions` | Web initial map | → latest positions array | A | memory only | R |
| POST `/api/reclamations` | Mobile complaint form | entity-shaped JSON title/type/description → saved record | A | reclamations, server binds driver | R; `{}` accepted with null title |
| GET `/api/reclamations/mes-reclamations` | Mobile history | → own records | A | reclamations | R with supervisor reply |
| GET `/api/reclamations` | Supervisor page | → all records | S | reclamations | R; null title causes web crash |
| POST `/api/reclamations/{id}/repondre` | Supervisor reply | `{reponse}` → TRAITEE record | S | reclamations | R |
| GET `/api/dashboard/stats` | Dashboard | → driver/truck/tour/critical-alert counts | S | operational collections; no AI | R; no tonnes timeseries |
| GET `/api/alertes` | Unrouted alert page | → alerts | S | alertes | S-only; seeds observed through dashboard |
| GET `/api/alertes/unread-count` | Layout polling | → count | S | unresolved alertes | Browser polling/source; displayed badge missing |
| PATCH `/api/alertes/{id}/resolve` | Unrouted alert page | → resolved record | S | alertes | S-only |
| GET `/api/rapports/tournee/{id}/pdf` | Tour details; unused report page | → application/pdf bytes | S | tour/resources/files; OpenPDF | R 200, valid PDF signature |

### AI and document endpoints

| Method and route | Caller / purpose | Request → response | Access | Data / AI interaction | Status |
| --- | --- | --- | --- | --- | --- |
| POST `/api/ai/cv/upload` | CandidaturesPage | multipart **`cv`**, PDF → Candidate | S | PDFBox → Gemini extraction or local parsing → rules → candidates/file | R 15 PDFs; provider execution unverified |
| GET `/api/ai/candidates` | Candidate list | → Candidate[] | S | candidates | R |
| GET `/api/ai/candidates/{id}` | Candidate detail | → Candidate | S | candidates | S-only |
| PATCH `/api/ai/candidates/{id}/decision` | Decision controls | `{statut, notesSuperviseur}` → Candidate | S | candidate update; ACCEPTE attempts users creation + SMTP | S-only; arbitrary status/fabricated fields/silent errors |
| POST `/api/ai/routes/optimize` | Unused aiService wrapper | `{camionId, chauffeurId?, pointCollecteIds, depotLatitude?, depotLongitude?, dateTournee?}` → route proposal/ordered details | S | resources → NN/2-opt → route_optimizations; no LLM | R; 240kg assigned to 100kg capacity |
| GET `/api/ai/routes/history` | No active UI | → optimization records | S | route_optimizations | S-only |
| PATCH `/api/ai/routes/{id}/status` | No active UI | `{statut}` → proposal | S | route_optimizations only | S-only; no tour creation |
| POST `/api/ai/vision/analyze` | Mobile after photo upload | multipart **`photo`**, required `pointCollecteId`; optional tour/driver/GPS → ImageAnalysis | S+D; unused AGENT expression | Gemini image JSON or fixed normal result; image_analyses/ai_problems/files | R fallback 200 NORMAL/0.7; live model Needs verification |
| PATCH `/api/ai/vision/confirm/{id}` | Mobile confirm/correct | `{validee, correctionChauffeur}` → ImageAnalysis | S+D | image_analyses only | R; correction does not create/reconcile problem |
| GET `/api/ai/vision/analyses` | Unrouted photo page | → ImageAnalysis[] | S | image_analyses | S-only |
| GET `/api/ai/problems` | Unrouted photo page | → AiProblem[] | S | ai_problems | R empty after corrected anomaly; no problem-update endpoint |
| POST `/api/ai/documents` | Document management | multipart **`file`** → DocumentInfoDto | S; nonexistent ADMIN expression | local file → loader/chunker/embedder → metadata/chunks | R; response UPLOADED while DB INDEXED |
| GET `/api/ai/documents` | Document page polls | → DocumentInfoDto[] | S | ai_documents | R |
| GET `/api/ai/documents/{id}` | Document status | → DocumentInfoDto | S | ai_documents | R |
| DELETE `/api/ai/documents/{id}` | Document page | → 204 | S | chunks/metadata + disk deletion | S-only; consistency on failure Needs verification |
| POST `/api/ai/documents/{id}/reindex` | Document page | → metadata snapshot | S | async parser/embedder, replaces chunks | S-only; concurrent/error behavior Needs verification |
| POST `/api/ai/rag/query` | Mobile assistant; unrouted web AssistantPage | `{question, conversationId?}` → answer/sources/maxSimilarityScore/relevantContextFound/conversationId | A | embeddings → chunk retrieval → Gemini/fallback → rag_conversations | R; empty question 400; unsupported fallback and ownership defect |
| GET `/api/ai/rag/conversations` | Assistant history | → own RagConversation[] | A | rag_conversations by username | R; listing scoped, supplied ID on query unchecked |
| POST `/api/ai/assistant/chat` | Unused frontend wrapper | `{message, conversationId?}` → ChatConversation with id/title/messages | S | DB context + prior messages → Gemini/intent fallback → chat_conversations | R fallback; distinct from document RAG |
| GET `/api/ai/assistant/conversations` | No active UI | → supervisor chats | S | chat_conversations | S-only |
| GET `/api/ai/statistics/summary` | No active UI | → live counts summary | S | AssistantTools repositories | S-only; not predictive analytics |

### Non-REST surfaces

| Surface | Behavior | Access / observed result |
| --- | --- | --- |
| `/ws` SockJS; `/ws/websocket` transport | STOMP connect; subscribe `/topic/positions`; publish `/app/position` | Public handshake, no channel identity/role binding; anonymous STOMP CONNECTED observed |
| `/uploads/**` | Local upload static resources | Public, including potential CV/document/photo content; guessing a file path should not authorize access |
| External Gemini REST | text/vision generateContent and embedding API through OkHttp | Key configured as placeholder; availability/model compatibility Needs verification |
| SMTP | Welcome email upon driver creation | Configured; disabled during audit, delivery Needs verification |

## Contract and reachability problems

1. Mobile login expects a nested user object the API never returns. JWT storage succeeds while user ID/name becomes unavailable.
2. Four web pages are unreachable; multiple `aiService` functions therefore do not have live callers. Their backend existence is not evidence of a demonstrated feature.
3. Tour order API and standalone route proposal API have different models, metrics and lifecycle semantics.
4. Chat endpoints are two different systems; the active mobile assistant calls RAG, not the DB-context assistant.
5. Status and decision bodies often use raw maps/strings; invalid values can reach storage or raise generic errors. Neither an authenticated user nor an arbitrary object ID proves ownership.
6. No refresh-token, driver change-password, telemetry history, real collected-weight, complaint-photo, problem-resolution or prediction endpoint exists.

## Recommendations

Use explicit DTOs and enums for meaningful contracts, bind identity from authentication, and test the mobile login/tour/photo sequence against the packaged backend. Keep controllers small and reuse existing services. OpenAPI generation can be useful later, but it does not substitute for fixing these contracts.
