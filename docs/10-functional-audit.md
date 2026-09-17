# Functional audit

## Current state: verification scope

Audit date: 2026-09-17. Tests used Java17/Maven, Node24.21.0, Flutter3.24.5/Dart3.5.4, a disposable Mongo process on `127.0.0.1:27018`, and the packaged Spring app on port8080. Browser checks used the clean React copy on port5173. Backend uploads went into a temporary working directory. Gemini and SMTP were redirected to unavailable loopback endpoints, so no external model request or email delivery was counted as successful.

The existing default Mongo database was inspected read-only and not used for application startup: startup code resets/reactivates drivers. Synthetic API data and repository sample CVs were processed only in the isolated environment. [69 recorded API probe observations](evidence/runtime-results.json) are evidence, not 69 passing assertions. Additional browser/build observations appear below.

### Build, lint and existing tests

| Check | Result | Interpretation |
| --- | --- | --- |
| Backend `mvn -B -DskipTests package` | PASS | Current Java source compiles/packages; test execution intentionally separate |
| Backend `mvn test` against disposable Mongo | **3 tests, 2 pass, 1 error** | `com.example.demo.DemoApplicationTests` cannot locate @SpringBootConfiguration; context test and UserDiagnosticTest pass; diagnostic prints users, no meaningful feature assertions |
| Original FRONT `npm run build` / lint | FAIL | Copied `.bin` executables lack permissions; direct Vite execution then lacks Linux native Rolldown binding |
| Clean temporary FRONT, `npm ci` → `npm run build` | PASS | Lockfile/source retained; Vite8.2.1 bundle ~1,049KB (~305KB gzip), size warning |
| Clean FRONT `npm run lint` | PASS with **34 warnings** | Mostly unused variables; hook dependency and fast-refresh warnings; no frontend test or typecheck script |
| GPS simulator `node simulateur_gps.js` | FAIL | `require is not defined in ES module scope`; its REST call also lacks JWT |
| Clean Flutter copy `flutter pub get` | PASS | SDK resolved packages; temporary lockfile updated vm_service14.2.4→14.2.5, original untouched |
| Flutter `analyze` | FAIL: 1 error + 1 warning | Stale widget test references nonexistent `MyApp` and unused app import |
| Flutter `test` | FAIL loading test | Same missing MyApp constructor; no passing application feature test |
| Flutter `build web` | PASS | Current app entry compiles; not proof of working camera/GPS or mobile release |
| Android/iOS build/device run | Needs verification | Existing APK is not a verified build of audited source; platform hardware and release behavior untested |

### Runtime and browser observations

