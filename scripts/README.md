# Development and evaluation tools

These tools support local development, regression checks and the PFE experiments. The deployed Spring, React and Flutter applications do not load this directory at runtime. Keep it in the source delivery: it makes the checks and experiments reproducible.

Run commands from the repository root. Use `python3` on Linux or `py -3` on Windows. Except for the optional routing plot, the Python tools use the standard library.

## Start the driver browser application

```sh
# Linux: Chrome, port 8088
scripts/run-mobile.sh http://127.0.0.1:8080/api
```

```bat
rem Windows: Microsoft Edge, port 8088
scripts\run-mobile.bat http://127.0.0.1:8080/api
```

The wrappers run `flutter pub get` and launch Flutter with the selected API URL. Start the backend separately using [the setup guide](../docs/DEMO_SETUP.md). They do not start MongoDB or the supervisor web application.

## Verification and diagnostics

| Tool | Purpose | External effects |
| --- | --- | --- |
| `verify-staged-workflow.py` | Reproduce authentication, uploads, tour progress, restart/resume, document access and candidate review against a disposable backend/database. Requires a built backend JAR, Java 17 and `mongod`. | Starts temporary local processes and writes a report. AI and road responses are simulated; occupied ports are rejected. |
| `check-live-providers.py` | Small synthetic checks for Gemini RAG/embeddings, the configured collection AI provider and ORS; `--vision` adds an abstract image. | Makes live provider requests when credentials are configured. Use explicitly, not in offline CI. |
| `diagnose-vision.py` | Check selected JPEG/PNG images against the configured collection provider and the application's AFTER-only prompt. | Sends the supplied images to that provider; does not modify tour history. |
| `test_check_live_providers.py` | Provider selection, credential isolation, request schemas and failure behavior. | Mocked HTTP only; no credentials or provider calls required. |

Live collection-provider tools use `COLLECTION_AI_PROVIDER` (`GROQ` by default, or explicit `OPENROUTER`) without automatic fallback. Credentials stay in local environment configuration, never in saved reports or source files. Use `--help` for each script's current options and choose a local output file for new diagnostics.

## PFE experiments

| Tool | Input and output |
| --- | --- |
| `run-routing-benchmark.py`, `.sh`, `.bat` | Saved directed matrices → production Java MANUAL/NN/NN+2-opt/exact comparisons. The shell/batch wrappers call the same Python runner and Maven wrapper. No road-provider calls during replay. |
| `evaluation/evaluate_rag.py` | Approved corpus capture, three retrieval modes, optional generation, independent review templates and metrics. Capture needs a supervisor token; summarization is offline. |
| `evaluation/evaluate_candidates.py` | Fictional or reviewed CV manifest → extraction/rubric metrics. A live run imports candidates and may submit supplied reviews to the chosen demo backend; it never hires or emails. |
| `evaluation/evaluate_vision.py` | Staged scene manifest → AFTER-only/paired results. Live runs create standalone analyses; `--validate-only` performs no network calls. |
| `evaluation/plot_routing.py` | Routing CSV → PNG/SVG comparison. Requires optional `matplotlib`; the numerical routing runner does not. |

See [evaluation/README.md](../evaluation/README.md) for fixtures, retained results, provenance and the area-specific instructions. Candidate fixtures now live in `evaluation/candidates/fixtures/`; the evaluator tests and backend fallback tests read them there.

## Offline Python checks

```sh
python3 -m unittest discover -s scripts -p 'test_check_live_providers.py'
python3 -m unittest discover -s scripts/evaluation -p 'test_*.py'
python3 -m unittest discover -s evaluation/rag -p 'test_*.py'
python3 -m unittest discover -s evaluation/vision -p 'test_*.py'
python3 -m unittest discover -s evaluation/timing -p 'test_*.py'
```

These suites use mocked services and synthetic fixtures. They do not establish live-provider availability or empirical AI accuracy. Python bytecode, temporary databases, generated builds, credentials and local diagnostic retries are not source-delivery material.
