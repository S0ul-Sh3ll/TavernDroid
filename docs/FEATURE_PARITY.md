# Feature parity and test results

TavernDroid runs the SillyTavern 1.19.0 server and web UI (plus the small fixes in [`patches/`](../patches/README.md)),
so every SillyTavern feature is the same code as on a PC. This page lists what was **verified** by the end-to-end
suite in [`tools/e2e`](../tools/e2e) and by hand, and the Android-specific differences.

**Final suite run (release build, Android 14 emulator, 2.5 GB RAM, clean install): 84 of 89 passed.** The five
failures were re-checked by hand and are test-side issues (all five features work; see the bottom of this page).
The phone checks ran on an OPPO CPH2437 (Android 16, Dimensity 9000).

## Verified

### Generation
| Feature | Result |
|---|---|
| Chat Completion, custom OpenAI-compatible endpoint (llama.cpp on a LAN PC), Semi-strict post-processing, custom body | ✅ |
| Streaming on and off | ✅ |
| Swipe (generate an alternative), swipe back, regenerate, continue, impersonate, quiet `/gen` | ✅ |
| Stop button mid-stream keeps the partial reply | ✅ |
| Send right after swipe + regenerate (regression test for patch 0001) | ✅ |
| Text Completion (llama.cpp) | ✅ |
| OpenRouter: key, model list (459 models), free model generation | ✅ (some `:free` models rate-limited upstream) |
| Reasoning block display | ✅ |
| All 19 tokenizers (SentencePiece, tiktoken, web-tokenizers; the 5 GitHub-hosted ones are bundled) | ✅ |
| Connection profiles (`/profile-create`, `/profile`, `/profile-update`) | ✅ |

### Characters, chats and prompts
| Feature | Result |
|---|---|
| Create a character in the UI with an avatar; card PNG carries `ccv3` data | ✅ |
| Edit, favorite, duplicate, rename, delete, tags | ✅ |
| Export PNG and JSON to `Downloads/SillyTavern` | ✅ |
| Import JSON (V2), PNG, CharX (with embedded lorebook), import from URL | ✅ |
| New chat, rename, list, search, chat backups | ✅ |
| Edit, hide/unhide messages; checkpoints and branches | ✅ |
| Export chat (JSONL, TXT); import JSONL | ✅ |
| Persona with avatar, description injected into the prompt | ✅ |
| World Info: keyword and constant entries injected into the prompt | ✅ |
| Author's Note in the prompt | ✅ |
| Group chat with two members generating | ✅ |
| Chat Completion presets (save, switch, delete); instruct/context templates | ✅ |

### Extensions and server features
| Feature | Result |
|---|---|
| System TTS (Android TextToSpeech bridge): voices, speak, cancel, `/speak` | ✅ |
| OpenAI-compatible TTS (Pocket TTS) through the server proxy, MP3 played in the WebView | ✅ |
| Translate (Google) and `/translate` | ✅ |
| Web page scraper | ✅ |
| Vector Storage with **local** transformers.js embeddings (ONNX/WASM in Node on Android) | ✅ |
| Expression classification with a local model | ✅ |
| Image captioning with a local model (ViT-GPT2) | ✅ (memory-heavy, see below) |
| Image generation (AI Horde), image saved in the user gallery | ✅ |
| Summarize (main API) | ✅ |
| Data Bank: add, list, vector search | ✅ |
| Quick Replies (set, button, run), Regex scripts, STscript (variables, conditionals, loops, closures), macros | ✅ |
| Backgrounds (upload, thumbnail), user images, themes, settings snapshots, stats, data maid, assets index | ✅ |
| Third-party extensions with the bundled git: install, version, update, branches, delete, global install | ✅ |

