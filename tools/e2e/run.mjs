// usage: node tools/e2e/run.mjs <suite> [suite...]   (suites live in tools/e2e/suites/<name>.mjs)
// Results are printed and appended to tools/e2e/report.jsonl.
import path from 'node:path';
import { Page, suite } from './harness.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname);
const report = path.join(HERE, 'report.jsonl');
const totals = { pass: 0, fail: 0 };
for (const name of process.argv.slice(2)) {
    const mod = await import(path.join(HERE, 'suites', `${name}.mjs`));
    const page = mod.native ? null : await Page.connect();
    if (page) await page.installHelpers();
    let tests = await mod.default(page);
    if (process.env.E2E_FROM) {
        const i = tests.findIndex(([t]) => t.startsWith(process.env.E2E_FROM));
        if (i > 0) tests = [tests[0], ...tests.slice(i)]; // keep the suite's setup step
    }
    const results = await suite(name, page, tests, report);
    for (const r of results) totals[r.status === 'PASS' ? 'pass' : 'fail']++;
    page?.close();
}
console.log(`\n${totals.pass} passed, ${totals.fail} failed`);
process.exit(0);
