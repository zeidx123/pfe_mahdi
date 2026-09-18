# GestiCollecte technical knowledge base

Updated **17 September 2026**. The application includes the core supervisor/driver workflow and P1 algorithm/AI evaluation tooling. Live Gemini checks have returned embeddings, a structured text response and an uncertain image assessment, alongside provider outages. Live ORS matrix/directions and a three-stop route in the Flutter browser now pass. Physical Android verification, broader road scenarios and independently reviewed AI experiments remain open. P2/P3 are deferred.

## Current implementation

| Document | Contents |
| --- | --- |
| [Project context](PROJECT_CONTEXT.md) | Current architecture, platform responsibilities, features, data and API contracts |
| [Implementation progress](IMPLEMENTATION_PROGRESS.md) | P0/P1 implementation, completed checks and remaining acceptance gates |
| [Demo setup](DEMO_SETUP.md) | Configuration, startup, Android installation/run, complete demonstration and evaluation commands |
| [Staged HTTP evidence](evidence/implementation-staged-workflow.json) | Isolated integration assertions with simulated providers and disposable storage |
| [Browser and mobile verification](evidence/implementation-browser-mobile.md) | Observed web behavior, Flutter checks, APK build and physical-device limitations |
| [P1 development results](evaluation/P1_RUN_RESULTS.md) | Evaluation tooling, real-provider development runs, remaining external gates and reproducible artifacts |

The **web application is for supervisors**: planning, resources, reviews, documents and recruitment. The **mobile application is for drivers**: assigned tours, navigation, visits/evidence, progress and the procedural assistant.

## P1 experiments

| Area | Documentation |
| --- | --- |
| Routing | [Saved-matrix runner and interpretation](../evaluation/routing/README.md) |
| RAG | [BM25/semantic/hybrid capture, independent review and metrics](../evaluation/rag/README.md) |
| Candidate matching | [Versioned rubric, reviewed manifest and evaluation runner](evaluation/CANDIDATE_MATCHING.md) |
| Vision | [AFTER-only/paired batch evaluation and independent-label metrics](../evaluation/vision/README.md) |
| Duration | [Actual-versus-estimated timing and measurement completeness](../evaluation/timing/README.md) |

Automated integration establishes software behavior. Synthetic fixtures establish algorithm/metric behavior. Neither establishes empirical AI accuracy or municipality-wide performance. Evaluation dataset sizes are targets, not rigid acceptance counts.

## Historical audit baseline

The following documents record the **pre-implementation audit** from 17 September 2026. Their descriptions of broken behavior, hardcoded outputs and proposed work are historical; use the implementation documents above for current status. They have been preserved rather than rewritten to imply those fixes already existed.

Start with [the original project context](PROJECT_CONTEXT_AUDIT_BASELINE.md) or [the original audit report](AUDIT_REPORT.md).

| Document | Contents |
| --- | --- |
| [00 — Overview](00-project-overview.md) | Actual product, boundaries and audit method |
| [01 — Product and users](01-product-and-users.md) | Actors, intended workflows and current restrictions |
| [02 — Features](02-feature-inventory.md) | Feature-to-UI/API/data/dependency/status map |
| [03 — Architecture](03-system-architecture.md) | Current components and request-flow diagrams |
| [04 — Repository map](04-repository-map.md) | Applications, packages, scripts, artifacts and abandoned code |
| [05 — Data model](05-data-model.md) | Collections, relationships, indexes and integrity gaps |
| [06 — APIs](06-api-inventory.md) | Methods, routes, callers, contracts, access and verification |
| [07 — Technology](07-technology-stack.md) | Libraries connected to actual functionality |
| [08 — AI architecture](08-ai-ml-architecture.md) | Providers, pipelines, storage and fallbacks |
| [09 — AI audit](09-ai-feature-audit.md) | Extraction, scoring, retrieval, routing, vision and assistants |
| [10 — Functional audit](10-functional-audit.md) | Runtime evidence, build/test results and remaining verification |
| [11 — Mock/broken/incomplete](11-mocks-broken-incomplete.md) | Hardcoded outputs, seeds, dead paths and missing implementation |
| [12 — Technical debt](12-technical-debt.md) | Correctness and practical maintenance issues |
| [13 — PFE assessment](13-pfe-technical-assessment.md) | Defensible achievements, limitations and experiments |
| [14 — AI/ML opportunities](14-ai-ml-opportunities.md) | Feasible LLM, deterministic, ML and hybrid work |
| [15 — Roadmap](15-improvement-roadmap.md) | P0–P3 work with evidence, complexity and acceptance criteria |

## Historical audit reading conventions

In the historical files, **Current state**, **Problems**, and **Recommendations** are separated and describe the original audit snapshot. A source path proves implementation exists; it does not prove the workflow succeeds. Runtime claims refer to the isolated audit environment described in document 10. **Needs verification** means no successful end-to-end demonstration was established at that audit stage.

Status vocabulary: WORKING; PARTIALLY WORKING; BROKEN; UI ONLY; MOCKED; HARDCODED; NOT IMPLEMENTED; LEGACY / UNUSED; UNKNOWN — NEEDS VERIFICATION. WORKING is scoped to the explicitly tested path, not a production-readiness claim.

Source shorthand used in tables: `B` = `BACK/src/main/java/com/municipalite/gestiontournees`; `W` = `FRONT/src`; `M` = `MOBILE/lib`. Paths are relative to the repository root so this knowledge base remains portable.

Historical evidence: [sanitized API observations](evidence/runtime-results.json), [repository file inventory and hashes](evidence/repository-inventory.csv), [verification record](evidence/verification.md). These are separate from the new implementation evidence above. No credentials, JWTs, CV text, or live personal data are copied into the evidence.
