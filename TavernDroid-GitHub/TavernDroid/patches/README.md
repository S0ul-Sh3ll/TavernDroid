# Patches to SillyTavern

TavernDroid ships the SillyTavern release unmodified except for the fixes in this folder. `tools/prepare.sh`
applies every `*.patch` (in name order, `patch -p1`) to the exported release tree, and the bundle version gets a
`+p<hash>` suffix so installed apps re-extract when the patches change. Each fix is meant to be sent upstream.

| Patch | Problem | Found by |
|---|---|---|
| `0001-always-release-swipe-lock.patch` | If a message is deleted or regenerated while a swipe is still animating, a step of `swipe()` throws (`Cannot read properties of undefined (reading 'swipe_id')` in `addOneMessage`/`endSwipe`) before `endSwipe()` resets `swipeState`. The fix wraps `swipe()` so the call that took the lock always releases it, and `endSwipe()` tolerates the missing message. `sendTextareaMessage()` then returns early for every send: the Send button silently stops working until the page is reloaded. Mobile WebViews make the window wider because animations finish later. | e2e suite (swipe → swipe back → `/regenerate` → send) |
