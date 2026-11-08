#!/usr/bin/env bash
# SaifAI Builder worker helpers — shared by all workflow steps.
set -euo pipefail

CURL_BIN="${CURL_BIN:-curl}"
JQ_BIN="${JQ_BIN:-jq}"
: "${GITHUB_OUTPUT:=/tmp/gh_output}"
SAIF_STATE_DIR="${SAIF_STATE_DIR:-${RUNNER_TEMP:-/tmp}/saif-state}"
mkdir -p "$SAIF_STATE_DIR"

err(){  echo "::error::$*" >&2; }
warn(){ echo "::warning::$*" >&2; }
mask(){ echo "::add-mask::$1"; }

# first non-empty env value among candidate names (secret-first ordering is the caller's job)
pick(){ local k v; for k in "$@"; do v="${!k:-}"; if [ -n "$v" ]; then printf '%s' "$v"; return 0; fi; done; return 1; }

# re-load names resolved in the validate step (values: API_BASE, GCS_BUCKET only — never secrets)
load_config(){
  if [ -f "$SAIF_STATE_DIR/config.env" ]; then
    # shellcheck disable=SC1090
    . "$SAIF_STATE_DIR/config.env"
  fi
  : "${API_BASE:?API_BASE unresolved — run 10_validate first}"
  : "${GCS_BUCKET:?GCS_BUCKET unresolved — run 10_validate first}"
}

save_config(){
  qs(){ printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }  # single-quote safe
  { echo "API_BASE=$(qs "$API_BASE")"; echo "GCS_BUCKET=$(qs "$GCS_BUCKET")"; } > "$SAIF_STATE_DIR/config.env"
}

# Gateway endpoint normalisation: base may be site origin or already end in /api
api_endpoint(){ # <base> <path-without-/api-prefix, e.g. worker/progress>
  local base="${1%/}"
  case "$base" in
    */api) printf '%s/%s' "$base" "$2" ;;
    *)     printf '%s/api/%s' "$base" "$2" ;;
  esac
}

# Contract callbacks: Authorization: Bearer WORKER_CALLBACK_SECRET, JSON body. No HMAC headers.
worker_post(){ # <worker/...> <json-body>
  local url; url=$(api_endpoint "$API_BASE" "$1")
  "$CURL_BIN" -sfS -X POST "$url" \
    -H "Authorization: Bearer ${WORKER_CALLBACK_SECRET:?WORKER_CALLBACK_SECRET not set}" \
    -H "Content-Type: application/json" \
    --retry 3 --retry-delay 5 --retry-all-errors \
    -d "$2"
}

# Private GCS object fetch via JSON API (service-account OAuth token)
gcs_get(){ # <object-key> <destination-file>
  local enc; enc=$("$JQ_BIN" -rn --arg v "$1" '$v|@uri')
  "$CURL_BIN" -sfS -o "$2" \
    -H "Authorization: Bearer ${SAIF_GCP_TOKEN:?SAIF_GCP_TOKEN not set}" \
    "https://storage.googleapis.com/storage/v1/b/${GCS_BUCKET}/o/${enc}?alt=media"
}
