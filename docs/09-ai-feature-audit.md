# Deep AI feature audit

## Current state: overall maturity

The project has implemented AI integration **plumbing and deterministic algorithms**, not validated AI performance. The checked-in key is a placeholder. Offline probes establish parser/fallback/control-flow behavior only; they do not measure Gemini extraction, vision accuracy or semantic embeddings. No random prediction generator was found. The misleading outputs mainly come from fixed rules/templates, fallback defaults and overconfident labels.

| Feature | Current method | Reality / quality | Suitable approach | Priority |
| --- | --- | --- | --- | --- |
| CV extraction | PDFBox → Gemini JSON or regex cleanup | Real integration, unverified provider; fallback identity/DOB errors | Schema-validated extraction with evidence spans and human correction | P0 reliability, P1 evaluation |
| Candidate score | Fixed eligibility/rubric | Deterministic score, not ML; UI/backend disagree | One transparent job-relevant rubric, no learned hiring ranker without evidence | P1 |
| Active tour ordering | NN/Haversine/open path | Real heuristic, false optimality guarantee | Shared deterministic route engine and baseline comparisons | P1 |
| Standalone route proposal | NN + 2-opt/closed loop | Real heuristic; not implemented CVRP | Enforce constraints if needed; retire duplicate semantics | P1 |
| Vision anomaly | Gemini image JSON / NORMAL fallback | Real API path; unavailable images shown as normal | Explicit unavailable state, human review, labeled-image evaluation | P0 |
| Document RAG | Parsing + hash/Gemini embeddings + hybrid scan + generation | Real ingestion/retrieval; unsupported fallback procedures | Grounded answers/abstention, provenance, retrieval experiments | P0/P1 |
| Supervisor assistant | DB snapshot prompt + optional Gemini, intent fallback | Real DB counts; inactive client path; no agent tools | Expose only if useful, constrain summaries to validated data | P2 |

## 1. CV extraction and screening

### Current implementation

Purpose: help a supervisor read driver CVs and review suitability. Sources: `B/ai/cv/service/CvExtractionService.java`, `CvAnalysisService.java`, `model/Candidate.java`; `W/pages/candidatures/CandidaturesPage.jsx`.

The upload expects multipart `cv` with PDF MIME. PDFBox extracts text, so scans without a text layer are not OCRed. The original PDF is stored locally. Gemini is prompted in French to extract Tunisian identity/contact/license/experience/training/skills data as JSON, avoid invention, and use the first 8,000 characters. Long CVs may lose relevant later evidence. There is no enforced generation schema. The mapper consumes selected fields and does not preserve every requested output (notably age/license issue date). Parse failure can return an empty Candidate; provider failure invokes regex extraction.

Local extraction searches labels, eight-digit numbers, phone/email patterns, license categories, dates and experience phrases. Cleanup may supplement fields by broad substring matches. It never populates `dateNaissance`, while age eligibility reads that field. It can confuse a phone with CIN, infer a C license from “poids lourds,” infer certification from security-related wording, sum overlapping employment periods and mishandle negation. These are parsing/rule defects, not ML uncertainty estimates.

The score is a fixed additive rubric:

| Criterion | Backend points |
| --- | --- |
| Known age 20–35 | 15 |
| Valid Tunisian CIN | 10 |
| Valid Tunisian phone | 5 |
| License text contains B or C | 20 |
| Experience >=5 / >=2 / >=1 years | 25 / 20 / 10 |
| Nonempty chauffeur experience | 15 |
| Profile completeness >=5 / >=3 counted fields | 10 / 5 |

Missing valid CIN, missing acceptable license or known out-of-range age triggers score 0 and REJETE. **Unknown age passes the eligibility gate but earns no age points.** The configured minimum two years and configured license list are not consistently the rules actually enforced. Thresholds 70/40 map scores to RECOMMANDE/A_EXAMINER/PEU_COMPATIBLE. Backend explanations can report 10 experience points for zero/negative experience and use a different completeness definition.