| ID | Scenario | Observed result / conclusion |
| --- | --- | --- |
| V01 | Correct and incorrect credentials; anonymous `/me`; driver calls supervisor endpoint | Login200, wrong401, anonymous401, driver agents403. Basic token and role mechanism works |
| V02 | Two drivers log in by matricule | Both succeed; response user fields flat, nested user absent. Conflicts with mobile AuthService |
| V03 | Create/update/read resources | Driver/truck/point fixtures persist; empty driver request400. Not all form/business constraints are enforced |
| V04 | Deactivate driver, then new login | Login still200; soft deactivation does not revoke access |
| V05 | Anonymous bulk password reset | POST succeeds200 without credentials |
| V06 | Create tour; list each driver's own tours | Tour201 with3points; assigned driver sees1, second sees0 via mes-tournees |
| V07 | Other driver uses global list and mutates tour | Other tour visible; PATCH EN_COURS200. Missing ownership checks |
| V08 | Start/finish/restart status and truck status | Truck remains DISPONIBLE; completion without photos200; reopening finished tour succeeds with old end time |
| V09 | Clear tour's point list | Response still contains previous three ordered point IDs and2.8km |
| V10 | Create tour with nonexistent resources |201; unresolved truck/driver labels and inconsistent point count |
| V11 | Recalculate and capacity route | Recalculate200. Standalone route accepts240kg in100kg truck, fill clipped100% |
| V12 | Ordinary point edit omits fill; bad coordinate ranges | Fill100→0. Coordinates999/999 and fill200 accepted201 |
| V13 | Upload point and final evidence photo | Both500, FileNotFoundException under Tomcat work-directory-relative uploads path |
| V14 | Call vision directly with unavailable provider |200 NORMAL/confidence0.7; no successful model inference |
| V15 | Correct fallback image to overflowing container |ANOMALIE saved, problemCreated false, severity INFO; data not reconciled |
| V16 | Generate basic tour PDF |200 application/pdf;1554bytes, `%PDF-` header. Photo-rich rendering and pagination Needs verification |
| V17 | Driver complaint → supervisor reply → driver history | Works; reply persisted as TRAITEE |
| V18 | Submit empty complaint and open web page |API200 with null title; browser blank page and TypeError on `titre.toLowerCase()` at ReclamationPage.jsx:62 |
| V19 | Tracking POST/GET and browser STOMP |API200; map first shows12km/h/75%, then synthetic24km/h/61% without reload; status “En direct (Connecté)” |
| V20 | Anonymous STOMP connect |CONNECTED frame received on `/ws/websocket`; channel authentication absent in source |
| V21 | Dashboard in initially empty operational DB |Real resource counts0 but one critical alert from seeds; tonnes chart still shows seven fixed days |
| V22 | Empty corpus/empty question |No-context answer abstains; response conversationId null even though saved; blank question400 |
| V23 | Upload short TXT and read status |POST returns UPLOADED/0, immediateGET INDEXED/1; indexing and storage work, asynchronous contract misleading |
| V24 | Ask with retrieved source lacking suggested procedures |Fallback invents fixed “official” photo/police steps while citing synthetic source; retrieval score~.819 is not grounding proof |
| V25 | Driver2 sends driver1 conversationId |200, same conversation ID used; ownership not enforced |
| V26 | Supervisor chat fallback after complaint resolved |Uses real counts but still describes pending mechanical complaint; count semantics wrong |
| V27 | CV explicit age55, then CV with phone/no CIN |Both score85 recommended; DOB null; phone becomes CIN in second case |
| V28 |13 repository CV PDFs |All processed offline;10 Moroccan-format samples reject0, Tunisian examples85/80/0; not a representative benchmark |
| V29 | Browser supervisor login, tour search/details |Login/navigation render; searching AUDIT-T1 removes other row; modal shows persisted order/estimate and false “minimum guaranteed” language |
| V30 | Browser `/assistant` |Redirects to `/dashboard`; assistant page unreachable in active router |

### Failure trace: photo storage

TourneeService constructs a relative upload directory and calls `MultipartFile.transferTo(File)`. In the packaged Tomcat runtime, transfer resolves under Tomcat's work directory rather than the directory created relative to the process working directory. Both point and final routes produced FileNotFoundException/500. This blocks the normal mobile sequence before vision and before final completion. Merely demonstrating the direct vision endpoint does not demonstrate the mobile collection workflow.

### Persistence evidence

Separate POST/GET requests and web reload/navigation showed saved users, trucks, points, tours, complaints, documents, candidates and conversations. The audit did not perform a database durability/failure-recovery exercise. Tracking stores only latest values in memory by source design. Core persistence is real; it does not make static chart data or invented AI answers real.

## Problems and remaining verification

The following are explicitly **Needs verification**: live Gemini availability/quality and cost; representative scanned/multilingual PDFs/DOCX tables; large/concurrent indexing and recovery; document reindex/delete; candidate acceptance/duplicate-email driver outcome; email delivery; photo-rich PDF layout; physical Android/iOS camera/GPS permissions; background/locked-phone tracking; web image upload; mobile reconnection/timeout/restart/resume; all CRUD edit/delete UI variants; all unsupported/malformed provider responses. No success claim is made for these paths.

## Recommendations

Use these observations as regression cases when implementation is later authorized. Fix core failures before expanding tests; add focused behavior tests for ownership, lifecycle, uploads, unavailable AI and grounding. Do not preserve obsolete counter/context-only tests as evidence of application quality. Source was not changed to make tests pass.
