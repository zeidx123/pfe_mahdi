# Final audit report

Audit: **17 September 2026**. Application source was preserved. See [PROJECT_CONTEXT](PROJECT_CONTEXT.md) for reuse in another AI session and [README](README.md) for the complete knowledge base.

1. **What the project actually does:** GestiCollecte manages municipal drivers, trucks, collection points and tours, links web supervision with a Flutter driver application, transports GPS positions, handles text complaints, produces tour PDFs and contains CV/document/image assistance pipelines.
2. **Current architecture:** React/Vite + Flutter → one Java17/Spring Boot3.2.5 backend → MongoDB/local files. REST/JWT for requests, STOMP/SockJS for web tracking, optional Gemini REST and SMTP integrations. No trained ML service or microservices.
3. **What genuinely works:** API login/basic roles, resource persistence, assigned-tour queries, route order computation, complaint replies, basic PDF generation, document indexing/retrieval, and a REST-to-browser live tracking update. Web login/tour search/details were exercised.
4. **What is partially working:** tour execution, mobile authentication, candidate extraction/scoring, document Q&A, image review, dashboards and incident management have real code but significant correctness gaps.
5. **What is fake/mocked/hardcoded:** weekly tonnes chart, mobile fuel75%, startup alert events, official-looking offline RAG procedures and normal-image fallback confidence. Route and candidate scores are real deterministic calculations, although AI/optimality labels overstate them.
6. **What is broken:** both tour photo uploads500; mobile expects nonexistent nested login user; anonymous global password reset; inactive-driver login; cross-driver tour mutation; stale optimized points; invalid references/transitions; null complaint crashes web; unused assistant route; stale Java/Flutter tests; GPS simulator ESM mismatch.
7. **AI/ML maturity:** meaningful integration prototype, not evaluated ML. No project training dataset/model/experiment results. Checked-in Gemini key is a placeholder; actual provider quality remains **Needs verification**.
8. **Biggest AI weaknesses:** hidden failure modes, unsupported source-cited answers, fabricated extraction fields, inconsistent rubric, uncalibrated image confidence, two competing route definitions, absent evaluation/provenance.
9. **Best realistic AI/ML improvements:** evaluated source-grounded RAG, shared deterministic routing with baseline/feasibility comparisons, evidence-backed CV extraction, and human-reviewed vision. Trained duration/image models are conditional on collecting real labeled data.
10. **Most important technical fixes:** photo storage; auth/ownership; tour state/resources; mobile identity/resume/timer lifecycle; complaint validation; explicit provider-unavailable states; portable configuration and focused regression checks.
11. **PFE strengths:** substantial cross-platform implementation, real persistence/live communication, explainable geospatial algorithms, document-processing pipeline and opportunities for controlled experiments.
12. **PFE weaknesses:** complete field demo not yet reliable, AI labels exceed evidence, no quantitative evaluation, fixed data looks measured, geography/configuration and duplicate/dead features reduce coherence.
13. **Recommended order:** fundamentals and device tour completion → honest AI/data behavior → RAG/routing evaluation → CV/vision evidence → optional data-backed ML → presentation polish. Keep the existing architecture.

## Feature status summary

| Feature | Status | Frontend | Backend | DB | AI/ML | Main issue |
| --- | --- | --- | --- | --- | --- | --- |
| Login/access | PARTIALLY WORKING | Web works; mobile user mismatch | JWT works; ownership/deactivation flawed | users | None | Anonymous reset and cross-user actions |
| Driver/truck/point CRUD | PARTIALLY WORKING | Active web pages | Real CRUD | Real | None | Inactive access, fill reset, weak ranges |
| Tour planning/lifecycle | PARTIALLY WORKING | Web + mobile | Real save/order/status | Real | Deterministic NN | Invalid references/transitions, no robust resume |
| Photo evidence/completion | BROKEN | Mobile capture flow | Both uploads500 | References exist | Vision follows upload | Relative storage destination |
| Live tracking | PARTIALLY WORKING | Browser live update verified | REST/STOMP works | Memory only | None | No history/stale expiry; mobile fuel fixed |
| Complaints/replies | PARTIALLY WORKING | Both clients | Normal API path works | Real | None | Invalid complaint crashes web |
| Dashboard | PARTIALLY WORKING / HARDCODED series | Active | Real count endpoint | Counts real | None | Tonnes static; alerts seeded |
| Basic tour PDF | WORKING (API) | Details button | Valid PDF generated | Reads current data | None | Photo-rich layout Needs verification |
| CV screening | PARTIALLY WORKING | Active candidate UI | Parsing/rules execute | Real | Gemini optional / regex | DOB/CIN errors, inconsistent rubric |
| Document administration/RAG | PARTIALLY WORKING | Web docs; mobile chat | Index/retrieve/history execute | Real | Embeddings/LLM or fallbacks | Unsupported fallback and owner checks |
| Vision analysis | PARTIALLY WORKING | Mobile; web page unrouted | Direct fallback executes | Real | Gemini optional | Unavailable reported NORMAL |
| Alerts / separate assistant / standalone routes | LEGACY / UNUSED UI paths | Unrouted/unused wrappers | Implemented endpoints | Real records/seeds | Mixed | Not coherent demonstrated features |

## AI feature summary

| AI Feature | Current Method | Real/Mock | Quality | Recommended Approach | Priority |
| --- | --- | --- | --- | --- | --- |
| CV extraction/score | PDFBox, Gemini or regex; rules | Real parsing/scoring; generated defaults | Unvalidated extraction, rubric mismatch | Evidence spans, one justified rubric, human verification, field metrics | P0/P1 |
| Tour ordering | NN; separate NN+2-opt | Real deterministic heuristic | No optimality guarantee; no enforced CVRP | Shared route definition, capacity checks, exact-small/baseline experiments | P1 |
| Image anomalies | Gemini JSON or fixed normal | Provider path real; failure result hardcoded | Unsafe false certainty; no benchmark | Unavailable state, schema checks, human labels, error analysis | P0/P1 |
| Document RAG | Ingest/chunk/embed/rank/generate | Real retrieval; fallback procedures hardcoded | Citation does not prove answer support | Approved corpus, grounded answer/abstention, retrieval/generation evaluation | P0/P1 |
| Supervisor assistant | DB context + Gemini/intent rules | Real counts, fixed procedures; inactive UI | Wrong count semantics; no measured value | Scoped factual summaries only if useful | P2 |
| Prediction/trained ML | None | NOT IMPLEMENTED | No suitable dataset established | Collect real labels first; conditional duration/image experiment | P2 conditional |

Build evidence: backend package, clean React build and Flutter web build pass. React lint reports34warnings. Backend tests:2pass/1error; Flutter analyze/test fail on stale MyApp test. **Live Gemini, email delivery and physical-device behavior were not verified.** Full runtime scope and69 API records: [functional audit](10-functional-audit.md). All proposals remain unimplemented: [roadmap](15-improvement-roadmap.md).