The web criterion breakdown implements a different rubric: experience 25, driving 10, collection 10, security 15, certification 10, license 20, availability 5, phone 5. Some fields contribute twice. It cannot faithfully explain the backend total.

Supervisor decision PATCH accepts arbitrary status strings. ACCEPTE attempts driver creation: existing email skips creation; missing data may become generated email/CIN, telephone `22000000`, license number from CIN, default B and expiry five years ahead. Exceptions are logged but not returned as failed acceptance. Candidate has no created-driver ID.

### Runtime reality check

- A synthetic CV with explicit DOB corresponding to age 55 received **85 / RECOMMANDE**, with dateNaissance null.
- A synthetic CV without CIN reused an eight-digit telephone as CIN and received **85 / RECOMMANDE**.
- All 13 repository CV PDFs were accepted by the offline pipeline. Ten Moroccan-format examples returned rejection/zero under Tunisian rules; three Tunisian examples returned 85, 80 and 0. Filename labels are not independent ground truth and these examples are not an accuracy benchmark.
- No model extraction quality or real hiring decision was tested. Acceptance-to-driver outcome remains **Needs verification**.

### Problems

The main issue is unsupported inference presented as verified information, compounded by inconsistent rules. A higher score is not probability of job performance. Age-based exclusion in existing code is a policy embedded in implementation, not an academically validated predictor; this audit describes it without endorsing it. The feature has no documented dataset, field-level annotation, error analysis or outcome validation.

### Recommendations

Evaluate **extraction**, not an opaque employment ranker. Preserve raw evidence spans/page references, distinguish missing/invalid/uncertain fields, forbid invented identity/license data, and allow human correction before account creation. Use one explicitly justified job-relevant eligibility rubric shared by backend/UI; review arbitrary age criteria rather than optimizing them. Measure field exact match/F1 and missing-field detection on consented or clearly synthetic examples, with disjoint test documents. Use Gemini only where it improves language extraction over regex; do not train a hiring model on these 13 samples.

## 2. Integrated tour ordering

### Current implementation

Source: `B/tournee/service/TourneeRouteOptimizationService.java`, invoked by TourneeService create/update/recalculate and shown by `W/pages/tournees/TourneeDetailsModal.jsx`.

It starts at the first point with coordinates in the loaded list, repeatedly chooses the nearest remaining point by Haversine distance, sums an **open route** and estimates travel at 30 km/h plus five minutes per stop. Missing-GPS points are appended; their travel is not correctly measured. It persists ordered IDs/addresses, km, duration and generated explanation inside Tournee. Explanation text is a template, despite “Agent IA” presentation.

Runtime: a three-point fixture generated 2.8km/21min and the browser displayed the order. The modal explicitly says it “guarantees” minimum distance. Nearest neighbor has no such guarantee. No LLM, learning, road network, depot, truck capacity, time windows or traffic model is used. When points are cleared, old ordered IDs/distance survive and override the response.

### Recommendations

Keep deterministic optimization and label it a heuristic. Define start/end/depot, distance units and service time. Compare initial order, nearest neighbor, NN+2-opt and exact enumeration for small instances using the **same** distance matrix/objective. Report feasible distance improvement, time and optimality gap where exact solutions exist. Road-network distance can be a later optional integration; no LLM is needed to calculate a route.

## 3. Standalone route optimization

### Current implementation

Source: `B/ai/route/service/RouteOptimizationService.java`. It uses a configured Casablanca depot (or request coordinates), nearest neighbor, 2-opt, closed-loop Haversine distance, 40km/h travel speed and 20L/100km fuel. Each full point is assumed to weigh 120kg; missing fill may use 50%. Weight/capacity becomes a displayed fill percentage clipped at 100. Points without GPS are omitted; the 2-opt loop starts at index 1 and leaves the first stop fixed. Explanations/recommendations are templates, not Gemini output. Records are saved to route_optimizations; approving a proposal only updates its status.

Runtime: 240kg was assigned to a 100kg truck; all three points remained in the route, and fill was clipped to 100%. Thus the “Nearest Neighbor + 2-opt (CVRP)” label is not a capacity-constrained vehicle-routing implementation. The request date is not properly propagated to actual scheduling. No active UI exposes this engine; `/optimisation` redirects to the other engine's tour page.

