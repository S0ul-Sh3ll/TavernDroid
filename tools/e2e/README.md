# End-to-end tests

Drives the real app (release or debug build) on an emulator or phone through Chrome DevTools (WebView) and adb.

```bash
export ANDROID_SERIAL=emulator-5554            # device to test
export E2E_LLM=http://10.0.2.2:8000             # OpenAI-compatible server (10.0.2.2 = the host, from an emulator)
export E2E_LLM_KEY_FILE=~/path/to/llm.key       # or E2E_LLM_KEY=...
export E2E_OPENROUTER_KEY_FILE=~/path/to/or.key # optional, for the OpenRouter test
export E2E_TTS=http://10.0.2.2:8092/v1/audio/speech   # optional OpenAI-compatible TTS
node tools/e2e/run.mjs setup generation content extensions native
```

* `setup` **clears the app's data** on the device, creates the test PIN (`E2E_PIN`, default 246802) and connects the backend.
* WebView inspection must be possible: a debug build, an emulator image (`userdebug`), or *Settings → WebView debugging*.
* `native` uses `adb root` (emulator images) for a few checks.
* Results are printed and appended to `tools/e2e/report.jsonl`. `E2E_FROM="<test title>"` resumes a suite.
