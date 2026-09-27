# GestiCollecte

GestiCollecte is a PFE collection-management system with three applications:

- a Spring Boot and MongoDB backend;
- a React/Vite web application for supervisors;
- a Flutter application for drivers, available on Android and in a web browser.

The demonstrated workflow covers tour planning, road optimization through OpenRouteService, driver execution and navigation, photo evidence with AI-assisted review, grounded RAG, candidate matching and estimated-versus-observed duration analysis.

## Repository layout

| Directory | Purpose |
| --- | --- |
| `BACK` | Active Spring Boot API and backend tests |
| `FRONT` | Active React/Vite supervisor application |
| `MOBILE` | Active Flutter driver application |
| `scripts` | Windows/Linux launchers, provider diagnostics and reproducible test/evaluation runners |
| `evaluation` | Fictional or attributed development inputs, saved road matrices, evaluation methods and selected results |
| `docs` | Current architecture, demonstration steps, delivery checks and concise PFE evidence |
| `local-data/cvs` | Your private CVs, ignored by Git; create this folder locally when needed |

Run frontend commands directly in `FRONT`. The old nested starter application has been removed.

`evaluation/` is retained because the PFE must compare algorithms and review AI results reproducibly. It contains small, labelled development examples and saved inputs; it is not required for normal application startup and does not claim a completed accuracy benchmark. Fictional CV fixtures live in `evaluation/candidates/fixtures/`. Private applicant CVs belong in `local-data/cvs/`, not the repository root or a tracked evaluation dataset.

The [scripts guide](scripts/README.md) explains the commands that launch Flutter, check providers and repeat experiments. The [evaluation guide](evaluation/README.md) explains the retained datasets and results. Generated builds, local uploads, private files and temporary `output/` results are ignored. Selected results are retained only when they support a documented experiment. See the [documentation index](docs/README.md) and [delivery checks](docs/DELIVERY_CHECKS.md) for the current status.

## Requirements

Install these tools before starting:

| Tool | Supported/recommended version | Used for |
| --- | --- | --- |
| Git | Current stable | Clone the repository |
| Java JDK | **17** | Spring Boot backend |
| Node.js | **22 LTS**; at least 20.19 | React/Vite web application |
| MongoDB | 7 or 8, or Docker Desktop | Application database |
| Flutter | 3.24.5 with Dart 3.5.4, or a compatible release | Driver application |
| Android Studio/SDK | SDK 35 | Android APK/device run |
| Edge or Chrome | Current stable | Flutter web run |
| Python | 3.10+ | Optional evaluation and local web-serving tools |

Provider keys are optional for basic startup. Chatbot generation selects Groq or Gemini independently; the example configuration uses Groq. Document/query embeddings remain on Gemini. Photo analysis and CV extraction use hosted Qwen through Groq's free plan by default; road routing uses OpenRouteService. Obtain keys from [Google AI Studio](https://aistudio.google.com/app/apikey), [GroqCloud](https://console.groq.com/keys) and [OpenRouteService](https://openrouteservice.org/dev/#/signup). OpenRouter remains an explicitly selectable alternative. Keep all provider keys only in `BACK/.env`.