### Recommendations

Consolidate semantics with the integrated engine before adding algorithms. If capacity is in the PFE scope, reject infeasible routes or implement a small actual capacitated assignment/solver with explicit assumptions. Test infeasibility and coverage, not just shorter distance. Fuel/weight remain estimates until measured and validated. This is operations research, not a reason to train an ML model.

## 4. Vision anomaly analysis

### Current implementation

Source: `B/ai/vision/service/ImageAnalysisService.java`, `VisionController`, image/problem models; `M/pages/active_tracking_page.dart`.

The client compresses photos and normally uploads evidence first, then separately uploads the image to the vision API. The server saves image bytes, takes the multipart MIME (or JPEG default), base64-encodes it and requests JSON for normal/full/overflowing/overturned/damaged container, surrounding waste, inaccessible point, obstacle or other anomaly. Outputs include NORMAL/ANOMALIE, type, description, severity and confidence. Types/ranges/consistency are not validated rigorously.

Successful parsing may default missing fields to NORMAL/0.8. Parse failure defaults to NORMAL/0.6; provider failure defaults to **NORMAL/0.7**. Anomalies at confidence >=0.65 create AiProblem. The `auto-create-problem` property is not honored in the creation decision. Problem creation occurs before the image analysis has a persisted ID, leaving a missing reverse association. Driver ID comes from optional request input and mobile omits it. A correction updates the analysis only; problem, severity and confidence can remain inconsistent.

Runtime direct vision call while provider was unavailable returned HTTP 200, NORMAL, confidence0.7. Changing it to CONTENEUR_DEBORDE yielded ANOMALIE but problemCreated false and severity INFO. The main mobile path cannot currently reach vision after its first photo upload fails. The web review page is unrouted; AiProblem has no lifecycle update endpoint.

### Quality assessment and recommendations

This is image interpretation through a general multimodal model, not a locally trained detector. The numeric confidence is an uncalibrated model claim/default. An unavailable model must yield unavailable/unanalysed, never “normal.” Validate image MIME/size/schema; record provider/model/version/error, derive driver identity server-side, preserve human labels separately and reconcile incidents idempotently. Provide normal/anomaly/cannot-tell choices.

Build a small consented labeled photo benchmark covering actual collection conditions, blur/night/occlusion and hard negatives. Report class-wise precision/recall, macro F1, anomaly false-negative rate and abstention coverage. Transfer learning is conditional on sufficient representative images; no image training dataset currently exists. Actual Gemini quality and mobile image MIME compatibility are **Needs verification**.

## 5. Document RAG assistant

### Current implementation

Sources: `B/ai/rag/service/{DocumentLoaderService,ChunkingService,DocumentProcessingService,GeminiEmbeddingService,MongoVectorStoreService,RagService}.java`; DTOs/models; `M/pages/assistant_page.dart`; `W/pages/ai/DocumentManagementPage.jsx` and inactive AssistantPage.

**Ingestion:** upload stores UUID-prefixed original filename and metadata. PDFBox extracts per page, POI extracts DOCX paragraphs (not tables); text/Markdown uses virtual pages of about 2,000 characters, DOCX about 2,500. Chunking is per page, roughly 1,000 characters with 150 overlap, adjusts at sentence boundaries, drops fragments under 20 characters and guesses headings. There is no OCR, robust Arabic normalization, authority/version validation or automatic ingestion of files already on disk. Eight repository document PDFs are broad reference material, not proof of approved local procedures.

**Indexing:** per-chunk embedding calls are sequential. Upload calls `@Async` processing inside the same bean, so the request blocks; a stale metadata object can still return UPLOADED/0 after the database has INDEXED/1. Reindex deletes old chunks before replacement. Failures set FAILED, but there is no resumable job or protection against concurrent delete/reindex.

