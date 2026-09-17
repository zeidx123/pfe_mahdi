# Verification record

Date: 2026-09-17. This record is deliberately sanitized; it contains no API keys, JWTs, SMTP passwords or CV text.

## Environment and isolation

- Workspace: `/home/habib/Projects/pfe_mahdi`.
- Audit working artifacts/logs: `/tmp/pfe-audit-20260917/` (temporary, may not persist across machines/sessions).
- Disposable Mongo port27018, DBs `pfe_audit` and `pfe_audit_tests`; default Mongo27017 not used for application writes.
- Packaged backend launched from temporary `runtime/`, bound127.0.0.1:8080; upload files remained there.
- Provider overrides: Gemini key `VOTRE_CLE_AUDIT`, base URL `http://127.0.0.1:9`, timeout1s; SMTP127.0.0.1:9 with1s connection/read timeouts. No external AI/email success was attempted or claimed.
- Clean frontend and Flutter copies retained app source; dependencies/generated directories were recreated outside the repository. Flutter pub get changed one dependency only in the temporary lockfile.
- At completion, the audit backend, Vite server and disposable Mongo process were stopped. The default Mongo database still returned an empty collection list on a final read-only check.

## Commands and observed outputs

| Command / action | Observed output |
| --- | --- |
| BACK: `mvn -B -DskipTests package` | BUILD SUCCESS |
| BACK: `mvn test` with Mongo27018 override | Tests run:3, Failures:0, Errors:1; stale DemoApplicationTests cannot find @SpringBootConfiguration |
| FRONT original: npm build/lint | Permission denied for copied dependency executables; direct Vite execution missing Linux native binding |
| Temporary FRONT: `npm ci`, `npm run build` | Install/build success; Vite8.2.1; bundle-size warning |
| Temporary FRONT: `npm run lint` | Exit0;34warnings |
| FRONT simulator: `node simulateur_gps.js` | ReferenceError: require is not defined in ES module scope |
| Temporary MOBILE: `flutter --no-version-check pub get` | Success; vm_service14.2.4→14.2.5 in temp copy |
| Temporary MOBILE: `flutter --no-version-check analyze` | Error MyApp isn't a class; unused test import warning |
| Temporary MOBILE: `flutter --no-version-check test` | Test compilation failed: couldn't find constructor MyApp |
| Temporary MOBILE: `flutter --no-version-check build web` | Built build/web |
| API probe set |69 sanitized observations in runtime-results.json; includes deliberately failing/inconsistent cases |
| Browser | Supervisor login; dashboard; tour filter/details; complaint crash; missing assistant route; live map update |
| Native WebSocket/STOMP client | Anonymous CONNECTED frame on `/ws/websocket` |
| PDF request |200 application/pdf,1554bytes, `%PDF-` signature; no full layout verification |

Raw command logs were retained under the temporary directory during the audit. The portable result is this record plus [runtime-results.json](runtime-results.json); do not assume raw logs remain available indefinitely.

## Reproduction notes

Use a disposable database and synthetic accounts. **Do not point the current application at valuable existing driver data during a casual smoke test:** GestionTourneesApplication resets CIN-based passwords and reactivates existing drivers on startup. The audit's original database inspection was read-only.

Start local Mongo in an isolated data directory on another port. Package the backend, then pass explicit Spring overrides for Mongo URI/database, storage-working-directory, AI and mail settings. Use a clean lockfile dependency install for FRONT instead of copied node_modules. Login with an intentional disposable supervisor fixture and keep the returned bearer token out of logs.

Representative reproductions:

1. Create two synthetic drivers, one truck, three valid points and one tour assigned to driver1. Login as driver2; global tour list and status PATCH expose the ownership defect.
2. Send multipart `photo` to both tour evidence endpoints from the packaged app. Observe FileNotFoundException rather than assuming the direct vision endpoint proves mobile success.
3. Start/finish/restart a tour and inspect truck status/end timestamp. Clear its point list and inspect ordered IDs.
4. Submit an empty complaint. Its accepted null title crashes the supervisor complaint page on navigation.
5. With unavailable Gemini, submit an image directly: response NORMAL/confidence.7. Correct to anomaly and inspect problem/severity consistency.
6. Upload a short text document that mentions only logging a blocked point and an arbitrary code. Ask how to handle an inaccessible point. Compare the fixed photo/police procedure against the actual source content.
7. Supply the returned conversationId from another authenticated driver and inspect the ownership gap.
8. Submit clearly synthetic CVs with an explicit out-of-range DOB or with a phone but no CIN; inspect extracted fields and score rather than only HTTP success.

## Integrity and audit limits

[source-integrity.json](source-integrity.json) records the167 source baseline hashes and the post-audit changed-file list. No application source changes were made. [repository-inventory.csv](repository-inventory.csv) covers337 non-generated files at discovery; it excludes docs, node_modules, target/build and cache trees. Existing useful README/HELP documents were retained.

Build outputs, temporary dependencies, test fixtures and Graphify-derived cache/reflection files are generated audit artifacts. The audit did not test every branch or decompile existing APKs. Tests do not establish live Gemini accuracy, SMTP delivery, real GPS/camera permissions, background execution, mobile release portability or production security. These remain “Needs verification” in the functional audit.
