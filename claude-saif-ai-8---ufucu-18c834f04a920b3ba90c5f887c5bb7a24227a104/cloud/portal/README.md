# SAIF AI Download Portal

Public download portal that resolves one-time download links into real
APK / ZIP / video downloads from the SAIF AI cloud gateway.

## Architecture

```
Android app ──upload──▶ cloud gateway (GCS → Drive → Firebase RTDB)
                                    │
                                    │ issues one-time claim token
                                    ▼
Recipient opens: https://saifai-downloads.ai.studio/download/{jobId}?claim={token}
                                    │
                                    ▼
                      portal server.ts (this code)
                                    │
              ┌─────────────────────┴──────────────────────┐
              │ SAIF_CLOUD_API_BASE_URL is set?             │
              │  YES → proxy to gateway /api/inspectDownload│
              │         & /api/claimDownload (streams file) │
              │  NO  → serve the in-memory demo vault       │
              └────────────────────────────────────────────┘
```

## Configuration

Only one environment variable controls the operating mode:

| Variable | When set | When unset |
|---|---|---|
| `SAIF_CLOUD_API_BASE_URL` | Production proxy to the live gateway | Self-contained demo vault |

Set it to the deployed cloud gateway URL, e.g.
`https://your-gateway-xyz.asia-southeast1.run.app`.

All other variables (`GEMINI_API_KEY`, `APP_URL`) are optional.

## Endpoints

| Method | Path | Behaviour |
|---|---|---|
| `GET` | `/healthz` | **200** for CI preflight; 502 if gateway configured but unreachable |
| `GET` | `/api/vault/:vaultId` | Returns vault metadata. Proxies claim inspection to gateway in production. |
| `POST` | `/api/vault/:vaultId/ad-complete` | Consumes the claim. In production, streams the real file from Drive via the gateway. In demo, burns the in-memory vault and returns base64 data. |
| `GET` | `/api/vault/:vaultId/rotating-status` | Demo-only rotating token refresh. |
| `POST` | `/api/push` | **501 in production** (uploads must go to the gateway directly). Creates demo vault when unset. |
| `POST` | `/api/trigger-build` | Dispatches `build.yml` in `shahudgg/SaifAI-Builder`. |
| `GET` | `/api/firebase-latest` | Reads `apps/` from Firebase RTDB. |

## Local development

```bash
npm install
npm run dev          # Vite + tsx, no build step needed
npm run build        # produces dist/ for production
npm run preview      # serves dist/ locally
```

## Deployment

The portal is deployed as the AI Studio project
`ea118c80-61dd-46d8-a5b6-7fd8bc066251`, published at
`https://saifai-downloads.ai.studio/`.

To push a code change:
1. Update the source in AI Studio editor **or** import this folder from GitHub.
2. Add the secret `SAIF_CLOUD_API_BASE_URL` = your deployed gateway URL.
3. Redeploy — the portal will switch from demo mode to production proxy.
