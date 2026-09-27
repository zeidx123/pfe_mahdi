# Assistant conversation check — 24 September 2026

The assistant now generates replies through the independently configured `RAG_AI_PROVIDER`. This live check used `GROQ` and `qwen/qwen3.8-27b`; Gemini continued to provide `gemini-embedding-2` query embeddings. The prompt is `grounded-rag-v3`.

| Input | Actual response | Time |
| --- | --- | ---: |
| `hello` | `ANSWERED` / `CONVERSATION`: a generated greeting and offer of help, without a missing-document warning | 1.374 s |
| Code of the photo exercise in PFE-DEMO-01 | `ANSWERED` / `GROUNDED`: `JASMIN-PHOTO-23`, with the exact supporting passage | 1.656 s |
| `Et si ce point est inaccessible ?` | `ANSWERED` / `GROUNDED`: issue reporting, available evidence, unsafe-photo exception and continuation, supported by PFE-DEMO-02 | 1.566 s |
| Draft a supervisor message from supplied facts about a parked car blocking P2 | `ANSWERED` / `DRAFT`: generated message using the supplied facts, with no invented identity or timestamp and no automatic sending | 0.704 s |
| Annual salary and night bonus | `INSUFFICIENT_INFORMATION`, without invented amounts | 1.237 s |

The four generated replies came through the Flutter app in visible Chrome. Sources are collapsed beneath the answer. The conversation/draft kinds were preserved in server history. Cited originals returned HTTP 200 to the authenticated driver and HTTP 401 anonymously; stored quotations matched the originals literally.

Validation: 243 backend tests passed across the full package run and final targeted RAG checks; 37 Flutter tests passed; Flutter analysis reported no issues; the HTML web build passed; 25 provider-probe tests passed. Regression cases cover embedding outages with healthy generation, invalid citations, foreign conversation access, mixed-language greetings containing a procedural question, draft clarification, and repeated follow-ups. Provider failure still yields labelled excerpts or explicit unavailability.

These are development smoke tests using fictional approved documents, not an independently reviewed accuracy benchmark. Literal citation checks establish that quotations exist; they do not automatically prove every generated claim is entailed. Earlier Gemini 503 results remain historical evidence and are not replaced. Groq generation shares the account's free allowance with image/CV requests; provider availability is not guaranteed.

Raw local responses and screenshots are kept in ignored `local-data/verification/assistant-upgrade-20260924-170736/` on the verification machine. New installations reproduce the setup through the README and `docs/LOCAL_AI_TEST_GUIDE.md`; they do not require that local directory.
