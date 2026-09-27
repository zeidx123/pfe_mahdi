# Delivery checks

Updated **24 September 2026**. This is the current verification record. The application and experiment tools are implemented; the final independently reviewed P1 benchmarks and physical Android demonstration are still pending.

## Software checks

| Check | Latest completed result | Scope |
| --- | --- | --- |
| Backend | 243 tests passed with zero failures/errors across the full package run and final targeted assistant checks on 24 September. | Authentication/ownership, tour transitions, evidence, providers, RAG and CV contracts. Provider tests use controlled responses. |
| React | Lint and production build passed again on 24 September. | Four existing lint warnings and the Vite large-bundle warning remain. |
| Flutter | Analyze clean, 37 tests passed, HTML web build passed after the assistant update on 24 September. | Driver interface and workflow contracts; no physical camera/GPS guarantee. |
| Python tools | 25 provider-probe tests passed after independent RAG provider selection. Earlier checks on 24 September passed evaluation runners 32, RAG 8, vision metrics 10 and timing 3. | Request formats, provider selection, fixtures, provenance and metric calculations; no real inference in these tests. |
| Portability | Windows and Linux launchers and CI jobs are provided; Bash launch scripts pass syntax checks. | Today's local checks ran on Linux. Windows/Linux CI results after push and a fresh Windows installation are not established by these local checks. |

The [root README](../README.md#verification) gives repeatable build commands. The [demo setup](DEMO_SETUP.md#automated-checks) explains the isolated integration runner. Passing checks demonstrate the covered software behavior, not model accuracy.

## Observed integration evidence

- **Real Groq photo response:** one public AFTER image returned `NOT_CLEANED` in 6,499 ms browser round-trip time. The driver workflow saved it, resumed after reload, reused the same assessment without another model call, completed/reported two stops and finished the tour. Supervisor review preserved the original prediction. See [the compact record](evidence/groq-live-simulation-2026-09-24.md).
- **Manual request savings:** that simulation uploaded two photos and requested one analysis. The second completion remained an explicitly unanalysed driver declaration. This is one observed test, not a general photos-per-day estimate.
- **Road routing:** ORS matrix and directions succeeded for a saved three-stop depot round trip. NN/NN + 2-opt reduced estimated driving time from 312.52 to 251.17 seconds against the chosen manual order; exact search agreed for that instance. See [P1 routing evidence](evaluation/P1_RUN_RESULTS.md#routing).
- **Assistant/RAG:** the updated Groq-backed browser check generated a greeting, two grounded answers and a draft message, and abstained on an unsupported salary question. Source access and literal quotations were verified. See [the conversation check](evidence/assistant-conversation-2026-09-24.md). Earlier Gemini runs returned excerpts during 503 failures; those historical results remain valid failure evidence.
- **Candidate matching:** two fictional documents and authored review values demonstrate the expected 70/10 rubric scores. Separate provider-unavailable results remain labelled as such. This does not establish extraction accuracy on real applicants.
- **Duration:** the Groq browser simulation saved 856.88 estimated, 423 elapsed and 344 observed service seconds. These measure browser actions, not a physical collection journey.

The earlier staged HTTP workflow exercised access restrictions, illegal transitions, upload/retry, persistence after restart, AFTER-only/paired evidence, issue handling, reviews and repeated candidate acceptance using disposable storage and simulated providers. It can be rerun with the delivered scripts; historical debug dumps are archived outside the repository.

## Remaining completion gates

1. Run the full tour on a physical Android phone: camera/location permissions, tracking while Google Maps is open, restart/resume, upload failure/manual retry, depot return and supervisor review.
2. Expand and independently review the successful grounded RAG examples, and obtain live CV extraction with the currently selected provider. Preserve unavailable states when limits or outages prevent a response.
3. Complete independent RAG, CV and staged image labels, including same-scene AFTER-only versus paired image comparisons. Freeze development/final splits and record model/prompt settings. Dataset counts remain targets.
4. Expand saved real-road scenarios and collect physical execution timestamps for actual-versus-estimated analysis. Keep matrices fixed for fair algorithm comparisons and report missing measurements.
5. Repeat the integrated demonstration with those reviewed experiments before declaring all P0/P1 gates complete. Optional AI tour summaries remain deferred.

## Delivery organization

Private CVs are in ignored `local-data/cvs/`; shared fictional CVs are in `evaluation/candidates/fixtures/`. Provider keys and generated uploads stay outside tracked source. Historical audits, repeated provider diagnostics, screenshots and generated result pages were archived outside the repository before cleanup. The delivery keeps current guides, compact live evidence and reproducible experiment inputs rather than temporary machine-specific output.
