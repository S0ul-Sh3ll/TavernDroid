import { readFileSync } from 'node:fs';
import { adb, assert, sleep } from '../harness.mjs';

const LLM = process.env.E2E_LLM || 'http://10.0.2.2:8000';
const LLM_KEY = (process.env.E2E_LLM_KEY ?? (process.env.E2E_LLM_KEY_FILE ? readFileSync(process.env.E2E_LLM_KEY_FILE, 'utf8') : '')).trim();

const FIX = new URL('../fixtures/', import.meta.url).pathname;
const b64 = (f) => readFileSync(FIX + f).toString('base64');
const downloads = () => { try { return adb('shell', 'ls', '/sdcard/Download/SillyTavern/'); } catch { return ''; } };
const clearDownloads = () => { try { adb('shell', 'rm', '-f', '/sdcard/Download/SillyTavern/*'); } catch { /* none */ } };
async function expectDownload(pattern, action) {
    clearDownloads();
    await action();
    for (let i = 0; i < 30; i++) {
        const list = downloads();
        const hit = list.split('\n').find((l) => pattern.test(l));
        if (hit) return hit.trim();
        await sleep(500);
    }
    throw new Error(`no download matching ${pattern}; have: ${downloads()}`);
}

export default (page) => [
    ['Create character through the UI (with avatar)', () => page.run(`
        await T.closePopups();
        jQuery('#rm_button_create').trigger('click'); await T.sleep(600);
        jQuery('#character_name_pole').val('E2E Hero').trigger('input');
        jQuery('#description_textarea').val('A brave tester who checks every feature.').trigger('input');
        jQuery('#firstmessage_textarea').val('Ready to test!').trigger('input');
        T.setFiles('#add_avatar_button', [T.fileFromB64('hero.png', 'image/png', '${b64('avatar.png')}')]);
        await T.sleep(1500);
        await T.closePopups(); // crop dialog, if shown
        jQuery('#create_button').trigger('click');
        await T.waitFor(() => T.ctx().characters.some(c => c.name === 'E2E Hero'), 20000);
        return T.ctx().characters.find(c => c.name === 'E2E Hero').avatar;`)],

    ['Saved card PNG embeds character data (tEXt chara)', () => page.run(`
        const av = T.ctx().characters.find(c => c.name === 'E2E Hero').avatar;
        const buf = new Uint8Array(await (await fetch('/characters/' + encodeURIComponent(av) + '?t=' + Date.now())).arrayBuffer());
        const text = new TextDecoder('latin1').decode(buf);
        const has = text.includes('chara') || text.includes('ccv3');
        if (!has) throw new Error('no card chunk; size ' + buf.length);
        return { bytes: buf.length, ccv3: text.includes('ccv3') };`)],

    ['Edit description persists on the server', () => page.run(`
        await T.selectChar('E2E Hero');
        jQuery('#description_textarea').val('Edited description 42').trigger('input');
        await T.sleep(2500);
        const av = T.ctx().characters[T.ctx().characterId].avatar;
        const ch = await T.api('/api/characters/get', { avatar_url: av });
        if (!ch.description.includes('Edited description 42')) throw new Error(ch.description);
        return 'ok';`)],

    ['Favorite toggle', () => page.run(`
        jQuery('#favorite_button').trigger('click'); await T.sleep(1500);
        const av = T.ctx().characters[T.ctx().characterId].avatar;
        const ch = await T.api('/api/characters/get', { avatar_url: av });
        return { fav: ch.fav ?? ch.data?.extensions?.fav };`)],

    ['Duplicate character', () => page.run(`
        const before = T.ctx().characters.length;
        const av = T.ctx().characters[T.ctx().characterId].avatar;
        await T.api('/api/characters/duplicate', { avatar_url: av });
        await T.ctx().getCharacters();
        if (T.ctx().characters.length !== before + 1) throw new Error('count ' + before + '→' + T.ctx().characters.length);
        return T.ctx().characters.length;`)],

    ['Rename character (/api/characters/rename)', () => page.run(`
        const dup = T.ctx().characters.filter(c => c.name === 'E2E Hero').map(c => c.avatar).sort().at(-1);
        const r = await T.api('/api/characters/rename', { avatar_url: dup, new_name: 'E2E Renamed' });
        await T.ctx().getCharacters();
        if (!T.ctx().characters.some(c => c.name === 'E2E Renamed')) throw new Error(JSON.stringify(r));
        return r.avatar || r;`)],

    ['Export character as PNG (download)', () => expectDownload(/E2E.*\.png$/, () => page.run(`
        await T.selectChar('E2E Hero');
        jQuery('#export_button').first().trigger('click'); await T.sleep(500);
        jQuery('.export_format[data-format=png]').first().trigger('click'); await T.sleep(2500); return 1;`))],

    ['Export character as JSON (download)', () => expectDownload(/E2E.*\.json$/, () => page.run(`
        jQuery('#export_button').first().trigger('click'); await T.sleep(500);
        jQuery('.export_format[data-format=json]').first().trigger('click'); await T.sleep(2500); return 1;`))],

    ['Import JSON card (file input)', () => page.run(`
        const card = { spec: 'chara_card_v2', spec_version: '2.0', data: { name: 'JSON Import', description: 'from json', first_mes: 'hi from json', personality: '', scenario: '', mes_example: '', creator_notes: '', system_prompt: '', post_history_instructions: '', alternate_greetings: [], tags: ['e2e'], creator: 'e2e', character_version: '1', extensions: {} } };
        T.setFiles('#character_import_file', [new File([JSON.stringify(card)], 'json-import.json', { type: 'application/json' })]);
        await T.waitFor(() => T.ctx().characters.some(c => c.name === 'JSON Import'), 20000);
        await T.sleep(1000); await T.closePopups();
        return 'ok';`)],

    ['Import CharX archive (with embedded lorebook)', () => page.run(`
        T.setFiles('#character_import_file', [T.fileFromB64('test.charx', 'application/zip', '${b64('test.charx')}')]);
        await T.waitFor(() => T.ctx().characters.some(c => c.name === 'CharX Tester'), 20000);
        await T.sleep(1000); await T.closePopups();
        const c = T.ctx().characters.find(c => c.name === 'CharX Tester');
        return { book: !!c.data?.character_book, greetings: c.data?.alternate_greetings?.length };`)],

    ['Import character from URL (GitHub raw PNG)', () => page.run(`
        const r = await T.api('/api/content/importURL', { url: 'https://raw.githubusercontent.com/SillyTavern/SillyTavern/release/default/content/default_Seraphina.png' }, { raw: true });
        if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + (await r.text()).slice(0, 200));
        const type = r.headers.get('X-Custom-Content-Type'); const size = (await r.arrayBuffer()).byteLength;
        return { type, size };`)],

    ['Tags: add and check', () => page.run(`
        await T.selectChar('E2E Hero');
        await T.cmd('/tag-add e2e-tag');
        const r = await T.cmd('/tag-exists e2e-tag');
        if (String(r) !== 'true') throw new Error('tag-exists → ' + r);
        return r;`)],

    ['Delete character', () => page.run(`
        const av = T.ctx().characters.find(c => c.name === 'E2E Renamed').avatar;
        await T.api('/api/characters/delete', { avatar_url: av, delete_chats: true });
        await T.ctx().getCharacters();
        if (T.ctx().characters.some(c => c.name === 'E2E Renamed')) throw new Error('still there');
        return 'ok';`)],

    ['New chat, rename, chat list', () => page.run(`
        await T.selectChar('E2E Hero');
        await T.cmd('/newchat'); await T.sleep(1500);
        await T.cmd('/renamechat E2E chat one'); await T.sleep(1000);
        const name = await T.cmd('/getchatname');
        const av = T.ctx().characters[T.ctx().characterId].avatar;
        const chats = await T.api('/api/characters/chats', { avatar_url: av });
        return { name, chats: Object.values(chats).length };`)],

    ['Send, edit, hide/unhide a message', () => page.run(`
        await T.send('Say exactly: test reply.');
        await T.cmd('/hide 1'); await T.sleep(500);
        const hidden = T.ctx().chat[1].is_system;
        await T.cmd('/unhide 1'); await T.sleep(500);
        // inline edit through the UI
        jQuery('.mes[mesid=1] .mes_edit').trigger('click'); await T.sleep(500);
        jQuery('#curEditTextarea').val('Edited user message').trigger('input');
        jQuery('.mes[mesid=1] .mes_edit_done').trigger('click'); await T.sleep(1000);
        return { hidden, unhidden: !T.ctx().chat[1].is_system, edited: T.ctx().chat[1].mes };`)],

    ['Checkpoint and branch', () => page.run(`
        const cp = await T.cmd('/checkpoint-create mesId=1 E2E checkpoint');
        const br = await T.cmd('/branch-create 1');
        await T.sleep(1500);
        const name = await T.cmd('/getchatname');
        return { cp, br, now: name };`)],

    ['Export chat as JSONL and TXT (download)', async () => {
        const a = await expectDownload(/\.jsonl$/, () => page.run(`
            jQuery('#option_select_chat').trigger('click'); await T.sleep(1500);
            jQuery('.exportRawChatButton').first().trigger('click'); await T.sleep(2500); return 1;`));
        const b = await expectDownload(/\.txt$/, () => page.run(`
            jQuery('.exportChatButton').first().trigger('click'); await T.sleep(2500); await T.closePopups(); jQuery('#select_chat_cross').trigger('click'); return 1;`));
        return [a, b];
    }],

    ['Import a JSONL chat', () => page.run(`
        const av = T.ctx().characters[T.ctx().characterId].avatar;
        const file = T.ctx().getCurrentChatId ? T.ctx().getCurrentChatId() : null;
        const res = await fetch('/api/chats/export', { method: 'POST', headers: T.headers(), body: JSON.stringify({ is_group: false, avatar_url: av, file: file + '.jsonl', exportfilename: 'x.jsonl', format: 'jsonl' }) });
        const data = await res.json();
        const fd = new FormData();
        fd.append('avatar', new File([data.result], 'imported.jsonl'));
        fd.append('file_type', 'jsonl'); fd.append('avatar_url', av); fd.append('character_name', 'E2E Hero'); fd.append('user_name', 'User');
        const h = T.headers(); delete h['Content-Type'];
        const r = await fetch('/api/chats/import', { method: 'POST', headers: h, body: fd });
        return { status: r.status, body: (await r.text()).slice(0, 120) };`)],

    ['Chat search and chat backups', () => page.run(`
        const s = await T.api('/api/chats/search', { query: 'Edited', avatar_url: T.ctx().characters[T.ctx().characterId].avatar });
        let backups = 'n/a';
        try { backups = (await T.api('/api/backups/chat/get', {})).length; } catch (e) { backups = 'ERR ' + e.message.slice(0, 80); }
        return { hits: Array.isArray(s) ? s.length : s, backups };`)],

    ['Persona: create with avatar, switch, appears in prompt', () => page.run(`
        const fd = new FormData();
        fd.append('avatar', T.fileFromB64('persona.png', 'image/png', '${b64('avatar.png')}'));
        const h = T.headers(); delete h['Content-Type'];
        const up = await (await fetch('/api/avatars/upload', { method: 'POST', headers: h, body: fd })).json();
        const pu = T.ctx().powerUserSettings;
        pu.personas[up.path] = 'Zed'; pu.persona_descriptions[up.path] = { description: 'Zed is a persona marker QX77.', position: 0 };
        T.ctx().saveSettingsDebounced(); await T.sleep(1500);
        await T.cmd('/persona mode=all Zed'); await T.sleep(1000);
        const prompt = await T.dryRun();
        return { avatar: up.path, name1: T.ctx().name1, inPrompt: prompt.includes('QX77') };`)],

    ['World Info: keyword entry activates in prompt', () => page.run(`
        await T.api('/api/worldinfo/edit', { name: 'E2E Book', data: { entries: { 0: { uid: 0, key: ['zorblax'], keysecondary: [], comment: 'kw', content: 'The secret code is FLAMINGO-9.', constant: false, selective: false, order: 100, position: 0, disable: false, probability: 100, useProbability: true, depth: 4 }, 1: { uid: 1, key: [], keysecondary: [], comment: 'const', content: 'CONSTANT-LORE-77', constant: true, selective: false, order: 100, position: 0, disable: false, probability: 100, useProbability: true, depth: 4 } } } });
        await T.ctx().updateWorldInfoList?.();
        await T.cmd('/world silent=true E2E Book'); await T.sleep(800);
        await T.cmd('/send Tell me about the zorblax.'); await T.sleep(800);
        const prompt = await T.dryRun();
        await T.cmd('/del 1');
        await T.cmd('/world state=off silent=true E2E Book');
        if (!prompt.includes('FLAMINGO-9') || !prompt.includes('CONSTANT-LORE-77')) throw new Error('not in prompt');
        return 'keyword + constant entries injected';`)],

    ["Author's note in prompt", () => page.run(`
        await T.cmd('/note AUTHORNOTE-55'); await T.sleep(500);
        const prompt = await T.dryRun();
        await T.cmd('/note');
        if (!prompt.includes('AUTHORNOTE-55')) throw new Error('missing');
        return 'ok';`)],

    ['Group chat: create, generate with two members', () => page.run(`
        const a = T.ctx().characters.find(c => c.name === 'Seraphina').avatar;
        const b = T.ctx().characters.find(c => c.name === 'E2E Hero').avatar;
        const g = await T.api('/api/groups/create', { name: 'E2E Group', members: [a, b], avatar_url: '', allow_self_responses: false, activation_strategy: 0, generation_mode: 0, disabled_members: [], chat_metadata: {}, fav: false, chat_id: 'E2E Group chat', chats: ['E2E Group chat'] });
        const all = await T.api('/api/groups/all', {});
        const created = all.find(x => x.id === g.id);
        if (!T.ctx().groups.some(x => x.id === g.id)) T.ctx().groups.push(created);
        const { openGroupById } = await import('/scripts/group-chats.js');
        await openGroupById(g.id); await T.sleep(1500);
        if (T.ctx().groupId !== g.id) throw new Error('group did not open');
        await T.gen(() => T.cmd('/trigger await=true 0'));
        const last = T.ctx().chat.at(-1);
        return { group: g.id, last: last?.name, text: last?.mes?.slice(0, 60) };`)],

    ['Presets: save, switch, delete (Chat Completion)', () => page.run(`
        const pm = T.ctx().getPresetManager('openai');
        const before = pm.getSelectedPresetName();
        await pm.savePreset('E2E Preset');                       // saves current settings + adds to the list
        await T.cmd('/preset E2E Preset'); await T.sleep(800);
        const selected = pm.getSelectedPresetName();
        await T.cmd('/preset ' + before); await T.sleep(500);
        await pm.deletePreset('E2E Preset');
        // Presets carry the connection (source, URL); reconnect to the test backend afterwards.
        jQuery('#chat_completion_source').val('custom').trigger('change'); await T.sleep(500);
        jQuery('#custom_api_url_text').val('${LLM}/v1').trigger('input').trigger('change');
        jQuery('#custom_model_id').val('qwen27').trigger('input').trigger('change'); await T.sleep(800);
        jQuery('#api_key_custom').val(${JSON.stringify(LLM_KEY)});
        jQuery('#api_button_openai').trigger('click');
        await T.waitFor(() => T.ctx().onlineStatus === 'Valid', 30000);
        const saved = { name: selected };
        if (selected !== 'E2E Preset') throw new Error('selected ' + selected);
        return { saved: saved.name, selected };`)],

    ['Instruct / context template switching', () => page.run(`
        const i = await T.cmd('/instruct ChatML'); const c = await T.cmd('/context ChatML');
        const iname = T.ctx().powerUserSettings.instruct.preset; const cname = T.ctx().powerUserSettings.context.preset;
        return { iname, cname };`)],
];
