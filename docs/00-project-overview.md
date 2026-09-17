# Project overview

## Current state

**GestiCollecte** is a municipal waste-collection management prototype. A supervisor uses a React web application to manage drivers, trucks, collection points and tours, inspect positions, handle complaints, screen uploaded CVs and administer a document corpus. A Flutter application lets a driver view assigned tours, start collection, transmit GPS positions, photograph collection points, submit complaints and question a document assistant.

One Spring Boot application serves both clients over REST, broadcasts vehicle positions through STOMP/SockJS, stores operational and AI records in MongoDB, and writes uploads to local disk. Optional Gemini calls support text extraction, text generation, embeddings and image interpretation. Several algorithmic or fixed fallback outputs are displayed with AI terminology.

This is a substantial integration prototype: it contains real business persistence, authentication, route heuristics, document ingestion/retrieval and mobile workflow code. It is **not yet a reliable complete field demo**, and there is no trained or quantitatively evaluated project-specific ML model.

### Scope actually present

- Drivers are `User` documents with role `CHAUFFEUR`; supervisors have `SUPERVISEUR`. There is no separate administrative role in the role enum.
- Resource management, tour planning, estimated route order, live latest positions, text complaints and PDF tour reports exist.
- CV screening, document RAG, vision analysis and a separate operational assistant exist in backend source. Their exposure and reliability differ substantially.
- No citizen application, real sensor firmware, dispatch worker, road-routing integration, forecasting model, training service, notebook or model registry was found.
- No Docker/Compose deployment or schema migrations were found. Mongo mapping annotations and Java startup code define the initial data shape and seeds.

### Evidence and limitations

The audit used the existing Graphify graph for discovery, then traced source implementations, callers, DTOs, repositories, configuration, prompts and error branches. It inspected the three applications, manifests, mobile platform configuration, scripts, existing tests, reference PDFs and generated/abandoned artifacts. A file inventory is provided rather than claiming every generated binary was reverse engineered.

Backend and frontend were run against **disposable MongoDB on port 27018**, separate from the existing localhost:27017 database. Gemini and SMTP were disabled with loopback overrides. API probes and browser interactions used synthetic records. Consequently, provider quality, email delivery and physical-device behavior are **Needs verification**. Build success is not used as evidence of camera/GPS success.

### Main findings

1. Basic CRUD, JWT login, assigned-tour queries, heuristic order computation, complaint replies, document indexing, report generation and REST-to-WebSocket position updates execute.
2. Point/final-photo uploads return HTTP 500 in the packaged backend; the mobile collection workflow depends on them.
3. Driver ownership, state transitions and deactivation are not enforced consistently. An anonymous bulk password reset endpoint is exposed.
4. Dashboard tonnes, mobile fuel and startup alerts are fixed/sample values without an adequate distinction in normal screens.
5. Missing AI service availability is hidden behind confident-looking outputs: vision returns NORMAL/0.7; RAG can return fixed procedures unsupported by its cited text.
6. The web assistant and several other developed pages are not in the active route tree. Mobile login reads a nonexistent nested `user` field.
7. The backend test suite has one error among three tests; the mobile starter test fails. The clean frontend and Flutter web builds succeed.

## Problems

The most important gap is the difference between labels and demonstrated behavior: “optimal,” “CVRP,” “AI confidence,” “official procedure,” “online,” and “tonnes collected” imply stronger guarantees or data sources than the implementation supplies.

## Recommendations

Preserve the monolithic backend, React client, Flutter client and MongoDB. First make the complete driver tour reliable and make fallback/data provenance explicit. Then build measurable experiments around RAG and route algorithms. See [the roadmap](15-improvement-roadmap.md); no redesign is implemented by this audit.
