# Collection-point image evaluation

The runner submits staged images through the existing vision pipeline using a supervisor account. Paired scenes always produce **both** `AFTER_ONLY` and `BEFORE_AFTER` requests. Ordinary application completion still needs only an AFTER photo.

The comparison asks whether the visible evidence appears `CLEANED`, `NOT_CLEANED`, `OBSTRUCTED`, or `CANNOT_DETERMINE`. `ANALYSIS_UNAVAILABLE` records a failed analysis, not a human scene label. Photographs do not establish who performed the work.

## Run a transport check

Run these commands from the repository root. Python 3 uses only its standard library.

```sh
python3 evaluation/vision/fixtures/generate_abstract_scene.py
python3 scripts/evaluation/evaluate_vision.py \
  --manifest evaluation/vision/fixtures/abstract-transport.json --validate-only
```

The bundled PNGs are abstract colored squares. This manifest deliberately has no reference labels. Its two requests check upload, image-mode selection, provider availability and result serialization only. The runner and metrics reject cleanliness labels for this fixture.

For a real API run, use an isolated demonstration backend and set `GESTI_EVAL_TOKEN` to a valid **supervisor** access token in your shell. Do not put credentials in manifests, source files, command arguments or saved output. The runner reads the token from this environment variable and never exports request headers, error bodies or API keys.

```sh
python3 scripts/evaluation/evaluate_vision.py \
  --base-url http://127.0.0.1:18180/api \
  --manifest evaluation/vision/fixtures/abstract-transport.json \
  --output evaluation/vision/runs/abstract-transport-001.jsonl
python3 evaluation/vision/metrics.py evaluation/vision/runs/abstract-transport-001.jsonl
```

This **does call the configured provider**. It saves evidence files and independent analysis records on the selected backend. It does not modify tours, visits, problems or human review. Requests run sequentially, once per mode, with no automatic retry. Choose a new output filename to repeat a run; previous results are never overwritten.

The endpoints are supervisor-only:

- `GET /api/ai/vision/evaluation-config` returns `provider`, `model` and `promptVersion`, with no secrets.
- `POST /api/ai/vision/evaluate` accepts multipart `afterPhoto`, optional `beforePhoto`, `expectedModel` and `expectedPromptVersion`. JPEG/PNG files must decode and be at most 10 MiB each. It reuses operational analysis and returns stored evidence references, original prediction, mode and configuration.

The runner freezes the preflight configuration for the entire run. The backend rejects a changed model or prompt with HTTP 409 before analyzing. The runner also validates the returned configuration/mode and stops on drift or authentication failure. `configurationConfirmed: false` means the row carries the intended preflight settings, without a confirmed matching response. Invalid responses and transport/HTTP errors remain explicit failures.

## Prepare a reviewed benchmark

Collect staged photographs with permission. Keep development examples separate from final evaluation. Aim for about 30–60 carefully reviewed examples or pairs, growing if time permits; counts are targets rather than completion gates.

Create a manifest with paths relative to that manifest:

```json
{
  "datasetVersion": "collection-scenes-v1",
  "dataKind": "STAGED_PHOTOS",
  "scenes": [
    {"sceneId": "scene-001", "split": "test", "afterPhoto": "photos/001-after.jpg", "beforePhoto": "photos/001-before.jpg"},
    {"sceneId": "scene-002", "split": "development", "afterPhoto": "photos/002-after.png"}
  ]
}
```

Allowed splits are `development`, `validation` and `test`. Scene IDs must be unique. The runner refuses identical photo bytes used across splits, before making requests. Related photographs and alternate views of the same scene must also remain together: byte hashes cannot detect different photographs of the same scene. An optional `expectedConfiguration` object with `provider`, `model` and `promptVersion` pins a planned experiment to previously recorded settings without requiring a particular model generation.

Review visible evidence independently of model output and keep labels in a separate file. Provide a separate label for each mode being evaluated, since the supplied evidence differs. Do not copy an AI prediction or an unexamined UI correction into reference labels. Example format (replace these illustrative entries with actual review):

```json
{
  "datasetVersion": "collection-scenes-v1",
  "labels": [
    {"sceneId": "scene-001", "inputMode": "AFTER_ONLY", "referenceLabel": "NOT_CLEANED", "reviewer": "reviewer-id", "reviewDate": "2026-09-17", "independentReview": true},
    {"sceneId": "scene-001", "inputMode": "BEFORE_AFTER", "referenceLabel": "NOT_CLEANED", "reviewer": "reviewer-id", "reviewDate": "2026-09-17", "independentReview": true}
  ]
}
```

Use `--labels path/to/labels.json`. Labels require the matching dataset version, valid mode/label, identified reviewer, ISO review date and `independentReview: true`. Human labels are never sent to the provider. Omitted labels remain `UNREVIEWED`; the runner never fills them from predictions. Validate first with `--validate-only` to check files, sizes, formats, split assignment and labels without any network call.

## Outputs and interpretation

Each JSONL row contains scene/split/input mode, original prediction, independent review status and label if supplied, timing, model/prompt/provider, run ID, dataset/manifest/label hashes, source image paths/hashes, saved analysis/evidence references, and a distinct `failureKind` where applicable. A companion `.jsonl.run.json` records intended configuration, backend URL, start/end time, planned/completed request counts, failures and completion state. No raw provider responses or secrets are exported. `COMPLETED` means all planned requests were attempted; consult failure counts before claiming provider success.

Metrics report availability and uncertainty for all submitted rows. Confusion matrices, class precision/recall/F1, macro F1 and missed-uncleaned-point rate use **independently reviewed, available predictions only**. Unreviewed or unavailable cases do not enter classification denominators. When none qualify, `accuracyAvailable` is false and scores are null, even if transport succeeds. The confusion matrix retains an unavailable column for reviewed cases to make missing coverage visible.

`pairedSceneCount` counts scenes with both input modes. `reviewedPairedSceneCount` and `matchedSceneMetrics` restrict comparison to scenes independently reviewed in **both** modes. Use those matched metrics for the AFTER-only versus paired comparison. Report analysis coverage and failure-kind counts alongside classification scores; an uncertain result and a provider outage are different outcomes. Empty/missing required review fields are errors unless explicitly marked `UNREVIEWED`. Every reviewed row, including legacy exports without `reviewStatus`, must explicitly declare `independentReview: true`; undeclared or false independence is rejected.

Tests perform no provider calls:

```sh
python3 -m unittest discover -s scripts/evaluation -p test_evaluate_vision.py
python3 -m unittest discover -s evaluation/vision -p test_metrics.py
```

Staged performance does not establish municipality-wide generalization. Transport fixtures and passing integration tests do not establish AI accuracy.
