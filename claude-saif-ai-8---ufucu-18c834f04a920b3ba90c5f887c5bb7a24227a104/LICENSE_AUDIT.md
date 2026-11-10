# SAIF AI — licence audit

**Date:** 2026-11-10 · **App:** `com.saifai.app` · **Method:** line-by-line comparison against the
upstream project this app was built from.

This is not an estimate. Every number below was produced by `tools/licence/provenance.py` against a
real checkout of [AntiGem by Santosh Kurmi](https://github.com/Santoshkurmi/antigem). Re-run it any
time:

```bash
git clone --depth 1 https://github.com/Santoshkurmi/antigem.git /tmp/upstream-antigem
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
```

---

## 1. The evidence

| | files | lines |
|---|---|---|
| **Original SAIF AI code** (never existed upstream) | **672** | **~96,000** |
| Still **identical** to upstream (≥95% of lines) | 241 | 55,219 |
| Still **modified** upstream code (60–95%) | 86 | 54,928 |
| **Heavily reworked** upstream code (20–60%) | 16 | 3,164 |
| Effectively **different** (<20%) | 4 | 274 |
| **Total upstream-derived** | **347** | **~113,600** |

So roughly **54% of the compared source lines in this app are still the upstream GPL project's
code.** The upstream code is concentrated in:

| Area | What it is | Derived lines |
|---|---|---|
| `app/src/main/java/com/example/gemini/**` | the AI-agent workspace: chat backends, terminal, IDE, MCP, plugins, settings | ~65,400 |
| `app/src/main/java/com/termux/**` | the terminal emulator | ~2,750 |
| `proto_generator/` | generated gRPC/protobuf bindings for the Antigravity protocol | ~27,400 |
| `agy_ide_bridge/` | the local Go bridge server | ~7,180 |
| `docs/`, `extensions/`, `extension_docs/`, `scripts/` | upstream docs and bundled extensions | ~15,000 |

And your own code — **672 files, ~96,000 lines** — is: `com/example/ui` (the whole SAIF AI look and
feel), `com/example/studio` (video), `com/example/compiler`, `com/example/cloud`,
`com/example/builder`, `com/example/agent`, `com/example/llm`, `com/example/data`,
`com/example/util(s)`, `com/example/github`, `com/example/audio`, `com/example/stt`,
`com/example/live`, `com/example/terminal`, and the `automation/` module.

**No upstream file was found copied under a different path** — the 672 files really are new work, not
renamed upstream files. That part is genuinely yours.

### What was done with this

`tools/licence/add_saif_headers.py` stamped

```
/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
```

on **454 of your source files** (Kotlin, Java and Go on the owned list; non-source files skipped).
It refuses to stamp anything on the upstream list, and it verifies afterwards that no
upstream-derived file carries the notice.

---

## 2. Why "rewrite it with different names" does not transfer copyright

You asked for the GPL code to be replaced by a from-scratch implementation so that you own the
copyright and it is not a derivative work. Here is the problem, stated plainly, because it would be
worse than useless to let you ship on a false assumption:

1. **Copyright covers more than the literal text.** Structure, sequence, organisation, the way the
   pieces fit together and the choices made while writing them are all protected. Renaming
   variables, reordering functions and splitting files produces a **derivative work**. That is the
   word courts use, and the answer does not change because the rewrite was done by a person or by a
   model.
2. **A rewrite made by reading the original is the classic infringement fact pattern.** The question
   a court asks is "could this have been written without the original?" When the author had the
   original open, the answer is no, and the evidence (commit history, chat logs, this very
   conversation) proves access. Asking an AI to "discard the structure and use different names" does
   not remove access — it documents it.
3. **Clean room is a *process*, not a style of writing.** A real clean-room reimplementation means
   the implementers **never saw the original source**. One team writes a specification from the
   outside; a different, isolated team implements from that specification; everything is documented;
   a lawyer reviews the trail. Neither I nor anyone who has read this tree can be that isolated team
   for the 113,600 lines that came from upstream.
4. **Deleting `LICENSE` and the notices makes it worse.** Removing the GPL text and stamping "SAIF
   AI, all rights reserved" over someone else's code converts a fixable compliance mistake into
   **wilful infringement** — the difference between "send a corrected notice" and statutory damages,
   an injunction, and personal liability for a commercial app. The notices are currently the thing
   protecting you.

None of this stops you shipping, charging money for, or branding this app as SAIF AI. Your brand,
your design, your 96,000 lines and everything in §1 are yours. It only decides **under what terms
the combined app must be distributed**.

---

## 3. The three routes that actually work

### Route A — Ship now under GPL v3 ✅ *zero risk, zero delay*

Keep `LICENSE` and `THIRD_PARTY_NOTICES.md`, then:

* publish the complete corresponding source for every build you distribute (public repo, or a
  written offer valid three years);
* keep the GPL notices in the upstream-derived files;
* show an in-app licence screen and a source link (Settings → **LICENCES & OPEN SOURCE** in this
  build does exactly that);
* only link GPL-compatible libraries into the app.

You keep ownership of your code, your name, your artwork, and you can still sell the app. You simply
cannot stop users from sharing the source. Everything in
[`GPL_SHIP_CHECKLIST.md`](GPL_SHIP_CHECKLIST.md) is already implemented in this build.

### Route B — Buy a licence from the upstream author ✅ *the only way to make it closed*

Only the copyright holder can release you from GPL. Ask Santosh Kurmi for a **commercial
dual-licence**. See [`DUAL_LICENCE_REQUEST.md`](DUAL_LICENCE_REQUEST.md) — a ready-to-send email.
This is one email and usually a modest fee, and it settles the question permanently. **Do this first;
it may make Route C unnecessary.**

### Route C — Replace the engine properly ⏳ *months, and only with a real process*

[`CLEANROOM_ROADMAP.md`](CLEANROOM_ROADMAP.md) sets out the phased plan: an isolation boundary, a
specification written from the outside, an implementation team with no access, and the audit trail a
lawyer will need. It is realistic about effort (~113,600 lines, 6–12 months with a small team) and it
names the subsystems that are cheapest to replace first.

Until a subsystem is genuinely replaced that way, **it stays under GPL v3**. A half-replaced app is
still a derivative work.

---

## 4. What was deliberately NOT done

* ❌ `LICENSE` and `THIRD_PARTY_NOTICES.md` were **not** deleted or edited.
* ❌ No GPL-licensed file was deleted. Deleting the 347 upstream-derived files would remove the
  entire agent workspace — chat backends, terminal, IDE, MCP, plugins, the Go bridge and the
  generated protocol bindings. There would be no app left to ship.
* ❌ No upstream-derived file was re-licensed, renamed into SAIF AI's namespace, or stamped with a
  SAIF AI notice.

Those three things are the difference between a licensing problem you can still fix quietly today
and one you cannot.

---

*Engineering analysis, not legal advice. Before shipping commercially, have a lawyer who works on
open-source licensing review the route you pick.*
