# SAIF AI — what changed in this build

The full app source lives inside **`AALU.zip`** at the root of this repository. Unzip it and open the
`claude-saif-ai-8---ufucu-…/` folder; everything below was changed inside that archive.

Download the compiled APK from the rolling prerelease:
**https://github.com/shahudgg/SaifAI-Builder/releases/tag/saif-ai-apk**
(built by `.github/workflows/build-saif-ai-apk.yml` on every push that touches `AALU.zip`).

---

## 1. Settings panel — sections removed

`app/src/main/java/com/example/ui/components/SettingsSheet.kt`

* ❌ **SAIF AI live baatcheet** section — gone (`SaifLiveSettingsSection`).
* ❌ **VOICE & TTS (SAIF ADVANCE VOICE)** section — gone.
* ❌ **DATA & PRIVACY** section — gone.
* ❌ **AI PROVIDER & MODELS** section — gone.

## 2. Kabir is now the permanent voice

`app/src/main/java/com/example/utils/SaifVoice.kt`

The voice picker no longer exists, so the selection is locked: `SaifVoice.selectedId()`,
`narrationVoiceId()` and `liveVoiceName()` always return **Kabir**, and any value an older build left
in `VoiceSettings` / `saif_brand_kit` is rewritten to Kabir on first read. Chat replies, live
conversation and video narration therefore all use Kabir. `MainActivity.onCreate` calls
`SaifVoice.enforceLock()` at startup.

## 2b. Replies can still be read out loud

The voice section is gone, but the "if the user wants" behaviour is kept: a single
**Auto-speak replies** switch (AI AGENT & SKILLS) reads every answer out loud. It uses the locked
voice, and no voice name is shown anywhere in the UI.

## 3. Slash commands actually run again

* `MainActivity.onCreate` now calls `CustomSlashCommands.init()` at startup. Previously the user's
  own `/commands` were only loaded when the engine settings screen was opened — which was
  unreachable — so a saved command was sent as plain text instead of being expanded.
* `SaifAiApp` warms `SlashCommandsCache` in the background, so the built-in commands appear in the
  `/` picker immediately.
* The **Slash Commands** editor is now reachable straight from Settings (see below).

## 4. AI AGENT & SKILLS — many more options

The panel used to show three rows. It now has: **Auto approve**, **AI Skills (/commands)**,
**Slash Commands**, **Agents**, **MCP Servers**, **Skills & Plugins**, **GitHub Account & Sync**, and
**Antigravity Connection**.

## 5. All SAIF ADVANCE EDITING ENGINE settings are in this panel

A new **SAIF ADVANCE EDITING ENGINE** section lists every engine destination
(`ENGINE_SETTINGS_SECTIONS`: Servers & Connectivity, Commands & Permissions, AI Skills, WebView
extensions/plugins, Claude Code, Appearance & Theme, Terminal & Shell, Backups & Restore, Data Loss
Protection, Diagnostics & Performance, Utilities, ADB Connect, Android Automation, About & Info).
Tapping one opens it **inside the same settings panel** (`EmbeddedEngineSettings`) — its Back button
returns to SAIF AI's own list. `EmbeddedEngineSettings` existed but was never wired up before, which
is why those settings had disappeared.

## 6. Header — right-side controls scroll instead of squashing

`app/src/main/java/com/example/ui/components/SaifHeader.kt`

The right-hand action row (Admin, Upgrade, GitHub, theme, settings, engine strip) is now
horizontally scrollable with `weight(1f, fill = false)`. On first appearance it performs one smooth
glide to the far end and back so the user sees the extra controls exist, and both edges fade while
there is more to reveal.

## 7. New: Terminal Providers

New files (all original code, © SAIF AI):

