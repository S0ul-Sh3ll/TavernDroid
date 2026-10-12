// Resets the app to a clean first-run state and prepares it for the other suites.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { adb, ui, tapText, topActivity, sleep, Page } from '../harness.mjs';

const PKG = 'io.github.taverndroid';
const LLM = process.env.E2E_LLM || 'http://10.0.2.2:8000';
const LLM_KEY = (process.env.E2E_LLM_KEY ?? (process.env.E2E_LLM_KEY_FILE ? readFileSync(process.env.E2E_LLM_KEY_FILE, 'utf8') : '')).trim();
const PIN = process.env.E2E_PIN || '246802';
const pinPad = () => Object.fromEntries(ui().filter((e) => /^\d$/.test(e.text)).map((e) => [e.text, e]));
const typePin = () => { const pad = pinPad(); for (const d of PIN) adb('shell', 'input', 'tap', String(pad[d].x), String(pad[d].y)); };

export const native = true;
export default () => [
    ['Clear app data, first launch shows "Create a PIN"', async () => {
        adb('shell', 'pm', 'clear', PKG);
        adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS');
        for (const d of ['/sdcard/Download/SillyTavern']) adb('shell', 'rm', '-rf', d);
        adb('shell', 'am', 'start', '-n', `${PKG}/.MainActivity`);
        await sleep(3000);
        if (!ui().some((e) => e.text === 'Create a PIN')) throw new Error(topActivity());
        return 'ok';
    }],
    ['Create PIN (enter + confirm)', async () => {
        typePin(); await sleep(1200); typePin(); await sleep(2500);
        return topActivity();
    }],
    ['Server starts; UI loads; welcome popup answered', async () => {
        const t0 = Date.now();
        let page;
        for (let i = 0; i < 90 && !page; i++) { try { page = await Page.connect(); } catch { await sleep(1000); } }
        await page.installHelpers();
        const r = await page.run(`
            await T.waitFor(() => document.querySelector('dialog[open]'), 90000);
            const d = document.querySelector('dialog[open]'); d.dataset.e2eKeep = '1';
            d.querySelector('.popup-button-ok')?.click();
            await T.waitFor(() => T.ctx().characters.length > 0, 60000);
            return T.ctx().characters.map(c => c.name);`);
        page.close();
        return { msToReady: Date.now() - t0, chars: r };
    }],
    ['Connect Chat Completion → custom Qwen (Mac)', async () => {
        const page = await Page.connect(); await page.installHelpers();
        const r = await page.run(`
            const $ = jQuery;
            $('#main_api').val('openai').trigger('change'); await T.sleep(500);
            $('#chat_completion_source').val('custom').trigger('change'); await T.sleep(500);
            $('#custom_api_url_text').val('${LLM}/v1').trigger('input').trigger('change');
            $('#custom_model_id').val('qwen27').trigger('input').trigger('change');
            $('#custom_prompt_post_processing').val('semi').trigger('change');
            T.ctx().chatCompletionSettings.custom_include_body = 'chat_template_kwargs:\\n  enable_thinking: false';
            $('#openai_max_tokens').val(150).trigger('input');
            $('#stream_toggle').prop('checked', true).trigger('change');
            await T.sleep(1000);
            $('#api_key_custom').val(${JSON.stringify(LLM_KEY)});
            $('#api_button_openai').trigger('click');
            await T.waitFor(() => T.ctx().onlineStatus === 'Valid', 30000);
            T.ctx().saveSettingsDebounced(); await T.sleep(2000);
            return T.ctx().onlineStatus;`);
        page.close();
        return r;
    }],
    ['Auto-lock → 1 hour (so long tests are not locked out)', async () => {
        adb('shell', 'am', 'start', '-n', `${PKG}/.MainActivity`, '--es', 'open', 'settings');
        await sleep(2500);
        for (let i = 0; i < 3 && !ui().some((e) => e.text === 'Auto-lock…'); i++) { adb('shell', 'input', 'swipe', '540', '1700', '540', '1000', '250'); await sleep(600); }
        tapText('Auto-lock…'); await sleep(1000);
        tapText('1 hour of inactivity'); await sleep(800);
        const label = ui().find((e) => e.text.startsWith('Auto-lock:'))?.text;
        adb('shell', 'input', 'keyevent', '4');
        return label;
    }],
];
