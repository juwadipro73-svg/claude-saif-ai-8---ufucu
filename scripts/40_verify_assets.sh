#!/usr/bin/env bash
# Step 40 — verify every staged asset: byte size + SHA-256 (contract requirement).
# Asset entries: {name, size, sha256} or {path, size, sha256}; default path = jobs/<job_id>/assets/<name>
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_config

IN="$SAIF_STATE_DIR/input.json"
[ -s "$IN" ] || { err "input.json not staged — run 20_fetch_input first"; exit 1; }
COUNT=$("$JQ_BIN" -r '(.assets // []) | length' "$IN")
if [ "$COUNT" -eq 0 ]; then
  echo "no assets declared for this job — nothing to verify"
  echo "assets_verified=0" >> "$GITHUB_OUTPUT"
  exit 0
fi

mkdir -p "$SAIF_STATE_DIR/assets"
i=0
while read -r asset; do
  name=$(printf '%s' "$asset" | "$JQ_BIN" -r '(.name // .path // "")')
  path=$(printf '%s' "$asset" | "$JQ_BIN" -r '.path // empty')
  want_size=$(printf '%s' "$asset" | "$JQ_BIN" -r '.size // empty')
  want_sha=$(printf '%s' "$asset" | "$JQ_BIN" -r '.sha256 // empty' | tr 'A-F' 'a-f')
  [ -n "$path" ] || path="jobs/$JOB_ID/assets/$name"
  [ -n "$want_size" ] || { err "asset '$name' missing .size"; exit 1; }
  [ -n "$want_sha" ]  || { err "asset '$name' missing .sha256"; exit 1; }

  dest="$SAIF_STATE_DIR/assets/asset-$i"
  gcs_get "$path" "$dest" || { err "asset download failed: $path"; exit 1; }
  got_size=$(stat -c%s "$dest")
  got_sha=$(sha256sum "$dest" | awk '{print $1}')
  [ "$got_size" = "$want_size" ] || { err "asset '$name' size mismatch: got $got_size bytes, expected $want_size"; exit 1; }
  [ "$got_sha" = "$want_sha" ]   || { err "asset '$name' SHA-256 mismatch: got $got_sha, expected $want_sha"; exit 1; }
  i=$((i + 1))
done < <("$JQ_BIN" -c '(.assets // [])[]' "$IN")

echo "verified $i asset(s): size + SHA-256 all match"
echo "assets_verified=$i" >> "$GITHUB_OUTPUT"