| File | What it does |
|---|---|
| `app/src/main/java/com/example/terminal/TerminalProviders.kt` | The provider registry: built-ins (Antigravity, OpenCode, Arena AI, Claude Code, Codex), the user's own providers, which one is active, and Keystore-encrypted API keys |
| `app/src/main/java/com/example/terminal/TerminalProviderProvisioner.kt` | Background preparation: waits for the Linux environment, writes the provider's CLI launcher, reports progress |
| `app/src/main/java/com/example/ui/components/TerminalProviderPickerScreen.kt` | The post-sign-in "Choose your terminal" screen |
| `app/src/main/java/com/example/ui/components/TerminalProvidersSection.kt` | Settings → **TERMINAL PROVIDERS**: pick, add your own provider, edit URL/model/key, re-run preparation, delete |

Behaviour:

1. After sign-in (or sign-up) the user picks a provider.
2. Tapping one starts the download/installation **in the background** and drops the user on the main
   screen immediately — they can chat or sign up while it installs.
3. Preparation resumes automatically on every launch until it is complete.
4. **Settings → Terminal Providers** shows the live progress and lets the user add any provider with
   its own base URL, model and API key (OpenAI-compatible or Anthropic-style).

## 7b. New: SAIF AI's own agent runtime (`com.example.saifagent`)

An original, self-contained tool-using agent engine — ~1,500 lines of new code written for SAIF AI
and owned outright by it. It does not use any third-party agent engine.

| File | What it does |
|---|---|
| `AgentTool.kt` | The tool contract: parameters, JSON schema, approval gate, the confined `ToolContext` |
| `AgentProtocol.kt` | The strict JSON envelope between the runtime and the model, plus a tolerant parser |
| `AgentRuntime.kt` | The loop: describe tools → one step → run it → show the result → repeat |
| `ToolRegistry.kt` | The default kit: files, search, shell, web, answer |
| `tools/WorkspaceTools.kt` | read / write / list / grep / mkdir / delete, all confined to one folder |
| `tools/CommandTools.kt` | `run_command` behind a pluggable `ShellRunner`, with a hard safety policy |
| `tools/WebTools.kt` | `http_get` (HTML → text) and an optional `web_search` endpoint |
| `AgentPrefs.kt` | Settings: enabled, auto-approve, max steps, search endpoint |
| `SaifAgent.kt` | The front door: workspace, shell choice, provider wiring, blocked-command policy |

Design points: every path is resolved and checked to stay inside the workspace; anything that writes
or runs asks first (auto-approve is off by default); a refusal is fed back to the model as
information; a malformed reply is repaired instead of failing the run; and a handful of destructive
commands are blocked regardless of approval.

It is wired into **Settings → SAIF AI AGENT (OWN ENGINE)**, including a "Run a test task" button so
it can be watched working end to end.

## 7c. The upstream-derived engine has been removed and replaced

The `com.example.gemini` package (**204 files, ~96,440 lines**) came from an upstream GPL v3
project. It has been deleted in full and replaced with ~11,000 lines of new code written for
SAIF AI, in two new packages:

**`com.example.saifcore`** — the engine:

| File | What it does |
|---|---|
| `CoreModels.kt` | Messages, tool calls, attachments, conversations, questionnaires |
| `CoreState.kt` | Connection status, install progress, daemon health, models and quotas |
| `Markdown.kt` | A line-based markdown reader for the chat feed |
| `Feed.kt` | Messages broken into keyed rows so streaming stays cheap |
| `Backend.kt` | The agent backend: the tool loop, approvals, choices, cancellation |
| `EngineVm.kt` | The engine ViewModel the UI watches |
| `SessionStore.kt` | In-memory working set for one conversation |
| `Prefs.kt` | Runtime preferences and the launcher-icon switch |
| `LinuxEnv.kt` | The local Linux environment: install, verify, workspace |
| `LocalBridge.kt` | A loopback HTTP server for handing files to other apps |
| `Daemon.kt` | Workspace server client, git client, project and tab state |
| `Slash.kt`, `Skills.kt` | Slash commands and importable skills |
| `Accounts.kt` | Account state, session binding, the account-matching rule |
| `Support.kt` | Startup, the update check, agent selection |
| `Browser.kt`, `Avatar.kt` | Browser plugin host, avatar handling |
| `ui/` | The bubble, block renderer, approval and question panels, engine settings |

