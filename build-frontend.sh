#!/usr/bin/env bash
# builds Angular into Spring Boot's static folder
# usage: ./build-frontend.sh [--watch | --package]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/laps/laps-frontend"
MAVEN_DIR="$SCRIPT_DIR/laps"
STATIC_APP="$SCRIPT_DIR/laps/src/main/resources/static/app"

if [ ! -d "$FRONTEND_DIR" ]; then
  echo "ERROR: frontend not found at $FRONTEND_DIR"
  exit 1
fi

MODE="${1:-}"

case "$MODE" in

  --watch)
    cd "$FRONTEND_DIR"
    ng build --watch
    ;;

  --package)
    rm -rf "$STATIC_APP"
    cd "$FRONTEND_DIR" && ng build
    cd "$MAVEN_DIR" && mvn clean package -Dmaven.test.skip=true
    ls "$MAVEN_DIR/target/"*.jar 2>/dev/null
    ;;

  "")
    rm -rf "$STATIC_APP"
    cd "$FRONTEND_DIR" && ng build
    echo "done — refresh browser to pick up changes"
    ;;

  *)
    echo "usage: $0 [--watch | --package]"
    exit 1
    ;;

esac