**Representation:** Gemini embedding failure/placeholder key falls back to normalized, positively accumulated SHA-256 word hashes in 768 dimensions. This is lexical fingerprinting, not semantic language understanding. No backend/model/dimension provenance is stored. Existing true/model embeddings and fallback vectors can therefore be mixed; cosine compares only the shorter length rather than rejecting incompatible spaces.

**Retrieval:** loads all chunks into Java and ranks with `0.4*cosine + 0.6*keywordScore`. Keyword matching uses lowercasing, French stopwords, substring/rough stemming and a `.35 + .65*matchRatio` form when matches exist. This is not BM25 despite the comment. Default threshold .15 and top5 are not empirically calibrated. Whole-corpus scanning is acceptable for a small PFE corpus if measured; lack of a dedicated vector database is not the core problem.

**Generation:** no results → abstention. Otherwise build a French prompt requiring source-grounded answers and attach retrieved chunk metadata/text. Gemini generation is attempted when configured. Offline/error handling chooses keywords and emits fixed official-sounding procedures (including photograph, complaint photo and police intervention) without deriving them from retrieved text. Another fallback branch repeats snippets. The DTO returns `answer`, `sources`, `relevantContextFound`, `maxSimilarityScore`, `question`, `conversationId`; sources are all selected chunks, not checked claim-level citations. Source snippets are shortened (about 180 chars).

**History:** messages/sources are stored in RagConversation. Prior turns are not included in answer generation, so saved history does not produce contextual follow-up understanding. Loading a supplied conversationId does not verify its owner. On a new empty-corpus question, history is saved but the response returns the incoming null ID.

### Runtime reality check

Empty corpus correctly abstained; empty question returned400. A short TXT document was indexed and retrieved. Its text only required logging an inaccessible point with synthetic code AUDIT-42. The fallback nevertheless supplied four “official” steps including photo/police instructions not in that document, while attaching the document as a source (score around .819). A second driver successfully appended to the first driver's conversation. These are demonstrated defects, not hypothetical hallucinations.

### Recommendations

Preserve the existing pipeline but enforce the source-only contract in **all modes**. Offline mode should quote relevant source text or abstain; display mode/provider and distinguish retrieval score from answer certainty. Store embedding identity, reindex the corpus when it changes, disallow mixed spaces, scope conversation IDs, return the saved ID, and support controlled follow-up context. Curate approved local procedures with document date/version/authority before tuning retrieval. Add a human-authored question set with relevant chunk/page labels and answerable/unanswerable cases. Measure Recall@k/MRR, citation support, unsupported-claim rate, abstention quality, latency and cost; compare lexical baseline, embeddings and hybrid search.

## 6. Operational supervisor assistant / statistics

### Current implementation

Sources: `B/ai/assistant/service/AssistantService.java`, `service/tools/AssistantTools.java`, ChatConversation. It reads live Mongo counts and details (including drivers' CIN/phone and candidate information), combines them with hardcoded procedural knowledge, and sends a prompt plus the last five messages to Gemini. It is not document retrieval, not a function-calling agent and does not execute a planning loop. Fallback intent rules return counts/templates. `/statistics/summary` is deterministic aggregation, not predictive AI.

The fallback executed with real audit counts but described a pending mechanical complaint after that complaint had already been treated; the tool counts all such complaints. Total driver counts include inactive records. Tour selection uses the first ten without a defined relevant chronological ordering. An arbitrary conversation ID is loaded without ownership validation. Empty provider output is not robustly treated as failure. No active web/mobile caller reaches this assistant.

### Recommendations

Keep it optional until the mobile RAG and core workflow are strong. If exposed, limit prompt context to necessary aggregates/authorized records, define count semantics, bind chat ownership, include timestamps and constrain narrative to tool-returned facts. A deterministic summary suffices for basic counts; LLM value would be explaining a real exception or summarizing linked evidence. Measure factual consistency against the same DB query results.

## Evaluation currently missing

There are no recorded extraction benchmarks, retrieval judgments, vision labels/confusion matrices, route optimality comparisons, prompt/version experiments or training/test separation. Curated CV filenames and reference PDFs are input examples, not evidence of model accuracy. A convincing PFE needs reproducible experiments and honest limitations more than additional features labeled AI.
