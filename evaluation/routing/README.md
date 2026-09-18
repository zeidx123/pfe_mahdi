# Routing experiment

Run `scripts\run-routing-benchmark.bat` on Windows or `scripts/run-routing-benchmark.sh` on Linux from the project root. Both call the portable Python runner, which uses the repository's Maven wrapper and production Java algorithms on saved matrices. The bundled fixtures are **synthetic algorithm validation**, not measured road experiments. One checks a small round trip; the six-stop fixture illustrates both improvement (150 → 119 → 114 seconds) and a remaining gap to exact (102 seconds). These illustrative costs must not be reported as municipality performance.

For real experiments, configure ORS_API_KEY, create a tour with a deliberate manual order, optimize it, and download `GET /api/tournees/{id}/route-export` using a supervisor bearer token. Save the JSON in a separate fixtures directory and pass that directory to the script. Keep the same depot, road profile and service-time assumptions when comparing methods. Avoid mixing synthetic and road matrices in a reported aggregate.

Outputs compare MANUAL, NN, NN_2OPT and EXACT for at most ten stops. Larger instances omit exact results. The application selects the best of the three heuristic/manual candidates; exact is a benchmark. Improvement is relative to manual travel time. Service time is shown separately and included in total time. Exact gaps refer to the saved directed matrix, not traffic-aware reality. Targets of 15–20 reviewed road scenarios are flexible; no road performance results have been invented.

Actual-versus-estimated duration uses tour start/finish timestamps and the estimate frozen at start. Stop arrival/completion measures service intervals. Incomplete intervals remain missing. Waiting and unrecorded breaks may affect total elapsed time; GPS distance is not synthesized from route distance.

The first live ORS development capture is in `live-development/ors-depot-three-stops.json`, with separate `live-development-results.csv`. The three demonstration locations have not been reviewed as municipality collection sites. Manual driving time is 312.52 seconds; NN/NN + 2-opt/exact are 251.17 seconds. This validates a small road-provider integration case, not general municipal improvement. Reproduce it without more provider calls:

```bash
scripts/run-routing-benchmark.sh evaluation/routing/live-development evaluation/routing/live-development-results.csv
```
