# Technology and library inventory

## Current state

Versions below come from manifests/lockfiles or the actual tool run, not generic README text. “Used” does not imply correctness of every workflow. The audit did not perform a full vulnerability or dependency-upgrade assessment.

| Technology / version | Where / purpose | Dependent feature | Assessment |
| --- | --- | --- | --- |
| Java 17; Spring Boot 3.2.5 | BACK Maven runtime | All backend APIs | Packaged successfully; existing test suite not fully green |
| Spring MVC, Validation, Security (Boot managed) | Controllers, request DTOs, method authorization | CRUD/auth/uploads | Appropriate; validation/ownership incomplete, public reset is a configuration defect |
| Spring Data MongoDB (Boot managed) | Document repositories | All persisted business/AI records | Appropriate for PFE; IDs not foreign keys; no vector DB extension used |
| MongoDB local server | `gestion_tournees` default; audit separate instance | Persistence | Real writes/readback observed; no migrations/import fixture workflow |
| jjwt 0.12.5 | JwtTokenProvider/filter | Signed access tokens | Actual signature/expiry authentication; seven-day lifetime, literal configured secret, inactive users still enabled |
| BCrypt / Spring Security | User password checks | Login | Actual hashing; CIN passwords/reset-on-startup negate sensible credential lifecycle |
| Lombok 1.18.32 processor | Java models/DTOs/services | Boilerplate reduction | Used; some builder/default interactions deserve care, no need to replace for PFE |
| Spring WebSocket/simple broker | WebSocketConfig/TrackingController | Live map | Works with REST broadcasts; process memory, no channel authorization |
| Spring Mail | agent/EmailService | Welcome mail | Actual integration, external delivery unverified |
| OpenPDF 1.3.38 | PdfRapportService | Tour PDF | Basic PDF generation verified; no document analytics implied |
| Apache PDFBox 3.0.2 | CV and RAG loaders | Text extraction | Real extraction, no OCR for scanned PDFs |
| Apache POI 5.2.5 | DocumentLoaderService | DOCX text | Paragraph extraction; table content not covered |
| OkHttp 4.12.0 | GeminiClient + embedding client | Remote text/vision/embedding | Direct REST, not Gemini SDK/Spring AI; error handling frequently hides failure |
| Jackson (Boot managed) + Java time | REST, provider JSON | DTOs/AI parsing | Models not validated adequately after AI parsing; strings/confidence accepted loosely |
| commons-io 2.16.1 | pom dependency | No meaningful source use located | Candidate for later dependency cleanup, not a necessary audit change |
| Spring Boot test + Security test | BACK test source | Context tests | Two pass, one configuration error; no meaningful workflow coverage |
| React / React DOM 19.2.8 resolved | FRONT | Web interface | Uses Context and hooks; no global store needed at current scope |
| Vite 8.2.1 resolved; plugin-react 6.0.4 | FRONT tooling | Build/dev/HMR | Clean Linux install builds; copied node_modules fail on permissions/native binding |
| JavaScript JSX / CSS | FRONT/src | Components/styles | No TS/typecheck script. Build is not type validation |
| react-router-dom 7.18.2 resolved | AppRoutes/MainLayout | Navigation | Works for actual route list; inactive pages omitted; no role-aware guard |
| Axios 1.19.0 resolved | services/api.js | HTTP/JWT | Central 401 redirect; server host is tightly coupled to localhost |
| Leaflet 1.9.4; react-leaflet 5.0.0 | Points and tracking maps | Geographic display | External OSM tiles; real map display, no road-routing engine |
| @stomp/stompjs 7.3.0; sockjs-client 1.6.1 | websocketService | Live tracking | Broadcast receipt verified; same SockJS object reused in reconnect factory is fragile |
| Recharts 3.10.1 resolved | DashboardPage | KPI charts | Rendering works; tonnes series is hardcoded rather than fetched |
| react-hot-toast 2.6.0 | Forms and login | Feedback | Toaster mounted only at login; post-login notifications lack global mount |
| react-icons 5.7.0 | Operational components | Icons | Used; not an AI dependency |
| Oxlint 1.78.0 resolved | FRONT lint script | Static checks | Exit 0 with 34 warnings; unused vars plus hook/fast-refresh warnings; no frontend test script |
| Flutter 3.24.5 / Dart 3.5.4 used in audit | MOBILE; pubspec SDK >=3.5 <4 | Driver app | Web compiles; analyze/test fail on stale MyApp test; Android/iOS runtime unverified |
| Dio ^5.4.0 | ApiService | REST/uploads | 10-second receive/connect timeout conflicts with up-to-60-second backend AI request |
| shared_preferences ^2.2.3 | AuthService/main | Token/onboarding/user persistence | Actual storage; user field contract wrong; token existence treated as session at startup |
| geolocator 13.0.1 | TrackingService/active page | GPS/proximity | Uses device location; permission/background behavior Needs verification |
| image_picker ^1.1.2 | ActiveTrackingPage | Camera/photo | Source integrated; upload backend broken; web File/fromFile path concerns |
| flutter_map ^7.0.2; latlong2 ^0.9.1 | Mobile maps/distance | Tour/location display | Used; tile display is not road navigation |
| stomp_dart_client ^3.0.1 | pubspec only | No live caller found | Unused: mobile sends REST positions |
| flutter_svg, permission_handler (declared) | pubspec | No lib imports found | Likely unused; actual permission calls use geolocator |
| cupertino_icons; flutter_lints 4 | Flutter assets/lints | Icons/checks | Starter tooling; not project tests |
| Android AGP 8.2.1 / Kotlin 1.9.20 / Java 17 / compileSdk 35 | MOBILE/android | Android build | Windows SDK paths in local properties; debug signing configured for release; current artifact provenance unverified |
| Gemini `gemini-1.5-flash` configured | ai.gemini text/vision models | Extraction/chat/images | Placeholder key. Live model availability, response behavior, quota and quality Needs verification |
| Gemini `text-embedding-004` configured | GeminiEmbeddingService | RAG vector representation | Placeholder key triggers local hashing. Live embedding compatibility Needs verification |
| 768-dimensional hashed word vectors | Local fallback implementation | RAG offline search | Deterministic lexical representation, not pretrained semantic embeddings |
| In-Java scan over Mongo chunk arrays | MongoVectorStoreService | Hybrid ranking | No Atlas Vector Search, FAISS, Qdrant, Pinecone or dedicated vector index |
| Regex / text cleanup / Haversine / NN / 2-opt | Java project source | CV parsing and routing | Real deterministic algorithms; not trained ML and not necessarily optimal routing |

