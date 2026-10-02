#!/usr/bin/env bash
# Runs the packaged app with the variables from .env (Spring Boot doesn't read .env by itself).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a
exec java -jar target/application.jar "$@"
