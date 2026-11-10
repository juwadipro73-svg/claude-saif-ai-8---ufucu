# Route B — request a commercial licence from the upstream author

**This is the single highest-value action in the whole licensing exercise.** One email can replace
6–12 months of clean-room engineering. Send it before starting any replacement work.

* **Upstream project:** AntiGem — https://github.com/Santoshkurmi/antigem
* **Author / copyright holder:** Santosh Kurmi
* **Licence today:** GNU GPL v3 (see `LICENSE` at the root of the upstream repository)
* **What you need:** permission to distribute the AntiGem-derived portions of SAIF AI under your own
  commercial terms, i.e. a **dual licence**

Only the copyright holder can grant this. No amount of rewriting substitutes for it, and no one else
— not GitHub, not a lawyer, not a model — is able to.

---

## Where to send it

Preferred, in this order:

1. A **GitHub issue or private security-advisory-style contact** on the upstream repository, or a
   direct message if the profile allows it.
2. The **email on the author's GitHub profile** (`gh api users/Santoshkurmi` shows it when public).
3. Any contact address in the upstream repository's `README.md`, `docs/`, or `AGENTS.md`.

---

## Email template

>Subject: Commercial dual-licence request for AntiGem (SAIF AI Android app)
>
>Hi Santosh,
>
>I'm building an Android app called **SAIF AI** (`com.saifai.app`) — an AI assistant with chat, a
>Linux terminal, a code editor and a video studio. The agent workspace inside it is built on
>**AntiGem**, and I want to be straightforward with you about that rather than quietly strip the
>notices.
>
>AntiGem is GPL v3, so as I understand it I can keep shipping SAIF AI as long as I publish the
>corresponding source and keep the licence and attribution in place. I'm happy to do that — and the
>app already carries your copyright and a link to the project.
>
>What I'd like to ask is whether you offer a **commercial dual licence**: permission to distribute
>the AntiGem-derived parts of SAIF AI under my own terms, without the source-publication
>obligation. Concretely:
>
>* **What I need:** the right to ship the app closed (or under my own EULA), and to keep your
>  attribution in an About/Licences screen.
>* **Scope:** the Android app, distributed outside the Play Store and possibly on it later.
>* **What I can offer:** a one-off fee, a revenue share, or a combination — whichever you prefer. I'm
>  also very happy to list you and AntiGem prominently in the app's credits.
>* **Not asking for:** exclusive rights, or anything that stops you licensing AntiGem to anyone else.
>
>For context, the AntiGem-derived code in my tree is about 347 files / ~113,000 lines. I can send
>you the exact list whenever useful.
>
>If you'd rather I simply continue under GPL v3, tell me and I'll do exactly that — no hard feelings,
>and I'll make sure the attribution and source link are correct.
>
>Thanks for building AntiGem in the first place; it saved me a year of work.
>
>Best,
>[your name]
>[your email] · [app / repo link]

---

## If the answer is yes

Get it **in writing** (email is fine) and make sure it states:

* the **exact scope** — which repositories/files, which apps, which distribution channels;
* whether it covers **future versions** of AntiGem or only the version you have;
* whether it is **perpetual and irrevocable**, and whether it transfers if you sell the app;
* the **attribution** the author still wants (most do want credit — that is cheap);
* the **fee and payment terms**.

Then:

1. Store the agreement, and record the granted scope in `THIRD_PARTY_NOTICES.md`.
2. Keep the attribution in the in-app **LICENCES & OPEN SOURCE** screen even if it is not required —
   it costs nothing and it is the right thing to do.
3. Only after the licence is signed: change `LICENSE` and remove the source-publication obligation.
   **Do not do it before** — until the signature exists, the app is GPL v3.

## If the answer is no, or there is no reply

Nothing is lost. Continue under **Route A** (ship under GPL v3 — the checklist is already
implemented in this build) and work **`CLEANROOM_ROADMAP.md`** starting with the cheap subsystems.

## If you cannot reach the author

Falling back on "I tried to contact them" is **not** a defence. In that situation the only safe
options are Route A and Route C. Note also that AntiGem may have other contributors besides the
repository owner — a dual licence needs the agreement of **every** copyright holder in the files you
use, so ask the author whether anyone else contributed to the parts you depend on.
