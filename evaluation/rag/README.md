# RAG evaluation

Two runners serve different purposes. The seed test checks BM25 arithmetic without a provider. The supervisor comparison runner measures lexical, semantic and hybrid retrieval against a saved approved corpus and optionally captures generated answers. Neither creates independent human labels.

## Authored lexical seed

`BACK/src/test/resources/pfe/rag-seed.json` contains 10 synthetic development questions. Its labels have **not** been independently reviewed. The source text is educational, not an approved municipal protocol, and is never imported into the running application.

```sh
mvn -f BACK/pom.xml -Dtest=RagSeedEvaluationTest test
```

`BACK/target/pfe-evaluation/rag-lexical-seed-results.json` reports BM25 Recall@3, MRR@3, retrieval latency and empty retrieval for unanswerable cases. A manually reviewed fixture with the same shape can be supplied with `-Dpfe.rag.fixture=/absolute/path/reviewed.json`. This seed runner does not measure semantic retrieval, generation or citation support.

## Reproducible approved-corpus comparison

Use a dedicated demonstration backend with approved staged/consented documents. `GESTI_EVAL_TOKEN` must contain a supervisor bearer token. Do not put tokens or provider keys in manifests or artifacts. The endpoints reject drivers and anonymous users; they read only approved, usable text and do not write chat histories or alter documents.

Export the corpus before labelling questions:

```sh
python3 scripts/evaluation/evaluate_rag.py corpus \
  --base-url http://127.0.0.1:8080/api --output /tmp/rag-corpus.json
```

The export contains real chunk IDs, literal passages, source metadata, content hashes, index compatibility and model/settings metadata. It intentionally contains document text: retain it with the same care as the approved source documents. It contains no provider key or embedding values. A SHA-256 fingerprint includes source references, text and actual stored vectors, so content edits or reindexing invalidate an earlier capture.

Create a manifest using `development-manifest.template.json` as a starting point. Its three authored questions have null/unreviewed labels and must remain development cases until independently reviewed. For a reviewed case, supply:

```json
{
  "datasetVersion": "reviewed-rag-v1",
  "cases": [{
    "id": "q-001", "split": "evaluation", "groupId": "conversation-001",
    "question": "Question based on the approved corpus", "history": [],
    "labels": {
      "independent": true, "reviewer": "reviewer-id", "reviewDate": "YYYY-MM-DD",
      "answerable": true,
      "relevantChunkIds": ["actual-chunk-id-from-export"],
      "expectedPassages": [{"chunkId": "actual-chunk-id-from-export", "quote": "Exact supporting passage from that chunk"}]
    }
  }]
}
```

Do not copy those placeholder IDs or labels into results. `answerable`, `relevantChunkIds` and reviewer metadata can remain null while preparing the experiment; the summary reports these as missing, never as correct or incorrect. A reviewed unanswerable question uses `answerable:false` and `relevantChunkIds:[]`. Relevant IDs and expected quotations are checked against the approved export before provider calls.

Follow-ups use at most six fixed history turns with `sender:USER|ASSISTANT` and `content`. Each mode receives the same history. This isolates retrieval/generation comparisons without creating or contaminating real conversations. Keep related questions in the same `groupId` and split; the runner rejects a group shared across splits. Include standalone topic changes as well as explicit anaphoric follow-ups.

Capture all three methods:

```sh
python3 scripts/evaluation/evaluate_rag.py capture \
  --base-url http://127.0.0.1:8080/api \
  --manifest /absolute/path/manifest.json --output /tmp/rag-results.json --generate
```

Omit `--generate` for retrieval-only evaluation. `--modes LEXICAL` permits a lexical-only run without embedding calls. A single comparison request snapshots one corpus, generates at most one real query embedding, and reuses it for semantic and hybrid retrieval. With generation enabled it makes at most one generation call per mode with relevant context. It uses the application's prompt and citation validator. Explicit lexical generation is an evaluation baseline; it does not change mobile fallback behavior.

- `LEXICAL`: BM25, k1=1.2 and b=0.75.
- `SEMANTIC`: cosine retrieval on compatible stored model/dimension/version metadata and valid nonzero vectors.
- `HYBRID`: reciprocal-rank fusion, k=60, over lexical and semantic rankings.

