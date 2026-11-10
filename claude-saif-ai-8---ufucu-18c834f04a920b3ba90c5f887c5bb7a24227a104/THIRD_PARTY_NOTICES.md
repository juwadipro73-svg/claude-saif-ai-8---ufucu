# Third-party notices

## SAIF ADVANCE EDITING ENGINE
The workspace in `app/src/main/java/com/example/gemini` (AI agents, Linux terminal, code editor, browser, local server bridge
`agy_ide_bridge`, `termux-daemon`) is based on **AntiGem** by **santoshkurmi**
(https://github.com/Santoshkurmi/antigem), licensed under the **GNU General Public License v3.0** - see `LICENSE`.

Under GPLv3 the copyright notice and licence text must stay with the code, and the combined application is distributed under
GPLv3 with its source available.

---

## Provenance of this build (measured)

These numbers were produced by comparing this tree against a real checkout of the upstream
AntiGem repository. They are re-generated with:

```bash
git clone --depth 1 https://github.com/Santoshkurmi/antigem.git /tmp/upstream-antigem
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
```

| | files | lines |
|---|---|---|
| Original SAIF AI code (never existed upstream) | 672 | ~96,000 |
| Identical to upstream (>=95% of lines) | 241 | 55,219 |
| Modified upstream code (60-95%) | 86 | 54,928 |
| Heavily reworked upstream code (20-60%) | 16 | 3,164 |
| Effectively different (<20%) | 4 | 274 |

The upstream-derived material covers `app/src/main/java/com/example/gemini/**`,
`app/src/main/java/com/termux/**`, `agy_ide_bridge/`, `proto_generator/` and the upstream
`docs/`, `extensions/`, `extension_docs/` and `scripts/` trees.

Per-file records: `tools/licence/upstream_derived_files.txt` (derived) and
`tools/licence/saif_owned_files.txt` (original). `tools/licence/add_saif_headers.py` stamps the
SAIF AI copyright notice on the original files only, and fails if an upstream-derived file ever
carries one.

Full analysis and the options for re-licensing: `LICENSE_AUDIT.md`, `CLEANROOM_ROADMAP.md`,
`GPL_SHIP_CHECKLIST.md` and `DUAL_LICENCE_REQUEST.md`.
