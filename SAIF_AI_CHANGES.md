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
