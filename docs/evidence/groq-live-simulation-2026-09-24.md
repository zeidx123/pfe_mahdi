# Groq live workflow evidence — 24 September 2026

A real Groq response was displayed in the Chrome driver workflow for tour **DEMO-GROQ-0924**. One public street-litter photograph received **NOT_CLEANED** in **6,499 ms** browser round-trip time. This records one successful provider/application interaction, not an accuracy benchmark.

## Recorded behavior

| Check | Observed result |
| --- | --- |
| AFTER evidence | Two AFTER photos uploaded; automatic analysis disabled. The first upload created no analysis. |
| Manual inference | One manual model request; one new saved analysis (7 → 8). |
| Same evidence requested again | Existing analysis ID returned in 76 ms; no additional analysis/model call. |
| Reload/resume | AFTER photo, ARRIVED progress, NOT_CLEANED assessment and manual mode restored. |
| First point | ISSUE_REPORTED, with the original NOT_CLEANED prediction. |
| Second point | COMPLETED_REPORTED with AFTER evidence and no AI analysis. This is a driver declaration, not an AI-cleanliness validation. |
| Finish | Return to depot confirmed in the staged interface; tour TERMINEE, truck DISPONIBLE. |
| Supervisor review | Both evidence images loaded; review label NOT_CLEANED saved separately while preserving the original prediction. The review was entered by automation to verify the workflow, not by an independent benchmark reviewer. |

The two uploads led to **one model request instead of two automatic analyses**: one call avoided in this specific simulation. Repeating the first analysis action reused its valid saved assessment. This is a demonstrated request count, not a general quota or cost-saving estimate.

## Actual provider response

- Provider: `GROQ`
- Model: `qwen/qwen3.8-27b`
- Prompt: `point-cleanliness-v1`
- Input: `AFTER_ONLY`
- Prediction: `NOT_CLEANED`
- Confidence: absent; no percentage was invented.
- Browser round trip: 6,499 ms, including application/network overhead rather than isolated model execution.

> Le point de collecte est visiblement sale. De nombreux déchets, y compris des sacs en papier, des gobelets en plastique, des boîtes et des canettes, sont éparpillés sur le trottoir et la chaussée autour de la poubelle.

The photo is [Litter on Carling Street in London, Ontario](https://commons.wikimedia.org/wiki/File:Litter_on_Carling_Street_in_London,_Ontario.jpg) by **Gogerr**, licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0). It is a public development image, not evidence from the simulated municipal point or a before/after pair. Source metadata and hash are recorded in [sources.json](../../evaluation/vision/internet-development/sources.json).

## Artifacts and limits

- [Machine-readable workflow evidence](groq-live-simulation-2026-09-24.json)
- Backend: **228 tests passing**, zero failures/errors. Python provider probes: **20 offline tests passing**. These establish software checks, not AI accuracy.

The recorded 423 seconds of total execution and 344 seconds of service are elapsed **browser simulation** times. The saved estimate was 856.88 seconds. They are not physical travel/collection measurements and must not be used to claim route-time savings. No physical cleaning, causality, general image accuracy, or complete Android camera/GPS field demonstration is established by this case. The JSON retains the selected observed values without runtime database identifiers or duplicated entity snapshots. The original detailed report, static result page, screenshots and earlier provider diagnostics were archived outside the repository. Current build verification is consolidated in [delivery checks](../DELIVERY_CHECKS.md).
