# Route A — shipping SAIF AI under GPL v3 today

**Status: everything below is already implemented in this build.** This file is both the checklist
and the record of where each item lives, so it can be re-verified before every release.

---

## 1. Keep the licence and the notices ✅

| Item | Where | State |
|---|---|---|
| Full GPL v3 text | `LICENSE` (repo root) | untouched |
| Attribution to AntiGem + notice that modified source stays under GPL | `THIRD_PARTY_NOTICES.md` | untouched |
| Termux and Ubuntu/PRoot upstream notices | `THIRD_PARTY_NOTICES.md` | untouched |
| Bundled asset licence | `app/src/main/assets/legal/LICENSE` | untouched |

**Never delete or edit these while any upstream-derived code is still in the tree.** Doing so is what
turns a fixable compliance issue into wilful infringement.

## 2. Keep the notices in the upstream-derived files ✅

No upstream-derived file carries a SAIF AI notice — verified by
`tools/licence/add_saif_headers.py`, which fails with exit code 2 if one ever does.

```bash
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
python3 tools/licence/add_saif_headers.py        # dry run + guard-rail check
```

## 3. Show the licences inside the app ✅

**Settings → LICENCES & OPEN SOURCE** (`com.example.ui.components.SaifLicencesSection`) shows, in
the app itself:

* the SAIF AI copyright line;
* the AntiGem attribution, its GPL v3 status, a link to the upstream project, and a plain-language
  statement that the app is distributed under GPL v3 with source available;
* the Termux and Ubuntu/PRoot notices;
* the exact upstream **source link** for the build.

Code: `app/src/main/java/com/example/ui/components/SaifLicencesSection.kt`.
The source URL is configured in `app/build.gradle.kts` → `buildConfigField("SAIF_SOURCE_URL", …)`.

## 4. Publish the corresponding source ✅ (one setting)

GPL v3 §13 requires the *complete corresponding source* of every build you distribute — the full
tree, build scripts and everything needed to rebuild it, not just the changed files.

* Point `SAIF_SOURCE_URL` in `app/build.gradle.kts` at the public repository or release archive that
  matches this build exactly.
* Tag each release with the same commit the APK was built from.
* If you distribute only inside your own organisation, a **written offer** valid for three years is
  the alternative — but for an app distributed to users, a public repository is simpler and safer.

## 5. Keep every dependency GPL-compatible ⚠️ *check before adding*

GPL v3 is incompatible with several common proprietary SDKs. Before adding a new library, confirm its
licence is GPL v3 compatible (MIT, BSD, Apache-2.0, LGPL are fine; GPL-2.0-only is **not**
compatible with GPL-3.0). Current Android dependencies are all permissive; the risk is future
additions, so make this part of code review.

## 6. Keep the in-app wording accurate ⚠️

The licence screen says the app is distributed under GPL v3. If you later complete Route B or
Route C, update this screen and `LICENSE` **together** — an app that claims GPL but hides its source
is worse off than one that never mentioned it.

---

## Release-time verification (2 minutes)

```bash
# 1. no SAIF AI notice on upstream code
python3 tools/licence/add_saif_headers.py | tail -3

# 2. the derived-line count has not grown since the last release
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem | tail -4

# 3. the licence files are present and unmodified
ls -l LICENSE THIRD_PARTY_NOTICES.md app/src/main/assets/legal/LICENSE

# 4. SAIF_SOURCE_URL points at the exact commit being shipped
grep -n "SAIF_SOURCE_URL" app/build.gradle.kts
```

## What GPL v3 does *not* cost you

* ✅ your copyright in your own 96,000 lines (now stamped in 454 files)
* ✅ your brand, name, logo, design and artwork
* ✅ the right to charge money for the app
* ✅ the right to keep your own additions closed *until you distribute them* — the obligation
  attaches to distribution, not to development
* ❌ only this: recipients get the source and the right to share it
