# Repository map

## Current state

No `.git` checkout metadata was found at the repository root during the audit; there is no commit identifier to attach to the findings. [The file inventory](evidence/repository-inventory.csv) records paths, sizes and hashes, excluding generated dependency/build/cache trees. Application source hashes were separately checked after the audit.

| Path | Responsibility / audit interpretation |
| --- | --- |
| `BACK/pom.xml`, `mvnw*`, `.mvn/` | Maven Spring Boot project and wrapper |
| `BACK/src/main/java/com/municipalite/gestiontournees/` | Actual backend application and all domain/AI modules |
| `BACK/src/main/resources/application.properties` | Mongo, SMTP, JWT, upload, Gemini, route and RAG configuration; contains literal secrets, not reproduced here |
| `BACK/src/test/` | Two context tests plus user diagnostic; one test has stale package configuration |
| `BACK/uploads/` | Existing uploaded/reference files and packaged APK; not automatically indexed merely because files exist |
| `BACK/target/` | Generated classes/JAR/test reports; not source of truth |
| `BACK/HELP.md` | Generated getting-started links; includes Spring AI/other version references not implemented by pom |
| `BACK/CvAnalysisService_backup.java` | Misleading name: UTF-16 PowerShell `git show` failure transcript, not a usable Java backup |
| `FRONT/package.json`, lockfile, `vite.config.*`, lint config | React/Vite application tooling |
| `FRONT/src/main.jsx`, `routes/`, `layouts/`, `context/`, `hooks/` | Actual app entry, navigation, authentication and shell |
| `FRONT/src/pages/` | Operational management screens and inactive AI/alert/report pages |
| `FRONT/src/services/` | Axios wrappers and SockJS/STOMP client |
| `FRONT/src/utils/constants.js` | Shared client constants |
| `FRONT/src/assets/`, `public/` | Branding, hero and starter artwork; not analytics/training data |
| `FRONT/src/App.jsx`, `App.css` | Unmounted Vite counter starter |
| `FRONT/gestion-tournees-front/` | Nested second starter React app; no connection to active frontend found |
| `FRONT/simulateur_gps.js` | Explicit development GPS simulator; currently ESM/CommonJS and authentication problems |
| `FRONT/node_modules`, `dist` | Copied/generated artifacts; original dependency executables/native bindings are not portable to this Linux runtime |
| `MOBILE/pubspec.yaml`, lockfile | Flutter application dependencies |
| `MOBILE/lib/` | Entry, pages, REST/auth/tracking services, tour card |
| `MOBILE/android`, `ios`, `web`, desktop platform folders | Flutter target scaffolds; Android has project-specific SDK/configuration; platform templates are not tested deployments |
| `MOBILE/test/widget_test.dart` | Unupdated Flutter counter test referring to nonexistent MyApp |
| Root `*.pdf` | 13 CV samples; curated examples, not a labeled representative dataset |
| Root `*.apk` and upload APK | Existing packaged artifacts; source/build provenance not established |
| `graphify-out/` | Existing derived graph/report and PDF knowledge material used for navigation; source verification takes precedence |
| `docs/` | This audit knowledge base and sanitized evidence |

### Backend package map

| Package under B | Files and responsibility |
| --- | --- |
| `auth`, `security`, `user` | Login/profile DTOs, JWT filter/provider/config, UserDetails and repositories |
| `agent` | Driver CRUD, CIN password reset and SMTP welcome messages; driver data lives in users |
| `camion`, `pointcollecte`, `tournee` | Resource DTOs/controllers/services/repositories; tour module also has integrated route engine |
| `tracking` | Position DTO and REST/STOMP controller; no repository |
| `dashboard`, `alerte` | Count aggregation and persistent sample alert list/resolve |
| `reclamation`, `rapport` | Text incident/reply persistence and generated PDF |
| `config`, `exception` | CORS/uploads/WebSocket setup, global errors |
| `ai/common`, `ai/config` | Shared OkHttp Gemini client, properties and beans |
| `ai/cv` | PDF extraction, very large extraction/cleanup/scoring/decision service and Candidate persistence |
| `ai/route` | Separate NN/2-opt proposal engine, history and status updates |
| `ai/vision` | Gemini image prompt, fallback, image analysis and AI problem records |
| `ai/assistant` | Supervisor chat with DB-context helper, separate chat history |
| `ai/rag` | Document ingestion/chunking, Gemini or hashed embeddings, Java vector scan, source DTOs, Q&A/history |

### Frontend/mobile reachability

Active web pages: login, dashboard, agents, camions, points, tournees, tracking, reclamations, candidatures, documents-ia. `pages/assistant/AssistantPage.jsx`, `pages/analyses/AnalysePhotosPage.jsx`, `pages/alertes/AlertePage.jsx`, `pages/rapports/RapportPage.jsx` are not imported into the active router. PDF remains reachable from tour details.

Mobile pages: onboarding, login, home, tour detail, active tracking, reclamations and assistant. Active tracking is navigated programmatically; REST wrappers are thin and transport untyped JSON maps. The declared STOMP package is not used by the mobile tracking service; mobile posts REST positions instead.

### Search coverage and absent components

Searches covered manifests, Java/JS/JSX/Dart sources, tests, platform files, scripts, configuration and sample artifacts, excluding dependencies/build outputs. No Python application, notebook, training pipeline, trained model weight, SQL migration, Dockerfile/Compose stack, CI workflow or ESP32 firmware was found. No actual Spring AI, LangChain or ML framework integration was found. Existing APKs were inventoried, not decompiled or assumed to match source.

## Problems

Starter files, a nested duplicate frontend, copied platform-specific dependencies, obsolete tests, mismatched help text and a fake-named backup obscure the executable project. Multiple large services/pages make behavior hard to trace, particularly candidate scoring and mobile execution.

## Recommendations

After functional fixes, document one startup path and archive demonstrably unused starters with explicit approval. Preserve useful samples but classify them and document provenance. No code or artifact was removed in this audit.
