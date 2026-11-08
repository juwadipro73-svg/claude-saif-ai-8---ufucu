#!/usr/bin/env bash
# Step 20 — fetch staged job spec from private GCS: jobs/<job_id>/input.json
# Schema: {jobId, ownerUid, kind: apk|zip|video, prompt, project, assets[]}
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_config

DEST="$SAIF_STATE_DIR/input.json"
KEY="jobs/$JOB_ID/input.json"
gcs_get "$KEY" "$DEST" \
  || { err "cannot read gs://$GCS_BUCKET/$KEY — check bucket name, that the gateway staged the job, and that the service account has storage.objectViewer"; exit 1; }

KIND=$("$JQ_BIN" -r '.kind // empty' "$DEST")
case "$KIND" in
  apk|zip|video) ;;
  *) err "input.json .kind must be one of apk|zip|video (got: '${KIND:-<missing>}')"; exit 1 ;;
esac

IN_JOB=$("$JQ_BIN" -r '.jobId // empty' "$DEST")
[ "$IN_JOB" = "$JOB_ID" ] || { err "input.json .jobId mismatch (stage is for a different job; refusing)"; exit 1; }

PROMPT=$("$JQ_BIN" -r '.prompt // "SaifAIApp"' "$DEST")
SAFE=$(printf '%s' "$PROMPT" | tr ' ' '_' | tr -cd 'a-zA-Z0-9._-' | cut -c1-48)
[ -n "$SAFE" ] || SAFE="SaifAIApp"
ASSET_COUNT=$("$JQ_BIN" -r '(.assets // []) | length' "$DEST")

{
  echo "kind=$KIND"
  echo "safe_name=$SAFE"
  echo "asset_count=$ASSET_COUNT"
} >> "$GITHUB_OUTPUT"
echo "staged input ok — kind=$KIND, assets=$ASSET_COUNT (ownerUid intentionally not logged)"
