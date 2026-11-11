#!/usr/bin/env bash
# Step 30 — contract callback: POST /api/worker/progress {"jobId","status":"building"}
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_config

body=$("$JQ_BIN" -cn --arg j "$JOB_ID" '{jobId:$j, status:"building"}')
worker_post "worker/progress" "$body" \
  && echo "progress callback sent (building)" \
  || warn "progress callback failed (non-fatal; continuing build)"
exit 0