### Configuration map

`BACK/src/main/resources/application.properties` sets port 8080, local Mongo URI/database, 20MB multipart limit, relative upload directories, SMTP host/credentials, JWT secret/expiry, Gemini model/base URL/key/60-second timeout, CV thresholds, route depot/speed/fuel and RAG chunk/retrieval options. Secrets are literal values; no secret value is reproduced in these docs. Spring properties can be overridden by CLI/environment without editing source.

Frontend service/API and photo URLs assume localhost:8080. Mobile `core/constants.dart` selects localhost for web and a fixed LAN address (`192.168.1.82`) for devices. Android cleartext HTTP release behavior and iOS camera/location/ATS configuration require device verification. iOS purpose strings are absent in the inspected Info.plist.

### Explicitly absent

No Python ML/NLP library, TensorFlow/PyTorch/scikit-learn, model-serving framework, notebook, trained artifact, Spring AI, LangChain, LLM function-calling framework, external vector DB, Docker or complex infrastructure is implemented. “Agent,” “semantic,” “CVRP,” and “AI” in source names do not establish those capabilities.

## Recommendations

Use clean lockfile installs and a short Java/Node/Flutter/Mongo setup guide. Externalize secrets and configure one reachable backend URL and municipality. Retain the current libraries unless a specific correctness issue justifies change. Document provider/model/fallback provenance; adding a new AI framework would not fix the present logic defects.
