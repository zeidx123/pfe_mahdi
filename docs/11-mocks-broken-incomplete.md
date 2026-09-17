# Mocks, hardcoding and incomplete functionality

## Current state: explicit classification

| Finding | Category | Evidence | Consequence |
| --- | --- | --- | --- |
| Seven fixed daily tonnes values (12.5,14.2,13.8,15.1,16.4,18.2,10.1) | HARDCODED | `W/pages/DashboardPage.jsx`; browser renders chart even with empty operations | Pretends to be seven-day collection measurement; no DB/API for these figures |
| Mobile `niveauCarburant:75` | HARDCODED | `M/services/tracking_service.dart` | GPS is device-derived, fuel is not; do not call the whole tracking feature fake |
| Two startup fuel/engine alerts | MOCKED events / persisted demo seeds | `B/alerte/service/AlerteService.java` | Critical dashboard count starts nonzero without actual vehicles; event producer is absent |
| Vision NORMAL with confidence0.7 when provider fails | HARDCODED fallback | `B/ai/vision/service/ImageAnalysisService.java`; V14 | Unanalysed image presented as a successful normal assessment |
| RAG fixed official-looking incident instructions | HARDCODED fallback | `B/ai/rag/service/RagService.java`; V24 | Cited documents may not support the answer; complaint-photo instructions refer to missing feature |
| Local SHA word vectors described as semantic embeddings | Deterministic logic mislabeled | GeminiEmbeddingService | Real lexical retrieval fallback, not pretrained semantic inference |
| “Minimum guaranteed”, “AI Agent” route explanation | Deterministic logic overstated | TourneeRouteOptimizationService; browser modal | Nearest neighbor cannot guarantee minimum; templates are not LLM reasoning |
| “CVRP” route algorithm label | Incorrect implementation claim | RouteOptimizationService; V11 | Capacity is displayed/clipped, not enforced |
| CV score called AI with competing UI breakdown | Deterministic rules, inconsistent explanation | CvAnalysisService; CandidaturesPage | Score is computed, not random, but not learned or internally consistent |
| Candidate-to-driver invented default fields | HARDCODED/generated replacements | CvAnalysisService.createAgentFromCandidate | Unverified identity/license data can look factual |
| Web Assistant/AnalysePhotos/Alerte/Rapport pages | LEGACY / UNUSED in current router | AppRoutes imports/routes | Backend-backed UI exists but is unreachable; not evidence of a usable feature |
| Standalone optimization and supervisor chat wrappers | LEGACY / UNUSED caller paths | aiService and active import/call search | APIs exist, but current tour/RAG flows use other implementations |
| Vite App.jsx counter and nested starter frontend | LEGACY / UNUSED | main.jsx mounts AppRoutes; nested project | Not part of municipal application |
| Flutter counter widget test | LEGACY / UNUSED expectation, BROKEN test | widget_test.dart references MyApp | Test/analyze failure |
| Java backup file | Non-code artifact | UTF-16 PowerShell error transcript | Cannot restore a service from it |
| Tracking history/background tracking | NOT IMPLEMENTED | latestPositions map; mobile Timer | No journey history or durable background execution despite continuous-tracking impression |
| ESP32 integration / predictive maintenance | NOT IMPLEMENTED | numeroESP32/maintenance fields only; no firmware/ingestion/model | Metadata is not an IoT or prediction pipeline |
| Complaint photo and automatic incident notifications | NOT IMPLEMENTED | Reclamation model/controllers; unused unread state | Assistant instructions and notification expectations exceed product capabilities |
| Operational post-login toast feedback | BROKEN placement | Toaster only in LoginPage | Calls from management pages lack a mounted global renderer |

### Acceptable seeds and generated values

The seed supervisor is a development bootstrap account, not proof of a user-management feature being fake. The 13 CV PDFs and eight document PDFs are samples/reference inputs, not generated production analytics. The GPS simulator is explicitly named and intended for development; its synthetic movement is acceptable when labeled, though the script currently fails. Random/UUID filenames, generated references and entity IDs are identifiers, not fake predictions. Frontend random tour reference suffixes can collide but are not analytics fabrication.

The problem with the two alert samples is not the existence of seed data: they feed ordinary operational counts without a demo distinction or actual source event. The same distinction applies to showing a fixed tonnes graph as current measurement.

### Broken fundamentals, separate from mocks

Actual failures include photo500s, anonymous reset, inactive logins, cross-driver tour mutation, mobile flat/nested auth mismatch, missing tour recovery, null-complaint page crash, stale optimized point lists, unsupported references and failing starter tests. These are defects in real implementations, not merely placeholder screens.

## Recommendations

Use explicit demo labels for simulated values or remove the measured claim until a real data source exists. Return unavailable/uncertain states instead of invented successful AI results. Expose only coherent features needed for the demonstration. Do not delete inactive code during this audit; decide its future after core behavior is stable.
