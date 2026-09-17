# PFE technical assessment

## Current state

The project has a credible municipal operations problem and substantial cross-platform implementation. Its academic value currently lies more in **software integration, document processing and algorithm implementation** than in demonstrated ML research. A strong defense should distinguish measured results from implemented possibilities.

| PFE dimension | Existing evidence | Gap |
| --- | --- | --- |
| Clear problem | Driver/truck/point/tour model, supervisor and driver applications | Define one municipality, geography and realistic operational constraints |
| Implemented solution | JWT, Mongo CRUD, REST, mobile code, live browser position broadcast, complaint response, PDF generation | End-to-end field completion broken by uploads and recovery/state defects |
| Data processing | PDF/DOCX extraction, chunking, embedding storage, CV regex cleanup | No documented corpus authority, annotation protocol or reliable missing-value handling |
| Algorithms | Nearest neighbor, 2-opt, Haversine, hybrid retrieval, deterministic score | Duplicate semantics, unjustified optimality/CVRP claims, no baseline comparisons |
| AI methodology | Gemini prompt/API integrations; fallback branches; human correction fields | Provider not configured successfully; provenance/schema/grounding unreliable |
| Experiments/results | Audit probes reveal repeatable behavior and failures | No project-owned benchmark/evaluation report or training/test separation |
| Explainability | Route template, score breakdown, retrieved sources | Some explanations conflict with calculation or cited content |
| Limitations | Now documented in this audit | Should be visible in demo/UI/report, not hidden behind AI labels |

### Strongest aspects worth showcasing

- A single backend serving web supervision and a mobile field role, with real persistence and authorization machinery.
- A live position moving through mobile-style REST → Spring broadcast → web map; this was actually observed with synthetic input.
- Route-ordering algorithms that can be explained, visualized and compared without pretending they are trained AI.
- Document ingestion into page/section-aware chunks, retrieval and source display, once fallback grounding is corrected.
- Human-reviewed CV extraction and image interpretation as assistive workflows, once missing-data and failed-provider states are honest.

### Weakest aspects

False certainty is more damaging to credibility than missing optional features: a normal image without analysis, a source-cited unsupported procedure, a minimum-distance guarantee from NN, a CVRP label without capacity enforcement, and collected-tonnes charts without measurements. The lack of evaluation means the project cannot currently defend AI accuracy or operational gains quantitatively.

The application also mixes Tunisian screening with Casablanca routing defaults, has inactive developed pages, and lacks a dependable mobile resume/completion sequence. These are visible coherence issues an examiner can discover through ordinary questions or navigation.

## Recommendations: a defensible demonstration

1. Show one coherent supervisor-created tour with validated resources.
2. Show driver login and assigned-tour isolation, starting/resuming the same tour, receiving real GPS or explicitly labeled simulated GPS, and successful point/final evidence upload.
3. Show supervisor review and a real complaint/reply. Distinguish estimated route distance from measured travel.
4. Demonstrate RAG on a small approved local corpus with source pages; ask an unsupported question and show abstention. Also show provider-unavailable behavior.
5. Present route experiments with identical objectives, feasibility checks and baseline distances. Explain NN/2-opt limitations.
6. Present extraction or vision error analysis only after evaluating actual labeled examples; otherwise describe those features as prototypes under evaluation.

A good written report includes the dataset/corpus provenance, preprocessing, assumptions, algorithm/model configuration, baselines, metrics, held-out examples, failure cases, latency and limitations. Do not invent improvement percentages. Synthetic route/CV tests can establish correctness of mechanisms, but must be labeled and cannot validate municipal real-world performance.

### Recommended focus

The best realistic PFE core is **reliable collection execution + evaluated document RAG + measured route heuristics**. CV extraction can provide a useful secondary language-processing experiment. Vision should remain a clearly uncertain human-reviewed prototype unless enough representative photos and working device integration can be obtained. A new trained model is optional, conditional on data, and not required to make the engineering academically strong.
