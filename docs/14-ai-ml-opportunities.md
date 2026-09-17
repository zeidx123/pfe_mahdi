# Realistic AI/ML opportunities

## Current state and data constraints

There is no trainable project dataset established by the repository. Thirteen CV samples, broad reference PDFs and fields named fuel/fill/weight do not amount to observed operational labels. Tracking has no history, collected weights are not recorded, and many “measurements” are estimates or constants. Recommendations below are proposals, not current capabilities. Dataset counts are planning targets for pilot studies, not guarantees of statistical adequacy.

## A. Language-model work with immediate value

| Opportunity | Product problem and proposed method | Data / evaluation | Inference and feasibility |
| --- | --- | --- | --- |
| Faithful procedural RAG | Driver needs an answer supported by an approved procedure; preserve existing retrieval and generate only supported instructions | Start with 10–30 short authoritative local documents and 50–100 human-reviewed questions, including unanswerable/out-of-scope/multiturn cases; label relevant pages and acceptable answers; Recall@k, MRR, citation support, unsupported-claim rate, abstention precision/recall, latency | Existing Spring pipeline; lexical vs model embeddings vs hybrid ablation; highly feasible after fallback/schema fixes |
| Verifiable CV extraction | Supervisor needs fields extracted accurately, not invented; Gemini JSON + evidence spans + human correction | Pilot 30–50 varied synthetic/consented CVs, expanding toward100+ if available; field labels, missing/invalid labels and page spans; exact match/F1, unsupported-field rate, correction rate, latency/cost | Existing PDFBox/Gemini service with schema validation; compare regex baseline. No need for a trained hiring score |
| Evidence-based tour summary | Supervisor wants concise summary of actual events | Only after visits/incidents/times are persisted; manually judge factual support, omissions and time saved on 20–30 representative tours | Deterministic aggregates → constrained text summarization; optional because current PDF already covers simple reporting |

For RAG, split tuning and held-out questions, avoid near-duplicate paraphrases leaking between splits, and keep source corpus/version fixed for each experiment. Include missing-provider tests. Check that the supposed correct answer is actually in the corpus; retrieval quality cannot fix nonexistent procedures.

For CV extraction, test accents, layout changes, multi-column text, conflicting dates, non-Tunisian formats, eight-digit phone/CIN ambiguity and missing fields. Human decisions remain separate from extraction metrics. Do not use a learned employment ranking target based on arbitrary existing scores.

## B. Classical ML / trained-model candidates

| Candidate | Target and product value | Features and labels | Possible method / training process | Dataset planning and current sufficiency | Metrics / inference / decision |
| --- | --- | --- | --- | --- | --- |
| Tour-duration prediction | Predict elapsed minutes to improve schedule estimates | Pre-departure route road distance/stop count/day/time/zone/vehicle type; labels actual start/end minus defined breaks; exclude post-outcome features | Baseline distance/speed+stop time; linear/Ridge regression; compare small tree/gradient boosting model; grouped chronological split, missing-data rules, tuning only on train/validation | Pilot200–500+ complete, consistent real tours across dates/routes; currently **not enough data** and timing/state defects contaminate labels | MAE/median AE, error by route/period, prediction interval coverage; small serialized model invoked from existing backend or offline estimates only after real benefit demonstrated; defer |
| Point fill forecasting | Decide when a point may need collection | Timestamped actual sensor/manual fill, elapsed time since collection, day/zone; label next measured fill or threshold-crossing time | Last-value and seasonal baselines, simple regression; chronological per-point validation; no future-fill leakage | Many weeks/months per point across tens of points; illustrative pilot20–50points ×8–12weeks only if collection possible; current fill overwritten/manual with **no time series** | MAE and overflow-event recall/false alarms; batch forecast stored for planner; **do not implement now** without reliable measurements |
| Image normal/anomaly classification | Flag collection evidence for human review | Consented point photos with independently labeled classes and cannot-tell label; include lighting, blur, occlusion, multiple locations | Compare Gemini zero-shot and simple human baseline; if sufficient data, transfer-learn MobileNet/EfficientNet with train-only augmentation; split by location/day to avoid duplicate leakage | Pilot200–500 images for exploratory error analysis; potentially1,000+ diverse images for transfer learning; required size depends on class diversity/balance; current repository has **no labeled image corpus** | Macro F1, per-class precision/recall, anomaly false-negative rate, confusion matrix, abstention/latency; human confirms; trained deployment optional after evaluation |
| Complaint type assistance | Suggest category during text complaint entry | Complaint text and human-corrected PANNE/ROUTE/BAC/AUTRE label; keep identity out | Keyword baseline → TF-IDF + logistic regression/linear SVM; stratified split plus duplicate grouping | Several hundred diverse labeled complaints with enough examples per class; currently absent; manually selecting four categories is already simple | Macro F1, per-class recall, correction/time saved; optional and low priority unless users actually need it |
| Predictive truck maintenance | Predict a defined failure horizon | True sensor history, usage and maintenance/failure labels | Cannot select a defensible model before data/target definition | ESP32 ID and dates alone are insufficient; no telemetry/failure dataset exists | **Not recommended in current scope**; do not train from fixed fuel/sample alerts |

No recommendation assumes synthetic data proves a model generalizes. Synthetic route instances are legitimate algorithm benchmarks under stated assumptions. Synthetic CVs are useful parser regression fixtures. Simulated duration/fill records can illustrate a pipeline, but any reported accuracy must be explicitly “on synthetic data,” with no real operational claim.

### A bounded ML process if data becomes available

1. Define the target, unit, observation timestamp and how a label is obtained.
2. Collect consenting/appropriate data, document missingness and remove duplicate/leaking samples.
3. Freeze training/validation/test splits by time or source as appropriate.
4. Compare the existing deterministic baseline before trying a more complex model.
5. Report confidence/variability and error slices, not just one average metric.
6. Store model version and feature schema; serve a small validated artifact in the existing system only if it improves the product. An offline notebook for experiments would be proposed new work, not an existing component.

## C. Hybrid approaches with a clear role for each part

| Proposed pipeline | Why each component exists | Gate before implementation |
| --- | --- | --- |
| Retrieval → grounded LLM → source checks / abstention | Retrieval finds approved evidence; LLM formulates an answer; validation prevents unsupported procedures | Curated corpus and evaluation set; no fixed factual fallback |
| LLM field extraction → deterministic validated rubric → human decision | Language model handles layout/text; code applies explicit criteria; human verifies identity/license and decides | Single rubric and evidence-backed fields; no invented data or automatic learned hiring decision |
| Deterministic route optimizer → optional duration regressor → factual explanation | Optimizer enforces routing constraints; regressor estimates travel/service duration if measured data exists; explanation states assumptions/results | Unified engine and enough real duration labels; an LLM is unnecessary for a simple route explanation |
| Vision classification → human correction → labeled evaluation corpus | Model suggests, operator verifies, stored correction improves evaluation | Working image upload, unavailable state, consent/provenance, separate prediction/label records |

## Recommendation order

First repair AI truthfulness and operational data capture. Then evaluate RAG and route algorithms. Add CV extraction metrics. Only pursue a trained image/duration model when the data gate is met and enough PFE time remains. Forecasting, maintenance ML and elaborate agent orchestration presently offer less value than making the existing pipelines correct and measurable.
