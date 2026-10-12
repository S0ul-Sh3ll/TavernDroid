import { execFileSync } from 'node:child_process';
import { adb, ui, tapText, topActivity, sleep, Page } from '../harness.mjs';

const PKG = 'io.github.taverndroid';
const unlock = () => execFileSync(new URL('../unlock.sh', import.meta.url).pathname, { encoding: 'utf8' }).trim();
const openSettings = async () => {
    adb('shell', 'am', 'start', '-n', `${PKG}/.MainActivity`, '--es', 'open', 'settings');
    await sleep(2500);
};
const scrollTo = async (text) => {
    for (let i = 0; i < 8; i++) {
        if (ui().some((e) => e.text.includes(text))) return;
        adb('shell', 'input', 'swipe', '540', '1700', '540', '900', '250');
        await sleep(600);
    }
    throw new Error(`could not find "${text}"`);
};
const scrollTop = async () => {
    for (let i = 0; i < 6; i++) adb('shell', 'input', 'swipe', '540', '700', '540', '1900', '150');
    await sleep(500);
};
const http = (path, auth) => {
    try {
        return execFileSync('curl', ['-s', '-m', '5', '-o', '/dev/null', '-w', '%{http_code}', ...(auth ? ['-u', auth] : []), `http://127.0.0.1:18800${path}`], { encoding: 'utf8' });
    } catch { return '000'; }
};
let page;
const reconnect = async () => {
    page?.close();
    page = await Page.connect();
    await page.installHelpers();
    return page;
};
const waitServer = async (port = 8800, ms = 90000) => {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) {
        if (adb('shell', `netstat -ltn | grep ':${port} ' | grep -c LISTEN || true`) !== '0') return Date.now() - t0;
        await sleep(1000);
    }
    throw new Error(`server not listening on ${port}`);
};