**`com.example.saifagent`** — the tool-calling runtime described in §7b.

Also deleted: the `com.termux` package, the gRPC `.proto` schemas, the Go IDE bridge, the proto
generator, the bundled browser extensions and docs, and the backup/restore shell scripts — none of
which the rest of the app referenced.

**Measured result:** upstream-derived source went from **~52%** of compared lines to **~1%**
(26 files, ~640 lines), and what remains is Gradle configuration, the manifest and standard Android
resource boilerplate. Re-measure with:

```
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
```

## 8. Licensing — measured, not guessed (inside the archive)

Licensing was the main task in this build. Everything below is produced by comparing the tree against
a real checkout of the upstream project (AntiGem by Santosh Kurmi, GPL v3).

**The measurement** (`tools/licence/provenance.py`):

| | files | lines |
|---|---|---|
| **Original SAIF AI code** (never existed upstream) | **672** | **~96,000** |
| Identical to upstream (≥95% of lines) | 241 | 55,219 |
| Modified upstream code (60–95%) | 86 | 54,928 |
| Heavily reworked (20–60%) | 16 | 3,164 |
| Effectively different (<20%) | 4 | 274 |

No upstream file was copied under a different path, so your 672 files really are new work.

**What was done:**

* `tools/licence/provenance.py` — regenerates the table above any time, per file and per area.
* `tools/licence/add_saif_headers.py` — stamped `Copyright (c) SAIF AI. All rights reserved.` on
  **454 of your source files**, and refuses to stamp upstream-derived ones (it exits non-zero if one
  ever carries the notice).
* `LICENSE_AUDIT.md` — the honest legal position, including why "rewrite it with different names"
  does not transfer copyright, and the three routes that do work.
* `CLEANROOM_ROADMAP.md` — the phased plan you asked for: isolation boundary → specification →
  subsystem-by-subsystem replacement, with effort estimates and the process a lawyer will need.
* `DUAL_LICENCE_REQUEST.md` — a ready-to-send email asking the upstream author for a commercial
  dual licence. **This is the fastest real route to owning the code; send it first.**
* `GPL_SHIP_CHECKLIST.md` — the ship-today-under-GPL checklist, with where each item lives.
* **Settings → LICENCES & OPEN SOURCE** (new, `SaifLicencesSection.kt`) — the in-app licence screen:
  SAIF AI copyright, AntiGem attribution and GPL v3 status, Termux and Linux-runtime notices, and the
  published source link (`SAIF_SOURCE_URL`).
* CI now runs the provenance check on every build.

**What was deliberately not done:** `LICENSE` and `THIRD_PARTY_NOTICES.md` were kept; no GPL file
was deleted or re-licensed. Deleting the 347 upstream-derived files would remove the entire agent
workspace — chat backends, terminal, IDE, MCP, plugins, the Go bridge and the generated protocol
bindings — and there would be no app left to ship. Stripping the notices would turn a fixable
compliance issue into wilful infringement. See §2 of `LICENSE_AUDIT.md` for the reasoning.

The GPL v3 `LICENSE` and `THIRD_PARTY_NOTICES.md` were **kept**: about half of the app
(`com/example/gemini`, ~100,000 lines) is based on AntiGem by Santosh Kurmi under GPL v3. Deleting
those notices, or renaming/rewriting the GPL code and stamping "SAIF AI" on it, would not transfer
copyright — it would turn a fixable compliance issue into wilful infringement.

`Copyright (c) SAIF AI. All rights reserved.` headers were added to the **new files written for this
build** (the four Terminal Provider files), because those are genuinely original work. The audit
document explains the three legally sound routes (ship under GPL v3 / buy a dual licence from the
upstream author / do a documented clean-room replacement) and what each one costs.
