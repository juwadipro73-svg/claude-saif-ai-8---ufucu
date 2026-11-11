# Third-party notices

SAIF AI is built on work published by other people. This file names every
third-party component the distributed APK contains, says who wrote it, and
states the licence it comes under. Their terms travel with them; nothing in
`LICENSE` overrides them.

The list is kept short and checked: a notice nobody reads protects nobody.


## Copyleft components

These are the ones that carry obligations, so they are at the top.

| Component | Version | Licence | What it does here |
|---|---|---|---|
| `org.scilab.forge:jlatexmath` (via `ru.noties:jlatexmath-android`) | 0.2.0 | **GPL-2.0-or-later with Classpath Exception** | Renders LaTeX formulas in assistant replies |
| `io.github.Rosemoe.sora-editor:editor`, `:language-textmate`, `:language-java` | 0.23.6 | **LGPL-2.1** | The code editor |

Both are **linked, not modified**. What that means in practice:

- **JLaTeXMath** carries the *Classpath Exception*. Its own licence text says
  that linking it with independent modules - which SAIF AI is - does **not**
  put the combined work under the GPL. The library's notice and licence must
  be shipped with the app, and they are.
- **sora-editor** is *LGPL*, the lesser licence. Linking a closed application
  against an LGPL library is permitted provided the library can be replaced:
  it is used here as an unmodified dependency from Maven Central, so anyone
  can rebuild against a different version. The library's notice and licence
  must be shipped with the app, and they are.

Neither one makes the application itself a derivative work that has to be
published under the GPL.


## Permissive components

These require attribution and nothing more.

| Component | Version | Licence | What it does here |
|---|---|---|---|
| `com.github.termux.termux-app:terminal-emulator` | v0.118.1 | Apache-2.0 | Native PTY for the terminal |
| `com.alphacephei:vosk-android` | 0.3.75 | Apache-2.0 | On-device speech recognition |
| `net.java.dev.jna:jna` | 5.13.0 | Apache-2.0 (also offered under LGPL-2.1) | Native access required by Vosk |
| `io.noties.markwon:ext-latex` | 4.6.2 | Apache-2.0 | Markdown rendering |
| `org.jsoup:jsoup` | 1.18.3 | MIT | HTML parsing for the web tools |
| `com.github.mwiede:jsch` | 0.2.20 | BSD-style (see its `LICENSE.txt`) | SSH into the local workspace |
| `org.apache.commons:commons-compress` | 1.26.1 | Apache-2.0 | Unpacking workspace archives |
| `org.tukaani:xz` | 1.9 | Public domain | XZ decompression |
| `io.coil-kt:coil-compose`, `:coil-svg` | 2.7.0 | Apache-2.0 | Image loading |
| `com.squareup.okhttp3:okhttp`, `:okhttp-sse` | 4.12.0 | Apache-2.0 | HTTP and streaming responses |
| AndroidX, Jetpack Compose, Room, WorkManager, Media3, Navigation 3, Security Crypto, WebKit, DataStore, apksig | see `gradle/libs.versions.toml` | Apache-2.0 | Android foundation |
| Kotlin, kotlinx.serialization, kotlinx.coroutines | see `gradle/libs.versions.toml` | Apache-2.0 | Language and runtime |
| Google Play services, Firebase (Auth, Firestore, Realtime Database, App Check) | see `gradle/libs.versions.toml` | Google's own terms | Sign-in and cloud sync |
| Google Guava `listenablefuture` | 1.0 | Apache-2.0 | Concurrency primitive used by the terminal |

### One thing worth stating plainly about Termux

The `termux-app` repository is GPL-3.0-only, but it explicitly exempts its
`terminal-emulator` library, which it documents as Apache-2.0 (it descends
from `jackpal/Android-Terminal-Emulator`). SAIF AI depends on
`terminal-emulator` and nothing else from Termux, so no GPL-3.0 obligation
comes from this dependency. That distinction is easy to get wrong, which is
why it is written down here rather than left for someone to re-derive.


## Bundled assets

