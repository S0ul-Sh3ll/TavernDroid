// Injected into every SillyTavern page by the Android app (document start).
// It never changes SillyTavern itself; it only adapts browser features that a
// WebView lacks to native Android equivalents through window.TavernDroidNative.
(() => {
    'use strict';
    if (window.__taverndroid || !window.TavernDroidNative) return;
    window.__taverndroid = true;
    const native = window.TavernDroidNative;

    // ---------------------------------------------------------------- downloads
    // SillyTavern exports files by clicking <a download href="blob:..."> and
    // usually revokes the blob URL right away. WebView has no download manager
    // for blob URLs, so keep the blobs alive briefly and stream them to Android.
    const blobs = new Map();
    const createObjectURL = URL.createObjectURL.bind(URL);
    const revokeObjectURL = URL.revokeObjectURL.bind(URL);
    URL.createObjectURL = (obj) => {
        const url = createObjectURL(obj);
        if (obj instanceof Blob) blobs.set(url, obj);
        return url;
    };
    URL.revokeObjectURL = (url) => {
        setTimeout(() => {
            blobs.delete(url);
            revokeObjectURL(url);
        }, 120000);
    };

    function guessName(href) {
        try {
            const path = new URL(href, location.href).pathname;
            return decodeURIComponent(path.split('/').filter(Boolean).pop() || 'download');
        } catch {
            return 'download';
        }
    }

    async function toBlob(href) {
        if (blobs.has(href)) return blobs.get(href);
        const response = await fetch(href, { credentials: 'include' });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return await response.blob();
    }

    function readChunk(blob) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result).split(',', 2)[1] || '');
            reader.onerror = () => reject(reader.error);
            reader.readAsDataURL(blob);
        });
    }

    const CHUNK = 768 * 1024; // multiple of 3 so base64 chunks concatenate cleanly
    async function saveHref(href, name) {
        const fileName = name || guessName(href);
        let id = null;
        try {
            const blob = await toBlob(href);
            id = native.saveBegin(fileName, blob.type || 'application/octet-stream', blob.size);
            for (let offset = 0; offset < blob.size; offset += CHUNK) {
                native.saveChunk(id, await readChunk(blob.slice(offset, offset + CHUNK)));
            }
            native.saveEnd(id);
        } catch (error) {
            console.error('[TavernDroid] download failed', error);
            if (id) native.saveAbort(id);
            native.toast(`Download failed: ${error.message || error}`);
        }
    }

    window.__taverndroidSave = saveHref;

    function isDownloadAnchor(a) {
        return a instanceof HTMLAnchorElement && a.hasAttribute('download') && a.href;
    }

    const anchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        if (isDownloadAnchor(this)) {
            saveHref(this.href, this.getAttribute('download'));
            return;
        }
        return anchorClick.call(this);
    };
    document.addEventListener('click', (event) => {
        const a = event.target instanceof Element ? event.target.closest('a') : null;
        if (a && isDownloadAnchor(a)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            saveHref(a.href, a.getAttribute('download'));
        }
    }, true);

    // ------------------------------------------------------------ page focus
    // In a desktop browser a hidden tab reports document.hasFocus() === false. Android WebView keeps
    // reporting true while the app is in the background or behind the lock screen. SillyTavern's Smooth
    // Streaming types each received chunk out with small timers when the page "has focus", and Chrome
    // throttles timers in hidden pages to 1/s (1/min after 5 minutes), so replies crawled or appeared to
    // stop until the user came back. Report no focus while hidden, like a browser tab does.
    const nativeHasFocus = Document.prototype.hasFocus;
    Document.prototype.hasFocus = function () {
        return !document.hidden && nativeHasFocus.call(this);
    };

    // ------------------------------------------------------- speech synthesis
    // Android WebView has no Web Speech synthesis (no SpeechSynthesisUtterance), which SillyTavern's
    // "System" TTS provider uses. This implements the API on top of Android's TextToSpeech.
    if (typeof window.SpeechSynthesisUtterance === 'undefined' && native.ttsVoices) {
        class Utterance extends EventTarget {
            constructor(text = '') {
                super();
                this.text = String(text);
                this.lang = '';
                this.voice = null;
                this.rate = 1;
                this.pitch = 1;
                this.volume = 1;
                for (const type of ['start', 'end', 'error', 'pause', 'resume', 'boundary', 'mark']) this['on' + type] = null;
            }
        }
        const fire = (target, type, extra = {}) => {
            const event = new Event(type);
            Object.assign(event, { utterance: target, charIndex: 0, elapsedTime: 0, name: '' }, extra);
            try {
                target['on' + type]?.call(target, event);
            } catch (error) {
                console.error(error);
            }
            target.dispatchEvent(event);
        };

        let seq = 0;
        let paused = false;
        let voices = null;
        let parked = [];
        const live = new Map(); // utterance id -> utterance, in speaking order
        const synth = new EventTarget();
        synth.onvoiceschanged = null;
        Object.defineProperties(synth, {
            speaking: { get: () => live.size > 0 },
            pending: { get: () => live.size > 1 || parked.length > 0 },
            paused: { get: () => paused },
        });
        const send = (utterance) => {
            const id = 'u' + (++seq);
            live.set(id, utterance);
            native.ttsSpeak(id, String(utterance.text ?? ''), utterance.voice?.voiceURI || '', utterance.lang || '',
                Number(utterance.rate) || 1, Number(utterance.pitch) || 1,
                utterance.volume == null ? 1 : Number(utterance.volume));
        };
        synth.getVoices = () => {
            if (!voices || voices.length === 0) {
                try {
                    voices = JSON.parse(native.ttsVoices()).map((v) => Object.freeze(v));
                } catch {
                    voices = [];
                }
            }
            return voices.slice();
        };
        synth.speak = (utterance) => {
            if (paused) parked.push(utterance);
            else send(utterance);
        };
        const flush = () => {
            // Utterances that were cleared end without being spoken, like in Chrome.
            const dropped = [...live.values(), ...parked];
            live.clear();
            parked = [];
            native.ttsStop();
            dropped.forEach((utterance) => fire(utterance, 'end'));
        };
        synth.cancel = () => {
            paused = false;
            flush();
        };
        synth.pause = () => {
            if (paused) return;
            paused = true;
            parked = [...live.values(), ...parked];
            live.clear();
            native.ttsStop();
            parked[0] && fire(parked[0], 'pause');
        };
        synth.resume = () => {
            if (!paused) return;
            paused = false;
            const queue = parked;
            parked = [];
            queue[0] && fire(queue[0], 'resume');
            queue.forEach(send);
        };
        window.__tdSpeech = {
            onNative(id, type, detail) {
                const utterance = live.get(id);
                if (!utterance) return;
                if (type === 'start') {
                    fire(utterance, 'start');
                    return;
                }
                live.delete(id);
                if (type === 'error') fire(utterance, 'error', { error: detail || 'synthesis-failed' });
                else fire(utterance, 'end');
            },
            onVoicesChanged() {
                voices = null;
                fire(synth, 'voiceschanged');
            },
        };
        window.SpeechSynthesisUtterance = Utterance;
        Object.defineProperty(window, 'speechSynthesis', { configurable: true, get: () => synth });
        synth.getVoices(); // starts the Android TTS engine
    }

    // ------------------------------------------------------- app lock activity
    // Typing goes to the IME, not the activity, so report page activity for the inactivity timer.
    let lastActive = 0;
    const reportActive = () => {
        const now = Date.now();
        if (now - lastActive > 5000) {
            lastActive = now;
            native.userActive();
        }
    };
    for (const type of ['input', 'keydown', 'scroll', 'wheel', 'pointerdown']) {
        window.addEventListener(type, reportActive, { capture: true, passive: true });
    }

    // ----------------------------------------------------------- back button
    // Returns true when some SillyTavern panel, popup or editor is open; the app
    // then sends a real Escape key, which SillyTavern already handles layer by layer.
    const visible = (el) => !!el && el.offsetParent !== null && getComputedStyle(el).visibility !== 'hidden';
    window.__taverndroidBack = () => {
        if (document.querySelector('dialog[open]')) return true;
        const selectors = [
            '.drawer-content.openDrawer', '#character_popup', '#select_chat_popup', '#dialogue_popup',
            '#curEditTextarea', '#dialogue_del_mes_cancel', '#logprobsViewer', '#floatingPrompt',
            '#cfgConfig', '.zoomed_avatar', '#movingDivs > div', '.select2-container--open',
        ];
        return selectors.some((selector) => Array.from(document.querySelectorAll(selector)).some(visible));
    };

    // ------------------------------------------------- SillyTavern integration
    let integrated = false;
    function onReady() {
        if (integrated) return true;
        const context = window.SillyTavern?.getContext?.();
        if (!context?.eventSource || !context.eventTypes) return false;
        const { eventSource, eventTypes } = context;

        eventSource.on(eventTypes.GENERATION_STARTED, (type, _options, dryRun) => {
            if (!dryRun) native.generationStarted(String(type || 'normal'));
        });
        const ended = () => native.generationEnded();
        eventSource.on(eventTypes.GENERATION_ENDED, ended);
        eventSource.on(eventTypes.GENERATION_STOPPED, ended);
        eventSource.on(eventTypes.MESSAGE_RECEIVED, (messageId, type) => {
            if (type === 'quiet' || type === 'impersonate') return;
            const message = window.SillyTavern.getContext().chat?.[messageId];
            if (!message || message.is_user || message.is_system) return;
            const text = String(message.mes || '').replace(/\s+/g, ' ').trim();
            native.replyReceived(String(message.name || 'SillyTavern'), text.slice(0, 400));
        });

        integrated = true;
        addAppButton();
        native.pageReady();
        return true;
    }

    function addAppButton() {
        const holder = document.getElementById('top-settings-holder');
        if (!holder || document.getElementById('taverndroid-button')) return;
        const wrap = document.createElement('div');
        wrap.id = 'taverndroid-button';
        wrap.className = 'drawer';
        const icon = document.createElement('div');
        icon.className = 'drawer-icon fa-solid fa-mobile-screen-button fa-fw closedIcon';
        icon.title = 'Android app settings';
        icon.tabIndex = 0;
        icon.addEventListener('click', () => native.openSettings());
        wrap.appendChild(icon);
        holder.appendChild(wrap);
    }

    // getContext() can throw while script.js is still initialising, so poll until it works.
    let attempts = 0;
    const waitForApp = () => {
        // The login page has no SillyTavern UI; nothing to integrate there.
        if (!document.getElementById('top-settings-holder')) return;
        let ready = false;
        try {
            ready = onReady();
        } catch (error) {
            if (++attempts % 40 === 0) console.warn('[TavernDroid] waiting for SillyTavern', error);
        }
        if (!ready) setTimeout(waitForApp, 250);
    };
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => setTimeout(waitForApp, 0));
    } else {
        waitForApp();
    }
})();
