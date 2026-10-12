// Installed into the SillyTavern page as window.__e2e by harness.installHelpers().
// Watchdog: a modal popup left open blocks awaited STscript commands; record and close it.
window.__e2ePopups = window.__e2ePopups || [];
if (!window.__e2eWatchdog) {
    const seen = new Map();
    window.__e2eWatchdog = setInterval(() => {
        for (const d of document.querySelectorAll('dialog[open]')) {
            const t0 = seen.get(d) ?? Date.now();
            seen.set(d, t0);
            if (Date.now() - t0 > 8000 && !d.dataset.e2eKeep) {
                window.__e2ePopups.push(d.innerText.replace(/\s+/g, ' ').trim().slice(0, 200));
                (d.querySelector('.popup-button-ok') || d.querySelector('.popup-button-cancel') || d.querySelector('.popup-button-close'))?.click();
                seen.delete(d);
            }
        }
    }, 1000);
}
window.__e2e = {
    sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
    ctx: () => SillyTavern.getContext(),
    $: window.jQuery,
    headers() {
        return SillyTavern.getContext().getRequestHeaders();
    },
    async api(path, body, { method = 'POST', raw = false } = {}) {
        const res = await fetch(path, { method, headers: this.headers(), body: body === undefined ? undefined : JSON.stringify(body) });
        if (raw) return res;
        const text = await res.text();
        let data = text;
        try { data = JSON.parse(text); } catch { /* text */ }
        if (!res.ok) throw new Error(`${path} → HTTP ${res.status}: ${String(text).slice(0, 300)}`);
        return data;
    },
    async cmd(script) {
        const r = await SillyTavern.getContext().executeSlashCommandsWithOptions(script, { handleExecutionErrors: false });
        if (r?.isError) throw new Error(`STscript error: ${r.errorMessage}`);
        return r?.pipe;
    },
    async waitFor(fn, timeout = 30000, step = 250) {
        const t0 = Date.now();
        while (Date.now() - t0 < timeout) {
            try {
                const v = await fn();
                if (v) return v;
            } catch { /* keep waiting */ }
            await this.sleep(step);
        }
        throw new Error(`timeout after ${timeout} ms waiting for ${fn.toString().slice(0, 120)}`);
    },
    generating() {
        return jQuery('#mes_stop').is(':visible');
    },
    /** Sends a user message through the UI and waits for the reply to finish. */
    async send(text, timeout = 240000) {
        const c = this.ctx();
        const before = c.chat.length;
        jQuery('#send_textarea').val(text).trigger('input');
        jQuery('#send_but').trigger('click');
        await this.waitFor(() => this.ctx().chat.length >= before + 2 && !this.generating(), timeout, 400);
        return this.ctx().chat.at(-1);
    },
    async waitIdle(timeout = 240000) {
        await this.sleep(500);
        await this.waitFor(() => !this.generating(), timeout, 400);
    },
    /** Runs `trigger` and resolves when SillyTavern reports the generation finished (or stopped). */
    async gen(trigger, timeout = 240000) {
        const { eventSource, eventTypes } = this.ctx();
        let done;
        const finished = new Promise((r) => { done = r; });
        const onEnd = () => done('ended');
        const onStop = () => done('stopped');
        eventSource.on(eventTypes.GENERATION_ENDED, onEnd);
        eventSource.on(eventTypes.GENERATION_STOPPED, onStop);
        try {
            let timer;
            const deadline = new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('generation timeout')), timeout); });
            finished.finally(() => clearTimeout(timer));
            await Promise.race([trigger(), deadline]);
            const how = await Promise.race([finished, deadline]);
            await this.waitFor(() => !this.generating(), 30000, 200);
            await this.sleep(300);
            return how;
        } finally {
            eventSource.removeListener(eventTypes.GENERATION_ENDED, onEnd);
            eventSource.removeListener(eventTypes.GENERATION_STOPPED, onStop);
        }
    },
    toasts() {
        return Array.from(document.querySelectorAll('#toast-container .toast')).map((t) => t.innerText.trim());
    },
    clearToasts() {
        document.querySelectorAll('#toast-container .toast').forEach((t) => t.remove());
    },
    async closePopups() {
        for (const d of document.querySelectorAll('dialog[open]')) {
            (d.querySelector('.popup-button-cancel') || d.querySelector('.popup-button-close') || d.querySelector('.popup-button-ok'))?.click();
            await this.sleep(300);
        }
    },
    async selectChar(name) {
        const c = this.ctx();
        const idx = c.characters.findIndex((x) => x.name === name);
        if (idx < 0) throw new Error(`character ${name} not found`);
        await c.selectCharacterById(idx);
        await this.waitFor(() => this.ctx().characterId == idx && this.ctx().chat.length > 0, 15000);
        return idx;
    },
    /** A small PNG (solid color) as a File, for avatar/background/attachment uploads. */
    async pngFile(name = 'test.png', color = '#c06030', w = 64, h = 64) {
        const canvas = document.createElement('canvas');
        canvas.width = w; canvas.height = h;
        const g = canvas.getContext('2d');
        g.fillStyle = color; g.fillRect(0, 0, w, h);
        g.fillStyle = '#ffffff'; g.fillRect(w / 4, h / 4, w / 2, h / 2);
        const blob = await new Promise((r) => canvas.toBlob(r, 'image/png'));
        return new File([blob], name, { type: 'image/png' });
    },
    fileFromB64(name, type, b64) {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return new File([bytes], name, { type });
    },
    /** Builds the prompt without sending it (Chat Completion) and returns the message list. */
    async dryRun() {
        const { eventSource, eventTypes } = this.ctx();
        let data = null;
        const h = (d) => { data = d; };
        eventSource.on(eventTypes.CHAT_COMPLETION_PROMPT_READY, h);
        try {
            await this.ctx().generate('normal', {}, true);
        } finally {
            eventSource.removeListener(eventTypes.CHAT_COMPLETION_PROMPT_READY, h);
        }
        if (!data) throw new Error('no prompt captured');
        return data.chat.map((m) => (typeof m.content === 'string' ? m.content : JSON.stringify(m.content))).join('\n');
    },
    /** Puts files into an <input type=file> and fires change (what the picker would do). */
    setFiles(selector, files) {
        const input = document.querySelector(selector);
        if (!input) throw new Error(`no input ${selector}`);
        const dt = new DataTransfer();
        files.forEach((f) => dt.items.add(f));
        input.files = dt.files;
        input.dispatchEvent(new Event('change', { bubbles: true }));
    },
};
'ok';