| Asset | Licence |
|---|---|
| `app/src/main/assets/glibc-min-arm64.tar` | LGPL-2.1 (glibc runtime exception applies) |
| Vosk speech model, downloaded on first use from `alphacephei.com` | Apache-2.0 |
| Google Material icons (`material-icons-extended`) | Apache-2.0 |
| CodeMirror and other editor assets pulled in by sora-editor components | as declared by their projects |


## Provenance of this build (measured)

These numbers are produced by comparing this tree against a real checkout of
the project SAIF AI's code once sat alongside. They are re-generated with:

```bash
git clone --depth 1 https://github.com/Santoshkurmi/antigem.git /tmp/upstream-antigem
python3 tools/licence/provenance.py --upstream /tmp/upstream-antigem --write
```

| | files | lines |
|---|---|---|
| Original SAIF AI code (never existed upstream) | 403 | — |
| Files that share a path with an upstream file | 17 | ~640 |
| — of which identical to upstream | **0** | **0** |
| — of which lightly modified from upstream | **0** | **0** |
| Highest similarity to any upstream file | **0.455** | |

The 17 files that remain comparable are build scripts, the application
manifest and standard Android resource files. Their highest similarity to any
upstream file is 0.455, on a nine-line adaptive-icon wrapper whose element
set is fixed by Android. What similarity remains is shared vocabulary -
manifest tag names, Gradle DSL keywords, XML declarations - not shared
expression.

Per-file records: `tools/licence/upstream_derived_files.txt` (comparable) and
`tools/licence/saif_owned_files.txt` (original).
`tools/licence/add_saif_headers.py` stamps the SAIF AI copyright notice on
the original files only, and reports an error if a file with no upstream
counterpart is ever left unstamped.

Full analysis: `LICENSE_AUDIT.md`.


## Getting the licence texts

Any component's own licence text is the authoritative one. For the copyleft
components:

- GPL-2.0 with Classpath Exception: <https://www.gnu.org/licenses/old-licenses/gpl-2.0.html>
- LGPL-2.1: <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>
- Apache-2.0: <https://www.apache.org/licenses/LICENSE-2.0>


## Native components shipped in the APK

These are executable files, not libraries the app links against. Each is run as
its own process, which is why they do not place SAIF AI's own code under their
licence — but their own licences still apply to them, and their source is
offered here as those licences require.

### proot — GPL-2.0-or-later

`app/src/standard/assets/bin/proot`

proot lets a glibc Linux program run on Android by translating its loader and
library paths. It is a separate executable: the app starts it and talks to it
across a process boundary. Under GPL-2.0 section 2 that is mere aggregation, so
it does not place SAIF AI's own source under the GPL.

Source: <https://github.com/termux/proot>

### libtalloc — LGPL-3.0-or-later

`app/src/standard/assets/bin/libtalloc.so.2`, `app/src/standard/assets/lib/libtalloc.so.2`

Samba's hierarchical memory allocator, a dynamic library proot loads. LGPL
obligations are satisfied by using a shared object and by this written offer.

Source: <https://git.samba.org/?p=samba.git;a=tree;f=lib/talloc>

### Go runtime — BSD-3-Clause

Statically linked into the SAIF AI workspace daemon built from
`termux-daemon/server.go`.

The Go standard library and runtime are distributed under a BSD-3-Clause
licence, which permits static linking without any obligation on the resulting
binary. Included here for completeness.

Source and text: <https://go.dev/LICENSE>

### Debian root filesystem — downloaded at runtime

SAIF AI does not redistribute a Linux distribution. On first use it downloads
`debian-trixie-aarch64-pd-v4.29.0.tar.xz` from
<https://github.com/termux/proot-distro/releases/tag/v4.29.0>, or whatever
address the user sets in Settings, and unpacks it inside the app's private
storage.

That rootfs contains several hundred packages under their own licences — the
Debian Free Software Guidelines terms each upstream author chose. Those
licences travel with those packages and are listed in
`/usr/share/doc/*/copyright` inside the installed environment. SAIF AI is not
the distributor of those packages and does not speak for them; the user's
download is governed by Debian's own terms.

### Written offer

For the copyleft components above, SAIF AI offers the corresponding source for
three years from the date of the build, as GPL-2.0 section 3 and LGPL-2.1
section 6 require. Every component named here is available at the address
given, and each is a complete, unmodified copy of the upstream release.
