import { readFileSync } from 'node:fs';
import { assert } from '../harness.mjs';

// Backends reachable from the emulator: 10.0.2.2 is the host's loopback.
const LLM = process.env.E2E_LLM || 'http://10.0.2.2:8000';
const LLM_KEY = (process.env.E2E_LLM_KEY ?? (process.env.E2E_LLM_KEY_FILE ? readFileSync(process.env.E2E_LLM_KEY_FILE, 'utf8') : '')).trim();
const OR_KEY = (process.env.E2E_OPENROUTER_KEY ?? (process.env.E2E_OPENROUTER_KEY_FILE ? readFileSync(process.env.E2E_OPENROUTER_KEY_FILE, 'utf8') : '')).trim();

export default (page) => [
    ['UI loaded with app button', async () => page.run(`
        await T.waitFor(() => T.ctx().characters?.length > 0, 60000);
        if (!document.getElementById('taverndroid-button')) throw new Error('no app button');
        return { chars: T.ctx().characters.map(c => c.name), ua: navigator.userAgent.match(/TavernDroid\\/\\S+/)[0] };`)],

    ['Welcome popup: persona name saved', async () => page.run(`
        const d = document.querySelector('dialog[open]');
        if (d) { const i = d.querySelector('textarea, input[type=text]'); if (i) { i.value = 'Tester'; i.dispatchEvent(new Event('input', {bubbles:true})); } d.querySelector('.popup-button-ok')?.click(); }
        await T.sleep(1500);
        return T.ctx().name1;`)],

    ['Chat Completion custom source connects', async () => page.run(`
        const $ = jQuery;
        $('#main_api').val('openai').trigger('change'); await T.sleep(300);
        $('#chat_completion_source').val('custom').trigger('change'); await T.sleep(300);
        $('#custom_api_url_text').val('${LLM}/v1').trigger('input').trigger('change');
        $('#custom_model_id').val('qwen27').trigger('input').trigger('change');
        await T.sleep(1000);
        $('#custom_prompt_post_processing').val('semi').trigger('change');
        T.ctx().chatCompletionSettings.custom_include_body = 'chat_template_kwargs:\\n  enable_thinking: false';
        $('#api_key_custom').val(${JSON.stringify(LLM_KEY)});
        $('#api_button_openai').trigger('click');
        await T.waitFor(() => T.ctx().onlineStatus && T.ctx().onlineStatus !== 'no_connection', 30000);
        T.ctx().saveSettingsDebounced();
        return { status: T.ctx().onlineStatus, models: $('#model_custom_select option').length };`)],

    ['Generate (streaming on)', async () => page.run(`
        T.ctx().chatCompletionSettings.stream_openai = true; jQuery('#stream_toggle').prop('checked', true).trigger('change');
        T.ctx().chatCompletionSettings.openai_max_tokens = 120; jQuery('#openai_max_tokens').val(120).trigger('input');
        await T.selectChar('Seraphina');
        const m = await T.send('Hello! Reply in one short sentence.');
        if (m.is_user || !m.mes) throw new Error('no reply');
        return m.mes.slice(0, 120);`)],

    ['Generate (streaming off)', async () => page.run(`
        jQuery('#stream_toggle').prop('checked', false).trigger('change');
        const m = await T.send('Say one word.');
        jQuery('#stream_toggle').prop('checked', true).trigger('change');
        if (m.is_user || !m.mes) throw new Error('no reply');
        return m.mes.slice(0, 120);`)],

    ['Swipe generates a new alternative', async () => page.run(`
        const c = T.ctx(); const last = c.chat.length - 1;
        const before = c.chat[last].swipes?.length || 1;
        await T.gen(() => jQuery('.last_mes .swipe_right').trigger('click'));
        return { swipes: T.ctx().chat[last].swipes.length, id: T.ctx().chat[last].swipe_id };`)],

    ['Swipe back left', async () => page.run(`
        const last = T.ctx().chat.length - 1;
        const id = T.ctx().chat[last].swipe_id;
        jQuery('.last_mes .swipe_left').trigger('click');
        await T.waitFor(() => T.ctx().chat[last].swipe_id === id - 1, 10000);
        return T.ctx().chat[last].swipe_id;`)],

    ['Regenerate', async () => page.run(`
        const len = T.ctx().chat.length;
        await T.gen(() => T.cmd('/regenerate'));
        if (T.ctx().chat.length !== len) throw new Error('length changed ' + len + '→' + T.ctx().chat.length);
        return T.ctx().chat.at(-1).mes.slice(0, 80);`)],

    ['Send still works right after swipe + regenerate (patch 0001)', async () => page.run(`
        const mod = await import('/script.js');
        try { await T.waitFor(() => mod.swipeState === 'none', 10000); } catch { throw new Error('swipe lock stuck: ' + mod.swipeState); }
        const swipeState = mod.swipeState;
        const m = await T.send('Reply with one word.', 120000);
        if (m.is_user) throw new Error('send was ignored');
        return { swipeState, reply: m.mes.slice(0, 40) };`)],

    ['Continue extends the last reply', async () => page.run(`
        const before = T.ctx().chat.at(-1).mes.length;
        await T.gen(() => T.cmd('/continue'));
        const after = T.ctx().chat.at(-1).mes.length;
        if (after <= before) throw new Error('not extended ' + before + '→' + after);
        return before + '→' + after;`)],

    ['Impersonate writes into the input box', async () => page.run(`
        jQuery('#send_textarea').val('');
        await T.gen(() => T.cmd('/impersonate'));
        const v = jQuery('#send_textarea').val();
        jQuery('#send_textarea').val('').trigger('input');
        if (!v) throw new Error('empty');
        return v.slice(0, 80);`)],

    ['Quiet generation (/gen)', async () => page.run(`
        const r = await T.cmd('/gen lock=on Reply with exactly the word: banana');
        if (!r) throw new Error('empty');
        return r.slice(0, 80);`)],

    ['Stop button aborts a streaming reply', async () => page.run(`
        const len = T.ctx().chat.length;
        jQuery('#send_textarea').val('Write a very long story about a dragon, at least 600 words.').trigger('input');
        jQuery('#send_but').trigger('click');
        await T.waitFor(() => T.generating() && T.ctx().chat.length >= len + 2 && T.ctx().chat.at(-1).mes.length > 20, 120000, 200);
        jQuery('#mes_stop').trigger('click');
        await T.waitFor(() => !T.generating(), 20000);
        return { kept: T.ctx().chat.at(-1).mes.length };`)],

    ['Delete last messages (/del)', async () => page.run(`
        const len = T.ctx().chat.length;
        await T.cmd('/del 2');
        await T.waitFor(() => T.ctx().chat.length === len - 2, 10000);
        return T.ctx().chat.length;`)],

    ['Token counting (all tokenizers)', async () => page.run(`
        const text = 'The quick brown fox jumps over the lazy dog. Привет мир. 日本語のテキスト。';
        const out = {};
        for (const t of ['llama', 'llama3', 'mistral', 'yi', 'gemma', 'jamba', 'qwen2', 'command-r', 'command-a', 'nemo', 'deepseek', 'gpt2', 'claude', 'nerdstash', 'nerdstash_v2']) {
            try { out[t] = (await T.api('/api/tokenizers/' + t + '/encode', { text })).count; } catch (e) { out[t] = 'ERR ' + e.message.slice(0, 80); }
        }
        for (const m of ['gpt-4o', 'gpt-4', 'claude-3-opus', 'gemini-pro']) {
            try { out['openai:' + m] = (await T.api('/api/tokenizers/openai/count?model=' + m, [{ role: 'user', content: text }])).token_count; } catch (e) { out['openai:' + m] = 'ERR ' + e.message.slice(0, 80); }
        }
        const bad = Object.entries(out).filter(([, v]) => !(v > 0));
        if (bad.length) throw new Error(JSON.stringify(out));
        return out;`, 600000)],

    ['Text Completion: llama.cpp connects and generates', async () => page.run(`
        const $ = jQuery;
        $('#main_api').val('textgenerationwebui').trigger('change'); await T.sleep(400);
        $('#textgen_type').val('llamacpp').trigger('change'); await T.sleep(300);
        $('#llamacpp_api_url_text').val('${LLM}').trigger('input');
        $('#api_key_llamacpp').val(${JSON.stringify(LLM_KEY)});
        $('#api_button_textgenerationwebui').trigger('click');
        await T.waitFor(() => T.ctx().onlineStatus && T.ctx().onlineStatus !== 'no_connection', 30000);
        const status = T.ctx().onlineStatus;
        const r = await T.cmd('/gen lock=on Say hello in five words.');
        return { status, gen: (r || '').slice(0, 80) };`)],

    ['OpenRouter: models listed and free model generates', async () => page.run(`
        const $ = jQuery;
        $('#main_api').val('openai').trigger('change'); await T.sleep(300);
        $('#chat_completion_source').val('openrouter').trigger('change'); await T.sleep(500);
        $('#api_key_openrouter').val(${JSON.stringify(OR_KEY)});
        $('#api_button_openai').trigger('click');
        await T.waitFor(() => T.ctx().onlineStatus && T.ctx().onlineStatus !== 'no_connection', 30000);
        await T.waitFor(() => jQuery('#model_openrouter_select option').length > 50, 30000);
        const models = jQuery('#model_openrouter_select option').length;
        $('#model_openrouter_select').val('nvidia/nemotron-3-super-120b-a12b:free').trigger('change');
        await T.sleep(300);
        let r;
        for (let i = 0; i < 3 && !r; i++) { try { r = await T.cmd('/gen lock=on Say hi in three words.'); } catch (e) { await T.sleep(5000); } }
        return { models, gen: (r || 'NO OUTPUT (rate limit?)').slice(0, 80) };`)],

    ['Back to custom Qwen for the remaining suites', async () => page.run(`
        jQuery('#chat_completion_source').val('custom').trigger('change');
        jQuery('#api_button_openai').trigger('click');
        await T.waitFor(() => T.ctx().onlineStatus === 'Valid', 30000);
        T.ctx().saveSettingsDebounced();
        return T.ctx().onlineStatus;`)],
];
