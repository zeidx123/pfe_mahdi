# GestiCollecte documentation

Current delivery documentation, updated **24 September 2026**. Start with the root [README](../README.md) for installation on Windows, then Linux.

| Guide | Purpose |
| --- | --- |
| [Project context](PROJECT_CONTEXT.md) | Architecture, platform responsibilities and implemented workflow |
| [Demo setup](DEMO_SETUP.md) | Configuration, supervisor/driver demonstration and repeatable checks |
| [Local AI testing](LOCAL_AI_TEST_GUIDE.md) | Test the assistant, fictional/private CVs and photo uploads without a camera |
| [Delivery checks](DELIVERY_CHECKS.md) | Dated verification results, limitations and remaining acceptance gates |
| [P1 development results](evaluation/P1_RUN_RESULTS.md) | Selected experiment evidence and its interpretation |
| [Groq workflow evidence](evidence/groq-live-simulation-2026-09-24.md) | One real photo analysis in the browser workflow, with compact JSON evidence |
| [Assistant conversation evidence](evidence/assistant-conversation-2026-09-24.md) | Generated greetings, grounded answers, drafting and source-access checks |

The **web application serves supervisors**: resources, tours, reviews, approved documents and recruitment. The **Flutter application serves drivers**: assigned tours, navigation, evidence, complaints and the procedural assistant.

## Why evaluation and scripts remain

The PFE contributions need repeatable comparisons. [evaluation/](../evaluation/README.md) holds fictional or attributed development examples, frozen routing inputs, selected results and methods. [scripts/](../scripts/README.md) holds launchers and tools that reproduce the checks; it is separate from application runtime code. Temporary output is not a deliverable, while a saved road matrix or explicitly labelled fixture is useful experimental input.

| Experiment | Method and reproducible inputs |
| --- | --- |
| Routing | [Manual, NN, directed 2-opt and exact comparison](../evaluation/routing/README.md) |
| RAG | [Retrieval modes, source review and metrics](../evaluation/rag/README.md) |
| Candidates | [Versioned rubric, fixtures and review manifest](evaluation/CANDIDATE_MATCHING.md) |
| Vision | [AFTER-only/paired experiments and class metrics](../evaluation/vision/README.md) |
| Duration | [Estimate errors and measurement completeness](../evaluation/timing/README.md) |

Private CVs are kept in ignored `local-data/cvs/`; shareable fictional fixtures are in `evaluation/candidates/fixtures/`. The original audits, repeated provider diagnostics, temporary screenshots and generated result pages were archived outside the delivery repository. Current guides do not depend on that local archive.

The browser demonstration and automated checks establish specific software behavior. Independent AI benchmarks and a physical Android camera/GPS demonstration remain open; dataset sizes are targets rather than mandatory counts.
