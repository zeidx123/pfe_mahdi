# GestiCollecte — local PFE demonstration setup

This guide runs the existing Java/MongoDB, React and Flutter applications. The web application is for supervisors; the Android application is for active drivers. Keep live-provider/physical verification separate from the explicit simulated-provider integration runner.

## 1. Prerequisites and configuration

Use Java 17/Maven, MongoDB, a Node.js version supported by Vite 8, and Flutter compatible with the project. The verified Flutter environment uses Flutter 3.24.5/Dart 3.5.4. Android builds require SDK 35 and accepted Android SDK licences. A physical Android device on the server's network is needed for camera/GPS/navigation verification.

Run backend commands from the repository's `BACK` directory so default relative storage resolves consistently to `BACK/uploads/{photos,cv,documents}` on Windows and Linux. Upload paths can instead be absolute through Spring configuration. Keep Mongo data and uploads together when retaining a demonstration. The root [README](../README.md) contains complete Windows-first and Linux installation steps.

**Save local secrets in `BACK/.env`.** This project explicitly imports that file when the backend starts from `BACK`. Replace the Gemini and Groq key placeholders, then restart the backend. Assistant generation is independently selected by `RAG_AI_PROVIDER`: Groq uses the configured Qwen text model, while Gemini uses its configured Flash model. The example configuration selects Groq; an omitted setting preserves Gemini for existing installations. Gemini continues to provide document/query embeddings. Qwen through Groq serves photos and CV extraction by default. Set `COLLECTION_AI_PROVIDER=GROQ`, `RAG_AI_PROVIDER=GROQ`, `GROQ_API_KEY`, and both `GROQ_MODEL_TEXT` and `GROQ_MODEL_VISION` to `qwen/qwen3.8-27b`. See the [Groq setup instructions](../README.md#free-hosted-photo-and-cv-analysis-with-groq). OpenRouter is available only when explicitly selected with `COLLECTION_AI_PROVIDER=OPENROUTER`. Use plain `KEY=value` lines without quotes or `export`. The private file is ignored by Git; `BACK/.env.example` contains only placeholders. Generate the JWT signing secret once using the README setup steps, then keep it unchanged across restarts.

After saving the key, restart from `BACK` with `.\mvnw.cmd spring-boot:run` in Windows PowerShell or `./mvnw spring-boot:run` in Linux Bash. For a fresh checkout, follow the README's Windows or Linux steps to copy `.env.example` and generate the JWT secret once. You can still set variables in the launch shell or pass explicit Spring arguments; those override the local file. Vite and Flutter have separate settings and must not contain provider keys.

| Environment variable / Spring setting | Purpose / default |
| --- | --- |
| `JWT_SECRET` | Required base64 signing secret representing at least 32 random bytes. Use a stable value across restarts when resuming existing sessions. |
| `SPRING_DATA_MONGODB_URI` | Mongo URI; use a dedicated demo database/instance. |
| `SPRING_DATA_MONGODB_DATABASE` | Set consistently with the intended database; the checked-in default database name is `gestion_tournees`. |
| `APP_BOOTSTRAP_ENABLED` | Set `true` only for explicit first-supervisor creation. Default is disabled. |
| `APP_BOOTSTRAP_EMAIL`, `APP_BOOTSTRAP_PASSWORD` | Your chosen first supervisor; password at least ten characters. Existing users are never reset by bootstrap. |
| `GEMINI_API_KEY` | Document/query embeddings and optional Gemini assistant generation. Without this key, approved text can still use lexical retrieval. |
| `RAG_AI_PROVIDER` | Assistant generation: `GROQ` or `GEMINI`. The example file selects `GROQ`; the application default when omitted is `GEMINI`. Independent of the collection provider, with no automatic fallback. |
| `GEMINI_MODEL_TEXT` | Configurable chatbot Flash model identifier, used when `RAG_AI_PROVIDER=GEMINI`. Current default: `gemini-3.8-flash`. |
| `COLLECTION_AI_PROVIDER` | Photo/CV provider: `GROQ` by default; `OPENROUTER` is an explicit alternative. No automatic provider fallback. |
| `GROQ_API_KEY` | Backend-only GroqCloud key; keep the account on its free plan. |
| `GROQ_MODEL_TEXT`, `GROQ_MODEL_VISION` | Reviewed assistant/CV and photo models: `qwen/qwen3.8-27b` for both. Groq IDs have no `:free` suffix. |
| `GROQ_TIMEOUT_SECONDS` | Optional shared Groq request timeout, default 60 seconds, bounded to 1–120 seconds. |
| `OPENROUTER_API_KEY` | Backend-only key used only when `COLLECTION_AI_PROVIDER=OPENROUTER`. |
| `OPENROUTER_MODEL_TEXT`, `OPENROUTER_MODEL_VISION` | Alternative CV/photo models, both defaulting to `qwen/qwen3.8-27b:free`; explicit `:free` IDs and zero-price routing are required. |
| `OPENROUTER_TIMEOUT_SECONDS` | Optional shared timeout for the OpenRouter selection, default 60 seconds, bounded to 1–120 seconds. |
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

Keep keys on the backend and use free allowances. There is no automatic paid-provider fallback. Missing ORS configuration prevents a new road calculation, and a tour requires a valid saved route before it can start. Missing configuration for the selected AI provider cannot become a successful generated answer or cleaned assessment.

Groq's current [free-plan rate table](https://console.groq.com/docs/rate-limits) lists 30 requests/minute, 1,000 requests/day, 8,000 tokens/minute and 200,000 tokens/day for this Qwen model. All four ceilings apply together; they do not guarantee a number of photographs per day. [Each image counts as 2,048 input tokens](https://console.groq.com/docs/vision), in addition to prompt/output tokens. Assistant generation on Groq, CV calls and retries share the allowance. The [explicit OpenRouter alternative](../README.md#free-hosted-photo-and-cv-analysis-with-openrouter) has its own shared free-model limits, currently 20 requests/minute and 50 requests/day without purchased credits. Keep image analysis manual while testing the interface, and honor any `Retry-After` response before a manual retry.

SMTP is unnecessary for the PFE workflow. CV acceptance sends no email. The older direct Agents creation path still invokes its existing welcome-email code; for a local demonstration without external email, point SMTP to a local disabled endpoint and do not configure production mail credentials.

## 2. Start a local instance

Follow the [root README](../README.md) to install the tools, start MongoDB and create `BACK/.env`. Use a dedicated demonstration database, a stable JWT secret and your own bootstrap supervisor credentials. After the first supervisor is created, disable bootstrap. Existing users are never reset, and normal startup has no universal login credentials.

Run each application in a separate terminal, starting from the repository root.

Windows PowerShell:

```powershell
Set-Location BACK
.\mvnw.cmd spring-boot:run
```

```powershell
Set-Location FRONT
npm ci
$env:VITE_API_URL = 'http://localhost:8080/api'
npm run dev -- --host 0.0.0.0
```

```powershell
.\scripts\run-mobile.bat
```

Linux Bash:

```bash
cd BACK
./mvnw spring-boot:run
```

```bash
cd FRONT
npm ci
VITE_API_URL=http://localhost:8080/api npm run dev -- --host 0.0.0.0
```

```bash
./scripts/run-mobile.sh
```

The supervisor app is normally on port 5173; the Flutter driver app uses port 8088 and the API uses port 8080. The mobile helper opens Edge on Windows and Chrome on Linux. If a client runs on another device, use the backend's reachable LAN address: `localhost` refers to the client itself.

Create an active driver, available truck and collection points with valid coordinates in React. Driver login accepts email or matricule. Existing onboarding uses the verified CIN as the initial password; do not invent missing identity/licence data. CV acceptance sends no email. For local direct Agents creation, keep SMTP directed to the disabled local endpoint in the example configuration.

## 3. Physical Android verification

From `MOBILE`, run these commands in PowerShell or Bash after connecting a device with USB debugging:

```text
flutter devices
flutter run -d DEVICE_ID --dart-define=API_BASE_URL=http://SERVER_LAN_ADDRESS:8080/api
```

Replace the device identifier and server address. Use `10.0.2.2` for the standard Android emulator; a physical phone needs the computer's LAN address, the same network and access through its firewall. **Configurer le serveur** on the login screen saves an override. Build an APK with:

```text
flutter build apk --debug --dart-define=API_BASE_URL=http://SERVER_LAN_ADDRESS:8080/api
```

The result is `MOBILE/build/app/outputs/flutter-apk/app-debug.apk`. Grant camera/location permissions and sign in as a driver. Google Maps links need no Google API key. Verify background tracking while Maps is open; browser GPS does not establish Android behavior. The current delivery has not completed this physical demonstration.

In Flutter web, the same evidence action selects a JPEG/PNG instead of opening the Android camera. Reloading and reopening a tour should restore confirmed progress and a private retry draft. Protected images are fetched through the authenticated API; a direct unauthenticated upload URL may return 401. See [the mobile README](../MOBILE/README.md) for platform-specific checks.

## 4. Complete the demonstration

1. In web Tours, create a tour with a deliberate manual point order, driver and truck. Optimize it. Inspect manual/NN/NN+2-opt comparisons and the exact small-instance benchmark when available. Confirm road geometry, depot return and separate travel/service estimates.
2. On Android, open the assigned tour and start it. Reopen/resume and verify the original start timestamp and confirmed progress are preserved. Unoptimized tours cannot start.
3. Navigate to the next point, confirm arrival, perform collection and take an AFTER photo. BEFORE is optional. To evaluate paired evidence, deliberately capture both in the correct order.
4. Confirm the saved evidence, then tap **Analyser la photo** to request AI verification. Automatic analysis is off by default to save test requests; enable **Analyse IA automatique** on the tour screen when demonstrating automatic analysis after upload. An unavailable/uncertain assessment must stay labelled as such. The driver can record a human observation and continue; a normal completion still requires the AFTER image.
5. Exercise one blocked/unsafe point as an issue with a reason. Do not force a photograph when unsafe. Retry one failed upload from the saved private draft and confirm the server receives it once the retry succeeds.
6. Progress through all stops, navigate back to the depot and explicitly confirm return/finish. Verify the truck becomes available and tracking stops/stales.
7. In the supervisor tour details, review photos and the independent human label, original AI assessment, total duration and measured service coverage. Export the PDF and route matrix. A human correction must not rewrite the original prediction.
8. In Documents IA, upload readable PDF/DOCX/text, wait for processing, approve the source, then ask the mobile assistant a supported question, an unsupported question and a follow-up. Inspect source excerpts/citations and ownership. Test provider failure: lexical excerpts are allowed; invented procedures are not.
9. In Candidatures, upload a staged/consented PDF/DOCX, inspect extraction evidence, correct/verify fields and compare the backend criterion table. Test partial experience below two years, change a profile version, explicitly re-evaluate, and preserve prior history. Hire only after the required reviewed onboarding fields are available; repeated acceptance must retain one linked agent.

A small live ORS case and a real Groq browser response have been observed; current evidence is in [delivery checks](DELIVERY_CHECKS.md). Physical navigation/GPS/camera and final independently reviewed benchmarks remain acceptance gates. A scripted run cannot substitute for actually driving/walking the route and collecting staged photographs.

If document indexing is interrupted by a backend restart, the document returns to `TEXT_READY` when usable text was saved, or `UPLOADED` otherwise. Read the interruption message and use manual reindex; startup does not call providers automatically. Historical tour photos with no recorded stage are labelled as historical, and missing visit history stays unknown. For an old active tour without a usable route/progress record, review it with the supervisor and plan a new demonstration tour rather than treating the old data as complete evidence.

## Automated checks

Run the Windows or Linux build/check commands in the [root README](../README.md#verification). The isolated HTTP runner needs the built backend JAR, Java 17, Python 3 and `mongod` on `PATH`. It starts disposable storage and deliberately simulated road/AI providers on ports 27019, 18080 and 18888; it refuses occupied ports.

Windows PowerShell:

```powershell
py -3 scripts\verify-staged-workflow.py --output output/staged-workflow.json
```

Linux Bash:

```bash
python3 scripts/verify-staged-workflow.py --output output/staged-workflow.json
```

The runner checks access, transitions, uploads, persistence/resume and explicit provider failures without live inference or external email. Its credentials apply only to its disposable instance. `--serve` keeps that instance running for inspection; `--java`/`--mongod` can select executable paths. Use `--api-port 18280 --mongo-port 27029 --fixture-port 18889` when the default test ports are occupied. Its simulated geometry, AI labels and timings cannot be treated as real provider or field results.

Generated results belong in ignored `output/`. Retain a result under `evaluation/` only when its inputs, provenance and interpretation are documented. See [delivery checks](DELIVERY_CHECKS.md) for completed checks and limitations.

## P1 evaluations

| Experiment | Entry point | Data boundary |
| --- | --- | --- |
| Routing | `scripts/run-routing-benchmark.bat` (Windows) or `scripts/run-routing-benchmark.sh` (Linux), optionally followed by input/output paths | Use saved `/tournees/{id}/route-export` JSON for real road experiments; both bundled fixtures are synthetic. |
| RAG | `python3 scripts/evaluation/evaluate_rag.py --help` | Supervisor-only corpus export and BM25/semantic/hybrid capture, optional generation and separate independent-review aggregation. The older Java lexical seed remains a development test. |
| CV | `python3 scripts/evaluation/evaluate_candidates.py --help` | Reviewed manifest and `GESTI_EVAL_TOKEN`; imports candidates/reviews into the specified demo instance, never hires or emails. |
| Vision | `python3 scripts/evaluation/evaluate_vision.py --help`, then `python3 evaluation/vision/metrics.py results.jsonl` | Supervisor-only batch inference without tour changes; AFTER-only/paired modes, frozen model/prompt, scene-level splits and explicit unreviewed/unavailable handling. |
| Timing | `python3 evaluation/timing/metrics.py completed-tours.json` | Authenticated tour export with frozen estimates and observed timestamps; staged HTTP timing is not field travel. |

The commands in the table use Linux `python3`; use `py -3` in Windows PowerShell. Read each evaluator's README before interpreting results. Suggested sizes remain targets: 50–80 RAG questions, 30–40 reviewed CVs, 30–60 staged image examples/pairs and 15–20 road scenarios. Prefer reviewed, reproducible evidence over arbitrary counts. Optional AI tour summaries and broader polish remain deferred.

Check configured live providers without exposing keys, from the repository root. This consumes live provider allowances. Windows PowerShell:

```powershell
py -3 scripts\check-live-providers.py --vision --output output/provider-check.json
```

Linux Bash:

```bash
python3 scripts/check-live-providers.py --vision --output output/provider-check.json
```

This makes a bounded set of real requests using `BACK/.env` (shell values override it). `text` checks the configured assistant generation provider (`RAG_AI_PROVIDER=GROQ` or `GEMINI`, defaulting to Gemini when omitted); `embedding` always checks Gemini embeddings. Each result names its provider and model. Verify conversational behavior, grounding and citations separately through the authenticated assistant application/API using the [local AI test guide](LOCAL_AI_TEST_GUIDE.md). `cvText` sends a small fictional CV snippet to the selected `COLLECTION_AI_PROVIDER`; `--vision` additionally sends one abstract PNG to that provider. The default is Groq; OpenRouter is used only when explicitly configured. Both checks run independently of Gemini settings and name their provider/model in the report. Groq uses the reviewed `qwen/qwen3.8-27b` model, strict structured output and non-thinking mode; OpenRouter retains explicit `:free` IDs and zero-price routing. Unsupported model/provider selections fail before a request. The probe never retries or falls back to another provider. A small road matrix is checked only when ORS is configured. Missing keys, HTTP failures, provider error envelopes and malformed assessments are reported without secrets. Exit code 2 means at least one check is unavailable, unconfigured or invalid. A successful probe demonstrates transport and a usable response, not photo-classification or CV-extraction accuracy.

For a direct check with an existing JPEG/PNG, `scripts/diagnose-vision.py` uses the selected collection provider, photo model and operational AFTER-only prompt without changing tour history. Optional `--provider GROQ` or `--provider OPENROUTER` and `--model` overrides apply only to that diagnostic, not the application. Groq accepts the reviewed `qwen/qwen3.8-27b`; OpenRouter accepts explicit `:free` IDs. Each supplied image sends one live request; stop after a provider failure and inspect the saved result before deciding on a manual retry.

Routing figures can be generated from an existing CSV. Install Matplotlib in a local Python virtual environment, then use:

```bash
python3 scripts/evaluation/plot_routing.py evaluation/routing/results.csv --output output/routing-comparison.png
```

The figure keeps synthetic and road provenance separate. Current development outputs and their limitations are listed in [P1_RUN_RESULTS.md](evaluation/P1_RUN_RESULTS.md).
