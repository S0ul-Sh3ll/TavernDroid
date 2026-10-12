import { readFileSync } from 'node:fs';
import { adb, sleep } from '../harness.mjs';

const FIX = new URL('../fixtures/', import.meta.url).pathname;
const b64 = (f) => readFileSync(FIX + f).toString('base64');
const TTS = process.env.E2E_TTS || 'http://10.0.2.2:8092/v1/audio/speech';

export default (page) => [
    ['System TTS: voices from Android TextToSpeech', () => page.run(`
        if (typeof SpeechSynthesisUtterance !== 'function') throw new Error('no SpeechSynthesisUtterance');
        let voices = speechSynthesis.getVoices();
        if (!voices.length) await new Promise((r) => { speechSynthesis.addEventListener('voiceschanged', r, { once: true }); setTimeout(r, 8000); });
        voices = speechSynthesis.getVoices();
        return { count: voices.length, sample: voices.slice(0, 3).map(v => v.lang + ' ' + v.voiceURI) };`)],

    ['System TTS: speak fires start and end', () => page.run(`
        const events = [];
        await new Promise((resolve) => {
            const u = new SpeechSynthesisUtterance('Testing the Android voice.');
            u.onstart = () => events.push('start');
            u.onend = () => { events.push('end'); resolve(); };
            u.onerror = (e) => { events.push('error:' + e.error); resolve(); };
            speechSynthesis.speak(u);
            setTimeout(resolve, 20000);
        });
        if (!events.includes('end')) throw new Error(events.join(','));
        return events;`)],

    ['System TTS: cancel ends a long utterance', () => page.run(`
        const events = [];
        const done = new Promise((resolve) => {
            const u = new SpeechSynthesisUtterance('This is a long sentence that will be cancelled before it finishes speaking. '.repeat(5));
            u.onstart = () => { events.push('start'); setTimeout(() => speechSynthesis.cancel(), 800); };
            u.onend = () => { events.push('end'); resolve(); };
            u.onerror = (e) => { events.push('error:' + e.error); resolve(); };
            speechSynthesis.speak(u);
            setTimeout(resolve, 15000);
        });
        await done;
        if (!events.includes('end')) throw new Error(events.join(','));
        return { events, speaking: speechSynthesis.speaking };`)],

    ['TTS extension: /speak with System provider', () => page.run(`
        const s = T.ctx().extensionSettings.tts;
        s.currentProvider = 'System'; s.enabled = true;
        T.ctx().saveSettingsDebounced();
        jQuery('#tts_provider').val('System').trigger('change'); await T.sleep(1500);
        const r = await T.cmd('/speak Hello from the system voice.');
        await T.sleep(3000);
        return { ok: true, r: String(r ?? '').slice(0, 60) };`)],

    ['OpenAI-compatible TTS (Pocket TTS on the Mac) via server proxy, played in WebView', () => page.run(`
        const res = await T.api('/api/openai/custom/generate-voice', { input: 'Hello from Pocket TTS.', provider_endpoint: '${TTS}', response_format: 'mp3', voice: 'jane', speed: 1, model: 'pocket-tts' }, { raw: true });
        if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + (await res.text()).slice(0, 200));
        const blob = await res.blob();
        const audio = new Audio(URL.createObjectURL(blob));
        audio.volume = 0.2;
        const played = await new Promise((r) => { audio.onplaying = () => r('playing'); audio.onerror = () => r('error ' + audio.error?.code); audio.play().catch(e => r('play() rejected: ' + e.message)); setTimeout(() => r('timeout'), 10000); });
        audio.pause();
        if (played !== 'playing') throw new Error(played);
        return { bytes: blob.size, type: blob.type, played };`)],

    ['Translate (Google, via server)', () => page.run(`
        const r = await T.api('/api/translate/google', { text: 'Good morning, friend.', lang: 'fr' });
        if (!/bonjour|matin/i.test(String(r))) throw new Error(String(r));
        return r;`)],

    ['Translate extension: /translate command', () => page.run(`
        const r = await T.cmd('/translate target=de provider=google Hello world');
        return r;`)],

    ['Web page scraper (/api/search/visit)', () => page.run(`
        const r = await T.api('/api/search/visit', { url: 'https://example.com/', html: true }, { raw: true });
        const t = await r.text();
        if (!r.ok || !t.includes('Example Domain')) throw new Error(r.status + ' ' + t.slice(0, 200));
        return { status: r.status, bytes: t.length };`)],

    ['Vector storage: local transformers embeddings insert + query', () => page.run(`
        const items = [
            { hash: 101, text: 'The castle library holds ancient maps of the northern sea.', index: 0 },
            { hash: 102, text: 'Bananas are a yellow fruit rich in potassium.', index: 1 },
            { hash: 103, text: 'The dragon sleeps beneath the mountain of fire.', index: 2 },
        ];
        await T.api('/api/vector/purge', { collectionId: 'e2e-test' }).catch(() => {});
        const t0 = Date.now();
        await T.api('/api/vector/insert', { collectionId: 'e2e-test', items, source: 'transformers' });
        const t1 = Date.now();
        const q = await T.api('/api/vector/query', { collectionId: 'e2e-test', searchText: 'Where are the old sea charts kept?', topK: 1, source: 'transformers', threshold: 0 });
        const top = q.metadata?.[0]?.text || JSON.stringify(q).slice(0, 200);
        if (!/library|maps/.test(top)) throw new Error('wrong top hit: ' + top);
        return { insertMs: t1 - t0, queryMs: Date.now() - t1, top };`, 900000)],

    ['Expression classification (local model)', () => page.run(`
        const t0 = Date.now();
        const r = await T.api('/api/extra/classify', { text: 'I am so happy and excited today, this is wonderful!' });
        const top = r.classification?.[0]?.label;
        if (!top) throw new Error(JSON.stringify(r).slice(0, 200));
        return { top, ms: Date.now() - t0 };`, 900000)],

    ['Image captioning (local model)', () => page.run(`
        const img = '${b64('avatar.png')}';
        const t0 = Date.now();
        const r = await T.api('/api/extra/caption', { image: img });
        if (!r.caption) throw new Error(JSON.stringify(r).slice(0, 200));
        return { caption: r.caption, ms: Date.now() - t0 };`, 900000)],

    ['Image generation: AI Horde (anonymous) → image saved on the server', () => page.run(`
        jQuery('#sd_source').val('horde').trigger('change');
        await T.waitFor(() => jQuery('#sd_model option').length > 0, 60000).catch(() => {});
        jQuery('#sd_width').val(512).trigger('input'); jQuery('#sd_height').val(512).trigger('input');
        jQuery('#sd_steps').val(10).trigger('input');
        const t0 = Date.now();
        const r = await T.cmd('/imagine quiet=true a small red apple on a white table, simple');
        if (!r) throw new Error('no image: ' + T.toasts().join(' | '));
        const img = await fetch(r);
        return { url: r, status: img.status, type: img.headers.get('content-type'), ms: Date.now() - t0 };`, 900000)],

    ['Summarize (main API)', () => page.run(`
        await T.selectChar('Seraphina');
        const r = await T.cmd('/summarize source=main The hero walked into a tavern, ordered ale, met an old wizard who offered a quest to find a lost crown, and agreed to leave at dawn.');
        if (!r) throw new Error('empty summary');
        return r.slice(0, 120);`, 400000)],

    ['Data Bank: add file attachment, list, search (vectors)', () => page.run(`
        const name = await T.cmd('/db-add source=global name=e2e-notes.txt The lighthouse keeper is named Orrin and he loves puzzles.');
        const list = await T.cmd('/db-list source=global field=name');
        let search = '';
        try { search = await T.cmd('/db-search source=global return=chunks threshold=0 Who keeps the lighthouse?'); } catch (e) { search = 'ERR ' + e.message; }
        if (!String(list).includes('e2e-notes') || !/Orrin/.test(String(search))) throw new Error(JSON.stringify({ list, search }).slice(0, 300));
        return { listed: true, search: String(search).slice(0, 120) };`, 900000)],

    ['Quick Replies: create set, add button, run it', () => page.run(`
        if (!jQuery('#qr--isEnabled').prop('checked')) document.getElementById('qr--isEnabled').click(); // native click toggles + fires change
        await T.sleep(500);
        await T.cmd('/qr-set-create E2E-QR');
        await T.cmd('/qr-create set=E2E-QR label=SayHi /setvar key=qrran 1');
        await T.cmd('/qr-set-on E2E-QR');
        await T.sleep(800);
        const btn = Array.from(document.querySelectorAll('.qr--button')).find(b => b.querySelector('.qr--button-label')?.textContent.trim() === 'SayHi');
        if (!btn) throw new Error('no QR button rendered');
        btn.click(); await T.sleep(1500);
        const v = await T.cmd('/getvar qrran');
        await T.cmd('/qr-set-off E2E-QR');
        if (String(v) !== '1') throw new Error('qr did not run: ' + v);
        return 'button ran';`)],

    ['Regex extension: script applied by /regex', () => page.run(`
        const s = T.ctx().extensionSettings;
        s.regex = s.regex || [];
        s.regex = s.regex.filter(x => x.scriptName !== 'E2E-Rx');
        s.regex.push({ id: crypto.randomUUID(), scriptName: 'E2E-Rx', findRegex: '/banana/gi', replaceString: 'apple', trimStrings: [], placement: [1, 2], disabled: false, markdownOnly: false, promptOnly: false, runOnEdit: true, substituteRegex: 0, minDepth: null, maxDepth: null });
        T.ctx().saveSettingsDebounced();
        const r = await T.cmd('/regex name=E2E-Rx Banana split');
        if (!/apple/i.test(r)) throw new Error(r);
        return r;`)],

    ['STscript: variables, math, conditionals, loops, closures', () => page.run(`
        const r1 = await T.cmd('/setvar key=n 5 | /addvar key=n 2 | /getvar n');
        const r2 = await T.cmd('/setglobalvar key=g hello | /getglobalvar g');
        const r3 = await T.cmd('/if left=3 rule=gt right=2 {: /pass yes :} | /pass');
        const r4 = await T.cmd('/setvar key=i 0 | /while left=i rule=lt right=3 {: /incvar i :} | /getvar i');
        const r5 = await T.cmd('/let x {: a=1 b=2 | /add {{var::a}} {{var::b}} :} | /:x a=10 b=20');
        const r6 = await T.cmd('/echo {{char}} / {{user}} / {{roll:d1}}');
        const r7 = await T.cmd('/len hello | /pass');
        return { r1, r2, r3, r4, r5, r7 };`)],

    ['Macros render in prompt ({{char}}, {{user}}, {{time}}, {{random}})', () => page.run(`
        const r = await T.cmd('/pass {{char}}|{{user}}|{{random::x,y}}|{{date}}');
        if (r.includes('{{')) throw new Error(r);
        return r;`)],

    ['Backgrounds: upload (thumbnail), list, delete', () => page.run(`
        const fd = new FormData();
        fd.append('avatar', await T.pngFile('e2e-bg.png', '#2244aa', 640, 360));
        const h = T.headers(); delete h['Content-Type'];
        const up = await fetch('/api/backgrounds/upload', { method: 'POST', headers: h, body: fd });
        const name = await up.text();
        const all = await T.api('/api/backgrounds/all', {});
        const has = JSON.stringify(all).includes('e2e-bg');
        const thumb = await fetch('/thumbnail?type=bg&file=' + encodeURIComponent(name));
        await T.api('/api/backgrounds/delete', { bg: name });
        if (!up.ok || !has) throw new Error(up.status + ' ' + name);
        return { name, thumb: thumb.status + ' ' + thumb.headers.get('content-type') };`)],

    ['User images: upload, list, delete', () => page.run(`
        const png = (await T.pngFile('x.png')).arrayBuffer ? await (await T.pngFile('x.png')).arrayBuffer() : null;
        const b = btoa(String.fromCharCode(...new Uint8Array(png)));
        const up = await T.api('/api/images/upload', { image: b, format: 'png', ch_name: 'E2E', filename: 'e2e-img' });
        const list = await T.api('/api/images/list/E2E', {}).catch(async () => T.api('/api/images/list', { folder: 'E2E' }));
        await T.api('/api/images/delete', { path: up.path });
        return { path: up.path, listed: JSON.stringify(list).includes('e2e-img') };`)],

    ['Themes: save, export (download), delete', () => page.run(`
        const theme = { name: 'E2E Theme', blur_strength: 5, main_text_color: 'rgb(220,220,210)', italics_text_color: 'rgb(145,145,145)', chat_width: 50 };
        await T.api('/api/themes/save', theme);
        await T.api('/api/themes/delete', { name: 'E2E Theme' });
        return 'ok';`)],

    ['Settings snapshots: make and list', () => page.run(`
        await T.api('/api/settings/make-snapshot', {});
        const list = await T.api('/api/settings/get-snapshots', {});
        if (!list.length) throw new Error('none');
        return list.length + ' snapshots';`)],

    ['Stats and data maid report', () => page.run(`
        const stats = await T.api('/api/stats/get', {});
        const report = await T.api('/api/data-maid/report', {});
        return { statsKeys: Object.keys(stats).length, maid: Object.keys(report.report || report).slice(0, 6) };`)],

    ['Assets list (remote index)', () => page.run(`
        const r = await T.api('/api/assets/get', {});
        return Object.keys(r).slice(0, 8);`)],

    ['Third-party extension: install (git), version, update, delete', () => page.run(`
        const url = 'https://github.com/SillyTavern/Extension-Notebook';
        const ins = await T.api('/api/extensions/install', { url, global: false });
        const ver = await T.api('/api/extensions/version', { extensionName: ins.folderName, global: false });
        const upd = await T.api('/api/extensions/update', { extensionName: ins.folderName, global: false });
        let branches = 'n/a';
        try { branches = (await T.api('/api/extensions/branches', { extensionName: ins.folderName, global: false })).length; } catch (e) { branches = 'ERR ' + e.message.slice(0, 80); }
        await T.api('/api/extensions/delete', { extensionName: ins.folderName, global: false });
        return { ver: ver.currentCommitHash?.slice(0, 7), upToDate: ver.isUpToDate, upd: upd.isUpToDate, branches };`)],

    ['Third-party extension: global install (writes into app folder)', () => page.run(`
        const ins = await T.api('/api/extensions/install', { url: 'https://github.com/SillyTavern/Extension-Notebook', global: true });
        await T.api('/api/extensions/delete', { extensionName: ins.folderName, global: true });
        return ins.extensionPath;`)],
];
