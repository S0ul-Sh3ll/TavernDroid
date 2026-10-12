// End-to-end test harness: drives the app's WebView over the Chrome DevTools Protocol (adb-forwarded)
// and the native UI over adb. Requires WebView debugging (debug build, debuggable OS, or the app setting).
import { execFileSync } from 'node:child_process';
import { readFileSync, appendFileSync } from 'node:fs';
import path from 'node:path';

const ADB = process.env.ADB || `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`;
const HERE = path.dirname(new URL(import.meta.url).pathname);
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function adb(...args) {
    return execFileSync(ADB, args, { encoding: 'utf8', maxBuffer: 64 << 20 }).trim();
}

export function topActivity() {
    const out = adb('shell', 'dumpsys activity activities');
    const m = out.match(/topResumedActivity=.*? ([\w.]+\/[\w.]+)/);
    return m ? m[1] : '';
}

/** Native UI elements on screen: [{x, y, text}] */
export function ui() {
    const out = execFileSync(path.join(HERE, '..', 'ui.sh'), { encoding: 'utf8', env: process.env });
    return out.split('\n').filter(Boolean).map((l) => {
        const [xy, ...t] = l.split('\t');
        const [x, y] = xy.split(',').map(Number);
        return { x, y, text: t.join('\t') };
    });
}

export function tapText(text) {
    const lower = text.toLowerCase();
    const el = ui().find((e) => e.text.toLowerCase() === lower) || ui().find((e) => e.text.toLowerCase().includes(lower));
    if (!el) throw new Error(`no UI element "${text}"`);
    adb('shell', 'input', 'tap', String(el.x), String(el.y));
}

export class Page {
    static async connect(port = Number(process.env.E2E_CDP_PORT) || 9300 + Number((process.env.ANDROID_SERIAL || '').match(/(\d+)$/)?.[1] ?? 34) % 100) {
        // Use the socket of TavernDroid's own process (other apps' WebViews have sockets too).
        const pid = adb('shell', 'pidof', 'io.github.taverndroid').split(/\s+/)[0];
        const all = adb('shell', 'cat /proc/net/unix').match(/webview_devtools_remote_\d+/g) || [];
        const sock = all.find((x) => x.endsWith('_' + pid)) || all.pop();
        if (!sock) throw new Error('no WebView devtools socket');
        try { adb('forward', '--remove', `tcp:${port}`); } catch { /* not forwarded yet */ }
        adb('forward', `tcp:${port}`, `localabstract:${sock}`);
        for (let i = 0; i < 30; i++) {
            try {
                const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
                const t = targets.find((x) => x.type === 'page' && x.url.startsWith('http://127.0.0.1'));
                if (t) return new Page(t.webSocketDebuggerUrl).open();
            } catch { /* retry */ }
            await sleep(1000);
        }
        throw new Error('page target not found');
    }

    constructor(url) {
        this.url = url;
        this.id = 0;
        this.pending = new Map();
        this.events = [];
    }

    async open() {
        this.ws = new WebSocket(this.url);
        await new Promise((r, j) => { this.ws.onopen = r; this.ws.onerror = j; });
        // A crashed or closed page must fail pending calls instead of leaving them unsettled.
        this.ws.onclose = () => {
            for (const resolve of this.pending.values()) resolve({ result: { exceptionDetails: { text: 'devtools connection closed' } } });
            this.pending.clear();
        };
        this.ws.onmessage = (e) => {
            const msg = JSON.parse(e.data);
            if (msg.id && this.pending.has(msg.id)) {
                this.pending.get(msg.id)(msg);
                this.pending.delete(msg.id);
            } else if (msg.method === 'Network.responseReceived') {
                const r = msg.params.response;
                if (r.status >= 500) this.events.push({ method: 'http5xx', params: { text: `HTTP ${r.status} ${r.url.replace(/^https?:\/\/[^/]+/, '')}` } });
            } else if (msg.method && !msg.method.startsWith('Network.')) {
                this.events.push(msg);
            }
        };
        await this.send('Runtime.enable');
        await this.send('Log.enable');
        await this.send('Network.enable');
        return this;
    }

    send(method, params = {}) {
        return new Promise((r) => {
            const i = ++this.id;
            this.pending.set(i, r);
            this.ws.send(JSON.stringify({ id: i, method, params }));
        });
    }

    /** Evaluates an async function body in the page; `T` is the page-side helper object. */
    async run(body, timeout = 300000) {
        const expression = `(async () => { const T = window.__e2e; ${body} })()`;
        const res = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, timeout });
        if (res.result?.exceptionDetails) {
            const d = res.result.exceptionDetails;
            throw new Error(d.exception?.description || d.text);
        }
        return res.result?.result?.value;
    }

    async installHelpers() {
        await this.run(readFileSync(path.join(HERE, 'page-helpers.js'), 'utf8'));
    }

    /** Console errors/exceptions seen since the last call. */
    drainErrors() {
        const errs = this.events.filter((e) =>
            e.method === 'http5xx' ||
            e.method === 'Runtime.exceptionThrown' ||
            (e.method === 'Runtime.consoleAPICalled' && e.params.type === 'error') ||
            (e.method === 'Log.entryAdded' && e.params.entry.level === 'error'));
        this.events = [];
        return errs.map((e) => {
            const p = e.params;
            if (p.text && !p.entry) return p.text;
            if (p.entry) return `${p.entry.text}${p.entry.url ? ' @ ' + p.entry.url.replace(/^https?:\/\/[^/]+/, '') : ''}`;
            const frame = (p.exceptionDetails?.stackTrace || p.stackTrace)?.callFrames?.[0];
            const where = frame ? ` @ ${frame.url.split('/').pop()}:${frame.lineNumber + 1}` : '';
            const msg = p.exceptionDetails?.exception?.description?.split('\n')[0] || p.args?.map((a) => a.value ?? a.description).join(' ');
            return `${msg}${where}`;
        });
    }

    close() { this.ws.close(); }
}

/** Runs named tests, printing PASS/FAIL lines and appending JSON lines to a report file. */
export async function suite(name, page, tests, reportFile) {
    const results = [];
    const keepAlive = setInterval(() => {}, 1000);
    for (const [title, fn] of tests) {
        const t0 = Date.now();
        let status = 'PASS', detail = '';
        try {
            const r = await fn();
            detail = typeof r === 'string' ? r : JSON.stringify(r ?? '');
        } catch (e) {
            status = 'FAIL';
            detail = String(e.message || e);
        }
        const errors = page ? page.drainErrors().filter(Boolean) : [];
        if (page) {
            try {
                const popups = await page.run('const p = window.__e2ePopups.splice(0); return p;', 10000);
                for (const p of popups || []) errors.push('POPUP auto-closed: ' + p);
            } catch { /* page gone */ }
        }
        const line = { suite: name, test: title, status, ms: Date.now() - t0, detail: detail.slice(0, 600), consoleErrors: errors.slice(0, 5) };
        results.push(line);
        console.log(`${status === 'PASS' ? '✅' : '❌'} [${name}] ${title} (${line.ms} ms) ${line.detail.slice(0, 220)}${errors.length ? `\n     console errors: ${errors.slice(0, 3).join(' | ').slice(0, 300)}` : ''}`);
        if (reportFile) appendFileSync(reportFile, JSON.stringify(line) + '\n');
    }
    clearInterval(keepAlive);
    return results;
}

export function assert(cond, message) {
    if (!cond) throw new Error(message || 'assertion failed');
}
