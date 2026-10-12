# How TavernDroid works

TavernDroid is SillyTavern on Android without Termux. Rather than re-implementing SillyTavern, it runs the
real, unmodified SillyTavern server inside the app and shows its web UI in a full-screen WebView. A thin
native layer handles what a phone browser cannot do on its own.

```
┌──────────────────────────── Android app (Kotlin) ────────────────────────────┐
│ MainActivity ── WebView ── http://127.0.0.1:8800 ──┐                          │
│   │  file picker · downloads · back key · mic       │                          │
│   │  window.TavernDroidNative  ◄── assets/taverndroid.js (document-start)     │
│   │                                                  ▼                          │
│ ServerService (foreground) ── ServerController ── libnode.so server.js ──────► LLM / image / TTS APIs
│   wake lock while generating        │               (SillyTavern 1.19, unmodified)
│ SettingsActivity · LogActivity      │               spawns git (libgit.so) for extensions
│ ConfigEditorActivity · Backups      ▼
│                       files/st/{app,data,config.yaml,home,bin,runtime,logs}
└───────────────────────────────────────────────────────────────────────────────┘
```

## Layers

| Layer | What it is | Where |
|---|---|---|
| JavaScript runtime | Node.js 24 LTS built for Android (bionic) by Termux, with its shared libraries | `app/src/main/jniLibs/arm64-v8a/` (generated) |
| git | git 2.56 + `git-remote-http` (libcurl) from Termux, used by SillyTavern's extension manager through `simple-git` | same |
| SillyTavern | `git archive` of the release checkout + production `node_modules`, zipped | `assets/st-bundle.zip` (generated) |
| Shell shim | Node `--require` preload that sends `shell: true` spawns to `/system/bin/sh` | `runtime/android-preload.cjs` |
| Page bridge | Injected script: blob downloads, back-button detection, generation events, app button | `app/src/main/assets/taverndroid.js` |
| Native shell | Kotlin activities and the foreground service | `app/src/main/java/io/github/taverndroid/` |

## Making Termux binaries run inside an ordinary app

Android only lets an app execute files from its `nativeLibraryDir` (W^X on Android 10+), and only
extracts files named `lib*.so` there. `tools/prepare.sh` therefore:

1. renames executables: `node` → `libnode.so`, `git` → `libgit.so`, `git-remote-http` → `libgitremotehttp.so`;
2. renames versioned libraries (`libz.so.1` → `libz1.so`, `libcrypto.so.3` → `libcrypto3.so`, ICU 78, …) and
   rewrites every `DT_NEEDED`/`DT_SONAME` in place with `tools/elfpatch.py` (new names are never longer, so
   `.dynstr` is patched in place and symbol-version records stay consistent);
3. packages them with `useLegacyPackaging = true` so they are extracted, and `keepDebugSymbols` so AGP
   does not strip them.

At start-up `Installer` symlinks `files/st/bin/{node,git}` and `files/st/bin/git-core/git-remote-https`
to those files; `ServerController` runs Node with:

| Variable | Why |
|---|---|
| `LD_LIBRARY_PATH=nativeLibraryDir` | Termux binaries have `RUNPATH=/data/data/com.termux/files/usr/lib` |
| `OPENSSL_CONF`, `SSL_CERT_FILE`, `CURL_CA_BUNDLE`, `GIT_SSL_CAINFO` | OpenSSL and curl were configured for Termux's prefix |
| `GIT_EXEC_PATH`, `GIT_TEMPLATE_DIR`, `GIT_CONFIG_NOSYSTEM=1` | git's helpers and templates live under Termux's prefix |
| `HOME`, `TMPDIR`, `PATH` | app-private home, cache temp dir, `bin/` first so `simple-git` finds `git` |

Command line: `server.js --configPath files/st/config.yaml --dataRoot files/st/data --port 8800 --listen false
--browserLaunchEnabled false --heartbeatInterval 0`, plus `--whitelist false --basicAuthMode true` and
`SILLYTAVERN_BASICAUTHUSER_*` when LAN access is on. CLI flags win over `config.yaml`, so everything else in
`config.yaml` stays user-editable.

## Storage

| Path (app-private) | Content | Survives app update |
|---|---|---|
| `files/st/app` | SillyTavern program files, re-extracted when the bundled version changes | global third-party extensions, `plugins/` and `backups/` are carried over |
| `files/st/data` | SillyTavern data root: users, characters, chats, worlds, presets, secrets, `_webpack` cache | yes |
| `files/st/config.yaml` | created by SillyTavern from `default/config.yaml` on first run | yes |
| `files/st/logs/server.log` | current run's console output (previous run in `server.previous.log`) | yes |

Backups (`BackupManager`) zip `data/`, `config.yaml`, global extensions and plugins. Restore accepts those
zips and plain zips of a desktop or Termux `data` folder (with or without a wrapping `SillyTavern/` folder).

## Lifecycle

* `MainActivity` starts `ServerService` (foreground, `specialUse`). `ServerController` extracts the bundle if
  needed, checks the port, starts Node and polls `HEAD /` until it answers.
* Every server start gets a run number. The WebView reloads when it sees a new one, because the old page's
  CSRF token belongs to the previous server process.
