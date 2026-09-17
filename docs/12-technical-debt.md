# Pragmatic technical audit

## Current state and problems

This review focuses on correctness, maintainability and PFE reliability. It is not an enterprise compliance assessment.

| Issue | Evidence and impact | Practical correction | Complexity |
| --- | --- | --- | --- |
| Unsafe credential lifecycle | Public reset-all; startup loops over drivers and resets BCrypt(CIN)/actif=true; User.isEnabled always true | Restrict/remove public reset path; make seeds one-time/demo-specific; enforce actif; offer intentional supervisor reset | Medium |
| Missing object ownership | Tour status/photo/detail and conversation-ID operations accept another user's object | Bind user from JWT and check assignment/owner in service; keep supervisor permissions explicit | Medium |
| Public uploads and unguarded WS | SecurityConfig/WebConfig permit uploads and `/ws/**`; anonymous STOMP connection observed | Authorize sensitive file access and tracking channels; avoid public CV delivery paths | Medium |
| Literal credentials | application.properties contains SMTP password/JWT secret | Move values to environment/local ignored config; rotate if repository/shared copies exposed; no secret values in docs | Low |
| Photo transfer destination | Relative path with MultipartFile.transferTo fails in packaged Tomcat | Resolve one absolute storage root, create parent, validate type and ownership; return clear upload errors | Low/medium |
| Tour lifecycle and referential integrity | V07–V12: invalid references/transitions, old finish time, no truck update, stale points | Enforce finite transitions and existing active resources; sync availability; clear all derived fields when inputs change | Medium |
| Missing numerical validation | Invalid GPS/fill accepted; capacity values insufficiently constrained | DTO bounds plus service domain checks; avoid treating estimates as measurements | Low |
| Direct entity/map input | Reclamation accepts null title and potentially caller ID; raw map decisions/confirms | DTOs, @Valid, allowed enum values; server-owned IDs/status/identity; null-safe clients | Low/medium |
| Mobile state contract | Flat login vs nested user; home/detail based on passed maps; no active-tour resume | One response mapping and server re-fetch; reconstruct execution state from persisted tour/visits | Medium |
| Mobile timer/permission lifecycle | Tracking timer not stopped by logout/dispose; stops before final status API; errors swallowed | Tie tracking to session/tour lifecycle, recover on failure, report permission errors; device test | Medium |
| Environment coupling | Hardcoded LAN/localhost, Windows local.properties, relative uploads, mixed geography | Configurable hosts/storage/depot; documented clean setup; verify on intended demo device/network | Low/medium |
| AI errors treated as success | NORMAL fallback and fixed RAG procedures | Typed provider outcome, unavailable state, faithful fallback; tests of provider timeout/invalid response | Medium |
| AI outputs weakly validated | JSON strings/ranges unchecked, no model/prompt provenance | Validate schemas/consistency and persist method/version/error/human override | Medium |
| Two competing route engines | Different origin/return/speed/stop-time assumptions and stores | Shared route definition/engine; expose proposal lifecycle only if needed | Medium |
| Duplicated CV scoring | Frontend and backend independently calculate different criteria | Backend returns versioned criterion breakdown; frontend renders it | Low/medium |
| Candidate creation ambiguity | Fabrication + swallowed creation exceptions, no agentId | Require verified missing fields; explicit success/failure link; idempotent acceptance | Medium |
| RAG async/provenance/history | Self-call @Async; mixed vector spaces; owner unchecked; history not grounded | Separate processing bean or honest sync contract; store embedding identity; scope histories and return saved ID | Medium |
| Vision incident consistency | Problem created before analysis ID; corrections do not reconcile; unused auto-create flag | Save analysis first and manage one linked incident; preserve original output/human correction | Medium |
| Misleading/stale dashboard | Static tonnes, sample critical alerts, truck status disconnected | Use measured series only or clear demo state; synchronize resource states | Low/medium |
| Tracking state weakly defined | Mutable process map; no expiry; client vehicle identity; fuel75 | Bind identity; last-seen/stale state; serialize concurrent updates safely; persist only if history needed | Medium |
| Missing global UI error feedback | Toaster only login; null title crashes whole page; no route error boundary | Mount feedback in app shell, handle null data and bounded errors | Low |
| Tests/build portability | Stale Java/Flutter tests; copied node_modules; no frontend behavior tests | Clean install procedure and focused real workflow tests; remove/update obsolete tests deliberately | Low/medium |
| Broad classes/dead code | ~890-line CV service, large active tracking page, unused pages/starter app | Extract only cohesive functions while fixing defects; archive duplicate starters after decision | Medium, later |

### Architectural choices worth retaining

Controllers/services/repositories, MongoDB, React hooks/Context, Flutter services and a local upload directory are adequate for the stated PFE scope. Synchronous APIs and an in-process broadcaster are acceptable for a small controlled demo if their limitations are explicit. A simple Java retrieval scan can be measured before introducing any specialized database.

## Recommendations

Fix observable correctness and label/provenance defects first. Add focused tests where failures would break the demo or corrupt workflow state. Avoid broad rewrites, framework migrations, microservices, Kubernetes, a distributed queue, or elaborate monitoring without a measured need. The roadmap orders these changes and defines reviewable completion conditions.
