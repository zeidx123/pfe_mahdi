# GestiCollecte supervisor web application

This is the React/Vite application for supervisors. Run commands from this `FRONT` directory.

Requirements: Node.js 22 LTS (or at least 20.19) and the GestiCollecte backend.

Windows PowerShell:

```powershell
npm ci
$env:VITE_API_URL = 'http://localhost:8080/api'
npm run dev -- --host 0.0.0.0
```

Linux Bash:

```bash
npm ci
VITE_API_URL=http://localhost:8080/api npm run dev -- --host 0.0.0.0
```

Verification:

```text
npm run lint
npm run build
```

See the repository [README](../README.md) for MongoDB, backend, Flutter, Android and first-supervisor setup.
