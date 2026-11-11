#!/usr/bin/env bash
# Step 90 — contract completion callback (always()).
#   ready  : POST /api/worker/complete {"jobId","status":"ready","driveFileId":"<id>"}
#   failed : POST /api/worker/complete {"jobId","status":"failed","error":"<msg>"}
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_config

if [ "${JOB_STATUS:-}" = "success" ] && [ -n "${DRIVE_FILE_ID:-}" ]; then
  body=$("$JQ_BIN" -cn --arg j "$JOB_ID" --arg d "$DRIVE_FILE_ID" \
    '{jobId:$j, status:"ready", driveFileId:$d}')
  echo "sending completion: status=ready"
else
  # first non-success, non-skipped step outcome becomes the human error hint
  reason=$(printf '%s' "${OUTCOMES:-}" | tr ',' '\n' \
    | awk -F= '$2 != "success" && $2 != "skipped" && $1 != "" {print $1; exit}')
  [ -n "$reason" ] || reason="workflow"
  msg="step '${reason}' did not succeed; run: ${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-shahudgg/SaifAI-Builder}/actions/runs/${GITHUB_RUN_ID:-0}"
  body=$("$JQ_BIN" -cn --arg j "$JOB_ID" --arg e "$msg" \
    '{jobId:$j, status:"failed", error:$e}')
  echo "sending completion: status=failed ($msg)"
fi

worker_post "worker/complete" "$body" || { err "complete callback failed — gateway will keep the job pending"; exit 1; }