* The first start compiles SillyTavern's frontend library bundle with webpack (about 17 s on a Dimensity
  9000). It is cached in `data/_webpack`, so later starts take a few seconds.
* Stopping sends SIGTERM (SillyTavern shuts down cleanly) and kills after 5 s.
* If Node exits unexpectedly (typically SIGKILL from Android's out-of-memory killer while large local models
  load on a low-RAM phone), it is restarted automatically, at most 3 times in 10 minutes and only if it had been up
  for 20 s; the log says why. Otherwise the error screen offers Retry.
* Tokenizers that SillyTavern downloads from GitHub on first use (Command R/A, Qwen2, Nemo, DeepSeek; 13 MB) are
  shipped in the APK and seeded into `data/_cache`, so token counting works offline and immediately.
* SillyTavern is shipped as released except for the fixes in [`patches/`](../patches/README.md).

## WebView adaptations (`taverndroid.js` + `MainActivity`)

| Browser feature SillyTavern relies on | Android replacement |
|---|---|
| `<a download href="blob:…">` exports (chats, cards, presets, themes, Quick Replies, Data Bank…) | `URL.createObjectURL` is wrapped so blobs outlive `revokeObjectURL`; the file is streamed in 768 KB base64 chunks to `FileSaver` → `Downloads/SillyTavern/` (MediaStore; system "Save as" on Android 7–9) |
| Server-sent downloads | `DownloadListener` fetches with the WebView's cookies (and basic auth) |
| `<input type=file>` (imports, avatars, backgrounds, attachments) | `onShowFileChooser` → system picker; MIME filter only when every accepted type is a known MIME |
| Escape key closing panels | Back key: if a dialog, drawer or editor is open, a real `KEYCODE_ESCAPE` is sent (SillyTavern's own Escape cascade closes the top layer); otherwise the app goes to the background |
| `window.open`, external links | opened in the default browser; local links stay in the WebView |
| `getUserMedia` (speech extensions) | `onPermissionRequest` → runtime RECORD_AUDIO/CAMERA permission |
| Fullscreen video | `onShowCustomView` |
| Web Speech synthesis (SillyTavern's **System** TTS provider) | WebView has none, so `taverndroid.js` implements `speechSynthesis` / `SpeechSynthesisUtterance` (voices, queue, rate/pitch/volume, start/end/error, cancel/pause/resume) on Android `TextToSpeech` (`NativeTts.kt`) |
| Tab stays open while generating | foreground service + wake lock between `GENERATION_STARTED` and `GENERATION_ENDED/STOPPED` |
| Reply arrives while tab hidden | `MESSAGE_RECEIVED` → notification (only when the app is in the background) |
| Server console (recovery codes, errors) | Log screen with share button |

## App lock

`AppLock` keeps the unlocked state only in memory, so every new process starts locked. Every activity that shows
user content extends `LockedActivity`, which opens `LockActivity` on resume and every 5 s while the app is locked
or the inactivity limit has passed.

* **PIN**: 6 digits, stored as PBKDF2-HMAC-SHA256 (SHA-1 below Android 8), 120 000 iterations, 16-byte salt, in
  the app-private `applock` preferences. There is no recovery; clearing app storage resets everything.
* **First launch**: `LockActivity` asks to create and confirm a PIN. Back sends the app to the background, so
  nothing behind it is reachable.
* **Attempts**: 5 free, then 30 s, 1 min, 2 min, … up to 30 min between tries. The counter is persisted, so
  killing the app does not reset it.
* **Inactivity**: native touches and keys (`onUserInteraction`) plus page `input`/`keydown`/`scroll`/`pointerdown`
  events reported through the bridge (on-screen keyboard typing never reaches the activity). Time in the
  background counts. "Immediately" locks when the last activity stops, except while a picker or save dialog
  launched by the app is open; that exemption ends as soon as the app is visible again.
* **Privacy extras**: reply notifications show only who replied unless "Show reply text in notifications" is on.
  On Android 13+ the recent-apps thumbnail is hidden.
* LAN clients are not affected by the PIN. They use the separate basic-auth password.

## What was reverse-engineered

Before building, SillyTavern 1.19.0 was mapped statically with REA (`analyze_javascript_application`
over `server.js`, `src/`, `public/scripts/`: 305 JS files, 1.27 M AST nodes; Evidence
`ev_6f32771fddaf2acda688c6e5c4c616627a2c5291a3be13adb9be76c07b9cd159`). Its 68 client request edges
resolve to 45 endpoints, all present in the server route table. The full maps are in
[`docs/research/`](research/):

* [`01-server.md`](research/01-server.md): boot, config keys, middleware, all 335 route handlers, file formats, portability.
* [`02-generation-core.md`](research/02-generation-core.md): `Generate()`, prompt building per API, World Info, macros, all 293 slash commands.
* [`03-ui-extensions.md`](research/03-ui-extensions.md): every drawer and control, power-user settings, the 14 built-in extensions, extension API.

The key finding behind the design: the server has **no native addons and no process spawning except `git`**.
Everything else (tokenizers, ONNX, image codecs) is JavaScript or WASM, so an Android build of Node runs it
unchanged. That made it possible to ship every feature by running the real thing instead of re-implementing
roughly 167 K lines of JavaScript.
