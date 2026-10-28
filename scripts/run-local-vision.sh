#!/usr/bin/env bash
set -euo pipefail

# Starts the local cleanliness model service (computer_vision/service) on 127.0.0.1:8090.
# First run: creates computer_vision/service/.venv_service and installs the pinned requirements.
service_dir="$(cd "$(dirname "$0")/../computer_vision/service" && pwd)"
cd "$service_dir"

if [ ! -x .venv_service/bin/python ]; then
  python3 -m venv .venv_service
  .venv_service/bin/python -m pip install -r requirements.txt
fi

exec .venv_service/bin/python app.py
