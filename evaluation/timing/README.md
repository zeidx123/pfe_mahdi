# Actual versus estimated duration

Export completed tours through the authenticated supervisor `GET /api/tournees` API to a local JSON file, then run:

```
python3 evaluation/timing/metrics.py /absolute/path/tours.json
python3 -m unittest discover -s evaluation/timing -p 'test_*.py'
```

The route estimate is frozen at start; actual total duration uses recorded start/finish times. Outputs include signed error, absolute error, error relative to estimate, total MAE and MAPE (denominator = actual duration, zero actual excluded). Service error is computed only when every stop has arrival/completion measurements. Partial service coverage remains visible but is not compared to a full-tour service estimate.

Staged API execution times validate recording and arithmetic; they are not actual vehicle travel. Field experiments require completed Android tours. Keep simulated and field exports separate and label the data origin in the report. Do not claim pure driving time, fuel savings or measured road distance from elapsed duration alone.