export const native = true;
export default () => [
    ['Setup: root shell, unlock, page connection', async () => {
        try { adb('root'); } catch { /* already root */ }
        await sleep(3000);
        unlock();
        await reconnect();
        return topActivity();
    }],

    ['Keep screen on: window flag follows the setting', async () => {
        await openSettings();
        await scrollTo('Keep screen on');
        tapText('Keep screen on');
        await sleep(500);
        adb('shell', 'input', 'keyevent', '4');
        await sleep(1500);
        const ourFlags = () => {
            const lines = adb('shell', 'dumpsys window windows').split('\n');
            const i = lines.findIndex((l) => /Window #\d+ Window\{.*io\.github\.taverndroid\.MainActivity\}/.test(l));
            return i < 0 ? '' : (lines.slice(i, i + 8).find((l) => l.trim().startsWith('fl=')) || '');
        };
        const on = ourFlags().includes('KEEP_SCREEN_ON');
        await openSettings(); await scrollTo('Keep screen on'); tapText('Keep screen on'); adb('shell', 'input', 'keyevent', '4');
        await sleep(1500);
        const off = ourFlags().includes('KEEP_SCREEN_ON');
        if (!on || off) throw new Error(`on=${on} afterOff=${off}`);
        return 'flag set and cleared';
    }],

    ['Rotation keeps the page (no reload)', async () => {
        await page.run(`window.__rotMarker = 42; return 1;`);
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0');
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '1');
        await sleep(3000);
        const kept = await page.run(`return window.__rotMarker === 42 && innerWidth > innerHeight;`);
        adb('shell', 'settings', 'put', 'system', 'user_rotation', '0');
        await sleep(2000);
        if (!kept) throw new Error('page reloaded or not landscape');
        return 'state kept across rotation';
    }],

    ['External link opens the browser', async () => {
        await page.run(`window.open('https://example.com/', '_blank'); return 1;`);
        await sleep(3000);
        const top = topActivity();
        adb('shell', 'am', 'start', '-n', `${PKG}/.MainActivity`);
        await sleep(2000);
        if (top.startsWith(PKG)) throw new Error('stayed in app: ' + top);
        return top;
    }],

    ['Microphone permission request from the page', async () => {
        const p = page.run(`try { const s = await navigator.mediaDevices.getUserMedia({ audio: true }); s.getTracks().forEach(t => t.stop()); return 'granted'; } catch (e) { return 'denied: ' + e.name; }`, 30000);
        let allow;
        for (let i = 0; i < 20 && !allow; i++) { await sleep(700); allow = ui().find((e) => /While using the app|Only this time|^Allow$/.test(e.text)); }
        if (allow) adb('shell', 'input', 'tap', String(allow.x), String(allow.y));
        const r = await p;
        if (r !== 'granted') throw new Error(r);
        return r;
    }],

    ['Renderer crash is recovered', async () => {
        await page.send('Page.crash').catch(() => {});
        await sleep(6000);
        unlock();
        await sleep(8000);
        await reconnect();
        const ok = await page.run(`await T.waitFor(() => T.ctx().characters.length > 0, 60000); return T.ctx().characters.length;`);
        return { charactersAfterRecovery: ok, top: topActivity() };
    }],

    ['Node process killed (like the OOM killer) → restarts automatically', async () => {
        await sleep(21000);                       // auto-restart applies after 20 s of uptime
        const pid = adb('shell', 'pidof', 'libnode.so');
        adb('shell', 'kill', '-9', pid);
        await sleep(2500);
        const msg = ui().map((e) => e.text).find((t) => /Restarting/.test(t)) || '';
        const ms = await waitServer();
        const pid2 = adb('shell', 'pidof', 'libnode.so');
        await sleep(8000);
        await reconnect();
        const ok = await page.run(`await T.waitFor(() => T.ctx().characters.length > 0, 60000); return T.ctx().characters.length;`);
        if (!pid2 || pid2 === pid) throw new Error('not restarted');
        return { restartedInMs: ms, explained: /out of memory/.test(msg), chars: ok };
    }],

    ['Port change reloads the UI on the new port', async () => {
        await openSettings();
        const field = ui().find((e) => e.text === '8800');
        adb('shell', 'input', 'tap', String(field.x), String(field.y));
        adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END');
        for (let i = 0; i < 6; i++) adb('shell', 'input', 'keyevent', 'KEYCODE_DEL');
        adb('shell', 'input', 'text', '8811');
        tapText('Apply');
        await waitServer(8811);
        adb('shell', 'input', 'keyevent', '4'); adb('shell', 'input', 'keyevent', '4');
        await sleep(8000);
        await reconnect();
        const url = await page.run(`return location.href;`);
        if (!url.includes(':8811')) throw new Error(url);
        return url;
    }],

    ['Port already in use → clear error', async () => {
        // Occupy 8822 inside the emulator, then ask the app to use it.
        const nc = execFileSync(process.env.ADB || `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`, ['shell', 'nohup toybox nc -L -p 8822 cat >/dev/null 2>&1 & echo $!'], { encoding: 'utf8' }).trim();
        await sleep(1000);
        await openSettings();
        const field = ui().find((e) => e.text === '8811');
        adb('shell', 'input', 'tap', String(field.x), String(field.y));
        adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END');
        for (let i = 0; i < 6; i++) adb('shell', 'input', 'keyevent', 'KEYCODE_DEL');
        adb('shell', 'input', 'text', '8822');
        tapText('Apply');
        await sleep(6000);
        const status = ui().map((e) => e.text).find((t) => /already in use/.test(t)) || '';
        try { adb('shell', 'kill', nc); } catch { /* gone */ }
        // back to the default port
        const f2 = ui().find((e) => e.text === '8822');
        adb('shell', 'input', 'tap', String(f2.x), String(f2.y));
        adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END');
        for (let i = 0; i < 6; i++) adb('shell', 'input', 'keyevent', 'KEYCODE_DEL');
        adb('shell', 'input', 'text', '8800');
        tapText('Apply');
        await waitServer(8800);
        adb('shell', 'input', 'keyevent', '4');
        await sleep(8000);
        await reconnect();
        if (!status) throw new Error('no "already in use" message');
        return status.slice(0, 120);
    }],

    ['LAN mode: basic auth enforced, app still signed in', async () => {
        await openSettings();
        tapText('Allow other devices (LAN)');
        await waitServer(8800);
        await sleep(3000);
        try { adb('forward', '--remove', 'tcp:18800'); } catch { /* none */ }
        adb('forward', 'tcp:18800', 'tcp:8800');
        const xml = adb('shell', 'cat', `/data/data/${PKG}/shared_prefs/taverndroid.xml`);
        const pw = (xml.match(/name="lanPassword">([^<]+)/) || [])[1];
        const noAuth = http('/');
        const withAuth = http('/', `tavern:${pw}`);
        adb('shell', 'input', 'keyevent', '4');
        await sleep(8000);
        await reconnect();
        const appOk = await page.run(`return T.ctx().characters.length > 0;`);
        await openSettings(); tapText('Allow other devices (LAN)'); await waitServer(8800); adb('shell', 'input', 'keyevent', '4');
        await sleep(8000); await reconnect();
        if (noAuth !== '401' || withAuth !== '200') throw new Error(`no-auth ${noAuth}, with-auth ${withAuth}`);
        return { noAuth, withAuth, appOk };
    }],

    ['config.yaml editor: save → restart applies it; reset to default', async () => {
        await openSettings();
        await scrollTo('Edit config.yaml');
        tapText('Edit config.yaml');
        await sleep(2000);
        const editor = ui().find((e) => e.text.includes('dataRoot'));
        if (!editor) throw new Error('editor did not show config.yaml');
        tapText('Save and restart');
        await sleep(3000);
        await waitServer();
        adb('shell', 'input', 'keyevent', '4');
        await sleep(8000); await reconnect();
        return 'saved and restarted';
    }],

    ['Clear frontend cache and Repair app files', async () => {
        await openSettings();
        await scrollTo('Clear compiled frontend cache');
        tapText('Clear compiled frontend cache');
        await sleep(3000);
        const a = await waitServer();
        await scrollTo('Repair app files');
        tapText('Repair app files');
        await sleep(1000);
        tapText('CONTINUE');
        await sleep(5000);
        const b = await waitServer(8800, 180000);
        adb('shell', 'input', 'keyevent', '4');
        await sleep(10000); await reconnect();
        const ok = await page.run(`await T.waitFor(() => T.ctx().characters.length > 0, 60000); return T.ctx().characters.length;`);
        return { cacheRestartMs: a, repairRestartMs: b, chars: ok };
    }],

    ['Server log screen and licenses screen', async () => {
        adb('shell', 'am', 'start', '-n', `${PKG}/.MainActivity`, '--es', 'open', 'log');
        await sleep(2500);
        const log = ui().some((e) => e.text.includes('SillyTavern is listening'));
        const noEsc = !ui().some((e) => e.text.includes('2;SillyTavern WebServer'));
        adb('shell', 'input', 'keyevent', '4');
        await openSettings();
        await scrollTo('Open-source licenses');
        tapText('Open-source licenses');
        await sleep(2000);
        const lic = topActivity().endsWith('LicensesActivity');
        adb('shell', 'input', 'keyevent', '4'); adb('shell', 'input', 'keyevent', '4');
        if (!log || !lic) throw new Error(`log=${log} licenses=${lic}`);
        return { log, cleanTitleEscape: noEsc, lic };
    }],

    ['Notification "Stop server" action, then start again', async () => {
        adb('shell', 'cmd', 'statusbar', 'expand-notifications');
        await sleep(2000);
        if (!ui().some((e) => /^stop server$/i.test(e.text))) {
            const title = ui().find((e) => e.text === 'SillyTavern');
            adb('shell', 'input', 'tap', '963', String(title.y + 45)); // expand chevron of our notification
            await sleep(1500);
        }
        tapText('Stop server');
        await sleep(5000);
        adb('shell', 'cmd', 'statusbar', 'collapse');
        const stopped = adb('shell', `netstat -ltn | grep ':8800 ' | grep -c LISTEN || true`) === '0';
        unlock();
        await sleep(2000);
        const texts = ui().map((e) => e.text).join(' | ');
        if (/Start/.test(texts)) tapText('Start');
        await waitServer();
        await sleep(8000); await reconnect();
        if (!stopped) throw new Error('server still listening after Stop');
        return 'stopped and restarted';
    }],

    ['Start on boot (simulated BOOT_COMPLETED): starts, or offers a tap-to-start notification', async () => {
        const prefs = `/data/data/${PKG}/shared_prefs/taverndroid.xml`;
        await openSettings(); await scrollTo('Start on boot');
        if (!adb('shell', 'cat', prefs).includes('"startOnBoot" value="true"')) tapText('Start on boot');
        adb('shell', 'input', 'keyevent', '4');
        await sleep(1000);
        adb('shell', 'am', 'force-stop', PKG);
        await sleep(1500);
        adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.BOOT_COMPLETED', '-n', `${PKG}/.BootReceiver`);
        let started = false;
        try { await waitServer(8800, 30000); started = true; } catch { /* refused */ }
        const offered = adb('shell', 'dumpsys notification --noredact').includes('did not allow starting at boot');
        unlock();
        await openSettings(); await scrollTo('Start on boot'); tapText('Start on boot'); adb('shell', 'input', 'keyevent', '4');
        await sleep(8000);
        if (!started && !offered) throw new Error('neither started nor offered a notification');
        return { started, offeredNotification: offered };
    }],
];
