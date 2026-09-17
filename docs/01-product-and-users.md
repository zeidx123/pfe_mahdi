# Product and users

## Current state

The operational problem is coordinating municipal collection work: maintain resources, assign a driver and truck to points, follow execution, record incidents and provide procedural help. Names in the source include GestiCollecte and SmartWaste. Geographic assumptions are mixed: Tunisian CV/CIN rules and mobile coordinates coexist with a Casablanca route depot and `.ma` seed identity. No tenant or municipality boundary is modeled.

| Actor | Implemented capabilities | Entry point | Important limitation |
| --- | --- | --- | --- |
| Supervisor (`SUPERVISEUR`) | Manage drivers/trucks/points/tours; inspect tracking; reply to complaints; upload/review candidates; administer documents; download PDF | React `/login`, protected layout | Some backend AI/alert functionality has no reachable page |
| Driver (`CHAUFFEUR`) | Login by email or matricule; list own tours; change tour status; send photos/GPS; submit/read own complaints; ask RAG | Flutter onboarding/login/home | Backend allows access/mutation beyond assigned tours; mobile identity contract is wrong |
| Candidate | No application account or self-service portal | Supervisor uploads candidate's PDF | Human screening workflow, not candidate-facing recruitment |
| Unauthenticated visitor | Login and static uploads; exposed reset and WS paths | API/public resources | Public bulk reset and unguarded WS are defects, not intended roles |

`ADMIN` and `AGENT` appear in a few authorization expressions but cannot be assigned through the actual `Role` enum. There is no citizen, analyst, maintenance technician or dedicated dispatcher account type.

### Intended workflow and actual implementation

1. **Prepare resources:** supervisor creates a driver, truck and collection points. Mongo persistence and several DTO validations work. A driver's initial password is their CIN; welcome email is attempted asynchronously. No self-service password-change endpoint exists despite the email instruction.
2. **Plan a tour:** select a date, available truck, active driver and points. Backend computes a nearest-neighbor order and estimate. It does not enforce resource existence, schedule collisions, license validity, capacity or truck availability itself.
3. **Execute collection:** driver opens assigned tour and starts it. Mobile sends GPS, prompts for photos near points and requests image interpretation. Upload failure currently blocks the normal evidence/vision/finalization sequence. Reopening an already active tour has no equivalent resume flow.
4. **Supervise:** web tracking loads last positions and subscribes to broadcasts. It shows real synthetic API updates in the audit, but does not distinguish stale vehicles and has no persisted journey history. Dashboard resource counts are real; the weekly tonnes graph is static.
5. **Handle an incident:** driver submits a text complaint; supervisor replies; the driver can fetch the reply. This works through API calls. There is no complaint photo field, association to a tour/point/truck, or notification delivery mechanism.
6. **Ask for guidance:** driver asks the RAG assistant over administrator-uploaded documents. Parsing, chunking, indexing, search and conversation persistence exist. Fallback answers can contradict the promised source-only behavior.
7. **Screen applicants:** supervisor uploads a text PDF, receives extracted fields and a rule-based score, and records a decision. Acceptance attempts to create a driver but does not reliably report failure or preserve a candidate-to-driver relationship.

### Product value and technical interest

The project connects planning with field activity across two clients. Its strongest engineering demonstrations are an actual cross-client workflow, geospatial ordering, live position broadcasting, document ingestion/retrieval and human review of machine-generated extraction. AI is most useful for interpreting unstructured text/photos, not for CRUD, capacity arithmetic or shortest-path claims.

## Problems

The supervisor sees several quantities that are estimates or samples presented as operational facts. The driver can lose continuity after navigation/restart. AI errors are often converted into apparently successful decisions. These issues directly weaken the product story during a PFE defense.

## Recommendations

Define one municipality/demo geography, a documented tour lifecycle, honest measurement labels, and one complete supervisor → driver → supervisor scenario. Keep candidate decisions under human review, based on verified job-relevant information; extraction uncertainty must be visible rather than replaced with invented identity/license data.

Sources: `B/user/model/Role.java`, `B/GestionTourneesApplication.java`, `W/routes/AppRoutes.jsx`, `M/main.dart`, `M/pages/active_tracking_page.dart`, `B/ai/cv/service/CvAnalysisService.java`.
