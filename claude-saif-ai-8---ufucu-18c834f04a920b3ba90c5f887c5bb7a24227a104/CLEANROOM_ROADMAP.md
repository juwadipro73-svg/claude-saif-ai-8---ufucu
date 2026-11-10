# SAIF AI — roadmap to owning 100% of the code

The goal: every line in the app owned by SAIF AI, with no upstream GPL obligations left.

The honest starting point (measured, not guessed — see `LICENSE_AUDIT.md`): **~113,600 lines across
347 files are still the upstream project's code**, and **~96,000 lines across 672 files are already
yours**. This roadmap is how the first number gets to zero legally.

---

## The one rule everything else follows

> Anyone who writes replacement code must never have read the code being replaced.

Everything below exists to enforce that rule. If the rule is broken for a subsystem, that subsystem
is not clean — no matter how different the code looks. This is why the work cannot simply be handed
to an AI that has the original in context, and why "rewrite it with different names" fails.

Three roles, and **one person cannot hold two of them for the same subsystem**:

| Role | May see the old code? | Writes |
|---|---|---|
| **Specifier** | Yes — must, in fact | a behavioural specification only. No code, no structure, no pseudocode copied from the original. |
| **Implementer** | **No. Never.** | the new code, from the specification alone |
| **Reviewer / auditor** | Yes | verifies the implementers were isolated and the spec matches behaviour |

For SAIF AI, the practical arrangement: you or I (we have read the tree) act as **Specifier**.
The implementers must be developers who have not — ideally contractors brought in for this, working
from the specification document alone, with no repository access.

---

## Phase 0 — Freeze and measure ✅ *done*

* [x] `tools/licence/provenance.py` — measures exactly how much is upstream, per file, per area.
* [x] `tools/licence/add_saif_headers.py` — stamped your 454 owned source files, and refuses to
      touch upstream files.
* [x] `LICENSE_AUDIT.md` — the current state with real numbers.

**Rule from now on:** every pull request re-runs `provenance.py`. If the upstream-derived line count
goes *up*, the change is rejected. That single check stops the problem growing while the fix is
being built.

---

## Phase 1 — Build the isolation boundary ⏳ *2–3 weeks, do this first*

Before replacing anything, put SAIF AI's own interface in front of the engine. Today your UI calls
straight into `com.example.gemini.**`. Change it to call **your** interfaces:

```
com.example.engine.api        <- YOUR code, owned by you, stable
    TerminalSession, AgentTransport, FileWorkspace, ToolRegistry, SkillCatalog…
com.example.engine.impl.gpl   <- the current upstream-derived implementation (temporary)
com.example.engine.impl.new   <- each clean-room implementation as it lands
```

Why this comes first:

* it makes each subsystem swappable without touching your UI;
* it proves the specification is written against *behaviour*, not the current implementation;
* it means you can replace the engine **one piece at a time** while still shipping.

This phase involves reading the old code, so it is done by you (or me) as Specifier — and
deliberately produces **no implementation**, only interfaces and tests.

Deliverables: the interface module, black-box contract tests (`com.example.engine.contract`), and a
feature-parity checklist.

---

## Phase 2 — Write the specification ⏳ *3–5 weeks*

Each subsystem gets a written specification describing **what it must do**, never **how the old code
does it**:

* the messages/commands that go in and the events/results that come out;
* the on-screen and on-disk behaviour a user can observe;
* error cases and edge cases;
* explicit "do not look at the old implementation" warnings.

**Important:** specify from **public or official documentation** wherever one exists (for example
the Antigravity/LanguageServer surface, MCP, the Claude Code CLI contract). Do **not** reverse
engineer private APIs — that creates separate legal and terms-of-service exposure even when the
copyright question is clean. Where only private APIs exist, that is a reason to ask the upstream
author for a licence (Route B), not to reverse engineer harder.

Deliverable: `docs/spec/` — one document per subsystem, reviewed by the auditor.

---

## Phase 3 — Replace subsystem by subsystem ⏳ *6–12 months*

Ordered by value per unit of effort. Effort assumes one senior Android/Kotlin developer.

| # | Subsystem | Derived lines | Effort | Risk | Notes |
|---|---|---|---|---|---|
| 1 | `docs/`, `extension_docs/`, `extensions/`, `scripts/` | ~15,000 | 2 weeks | none | Documentation and bundled assets. Rewrite or delete outright; nothing depends on them. **Start here — it removes 13% of the problem for almost no work.** |
| 2 | `com/termux/**` (terminal emulator) | ~2,750 | 4–6 weeks | low | Well-understood component: a terminal view + escape-sequence parser + PTY. Write from the public terminal/ANSI specifications. |
| 3 | `proto_generator/` | ~27,400 | 2–3 weeks | **check first** | Almost all mechanically generated bindings. **First establish where the `.proto` schema came from.** If it is from public/official API documentation, regenerating from that schema is low risk. If it was reverse-engineered, it is not — and it needs Route B. |
| 4 | `com/example/gemini/ui/**` (engine screens) | ~65,400 | 3–4 months | medium | Large but the most mechanical: settings screens, chat surfaces, IDE, browser. You already have your own design language in `com/example/ui`, so this is mostly rebuilding screens you have already rebuilt once — against the spec, in your own style. |
| 5 | `com/example/gemini/data/**` (agent core) | ~30,000 | 3–4 months | high | The wire protocol, cascade/conversation handling, tool execution, MCP, skills. Hardest part and the most entangled with the reverse-engineering question. **Do this last, and only after Route B has been attempted.** |
| 6 | `agy_ide_bridge/` (Go bridge) | ~7,180 | 4 weeks | medium | Fold into #5: it is the same protocol surface in Go. |

A subsystem is **done** when:

* [ ] the new implementation passes the Phase 1 contract tests;
* [ ] the old implementation is deleted (not left side by side "just in case");
* [ ] `provenance.py` no longer matches it to any upstream file;
* [ ] the git history for it contains no commit by anyone who had access to the old code;
* [ ] the auditor signed it off.

---

## Phase 4 — Verify and re-licence ⏳ *2 weeks*

* `provenance.py` reports **0 derived lines**.
* Your lawyer reviews the audit trail: specifications, team isolation, commit history, sign-offs.
* Only then: replace `LICENSE` and `THIRD_PARTY_NOTICES.md`, and update the in-app licence screen.

Until Phase 4 is complete, the app is **distributed under GPL v3** — including the new code, because
it is part of a combined work. That is exactly why Route B is worth one email before Phase 3 starts:
if the upstream author grants a dual licence, Phases 3 and 4 largely disappear.

---

## What to do this week

1. **Send the email** in `DUAL_LICENCE_REQUEST.md`. One message; potentially removes the whole
   problem.
2. **Ship under GPL v3** meanwhile — the checklist in `GPL_SHIP_CHECKLIST.md` is already implemented
   in this build, so there is nothing blocking release.
3. **Start Phase 1** (the interface boundary) and **subsystem #1** (docs/extensions/scripts) —
   13% of the derived code for about two weeks of work, at essentially zero legal risk.
4. **Add the CI gate**: fail the build if the derived-line count increases.

## Cost summary

| Route | Time | Cost | Result |
|---|---|---|---|
| A — ship under GPL v3 | done | $0 | You ship today, own your code, users may share source |
| B — buy a dual licence | days–weeks | licence fee | You own it outright, closed if you want |
| C — clean-room replacement | 6–12 months | 1–2 developers | You own it outright, closed, and it is your own engineering |

B and C are not exclusive: **try B first**, and run C in parallel starting with the cheap
subsystems (#1 and #2), which are worth doing regardless.
