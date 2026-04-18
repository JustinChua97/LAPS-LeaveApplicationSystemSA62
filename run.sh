#!/usr/bin/env bash
# starts the app, builds angular first if static output is missing

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/laps/laps-frontend"
STATIC_APP="$SCRIPT_DIR/laps/src/main/resources/static/app"

if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a; source "$SCRIPT_DIR/.env"; set +a
fi

if [ ! -f "$STATIC_APP/index.html" ]; then
  if command -v ng &>/dev/null; then
    echo "Angular output missing, building..."
    cd "$FRONTEND_DIR" && ng build && cd "$SCRIPT_DIR"
  else
    echo "WARNING: $STATIC_APP/index.html not found and ng is not on PATH — /app will 404"
  fi
fi

cd "$SCRIPT_DIR/laps"
exec mvn spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.profiles=local