Scores are ranking scores, not confidence percentages. Compare the same `topK` and cosine threshold recorded in the artifact. The configured generation/embedding models and prompt/index versions are saved rather than assumed.

`retrievalStatus` distinguishes `AVAILABLE`, `EMPTY_CORPUS`, `INDEX_NOT_READY`, `INDEX_INCOMPLETE` and `PROVIDER_UNAVAILABLE`. A partial index or failed embedding call cannot count as a successful semantic/hybrid benchmark. Hybrid lexical fallback is retained as operational evidence and explicitly marked with `effectiveMode:LEXICAL`. No synthetic/hash embeddings are created.

`generationStatus` distinguishes `ANSWERED`, `INSUFFICIENT_INFORMATION`, `NO_CONTEXT`, `NOT_REQUESTED`, `PROVIDER_UNAVAILABLE`, `INVALID_RESPONSE` and `SKIPPED_RETRIEVAL_UNAVAILABLE`. The normal four response states and literal excerpt fallback are captured separately. The report stores retrieved full passages, validated answer/citations, source IDs, actual retrieval query, history and latencies. Invalid provider replies are not presented as valid generated answers.

Every case submits the initial corpus fingerprint. Corpus/configuration drift stops the run with partial results saved. HTTP redirects are rejected so the supervisor token cannot be forwarded to another address. HTTP/network errors are recorded without raw provider bodies or headers; there are no automatic retries. Restart a failed run explicitly as a new run. The implementation is sequential and bounded; generation can take up to three configured provider timeouts per question.

## Independent answer review and metrics

```sh
python3 scripts/evaluation/evaluate_rag.py review-template \
  --results /tmp/rag-results.json --output /tmp/rag-reviews.json
```

This creates **empty** labels for `ANSWERED` responses. A reviewer examines the original approved sources, question and captured answer. Set reviewer/date/independent explicitly; record `answerCorrect` if assessed. Enumerate every factual/procedural claim under `claims:[{"text":"...","supported":true|false|null}]`. Set `claimsComplete:true` only after every claim has been checked. For each captured citation index, label `supportsAnswer:true|false|null`; literal quotation existence alone does not establish semantic support. Keep uncertainty null rather than guessing. `reviewVersion` should identify the reviewed file version. Reviews are bound to the run and response fingerprint; stale or unmatched reviews are rejected.

```sh
python3 scripts/evaluation/evaluate_rag.py summarize \
  --results /tmp/rag-results.json --reviews /tmp/rag-reviews.json --output /tmp/rag-metrics.json
```

Omit `--reviews` to report retrieval/availability and explicit missing answer-review counts. Summarization makes no provider calls. Outputs are separated by split and requested mode and include:

- Recall@K, MRR@K and optional literal supporting-passage recall over independently labelled, available retrieval cases.
- Empty retrieval on independently labelled unanswerable questions.
- Answerable abstention, correct abstention and answers to unanswerable questions over valid available generation/no-context outcomes. Provider failures, invalid responses and incomplete indexes are reported separately and do not count as correct abstention.
- Grounded/unsupported claim rates, citation support, optional answer correctness and all relevant reviewed/missing denominators. Partial claim reviews remain explicitly incomplete.
- Response/retrieval/generation state counts; retrieval, generation and per-mode latency mean/median/p95. Semantic/hybrid total latency includes the shared embedding latency; do not sum those totals as wall-clock request time.

Null metrics mean no eligible labels/cases, not zero accuracy. Do not report authored/unreviewed examples as a final benchmark. Aim for approximately 50–80 carefully reviewed questions, including unsupported procedures, ambiguous requests, follow-ups, topic changes, revoked documents and provider outages. Counts are targets. Freeze corpus/model/prompt settings, keep development and final questions separate, and report model availability alongside answer quality.

## Checks

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s evaluation/rag -p 'test_*.py'
mvn -f BACK/pom.xml -Dtest=RagEvaluationServiceTest,RagEvaluationControllerTest,RagServiceTest,MongoVectorStoreServiceTest test
```

These checks use synthetic values or mocked providers. They cover approval/access, mode separation, one shared query embedding, lexical-only operation, index/provider failures, citation rejection, corpus drift, split separation, independent-review denominators and missing/stale review handling. They do not establish live model accuracy.
