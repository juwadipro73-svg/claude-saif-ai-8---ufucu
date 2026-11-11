#!/usr/bin/env bash
# Step 10 — validate dispatch inputs + resolve required secret/variable NAMES.
# Values are never logged; missing names fail fast with the exact name to set.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

[[ "${JOB_ID:-}" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] \
  || { err "job_id must be a UUID (gateway stages jobs/<job_id>/input.json). Got: '${JOB_ID:-<empty>}'"; exit 1; }

missing=0
for kv in GCP_CREDENTIALS GDRIVE_FOLDER_ID; do
  if [ -z "${!kv:-}" ]; then err "Missing secret: $kv"; missing=1; fi
done
if [ -z "${WORKER_CALLBACK_SECRET:-}" ]; then
  err "Missing secret: WORKER_CALLBACK_SECRET (gateway only accepts Bearer-signed worker callbacks)"
  missing=1
fi

API_BASE=$(pick SEC_SAIF_CLOUD_API_BASE_URL VAR_SAIF_CLOUD_API_BASE_URL || true)
if [ -z "$API_BASE" ]; then
  err "Missing name: SAIF_CLOUD_API_BASE_URL (secret or variable) — required for /api/worker/*"
  missing=1
fi
GCS_BUCKET=$(pick SEC_SAIF_GCS_BUCKET VAR_SAIF_GCS_BUCKET SEC_GCS_BUCKET VAR_GCS_BUCKET || true)
if [ -z "$GCS_BUCKET" ]; then
  err "Missing name: SAIF_GCS_BUCKET (secret or variable) — worker reads gs://<bucket>/jobs/<job_id>/input.json"
  missing=1
fi
[ "$missing" -eq 0 ] || exit 1

mask "$WORKER_CALLBACK_SECRET"
save_config   # writes API_BASE + GCS_BUCKET (no secret values)
{
  echo "endpoint_progress=$(api_endpoint "$API_BASE" worker/progress)"
  echo "endpoint_complete=$(api_endpoint "$API_BASE" worker/complete)"
} >> "$GITHUB_OUTPUT"
echo "validation ok — job_id is a UUID; required names resolved: GCP_CREDENTIALS, GDRIVE_FOLDER_ID, WORKER_CALLBACK_SECRET, SAIF_CLOUD_API_BASE_URL, SAIF_GCS_BUCKET"
