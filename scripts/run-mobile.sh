#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
api_url="${1:-http://127.0.0.1:8080/api}"

cd "$project_dir/MOBILE"
flutter pub get
flutter run -d chrome --web-port 8088 --dart-define="API_BASE_URL=$api_url"
