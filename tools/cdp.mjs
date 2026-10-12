// Tiny Chrome DevTools Protocol client for testing the app's WebView over `adb forward`.
// usage: node tools/cdp.mjs [port] < script.js   (the script's completion value is printed as JSON)
import { readFileSync } from 'node:fs';
const port = process.argv[2] || 9333;
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const page = targets.find(t => t.type === 'page' && t.url.startsWith('http://127.0.0.1')) || targets[0];
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r, { once: true }));
let id = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
});
const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const expression = readFileSync(0, 'utf8');
const res = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, timeout: 600000 });
if (res.result?.exceptionDetails) console.log('EXCEPTION', JSON.stringify(res.result.exceptionDetails, null, 1).slice(0, 2000));
else console.log(JSON.stringify(res.result?.result?.value, null, 1));
ws.close();
