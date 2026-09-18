# GestiCollecte — local PFE demonstration setup

This guide runs the existing Java/MongoDB, React and Flutter applications. The web application is for supervisors; the Android application is for active drivers. Keep live-provider/physical verification separate from the explicit simulated-provider integration runner.

## 1. Prerequisites and configuration

Use Java 17/Maven, MongoDB, a Node.js version supported by Vite 8, and Flutter compatible with the project. The verified Flutter environment uses Flutter 3.24.5/Dart 3.5.4. Android builds require SDK 35 and accepted Android SDK licences. A physical Android device on the server's network is needed for camera/GPS/navigation verification.

Run backend commands from the repository's `BACK` directory so default relative storage resolves consistently to `BACK/uploads/{photos,cv,documents}` on Windows and Linux. Upload paths can instead be absolute through Spring configuration. Keep Mongo data and uploads together when retaining a demonstration. The root [README](../README.md) contains complete Windows-first and Linux installation steps.

**Save local secrets in `BACK/.env`.** This project explicitly imports that file when the backend starts from `BACK`. Replace `VOTRE_CLE_GEMINI_ICI` on its `GEMINI_API_KEY=` line with your Gemini key, then restart the backend. Use plain `KEY=value` lines without quotes or `export`. The private file is ignored by Git; `BACK/.env.example` contains only placeholders. The local file created during setup already includes a generated, stable JWT signing secret. Keep it unchanged across restarts.

Start from `BACK` with `./mvnw spring-boot:run` after saving the key. For a fresh checkout, copy `.env.example` to `.env` and replace its JWT placeholder with a secret generated once using `openssl rand -base64 32`. You can still export variables in the launch shell or pass explicit Spring arguments; those override the local file. Vite and Flutter have separate settings and must not contain provider keys.

| Environment variable / Spring setting | Purpose / default |
| --- | --- |
| `JWT_SECRET` | Required base64 signing secret representing at least 32 random bytes. Use a stable value across restarts when resuming existing sessions. |
| `SPRING_DATA_MONGODB_URI` | Mongo URI; use a dedicated demo database/instance. |
| `SPRING_DATA_MONGODB_DATABASE` | Set consistently with the intended database; the checked-in default database name is `gestion_tournees`. |
| `APP_BOOTSTRAP_ENABLED` | Set `true` only for explicit first-supervisor creation. Default is disabled. |
| `APP_BOOTSTRAP_EMAIL`, `APP_BOOTSTRAP_PASSWORD` | Your chosen first supervisor; password at least ten characters. Existing users are never reset by bootstrap. |
| `GEMINI_API_KEY` | Provider key for live RAG generation/embeddings, CV extraction and vision. No configured key means explicit unavailable/degraded behavior. |
| `GEMINI_MODEL_TEXT`, `GEMINI_MODEL_VISION` | Configurable Flash model identifiers. Current default: `gemini-3.8-flash`; select a stable available model for the actual account at implementation/demo time. |
| `GEMINI_EMBEDDING_MODEL` | Default `gemini-embedding-2`. Model/dimension/version changes require semantic reindexing. |
| `AI_RAG_EMBEDDING_DIMENSIONS`, `AI_RAG_EMBEDDING_VERSION` | Defaults 768 and `text-v1`; remain compatible across indexed corpus and queries. |
| `ORS_API_KEY` | OpenRouteService key for live road matrix/geometry. It is not a Google Maps key. |
| `ORS_BASE_URL`, `ORS_PROFILE` | Defaults `https://api.openrouteservice.org` and `driving-car`. |
| `DEPOT_LATITUDE`, `DEPOT_LONGITUDE` | User-confirmed demonstration depot: **36.722857**, **10.283535**. Override only when deliberately evaluating another depot. |
| `DEPOT_LABEL` | Display name of the depot. |
| `ROUTING_MAX_POINTS`, `STOP_SERVICE_SECONDS` | Configurable demonstration limit, default 20 stops, and default 300 seconds per stop. Exact benchmark is limited to ten stops. |
| `AI_STORAGE_PHOTOS_DIR`, `AI_STORAGE_CV_DIR`, `AI_STORAGE_DOCUMENTS_DIR` | Optional absolute local storage directories. |
| `VITE_API_URL` | React API base; default `http://localhost:8080/api`. |
| Flutter `API_BASE_URL` | Compile-time mobile API base; the saved login-screen server preference takes priority. |

Keep keys on the backend and use free allowances. There is no automatic paid-provider fallback. Missing ORS configuration prevents a new road calculation, and a tour requires a valid saved route before it can start. Missing Gemini configuration cannot become a successful generated answer or cleaned assessment.

SMTP is unnecessary for the PFE workflow. CV acceptance sends no email. The older direct Agents creation path still invokes its existing welcome-email code; for a local demonstration without external email, point SMTP to a local disabled endpoint and do not configure production mail credentials.

## 2. Start the backend and create the first supervisor

In a shell, choose the dedicated database. Keep the stable JWT secret already saved in `BACK/.env` rather than generating a new value on every launch.

