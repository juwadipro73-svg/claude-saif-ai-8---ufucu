#!/usr/bin/env bash
# Step 60 — upload the ONE artifact to the PRIVATE Drive folder.
# No permissions.create, no public links: the file stays owner/private to the
# service account + folder; the gateway only receives the file id.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

: "${ARTIFACT_PATH:?set ARTIFACT_PATH env (step build output)}"
[ -s "$ARTIFACT_PATH" ] || { err "artifact missing: $ARTIFACT_PATH"; exit 1; }

meta=$("$JQ_BIN" -cn --arg n "$(basename "$ARTIFACT_PATH")" --arg p "$GDRIVE_FOLDER_ID" \
  '{name:$n, parents:[$p]}')
resp=$("$CURL_BIN" -sfS -X POST \
  "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name" \
  -H "Authorization: Bearer ${SAIF_GCP_TOKEN}" \
  -F "metadata=$meta;type=application/json" \
  -F "file=@${ARTIFACT_PATH};type=application/octet-stream")

fid=$(printf '%s' "$resp" | "$JQ_BIN" -r '.id // empty')
[ -n "$fid" ] || { err "Drive upload failed (no file id in response)"; exit 1; }
echo "drive_file_id=$fid" >> "$GITHUB_OUTPUT"
echo "uploaded to private Drive folder (file id recorded; file remains PRIVATE)"
