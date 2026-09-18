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
| `scripts` | Portable integration and evaluation helpers |
| `evaluation` | Versioned experiment fixtures and result formats |
| `docs` | Architecture, workflow, evidence and detailed PFE documentation |

Run frontend commands directly in `FRONT`. The nested `FRONT/gestion-tournees-front` directory is an older scaffold and is not the application entry point.

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

Gemini and OpenRouteService are optional for basic startup, but required for live AI and road-routing demonstrations. Obtain keys from [Google AI Studio](https://aistudio.google.com/app/apikey) and [OpenRouteService](https://openrouteservice.org/dev/#/signup). Keep both keys only in `BACK/.env`.

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

1. paste the Gemini and ORS keys after `GEMINI_API_KEY=` and `ORS_API_KEY=`;
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
.\run_mobile.bat
```

The script installs Flutter packages and starts the driver application in Edge on port 8088. To use another backend address:

```powershell
.\run_mobile.bat http://192.168.1.20:8080/api
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

Add the Gemini and ORS keys, set `APP_BOOTSTRAP_ENABLED=true`, and choose the initial supervisor email and a password of at least 10 characters. Start the backend:

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
chmod +x run_mobile.sh
./run_mobile.sh
```

Pass another API address as the first argument when necessary:

```bash
./run_mobile.sh http://192.168.1.20:8080/api
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
- The React API URL comes from `VITE_API_URL` at build/start time.
- The Flutter API URL comes from `--dart-define=API_BASE_URL=...`; a value saved through **Configurer le serveur** takes priority.
- Android emulator networking uses `10.0.2.2`; a physical phone uses the server's LAN address; browser clients use `localhost` when running on the same computer.
- If port 8080, 5173, 8088 or 27017 is already occupied, stop the conflicting process or configure a different port consistently.

For the complete demonstration sequence, evaluation commands and known limitations, see [the detailed setup guide](docs/DEMO_SETUP.md), [implementation progress](docs/IMPLEMENTATION_PROGRESS.md) and [P1 results](docs/evaluation/P1_RUN_RESULTS.md).