```bash
cd BACK
export SPRING_DATA_MONGODB_URI='mongodb://127.0.0.1:27017/gestion_tournees_demo'
export SPRING_DATA_MONGODB_DATABASE='gestion_tournees_demo'
export SPRING_MAIL_HOST='127.0.0.1'
export SPRING_MAIL_PORT='9'
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT='500'
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT='500'
```

Ensure MongoDB is running before launching Spring. For an empty demo database, opt into supervisor bootstrap using credentials you choose:

```bash
export APP_BOOTSTRAP_ENABLED='true'
read -rp 'Supervisor email: ' APP_BOOTSTRAP_EMAIL
read -rsp 'Initial supervisor password: ' APP_BOOTSTRAP_PASSWORD
export APP_BOOTSTRAP_EMAIL APP_BOOTSTRAP_PASSWORD
mvn spring-boot:run
```

On subsequent launches disable bootstrap (`APP_BOOTSTRAP_ENABLED=false`). It does not overwrite an existing account, change existing passwords or reactivate drivers. There is no hardcoded default supervisor login in normal startup.

Set the Gemini and ORS keys in `BACK/.env` (or export them in this launch environment) for the real-provider demonstration. If starting without them, use the resulting clear unavailable states to verify failure handling; do not label that run as live AI/road success.

## 3. Start the supervisor web application

In a separate shell:

```bash
cd FRONT
npm ci
VITE_API_URL=http://localhost:8080/api npm run dev -- --host 0.0.0.0
```

Open the Vite URL and sign in with the supervisor created above. If browsing from another computer, use the backend's reachable address in `VITE_API_URL`; `localhost` would refer to that computer.

Create an active driver, available truck and collection points with valid real coordinates. Driver login accepts email or matricule. The current driver onboarding convention uses the verified CIN as the initial password; do not infer that a production password-management flow was added. Do not use invented identity/licence fields when converting a candidate into a driver.

The normal browser client fetches protected images/files with authentication. Directly pasting an upload URL in a new unauthenticated request can return 401; do not expose files publicly to work around it. Tracking uses STOMP CONNECT `Authorization: Bearer <token>` and a supervisor subscription to `/topic/positions`.

## 4. Run/install the driver app

The debug APK build output is `MOBILE/build/app/outputs/flutter-apk/app-debug.apk`. A successful build is distinct from a verified physical tour. To build/run from source:

```bash
cd MOBILE
flutter pub get
flutter run --dart-define=API_BASE_URL=http://SERVER_LAN_ADDRESS:8080/api
```

Or build an APK with the reachable server address:

```bash
flutter build apk --debug --dart-define=API_BASE_URL=http://SERVER_LAN_ADDRESS:8080/api
```

Replace `SERVER_LAN_ADDRESS` with the actual server address. Android emulator default `10.0.2.2` addresses its host; a physical phone needs the server's LAN address. `localhost` on the phone means the phone. The login screen's **Configurer le serveur** option is useful when an installed APK points to another host; its saved preference overrides the compiled default.

The Android manifest permits HTTP for the LAN demonstration. Grant camera/location permissions, sign in as the active `CHAUFFEUR`, and verify the app can reach the backend. Supervisor accounts belong in the web application. Google Maps navigation links require no Google Maps API key and do not carry application credentials.

For convenient driver-flow testing on this computer, run Flutter in a browser instead:

```bash
cd MOBILE
flutter build web --no-pub --no-web-resources-cdn --dart-define=API_BASE_URL=http://127.0.0.1:8080/api
python3 -m http.server 8088 --bind 127.0.0.1 --directory build/web
```

Open `http://127.0.0.1:8088`. Use port `18080` in `API_BASE_URL` only when targeting the isolated demonstration backend. In the browser, choose a JPEG/PNG file instead of using the Android camera. CanvasKit is served locally to avoid depending on its CDN. Reload returns to the authenticated home screen; reopening a tour resumes confirmed progress and its saved retry draft. Browser GPS may pause in the background and does not validate Android tracking while Maps is open. See [the mobile README](../MOBILE/README.md) for Chromium test commands and platform limitations.

## 5. Complete the actual demonstration

1. In web Tours, create a tour with a deliberate manual point order, driver and truck. Optimize it. Inspect manual/NN/NN+2-opt comparisons and the exact small-instance benchmark when available. Confirm road geometry, depot return and separate travel/service estimates.
2. On Android, open the assigned tour and start it. Reopen/resume and verify the original start timestamp and confirmed progress are preserved. Unoptimized tours cannot start.
3. Navigate to the next point, confirm arrival, perform collection and take an AFTER photo. BEFORE is optional. To evaluate paired evidence, deliberately capture both in the correct order.
4. Confirm the saved evidence and AI assessment. An unavailable/uncertain assessment must stay labelled as such. The driver can record a human observation and continue; a normal completion still requires the AFTER image.
5. Exercise one blocked/unsafe point as an issue with a reason. Do not force a photograph when unsafe. Retry one failed upload from the saved private draft and confirm the server receives it once the retry succeeds.
6. Progress through all stops, navigate back to the depot and explicitly confirm return/finish. Verify the truck becomes available and tracking stops/stales.
7. In the supervisor tour details, review photos and the independent human label, original AI assessment, total duration and measured service coverage. Export the PDF and route matrix. A human correction must not rewrite the original prediction.
8. In Documents IA, upload readable PDF/DOCX/text, wait for processing, approve the source, then ask the mobile assistant a supported question, an unsupported question and a follow-up. Inspect source excerpts/citations and ownership. Test provider failure: lexical excerpts are allowed; invented procedures are not.
9. In Candidatures, upload a staged/consented PDF/DOCX, inspect extraction evidence, correct/verify fields and compare the backend criterion table. Test partial experience below two years, change a profile version, explicitly re-evaluate, and preserve prior history. Hire only after the required reviewed onboarding fields are available; repeated acceptance must retain one linked agent.

