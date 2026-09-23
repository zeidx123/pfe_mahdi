# PFE evaluation material

This directory contains reproducible experiment inputs, metric implementations and selected development results. It is separate from runtime application data. Keep it in the source delivery: backend tests depend on the routing matrices and fictional CV fixtures, and the Python metric tests depend on this layout.

| Area | Material to keep | Instructions |
| --- | --- | --- |
| `routing/` | Two synthetic directed matrices, compact comparison CSVs and a saved ORS development matrix/geometry that can be replayed offline. | [Routing](routing/README.md) |
| `rag/` | Fictional source documents, questions/templates, metric tests, lexical seed results and selected captured development runs. | [RAG](rag/README.md) |
| `candidates/` | Explicitly fictional DOCX examples and manifests under `fixtures/`, synthetic evaluator responses, and compact development/outage-contract results. | [Candidate matching](../docs/evaluation/CANDIDATE_MATCHING.md) |
| `vision/` | Abstract transport fixtures and generator, public development photographs with attribution, inference/metric instructions, tests and a small recorded transport run. | [Vision](vision/README.md) |
| `timing/` | Duration metric code/tests and a browser-simulation export/result pair. | [Timing](timing/README.md) |

Executable capture tools are in [scripts/evaluation/](../scripts/README.md). The smaller vision and timing metric programs remain beside their area documentation. Application startup does not import or execute any fixture automatically.

## Fixtures are not personal records

- `candidates/fixtures/cv-development/fictional-driver-*.docx` are authored examples marked **CV FICTIF**. Their manifests identify synthetic provenance; they are safe regression inputs, not real applicants or independent evaluation labels.
- `rag/development-documents/PFE-DEMO-*.md` are fictional exercise documents. Import only those source files for a demo; `expected-cases.json` contains evaluation expectations and must not enter the RAG source corpus.
- `vision/internet-development/` contains unrelated public scenes, not municipal execution evidence or before/after pairs. Preserve `sources.json` and `new-sources.json`, including creators, licences, source links and image hashes, whenever distributing the images.
- Abstract image squares and synthetic road costs test software contracts; they do not measure image accuracy or road performance in a municipality.

Loose local CV PDFs belong in the ignored `local-data/cvs/` directory. Keep private source documents, real candidate information, credentials and runtime uploads out of version control. Application approval of a RAG document does not itself make that document appropriate for public source delivery. Captured RAG exports contain source passages, so only the fictional demonstration corpora are retained here.

## Results and provenance

Retained results are dated development observations with their original model/provider settings and explicit uncertainty or failure states. They are not a completed independent PFE benchmark. Use independently reviewed labels and separate development/final splits before reporting quality metrics. Never infer a hiring decision from a filename, mark an unavailable response correct, or treat browser timings as physical travel measurements.

The delivery cleanup moved candidate fixtures from `scripts/evaluation/fixtures/` to `evaluation/candidates/fixtures/`. The runnable fallback manifest now uses the new relative paths; source document bytes and hashes are unchanged. Historical result files retain the manifest hashes captured before that path relocation. A fresh run records the new manifest hash instead of rewriting old evidence.

Raw root-CV imports, repeated provider diagnostics and intermediate retry logs were archived outside the repository during delivery preparation. Selected final development outputs and summaries remain. New runs should use a fresh output path, preserve their input/model/prompt provenance, and be reviewed before inclusion in the published results. Keep bytecode, temporary files and private raw runs local.
