# Browser and mobile implementation verification

Date: 2026-09-17. The browser checks below used the isolated staged backend on port 18080 and React development server on port 5173. The backend used a temporary MongoDB database and simulated mapping/AI providers. These checks establish integration behavior, not real road-provider accuracy, AI accuracy, or physical Android behavior.

## Flutter checks

Executed from the repository's `MOBILE` directory with Flutter 3.24.5 and Dart 3.5.4:

| Check | Observed result |
|---|---|
| `flutter pub get` | Passed. Added `path_provider` and `url_launcher` with compatible platform dependencies. Existing `vm_service: 14.2.5` change was preserved. |
| `flutter analyze --no-pub` | Passed with no issues. |
| `flutter test` | All 9 tests passed. |
| `flutter build web --no-pub` | Passed. |
| `flutter build apk --debug --no-pub` | Passed. APK: `MOBILE/build/app/outputs/flutter-apk/app-debug.apk`. |
| `flutter devices` | Only Linux desktop was detected. No Android phone or emulator was available. |

The Android build initially exposed two environment/project defects: a truncated cached Gradle 8.5 distribution ZIP and a Windows-only `org.gradle.java.home` override. The official distribution download was resumed and its ZIP integrity checked; the tracked Java-home override was removed so the installed Java 17 can be used. Gradle installed required Android SDK packages using licences already accepted in the SDK installation, then built the APK successfully.

The regression tests cover:

1. Flat Spring authentication response, saved driver identity, session restoration and unmodified password input.
2. Rejection of supervisor login in the driver app and clearing corrupt persisted sessions.
3. Preserving the session after HTTP 403 and expiring it after HTTP 401.
4. Protected photo URL normalization, including bare backend filenames, and rejection of another origin.
5. Start, stop completion and depot-return request contracts.
6. Reusing saved image bytes with explicit JPEG MIME and AFTER stage on upload retry.
7. Resuming persisted ordered visits, treating issue reports as handled, excluding an already-reached leg from remaining travel, and navigating to the depot last.
8. No normal-completion control for an arrived visit without AFTER evidence; issue reporting remains available.
9. Unauthenticated app startup reaches the driver login with server configuration controls.

## React checks

`npm run build` passed. `npm run lint` completed with existing warnings outside the tour components and protected-image component changed in this work. The build still reports the existing large application bundle; bundle restructuring was not added to this PFE milestone.

The following behaviors were verified through the Codex in-app browser using the UI:

| Scenario | Observed result |
|---|---|
| Backend unavailable during login | Displayed “Serveur injoignable…” instead of incorrectly reporting a bad password. |
| Staged supervisor login | Reached the dashboard and tour management after the backend became available. |
| Completed `STAGED-T1` | Displayed 3/3 handled visits, confirmed depot return, recorded start/end times, total estimate, actual duration and observed service coverage. |
| Routing comparison | Rendered road geometry, depot and stop markers, and rows for manual, NN, NN + 2-opt and exact benchmark with costs, relative comparisons and runtime. Geometry and values were supplied by the simulated provider. |
| Paired and AFTER-only evidence | Point 1 showed BEFORE and AFTER photos; point 2 showed AFTER only. DOM inspection confirmed the AFTER image loaded from an authenticated blob URL with nonzero intrinsic image width. |
| Distinct evidence outcomes | Point 1's CLEANED AI prediction and human correction were displayed separately. Point 2 showed ANALYSIS_UNAVAILABLE while preserving the driver's collection report. Point 3 showed an issue reason without a photo. |
| Human review | Changed point 1's supervisor label to CANNOT_DETERMINE with a staged-review note. The updated persisted result appeared immediately; the original CLEANED AI prediction remained unchanged. |
| Manual planning | Created disposable `STAGED-UI-T2` from the form. Selected three points, moved P3 before P2, and saved manual order P1 → P3 → P2. |
| Planned-tour optimization | Before calculation, displayed missing route and unmeasured actual durations. Calculation then refreshed the open modal with geometry, ordered visits and all four algorithm comparisons. |
| Workflow roles | Completed-tour edit/cancel controls were disabled. The web form offered PLANIFIEE only and explained that the driver starts/finishes in the mobile app. |
| Document approval | Staged text-ready document remained available for lexical retrieval despite unavailable semantic indexing. Withdrawal changed “Approuvé” to “À examiner”; reapproval restored “Approuvé”. |
| Candidate profile | Displayed C/CE as required, two years as a proportional experience target with experience not mandatory, and weights 40/40/10/10. Saving a changed title created version 2. No real applicant or hiring decision was involved. |

The source-download button was exercised and produced no application error, but the in-app browser did not emit a download event or expose a downloaded file. **Completion of the file download was not verified through the browser.** Authenticated source endpoint behavior is covered separately by the staged HTTP verification.

