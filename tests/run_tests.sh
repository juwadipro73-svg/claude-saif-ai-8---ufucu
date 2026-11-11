#!/usr/bin/env bash
# SaifAI Builder — contract tests (offline).
# A stubbed curl records every outbound call and serves canned GCS/Drive data,
# so we can prove the dispatch/callback/claim behaviour matches the REAL gateway
# contract (app PR #4 @ f29e032) without touching any credential or network.
set -uo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir -p "$T/bin" "$T/state/assets"
PASS=0; FAIL=0

# ------------------------------ stub curl ----------------------------------
cat > "$T/bin/curl" <<'STUB'
#!/usr/bin/env bash
# Records: REQ <method> <url> , H:<header> , D:<data>  into $REQLOG
# Serves: fixture GCS objects, fake Drive id, {} for worker endpoints.
method="GET"; url=""; out=""; data=""
args=("$@"); i=0
while [ $i -lt ${#args[@]} ]; do
  a="${args[$i]}"
  case "$a" in
    -X) i=$((i+1)); method="${args[$i]}" ;;
    -H) i=$((i+1)); echo "H:${args[$i]}" >> "$REQLOG" ;;
    -d) i=$((i+1)); data="${args[$i]}" ;;
    -o) i=$((i+1)); out="${args[$i]}" ;;
    -F) i=$((i+1)) ;;   # multipart form field (Drive upload) — recorded via URL only
    --retry|--retry-delay) i=$((i+1)) ;;
    --retry-all-errors|-sfS|-sf|-sS|-s) ;;
    http*) url="$a" ;;
    *) ;;
  esac
  i=$((i+1))
