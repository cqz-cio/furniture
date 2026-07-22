#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/oakved}"
ENV_FILE="$APP_HOME/backend/backend.env"
JAR_FILE="$APP_HOME/backend/yudao-server.jar"
LOG_DIR="$APP_HOME/backend/logs"

if [ ! -f "$ENV_FILE" ]; then
  echo "Backend env file not found: $ENV_FILE" >&2
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  echo "Backend jar not found: $JAR_FILE" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"
set -a
source "$ENV_FILE"
set +a

nohup java -jar "$JAR_FILE" > "$LOG_DIR/yudao-server.out" 2>&1 &
echo $! > "$APP_HOME/backend/yudao-server.pid"
echo "Started yudao-server, pid=$(cat "$APP_HOME/backend/yudao-server.pid")"