A depot-label encoding defect (“DÃ©pÃ´t”) was observed in the running staged JAR and reported to the backend owner. The configuration was corrected with Unicode escapes after that JAR was built. This browser pass does not claim that a restarted JAR was visually rechecked.

## Additional Flutter browser run with live Gemini configuration

Later on the same date, the compiled Flutter driver application was served on `http://127.0.0.1:8088` and exercised through Playwright in Chrome. The browser plugin was unavailable, so the installed Playwright CLI was used. The backend and MongoDB were isolated from normal application data. **Road responses and browser GPS were simulated; Gemini requests used the configured real provider.** All photos were abstract synthetic PNGs, and the approved source was a fictional test document.

| Scenario | Observed result |
|---|---|
| Login/start | Real backend login restored the assigned tour and saved its actual start timestamp. |
| Navigation | The next-point action opened Google Maps. After the stops, the depot link used `36.722857,10.283535`. These checks verify handoff destinations, not actual driving. |
| Upload failure/reload/retry | A deliberate HTTP 503 left the photo draft available. Refresh restored authentication and confirmed arrival; reopening the tour retained the draft. Manual retry uploaded the PNG successfully. |
| AFTER-only completion | First stop completed with AFTER evidence and no BEFORE image. Gemini outage remained `ANALYSIS_UNAVAILABLE`; it did not become `CLEANED`. |
| Optional pair | Second stop saved both BEFORE and AFTER evidence, then completed despite the honestly labelled unavailable analysis. |
| Inaccessible point | Third stop reported an issue with a required reason, without an arrival timestamp or photo. An empty reason was rejected in the UI. |
| Depot return/finish | Explicit confirmation saved `TERMINEE`, 3/3 handled stops and depot return. Ten read-only backend assertions confirmed evidence modes, unavailable predictions, final status, available truck and stale tracking. |
| Timing | The demonstration saved 1,636 estimated total seconds, 900 estimated service seconds, 1,506 elapsed seconds and 777 observed service seconds. These include browser-testing delays and simulated routing; they are not an algorithm performance experiment. |
| Real embeddings and RAG outage | `gemini-embedding-2` returned 768 finite dimensions. A fictional approved document indexed successfully. The browser question about its `DEMO-42` code displayed `SOURCE_EXCERPTS`, the exact source passage and an explicit generation-unavailable label. |
| Real generation availability | Text and vision calls returned HTTP 503 high-demand/unavailable responses. Model discovery and embedding calls succeeded, establishing that the key was accepted. No successful generated answer or cleanliness classification is claimed. |
| Layout | Tour execution/completion rendered without clipped controls at 430×932 and 1280×900. |

The browser run exposed and fixed a missing browser file-picker path, local-storage quota risk for photo drafts, a blank page when the remote CanvasKit CDN was unreachable, refresh routing that expected transient navigation arguments, and issue reporting that required an arrival click. The browser build now serves CanvasKit locally, uses IndexedDB for one retry draft per driver, accepts JPEG/PNG with size/type validation, and restores the authenticated home screen after reload. Source cards omit invalid zero page references while retaining text-section references.

An injected local-draft deletion failure also reproduced a false upload-failure state after a successful server upload. Confirmed evidence now remains usable and analysis continues when local cleanup fails. Focused tests verify that a leftover identical draft does not block resumed progress, a different replacement draft is preserved, and a manual discard failure stays visible and retryable. The final complete suites pass **17 VM tests and 20 Chromium tests**, with clean static analysis.

Screenshots, sanitized provider results and the ten final backend assertions are stored outside the repository under `/tmp/gesticollecte-browser-check` and `/tmp/gesticollecte-live-provider-check.json`; these temporary artifacts may be removed by the operating system. Latest automated test totals are recorded in [IMPLEMENTATION_PROGRESS.md](../IMPLEMENTATION_PROGRESS.md). Browser console inspection still observed Flutter renderer `VideoFrame was garbage collected without being closed` messages; no app-blocking JavaScript exception was observed after the fixes. This is not a claim of a completely clean console.

## Remaining hardware and provider verification

The APK build and mocked Flutter tests do not establish the following:

- Camera permission/capture on a physical Android device, including interruption during capture.
- GPS permissions and position transmission while Google Maps is open.
- Force-close/reopen behavior and foreground service behavior on a particular Android manufacturer's power-management settings.
- Complete physical collection demonstration, actual route driving, or meaningful actual-versus-estimated timing error.
- Successful real Gemini text/vision generation, real OpenRouteService requests, image cleanliness accuracy, RAG grounding accuracy or reviewed CV extraction accuracy. Live embeddings and the real generation outage were checked in the additional browser run above.

The implementation includes one private local photo draft per driver and manual retry, server-confirmed visit progress, explicit AI failure states, and tracking stop on tour completion/logout. Physical Android acceptance and live-provider/manual benchmark evaluation remain explicit gates rather than inferred passes.