Real-road/provider results, physical navigation/GPS/camera, interrupted upload/restart behavior and independent benchmark labels remain the final verification gates. A scripted run cannot substitute for actually driving/walking the route and collecting staged photographs.

If document indexing is interrupted by a backend restart, the document returns to `TEXT_READY` when usable text was saved, or `UPLOADED` otherwise. Read the interruption message and use manual reindex; startup does not call providers automatically. Historical tour photos with no recorded stage are labelled as historical, and missing visit history stays unknown. For an old active tour without a usable route/progress record, review it with the supervisor and plan a new demonstration tour rather than treating the old data as complete evidence.

## 6. Automated checks and explicit simulated-provider run

From the repository root:

```bash
mvn -f BACK/pom.xml clean package
npm --prefix FRONT run build
npm --prefix FRONT run lint
```

From `MOBILE`:

```bash
flutter analyze --no-pub
flutter test
flutter build apk --debug --no-pub
```

The isolated integration runner starts disposable MongoDB/uploads and explicit local ORS/Gemini fixture responses on ports 27019, 18080 and 18888:

```bash
python3 scripts/verify-staged-workflow.py
```

Build the backend JAR first and have Java 17 and `mongod` available on `PATH`. The runner refuses occupied ports. It records `docs/evidence/implementation-staged-workflow.json`, performs no real road/model inference, and uses disabled local SMTP. Its fixed credentials belong only to its newly created disposable test instance; they are not normal application defaults. `--serve` keeps that staged backend available for browser inspection. Use `--java` or `--mongod` to provide an explicit executable path when either tool is not on `PATH`.

To keep an existing demonstration running, select separate ports and an output file:

```bash
python3 scripts/verify-staged-workflow.py --api-port 18280 --mongo-port 27029 --fixture-port 18889 --output docs/evidence/p1-staged-workflow.json
```

This run checks HTTP integration, access control, persistence/resume and honest failure states. Its route geometry, predictions and elapsed times must remain labelled simulated in the PFE report.

## 7. Run P1 evaluations

| Experiment | Entry point | Data boundary |
| --- | --- | --- |
| Routing | `scripts/run-routing-benchmark.sh [fixtures-directory] [results.csv]` | Use saved `/tournees/{id}/route-export` JSON for real road experiments; both bundled fixtures are synthetic. |
| RAG | `python3 scripts/evaluation/evaluate_rag.py --help` | Supervisor-only corpus export and BM25/semantic/hybrid capture, optional generation and separate independent-review aggregation. The older Java lexical seed remains a development test. |
| CV | `python3 scripts/evaluation/evaluate_candidates.py --help` | Reviewed manifest and `GESTI_EVAL_TOKEN`; imports candidates/reviews into the specified demo instance, never hires or emails. |
| Vision | `python3 scripts/evaluation/evaluate_vision.py --help`, then `python3 evaluation/vision/metrics.py results.jsonl` | Supervisor-only batch inference without tour changes; AFTER-only/paired modes, frozen model/prompt, scene-level splits and explicit unreviewed/unavailable handling. |
| Timing | `python3 evaluation/timing/metrics.py completed-tours.json` | Authenticated tour export with frozen estimates and observed timestamps; staged HTTP timing is not field travel. |

Read each evaluator's README before interpreting results. Suggested sizes remain targets: 50–80 RAG questions, 30–40 reviewed CVs, 30–60 staged image examples/pairs and 15–20 road scenarios. Prefer reviewed, reproducible evidence over arbitrary counts. Optional AI tour summaries and broader polish remain deferred.

Check configured live providers without exposing keys:

```bash
python3 scripts/check-live-providers.py --vision --output /tmp/gesticollecte-provider-check.json
```

This makes a bounded set of real requests using `BACK/.env` (shell values override it). It sends only a fictional text prompt and optional abstract PNG, checks a small road matrix only when ORS is configured, does not retry or switch providers, and reports missing/unavailable results explicitly. Exit code 2 means at least one check is unavailable, unconfigured or invalid. A successful probe is not an AI accuracy result.

Routing figures can be generated from an existing CSV. Install Matplotlib in a local Python virtual environment, then use:

```bash
python3 scripts/evaluation/plot_routing.py evaluation/routing/results.csv --output /tmp/routing-comparison.png
```

The figure keeps synthetic and road provenance separate. Current development outputs and their limitations are listed in [P1_RUN_RESULTS.md](evaluation/P1_RUN_RESULTS.md).
