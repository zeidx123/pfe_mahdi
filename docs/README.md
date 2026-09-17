# GestiCollecte technical knowledge base

Audit date: **17 September 2026**. This is an audit of the existing implementation, not an implementation of the proposed fixes. Existing application sources and earlier README/HELP files were preserved.

Start with [PROJECT_CONTEXT](PROJECT_CONTEXT.md) for reuse in ChatGPT/Codex, or [the final audit report](AUDIT_REPORT.md) for the verdict and status tables.

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

## Reading conventions

**Current state**, **Problems**, and **Recommendations** are separated. A source path proves implementation exists; it does not prove the workflow succeeds. Runtime claims refer to the isolated audit environment described in document 10. **Needs verification** means no successful end-to-end demonstration was established.

Status vocabulary: WORKING; PARTIALLY WORKING; BROKEN; UI ONLY; MOCKED; HARDCODED; NOT IMPLEMENTED; LEGACY / UNUSED; UNKNOWN — NEEDS VERIFICATION. WORKING is scoped to the explicitly tested path, not a production-readiness claim.

Source shorthand used in tables: `B` = `BACK/src/main/java/com/municipalite/gestiontournees`; `W` = `FRONT/src`; `M` = `MOBILE/lib`. Paths are relative to the repository root so this knowledge base remains portable.

Evidence: [sanitized API observations](evidence/runtime-results.json), [repository file inventory and hashes](evidence/repository-inventory.csv), [verification record](evidence/verification.md). No credentials, JWTs, CV text, or live personal data are copied into the evidence.