done
echo "REQ $method $url" >> "$REQLOG"
[ -n "$data" ] && echo "D:$data" >> "$REQLOG"
case "$url" in
  *storage.googleapis.com/storage/v1/b/TESTBUCKET/o/*input.json*alt=media*)
    case "${FIX_MODE:-good}" in
      badid)  cp "$FIXDIR/input_badid.json" "$out" ;;
      badsha) cp "$FIXDIR/input_badsha.json" "$out" ;;
      *)      cp "$FIXDIR/input.json" "$out" ;;
    esac ;;
  *storage.googleapis.com/storage/v1/b/TESTBUCKET/o/*assets*alt=media*)
    printf 'hello' > "$out" ;;
  *googleapis.com/upload/drive/v3/files*)
    printf '{"id":"DRIVE_FAKE_ID","name":"%s"}' "x" ;;
  */api/worker/*)
    printf '{}' ;;
  *)
    echo "stub-curl: unexpected URL: $url" >&2; exit 22 ;;
esac
exit 0
STUB
chmod +x "$T/bin/curl"

# ----------------------------- common env ----------------------------------
export JOB_ID="11111111-2222-3333-4444-555555555555"
export GCP_CREDENTIALS="{}" GDRIVE_FOLDER_ID="FOLDER123" WORKER_CALLBACK_SECRET="TESTSECRET"
export SEC_SAIF_CLOUD_API_BASE_URL="https://gw.example/site" VAR_SAIF_CLOUD_API_BASE_URL=""
export SEC_SAIF_GCS_BUCKET="TESTBUCKET" VAR_SAIF_GCS_BUCKET="" SEC_GCS_BUCKET="" VAR_GCS_BUCKET=""
export SAIF_GCP_TOKEN="GT"
export SAIF_STATE_DIR="$T/state"
export GITHUB_OUTPUT="$T/gh_output"
export REQLOG="$T/reqlog"
export FIXDIR="$T/fixtures"
export CURL_BIN="$T/bin/curl"
export GITHUB_SERVER_URL="https://github.com" GITHUB_REPOSITORY="shahudgg/SaifAI-Builder" GITHUB_RUN_ID="1"
mkdir -p "$FIXDIR"

GOODSHA="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"  # sha256("hello")
cat > "$FIXDIR/input.json" <<EOF
{"jobId":"$JOB_ID","ownerUid":"uid1","kind":"zip","prompt":"My Test App!","project":{},
 "assets":[{"name":"photo.jpg","size":5,"sha256":"$GOODSHA"}]}
EOF
cat > "$FIXDIR/input_badid.json" <<'EOF'
{"jobId":"99999999-9999-9999-9999-999999999999","ownerUid":"uid1","kind":"zip","prompt":"X","project":{},"assets":[]}
EOF
cat > "$FIXDIR/input_badsha.json" <<EOF
{"jobId":"$JOB_ID","ownerUid":"uid1","kind":"zip","prompt":"X","project":{},
 "assets":[{"name":"photo.jpg","size":5,"sha256":"0000000000000000000000000000000000000000000000000000000000000000"}]}
EOF

# ----------------------------- helpers -------------------------------------
reset_case(){ : > "$GITHUB_OUTPUT"; : > "$REQLOG"; unset FIX_MODE; rm -rf "$ROOT/output"; }
ok(){ echo "  ok   - $1"; PASS=$((PASS+1)); }
bad(){ echo "  FAIL - $1"; FAIL=$((FAIL+1)); }
run_rc(){ # <script> -> returns rc, stderr in $T/err
  set +e; bash "$1" >"$T/out" 2>"$T/err"; local rc=$?; set -e; return $rc
}
expect_ok(){ if run_rc "$1"; then ok "$2"; else bad "$2 (rc!=0, stderr: $(head -c 200 "$T/err"))"; fi; }
expect_fail(){ # <script> <desc> <grep-what-in-stderr>
  if run_rc "$1"; then bad "$2 (unexpectedly succeeded)"; else
    if grep -q "$3" "$T/err"; then ok "$2"; else bad "$2 (stderr missing '$3': $(head -c 200 "$T/err"))"; fi
  fi
}
out_has(){ grep -q "^$1" "$GITHUB_OUTPUT" && ok "$2" || bad "$2 (missing in GITHUB_OUTPUT: $1)"; }
log_has(){ grep -q -- "$1" "$REQLOG" && ok "$2" || bad "$2 (missing in request log: $1)"; }
log_free(){ grep -q -- "$1" "$REQLOG" && bad "$2 (forbidden in request log: $1)" || ok "$2"; }
file_free(){ # pattern dirs desc — strip shell/yaml comment text first; scan only executable code
  local pat="$1" dirs="$2" desc="$3" hit="" d f
  for d in $dirs; do
    while IFS= read -r f; do
      sed 's/#.*$//' "$f" | grep -q -- "$pat" && hit="$f"
    done < <(find "$d" -type f \( -name '*.sh' -o -name '*.yml' -o -name '*.yaml' \))
  done
  if [ -n "$hit" ]; then bad "$desc (forbidden pattern '$pat' in executable code of $hit)"; else ok "$desc"; fi
}

echo "== contract tests: dispatch/in/staging =="

# 1) validate: success path
reset_case
expect_ok scripts/10_validate.sh "validate passes with all required names set"
out_has "endpoint_progress=https://gw.example/site/api/worker/progress" "progress endpoint derived from API base"
out_has "endpoint_complete=https://gw.example/site/api/worker/complete" "complete endpoint derived from API base"

# 1b) base already ending in /api must NOT double-prefix
reset_case
OLD="$SEC_SAIF_CLOUD_API_BASE_URL"; export SEC_SAIF_CLOUD_API_BASE_URL="https://gw.example/api"
bash scripts/10_validate.sh >/dev/null 2>&1 || true
out_has "endpoint_complete=https://gw.example/api/worker/complete" "endpoint normalisation avoids /api/api"
export SEC_SAIF_CLOUD_API_BASE_URL="$OLD"

# 2) bad job_id rejected
reset_case
OLDJ="$JOB_ID"; export JOB_ID="abc123"
expect_fail scripts/10_validate.sh "non-UUID job_id rejected" "UUID"
export JOB_ID="$OLDJ"

# 3) missing callback secret is fatal with the exact NAME
reset_case
unset WORKER_CALLBACK_SECRET
expect_fail scripts/10_validate.sh "missing WORKER_CALLBACK_SECRET named in error" "WORKER_CALLBACK_SECRET"
export WORKER_CALLBACK_SECRET="TESTSECRET"

# 4) missing bucket name is fatal with the exact NAME
reset_case
SEC_SAIF_GCS_BUCKET_TMP="$SEC_SAIF_GCS_BUCKET"; unset SEC_SAIF_GCS_BUCKET
expect_fail scripts/10_validate.sh "missing SAIF_GCS_BUCKET named in error" "SAIF_GCS_BUCKET"
export SEC_SAIF_GCS_BUCKET="$SEC_SAIF_GCS_BUCKET_TMP"
# re-run validate so config.env exists again for later cases
bash scripts/10_validate.sh >/dev/null 2>&1

echo "== contract tests: staged input + asset integrity =="

# 5) fetch staged input.json (good)
reset_case
expect_ok scripts/20_fetch_input.sh "fetches jobs/<uuid>/input.json from private GCS"
out_has "kind=zip" "kind parsed from staged input (not a workflow input)"
out_has "asset_count=1" "asset count parsed"

# 5b) prompt sanitisation did not take over flow inputs
log_has "REQ GET https://storage.googleapis.com/storage/v1/b/TESTBUCKET/o/jobs%2F11111111-2222-3333-4444-555555555555%2Finput.json?alt=media" "staged object key is exactly jobs/<job_id>/input.json"
log_has "H:Authorization: Bearer GT" "GCS reads carry the SA OAuth bearer"

# 6) jobId mismatch in staged file -> refuse
reset_case
FIX_MODE=badid expect_fail scripts/20_fetch_input.sh "staged jobId mismatch refused" "mismatch"

# 7) asset verify passes on correct size+sha
reset_case
bash scripts/20_fetch_input.sh >/dev/null 2>&1
expect_ok scripts/40_verify_assets.sh "asset size+SHA-256 verified"
out_has "assets_verified=1" "verification count reported"

# 8) wrong sha -> fail
reset_case
export FIX_MODE=badsha
bash scripts/20_fetch_input.sh >/dev/null 2>&1
expect_fail scripts/40_verify_assets.sh "SHA-256 mismatch rejected" "SHA-256"
unset FIX_MODE
# restore good staged input
reset_case
bash scripts/20_fetch_input.sh >/dev/null 2>&1

echo "== contract tests: callbacks (Bearer, exact bodies) =="

# 9) progress callback
reset_case
bash scripts/10_validate.sh >/dev/null 2>&1
expect_ok scripts/30_progress.sh "progress callback sent"
log_has "REQ POST https://gw.example/site/api/worker/progress" "progress hits /api/worker/progress"
log_has "H:Authorization: Bearer TESTSECRET" "progress uses Bearer WORKER_CALLBACK_SECRET"
log_has 'D:{"jobId":"11111111-2222-3333-4444-555555555555","status":"building"}' "progress body is exactly {jobId,status:building}"
log_free "X-Saif-Signature" "no HMAC header on progress"

# 10) build + drive upload + ready callback
reset_case
bash scripts/10_validate.sh >/dev/null 2>&1
bash scripts/20_fetch_input.sh >/dev/null 2>&1
ART=$(JOB_ID="$JOB_ID" bash -c 'bash scripts/50_build.sh >/dev/null && grep "^artifact_path=" "$GITHUB_OUTPUT"')
ARTPATH="${ART#artifact_path=}"
log_free "REQ POST https://gw.example/site/api/worker/complete" "no completion sent before artifact exists"
# (run the pipeline in its real order: progress -> verify -> upload -> complete)
bash scripts/30_progress.sh >/dev/null 2>&1
bash scripts/40_verify_assets.sh >/dev/null 2>&1
ARTIFACT_PATH="$ARTPATH" bash scripts/60_drive_upload.sh >/dev/null 2>&1 \
  && ok "Drive multipart upload returns a file id" || bad "Drive multipart upload"
out_has "drive_file_id=DRIVE_FAKE_ID" "single private Drive file id captured"
log_free "REQ POST https://www.googleapis.com/drive/v3/permissions" "no public permission grant attempted"
JOB_STATUS="success" DRIVE_FILE_ID="DRIVE_FAKE_ID" OUTCOMES="validate=success,auth=success,fetch=success,progress=success,verify=success,build=success,upload=success" \
  bash scripts/90_callback_complete.sh >/dev/null 2>&1 \
  && ok "ready completion callback sent" || bad "ready completion callback"
log_has "REQ POST https://gw.example/site/api/worker/complete" "completion hits /api/worker/complete"
log_has 'D:{"jobId":"11111111-2222-3333-4444-555555555555","status":"ready","driveFileId":"DRIVE_FAKE_ID"}' "ready body has exactly {jobId,status:ready,driveFileId}"
log_has "H:Authorization: Bearer TESTSECRET" "completion uses Bearer"
log_free "X-Saif-Signature" "no HMAC header on completion"
log_free "portalUrl" "no invented portalUrl field in callback"
log_free "token" "no token minted/sent by worker"

# 11) failed-path callback
reset_case
bash scripts/10_validate.sh >/dev/null 2>&1
JOB_STATUS="failure" DRIVE_FILE_ID="" OUTCOMES="validate=success,auth=success,fetch=success,progress=success,verify=failure,build=skipped,upload=skipped" \
  bash scripts/90_callback_complete.sh >/dev/null 2>&1 \
  && ok "failed completion callback sent" || bad "failed completion callback"
grep -q '"status":"failed"' "$REQLOG" && ok "failed body has status failed" || bad "failed body has status failed"
grep -q '"error":"step '"'"'verify'"'"' did not succeed' "$REQLOG" && ok "failed body names the failing step" || bad "failed body names the failing step"
log_free "driveFileId" "failed body has no driveFileId"

echo "== contract tests: forbidden legacy shapes (static) =="

file_free "downloadTokens" "scripts/ .github/" "no /downloadTokens writes anywhere"
file_free "X-Saif-Signature" "scripts/ .github/" "no HMAC/signature header anywhere"
file_free "hmac" "scripts/ .github/" "no hmac usage anywhere"
file_free "uc?id=" "scripts/ .github/" "no public Drive links"
file_free "permissions.create" "scripts/ .github/" "no public permission API calls"
file_free "apps.json" "scripts/ .github/" "no legacy /apps.json usage"
grep -q "worker/progress" scripts/30_progress.sh && grep -q "worker/complete" scripts/90_callback_complete.sh \
  && ok "exact worker endpoint paths present" || bad "exact worker endpoint paths present"

# workflow dispatch shape must accept ONLY job_id
if command -v python3 >/dev/null 2>&1 && [ -x /tmp/lintenv/bin/python ]; then
  INPS=$(/tmp/lintenv/bin/python - <<'PY'
import yaml
d = yaml.safe_load(open('.github/workflows/build.yml'))
on = d.get('on', d.get(True))
ins = on['workflow_dispatch']['inputs']
print(",".join(sorted(ins.keys())))
PY
)
  [ "$INPS" = "job_id" ] && ok "workflow dispatch inputs == {job_id} only" || bad "workflow dispatch inputs == {job_id} only (got: $INPS)"
fi

echo
echo "============================================"
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