Official installers: [Git](https://git-scm.com/downloads), [Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17), [Node.js](https://nodejs.org/en/download), [Docker Desktop](https://www.docker.com/products/docker-desktop/), [Flutter](https://docs.flutter.dev/get-started/install), and [Android Studio](https://developer.android.com/studio).

## Windows installation and startup

The commands below use PowerShell. Open separate PowerShell windows for MongoDB, the backend, the supervisor web application and the driver application.

### 1. Clone and verify the tools

```powershell
git clone https://github.com/GitHackerz/pfe_mahdi.git
Set-Location pfe_mahdi
java -version
node --version
npm --version
flutter doctor
py -3 --version
```

`java -version` must report Java 17. Resolve any required Android licences reported by `flutter doctor`:

```powershell
flutter doctor --android-licenses
```

### 2. Start MongoDB

With Docker Desktop installed and running:

```powershell
docker run --name gesticollecte-mongo -d -p 27017:27017 -v gesticollecte-mongo-data:/data/db mongo:7
```

On later starts, use `docker start gesticollecte-mongo`. If MongoDB was installed natively instead, start its Windows service and confirm that it listens on `localhost:27017`.

### 3. Configure and start the backend

```powershell
Set-Location BACK
Copy-Item .env.example .env
$bytes = New-Object byte[] 64
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$secret = [Convert]::ToBase64String($bytes)
(Get-Content .env -Raw).Replace('REPLACE_WITH_A_RANDOM_BASE64_SECRET', $secret) | Set-Content .env -Encoding ascii
notepad .env
```

In `.env`:

1. paste the Gemini and ORS keys after `GEMINI_API_KEY=` and `ORS_API_KEY=`; for the assistant, photos and CV extraction, also follow [Groq configuration](#free-hosted-photo-and-cv-analysis-with-groq);
2. set `APP_BOOTSTRAP_ENABLED=true` for the first startup;
3. replace the example supervisor email and password; the password needs at least 10 characters.

Use plain `KEY=value` lines without quotes. Then start Spring Boot:

```powershell
.\mvnw.cmd spring-boot:run
```

The API is available at `http://localhost:8080/api`. After the first supervisor is created, stop the backend with `Ctrl+C`, set `APP_BOOTSTRAP_ENABLED=false` in `.env`, and start it again. Existing accounts are never reset by bootstrap.

### 4. Start the supervisor web application

In another PowerShell window from the repository root:

```powershell
Set-Location FRONT
npm ci
$env:VITE_API_URL = 'http://localhost:8080/api'
npm run dev -- --host 0.0.0.0
```

Open the URL printed by Vite, normally `http://localhost:5173`, and sign in with the supervisor created above.

### 5. Start the driver application in Edge

From the repository root:

```powershell
.\scripts\run-mobile.bat
```

The script installs Flutter packages and starts the driver application in Edge on port 8088. To use another backend address:

```powershell
.\scripts\run-mobile.bat http://192.168.1.20:8080/api
```

### 6. Run on Android

Start an Android emulator or connect a device with USB debugging, then check its identifier:

```powershell
Set-Location MOBILE
flutter devices
flutter run -d DEVICE_ID --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

`10.0.2.2` reaches the host computer from the standard Android emulator. For a physical phone, use the computer's LAN address from `ipconfig`, keep the phone and computer on the same network, and allow inbound TCP port 8080 in Windows Firewall. The login screen's **Configurer le serveur** option can change and save the API address after installation.

Build an installable debug APK with:

```powershell
flutter build apk --debug --dart-define=API_BASE_URL=http://YOUR_COMPUTER_LAN_IP:8080/api
```

The APK is written to `MOBILE\build\app\outputs\flutter-apk\app-debug.apk`.

## Linux installation and startup

The commands below target a normal Bash shell. Package names can differ between distributions.

### 1. Clone and verify the tools

Install Git, JDK 17, Node.js 22, Flutter, Python 3 and either MongoDB or Docker using the official documentation for your distribution. Then run:

```bash
git clone https://github.com/GitHackerz/pfe_mahdi.git
cd pfe_mahdi
java -version
node --version
npm --version
flutter doctor
python3 --version
flutter doctor --android-licenses
```

### 2. Start MongoDB

With Docker installed and running:

```bash
docker run --name gesticollecte-mongo -d -p 27017:27017 -v gesticollecte-mongo-data:/data/db mongo:7
```

Use `docker start gesticollecte-mongo` on later starts. For a native MongoDB installation, start its service and confirm that it listens on `localhost:27017`.

### 3. Configure and start the backend

```bash
cd BACK
cp .env.example .env
sed -i "s|REPLACE_WITH_A_RANDOM_BASE64_SECRET|$(openssl rand -base64 48)|" .env
${EDITOR:-nano} .env
```

Add the Gemini and ORS keys and, for the assistant, photos and CV extraction, follow [Groq configuration](#free-hosted-photo-and-cv-analysis-with-groq). Set `APP_BOOTSTRAP_ENABLED=true` and choose the initial supervisor email and a password of at least 10 characters. Start the backend:

```bash
./mvnw spring-boot:run
```

After the first supervisor is created, stop the backend, set `APP_BOOTSTRAP_ENABLED=false`, and restart it. The API is `http://localhost:8080/api`.

### 4. Start the supervisor web application

In another terminal from the repository root:

```bash
cd FRONT
npm ci
VITE_API_URL=http://localhost:8080/api npm run dev -- --host 0.0.0.0
```

Open the Vite URL, normally `http://localhost:5173`.

### 5. Start the driver application in Chrome

From the repository root:

```bash
chmod +x scripts/run-mobile.sh
./scripts/run-mobile.sh
```

Pass another API address as the first argument when necessary:

```bash
./scripts/run-mobile.sh http://192.168.1.20:8080/api
```

### 6. Run on Android

```bash
cd MOBILE
flutter devices
flutter run -d DEVICE_ID --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

Use the Linux computer's LAN address for a physical phone. Build the APK with:

```bash
flutter build apk --debug --dart-define=API_BASE_URL=http://YOUR_COMPUTER_LAN_IP:8080/api
```

## Ports and application roles

| Address | Purpose |
| --- | --- |
| `localhost:27017` | MongoDB |
| `localhost:8080/api` | Spring Boot API |
| `localhost:5173` | React supervisor application |
| `localhost:8088` | Flutter driver application when using the helper scripts |

Supervisors create drivers, trucks, points and tours in React. Drivers sign in through Flutter to start/resume tours, navigate, record visits and photos, and use the grounded assistant. Google Maps links do not need a Google Maps API key; ORS is used by the backend to calculate the planned road matrix and route geometry.

## Verification

Run these checks before demonstrating or submitting changes.

Windows PowerShell:

```powershell
Set-Location BACK
.\mvnw.cmd clean package
Set-Location ../FRONT
npm ci
npm run lint
npm run build
Set-Location ../MOBILE
flutter pub get
flutter analyze
flutter test
flutter build web --no-web-resources-cdn
flutter build apk --debug
```

Linux Bash:

```bash
cd BACK && ./mvnw clean package && cd ..
cd FRONT && npm ci && npm run lint && npm run build && cd ..
cd MOBILE && flutter pub get && flutter analyze && flutter test
flutter build web --no-web-resources-cdn
flutter build apk --debug
```

The optional isolated HTTP workflow requires Java 17, Python 3 and `mongod` on `PATH`. Build the backend first, then run from the repository root:

```text
Windows: py -3 scripts\verify-staged-workflow.py
Linux:  python3 scripts/verify-staged-workflow.py
```

The script uses disposable ports and storage and does not call live AI or road providers. Routing experiment helpers are available as `scripts\run-routing-benchmark.bat` on Windows and `scripts/run-routing-benchmark.sh` on Linux.

## Configuration notes

- `BACK/.env` is ignored by Git. Never commit API keys, JWT secrets or mail credentials.
- Start the backend from the `BACK` directory so `.env` and relative upload folders resolve there.
- `COLLECTION_AI_PROVIDER` selects photo analysis and CV extraction: `GROQ` by default, or `OPENROUTER` when explicitly configured. Groq uses `GROQ_MODEL_VISION` and `GROQ_MODEL_TEXT`; the alternative uses `OPENROUTER_MODEL_VISION` and `OPENROUTER_MODEL_TEXT`. `RAG_AI_PROVIDER` independently selects assistant generation: `GROQ` uses `GROQ_MODEL_TEXT`, while `GEMINI` uses `GEMINI_MODEL_TEXT`. The example `.env` selects Groq; omitting the setting preserves Gemini for existing installations. Document/query embeddings use `GEMINI_EMBEDDING_MODEL`. Restart the backend to apply changes. Changing either generation provider does not require reindexing RAG documents.
- Gemini allowances apply to semantic retrieval/indexing and to chatbot generation when `RAG_AI_PROVIDER=GEMINI`. Check your project/model/tier limits in [Google AI Studio](https://aistudio.google.com/rate-limit); daily request quotas reset at midnight Pacific time. Groq generation, CV extraction and photos share the Groq model allowance; OpenRouter has its own photo/CV allowance, described below. HTTP 429 means quota/rate rejection, not a valid AI assessment.
- The React API URL comes from `VITE_API_URL` at build/start time.
- The Flutter API URL comes from `--dart-define=API_BASE_URL=...`; a value saved through **Configurer le serveur** takes priority.
- Android emulator networking uses `10.0.2.2`; a physical phone uses the server's LAN address; browser clients use `localhost` when running on the same computer.
- If port 8080, 5173, 8088 or 27017 is already occupied, stop the conflicting process or configure a different port consistently.

## Free hosted photo and CV analysis with Groq

Create a [GroqCloud API key](https://console.groq.com/keys) and keep the account on the free plan. No local model or billing setup is needed for this configuration. Edit the existing `BACK/.env`, replacing the placeholder with your key:

```dotenv
COLLECTION_AI_PROVIDER=GROQ
RAG_AI_PROVIDER=GROQ
GROQ_API_KEY=VOTRE_CLE_GROQ_ICI
GROQ_MODEL_TEXT=qwen/qwen3.8-27b
GROQ_MODEL_VISION=qwen/qwen3.8-27b
GROQ_TIMEOUT_SECONDS=60
```

The shared timeout setting is optional and bounded to 1–120 seconds. Groq model IDs do not use the OpenRouter `:free` suffix; the Groq account plan governs the allowance. Qwen handles the assistant, photo analysis and CV extraction with these settings. Keep Gemini configured for document/query embeddings. To choose Gemini generation instead, set `RAG_AI_PROVIDER=GEMINI`; changing this setting never switches the embedding model or collection provider. Candidate suitability scoring remains the deterministic backend rubric, with a human hiring decision. Keys belong in the ignored backend file, never in React, Flutter, browser storage or committed files.

These requests disable extended reasoning and cap output at 1,024 tokens for photos and 4,096 for CVs and assistant responses. This avoids the selected model's default extended-thinking workload, but does not remove provider capacity limits. A timed-out or truncated response remains unavailable and can be retried manually.

The current [Groq free-plan rate table](https://console.groq.com/docs/rate-limits) lists these ceilings for `qwen/qwen3.8-27b`:

| Requests per minute | Requests per day | Tokens per minute | Tokens per day |
| ---: | ---: | ---: | ---: |
| 30 | 1,000 | 8,000 | 200,000 |

All four limits apply together; these are published ceilings, not a guaranteed number of photos per day. [Each image counts as 2,048 input tokens](https://console.groq.com/docs/vision), plus the prompt and generated response. Assistant generation, CV extraction and retries also consume the model's allowance. An AFTER-only analysis uses one request and one image; a BEFORE/AFTER pair uses one request and two images. Check your account's current limits and honor `Retry-After` when a request is rate-limited. No provider switch occurs automatically.

### Save AI requests while testing

The driver app defaults to **manual image analysis**. Uploading an AFTER photo saves the evidence without calling an AI provider. Tap **Analyser la photo** only when you want a real assessment, or enable **Analyse IA automatique** on the current tour screen. To change the initial switch value for a Flutter build, add `--dart-define=AUTO_VISION_ANALYSIS=true`; it defaults to `false` on both web and Android.

A valid saved visit assessment is shown without another analysis button. The backend also reuses that visit's original result when its photo references, provider, model and prompt version are unchanged. Replacing evidence clears the result; an unavailable assessment can still be retried manually. Concurrent requests for the same visit are rejected while the first is running. This guard is local to the single backend process used by the PFE, not a distributed queue. Supervisor evaluation requests always run a fresh assessment so experiment results are not silently reused.

For example, uploading ten photos and explicitly analyzing two sends two image-analysis requests. Use AFTER-only evidence for ordinary tests; a BEFORE/AFTER pair still uses one request but contains two image inputs. Image compression reduces upload size, not request count or Groq's fixed per-image token count. Existing CV field review and score recalculation do not call an AI model. No prediction is invented when analysis is skipped.

Stop and restart the backend from `BACK` after editing: `.\mvnw.cmd spring-boot:run` in Windows PowerShell, or `./mvnw spring-boot:run` in Linux Bash. The Windows and Linux setup sections above cover the remaining services.

### Free hosted photo and CV analysis with OpenRouter

OpenRouter is an explicit alternative, not an automatic fallback. To select it, create an [OpenRouter API key](https://openrouter.ai/settings/keys), replace these values in `BACK/.env`, and restart the backend:

```dotenv
COLLECTION_AI_PROVIDER=OPENROUTER
OPENROUTER_API_KEY=VOTRE_CLE_OPENROUTER_ICI
OPENROUTER_MODEL_TEXT=qwen/qwen3.8-27b:free
OPENROUTER_MODEL_VISION=qwen/qwen3.8-27b:free
OPENROUTER_TIMEOUT_SECONDS=60
```

Keep both `:free` suffixes. The OpenRouter adapter rejects paid/automatic model IDs and sets zero-price routing caps. OpenRouter currently allows a zero-credit account **50 free-model requests per day in total and up to 20 requests per minute**, shared across free models; see [official limits](https://openrouter.ai/docs/api/reference/limits). CV extraction, image analysis and retries share that allowance. Provider capacity and token limits can restrict usage further. Credit usage of zero does not establish how many free requests remain.

Neither selection falls back automatically to another provider, paid model or Gemini. Photo failures remain `ANALYSIS_UNAVAILABLE`; failed CV extraction stays explicit and available for manual review.

Verify live Qwen responses on your installation with your real key; a configured model name or a passing integration test does not establish classification or extraction accuracy. Follow the [CV and photo test steps](docs/LOCAL_AI_TEST_GUIDE.md) to verify the results in the application.

For the complete demonstration sequence, evaluation commands and known limitations, see [the detailed setup guide](docs/DEMO_SETUP.md), [delivery checks](docs/DELIVERY_CHECKS.md) and [P1 results](docs/evaluation/P1_RUN_RESULTS.md).

To test the chatbot, local CVs and image uploads in Flutter web, follow [the local AI test guide](docs/LOCAL_AI_TEST_GUIDE.md). It identifies the prepared documents and public photos, expected checks, and the difference between successful AI output and an unavailable provider.