### Android shell
| Feature | Result |
|---|---|
| PIN lock: required on first launch, mismatch rejected, salted hash, rate limiting, change PIN, lock now, auto-lock timer (foreground, background, typing keeps it open), "Immediately" mode with picker exemption | ✅ |
| File picker imports, downloads to `Downloads/SillyTavern` (byte-exact), Back closes panels first | ✅ |
| Background generation with wake lock and reply notification | ✅ |
| Rotation keeps the page; external links open the browser; microphone permission for the page | ✅ |
| WebView renderer crash → page rebuilt automatically | ✅ |
| Node killed (like the out-of-memory killer) → restarted automatically, reason in the log | ✅ |
| Port change; port already in use → clear message | ✅ |
| LAN mode: 401 without the password, 200 with it, the app stays signed in | ✅ |
| config.yaml editor, server log, licenses, clear frontend cache, repair app files (data kept) | ✅ |
| Notification "Stop server" action; Start from the stopped screen | ✅ |
| Start on boot (real reboot); if Android refuses a boot start, a tap-to-start notification instead of a crash | ✅ |
| Keep screen on | ✅ |
| Backup to zip and restore; import of a Termux/PC `data` folder | ✅ |

## Bugs found by testing and fixed

| Bug | Fix |
|---|---|
| Android WebView has no Web Speech synthesis: SillyTavern's default **System** TTS threw on every tap and could not speak | `speechSynthesis` polyfill on Android TextToSpeech |
| Swipe, then regenerate/delete before the animation finished → Send silently stopped working until reload (SillyTavern) | [`patches/0001`](../patches/README.md) |
| First token count on a fresh install took 5+ minutes (5 tokenizers downloaded from GitHub) and failed offline | tokenizers bundled in the APK |
| Out-of-memory kill of Node while large local models load left the user on an error screen | automatic restart with crash-loop protection |
| Start on boot crashed on Android versions that refuse background starts at boot | caught; tap-to-start notification |
| Terminal-title escape codes leaked into the server log | stripped |
| On vivo/iQOO (and similar vendor ROMs) a reply paused while the app was in the background | one-time prompt for unrestricted battery use, *Background activity* settings, renderer kept important while generating |
| With **Smooth Streaming** on, replies crawled or stopped while the app was in the background or behind the lock screen: WebView reports `document.hasFocus() === true` when hidden, so SillyTavern kept "typing" with timers that Chrome throttles in hidden pages | `hasFocus()` reports `false` while the page is hidden, like a browser tab (verified on an iQOO 12: whole reply received in the background) |
| Reply notifications played a quiet sound with no vibration and no pop-up (normal-importance channel without vibration) | new high-importance **Replies** channel with vibration and pop-up (old channel removed); the previous reply notification is removed before posting, because an identical update ("New reply" again) is silent |

## Known limits

* **Local AI models are memory-heavy.** Captioning, embeddings and classification run inside the phone's Node
  process. On a 2.5 GB device, loading all three can exceed RAM; the server then restarts automatically. On phones
  with ≤4 GB RAM prefer remote providers for captioning.
* Some SillyTavern features need a key or service of their own (for example Pollinations image generation now needs an
  API key); these behave exactly as on desktop.
* Server plugins that need `npm install` are not supported (npm is not bundled).

## Android-specific behaviour

| Desktop behaviour | On TavernDroid |
|---|---|
| Drag and drop files onto the page | use the import buttons (system file picker) |
| Downloads go to the browser's folder | `Downloads/SillyTavern/` (Android 10+) or a "Save as" dialog (Android 7–9) |
| Updating with `git pull` | SillyTavern updates ship as app updates; data is kept |
| Reading the server console | App settings → Server log |
| Editing `config.yaml` in a text editor | App settings → Edit config.yaml |
| `127.0.0.1:8000` | `127.0.0.1:8800` by default, so it does not clash with Termux SillyTavern or a local LLM |

## The five failed tests in the final run

| Test | Why it failed | Verified by hand |
|---|---|---|
| Keep screen on | the window-flag check reads the window list too soon after navigating back | flag set and cleared ✅ |
| Node dies → Retry | written before automatic restart existed | auto-restart ✅, Retry ✅ |
| Notification Stop action | the scripted tap on the notification's expand arrow misses | Stop and Start ✅ |
| Start on boot (simulated) | a shell broadcast is not the real boot | real reboot ✅, fallback notification ✅ |
| Summarize | the preceding preset test switched the connection to OpenAI (presets carry the connection) | Summarize ✅ |

## Observed SillyTavern behaviour (same as desktop)

* After switching connection profiles the status can stay "Not connected" until **Connect** is pressed again.
* Quick Replies are disabled by default; the bar appears after enabling them in the extension settings.
* Data Bank search uses a score threshold; very short files may need a lower threshold to match.
