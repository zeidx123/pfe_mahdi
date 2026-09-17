# AI/ML architecture

## Current state

There are **four provider-oriented areas** (CV extraction, vision, document RAG, supervisor chat) and **two deterministic route implementations**. There is no trained local model, training pipeline, model artifact, evaluation dataset or implemented autonomous agent loop. A class named “tools” builds context; it is not LLM function calling.

`B/ai/common/GeminiClient.java` uses OkHttp to call the configured Google REST `generateContent` endpoint. Text calls send a prompt in `contents.parts.text`; vision adds base64 `inline_data` and MIME type. Jackson extracts response text, strips/parses model JSON in callers, and exceptions are wrapped as AiServiceException. Requests do not use a strongly enforced provider response schema. Configured text/vision model: `gemini-1.5-flash`; key: placeholder. Model availability and actual provider behavior are **Needs verification**. The audit did not send CVs or photos to an external provider.

GeminiEmbeddingService implements a separate embedding REST path configured for `text-embedding-004`. Missing/failed credentials invoke a 768-dimensional word-hash fallback. Consumers are not told which representation or generation mode was used.

### CV screening

```mermaid
flowchart TD
  F[Supervisor PDF upload] --> P[PDFBox text extraction]
  P --> S[Save original PDF locally]
  P --> G[Gemini extraction prompt: first 8000 characters]
  G --> J[Parse JSON into Candidate fields]
  G -->|Provider exception| L[Local regex extraction]
  J --> C[Cleanup and inferred fields]
  L --> C
  C --> E[Eligibility checks + fixed point rubric]
  E --> DB[(Mongo candidates)]
  DB --> W[Web score/details/decision]
  W -->|ACCEPTE| A[Attempt driver creation and welcome email]
  A --> U[(Mongo users)]
```

Malformed model JSON can produce an empty Candidate rather than correctly invoking local extraction. Driver creation has no persistent candidate-to-user link and may silently fail.

### Integrated route engine — active tour workflow

```mermaid
flowchart LR
  R[Create / edit / recalculate tour] --> P[Load points]
  P --> N[First valid point + nearest neighbor]
  N --> H[Open-path Haversine sum]
  H --> T[30 km/h + 5 min per stop]
  T --> X[Fixed textual explanation]
  X --> DB[(Tournee optimized fields)]
  DB --> W[Tour modal and mobile]
```

No Gemini call is made. Points lacking GPS are appended without a complete travel estimate. This engine does not use a depot or truck capacity.

### Standalone route engine — separate proposal API

```mermaid
flowchart LR
  R[Truck / points / optional depot] --> P[Load resources, omit points lacking GPS]
  P --> N[Nearest neighbor from depot]
  N --> O[2-opt improvement]
  O --> H[Closed-loop Haversine distance]
  H --> E[40 km/h; 20 L per 100 km; 120 kg per full point]
  E --> DB[(route_optimizations)]
  DB --> V[Proposal status update only]
```

No Gemini call, road network, capacity-constrained assignment or multi-vehicle routing is implemented. The route proposal is not converted into a Tournee by validation.

### Vision

```mermaid
flowchart TD
  M[Mobile point photo] --> U[Tour evidence upload]
  U -->|Only after upload success| V[Vision API saves image]
  V --> G[Gemini image + category prompt]
  G --> J[Parse status/type/confidence/severity]
  G -->|Provider failure| N[Fixed NORMAL, confidence 0.7]
  J --> C[Anomaly and confidence threshold 0.65]
  C -->|Threshold met| P[Create AiProblem before analysis gets ID]
  C --> A[(image_analyses)]
  P --> A
  N --> A
  A --> D[Driver confirm / correct dialog]
  D --> H[Update analysis only]
```

The first upload fails in the audited packaged backend. Direct vision endpoint calls demonstrate its fallback, not successful classification. The human correction does not reconcile the linked problem.

### Document ingestion and retrieval

```mermaid
flowchart TD
  F[PDF / DOCX / TXT / MD upload] --> D[(ai_documents + original file)]
  D --> L[PDF pages / DOCX paragraphs / text virtual pages]
  L --> C[1000-character chunks, 150 overlap, page/section metadata]
  C --> E[Gemini embeddings or local hashed word vectors]
  E --> V[(document_chunks with float arrays)]
  Q[Question] --> QE[Same embedding service]
  QE --> S[Load all chunks; hybrid cosine/keyword ranking]
  V --> S
  S --> K[Top 5; minimum score 0.15]
  K -->|No context| A[Abstain]
  K -->|Context exists| P[Grounding prompt + retrieved content]
  P --> G[Gemini text]
  P -->|Unavailable| H[Keyword-based fixed procedure or snippet fallback]
  G --> R[Answer + retrieved source metadata]
  H --> R
  A --> DB[(rag_conversations)]
  R --> DB
  R --> M[Mobile assistant / inactive web page]
```

Despite an `@Async` annotation, upload self-invokes processing and runs inline. Reindex invoked by its controller crosses the proxy. No durable index job exists. Vector search is a Java scan, not a Mongo vector index. Conversation history is saved/displayed but not passed to the RAG generation prompt.

### Operational supervisor assistant

```mermaid
flowchart LR
  Q[Supervisor chat API] --> T[AssistantTools reads repositories]
  T --> C[Counts/details + hardcoded procedures]
  H[(chat_conversations)] --> P[Prompt + last 5 messages]
  C --> P
  P --> G[Gemini generation]
  G -->|Failure| F[Keyword intent templates]
  G --> S[Save assistant message]
  F --> S
  S --> H
```

This is a separate system from document RAG, with different storage, response contract and grounding. Its API returns a ChatConversation, not a RagResponse. No active client calls its chat endpoint.

## Problems

Output provenance is lost: “model unavailable,” “regex extraction,” “lexical retrieval,” “fixed procedure,” and “successful inference” are not consistently distinct records or UI states. Model JSON is loosely parsed. There are no recorded prompt versions, retrieval experiments, extraction accuracy, image confusion matrices, latency distributions or route baseline comparisons.

## Recommendations

Make provenance and failure states part of existing DTOs and documents; add small reproducible evaluation fixtures. Use LLMs for text/image interpretation, deterministic code for explicit rules and routing constraints, and postpone trained models until a meaningful labeled dataset exists. See [09](09-ai-feature-audit.md) for exact defects and [14](14-ai-ml-opportunities.md) for feasible experiments.
