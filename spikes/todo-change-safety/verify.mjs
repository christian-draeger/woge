import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../../client/woge-fallback-client/package.json', import.meta.url));
const { chromium } = require('playwright');
const [origin, requested] = process.argv.slice(2);
const browser = await chromium.launch();
const checks = ['initial', 'filter', 'native-add', 'native-invalid', 'enhanced-add', 'enhanced-invalid', 'keys', 'output'];

async function prepare(check) {
    const enhanced = check.startsWith('enhanced-');
    const context = await browser.newContext({ javaScriptEnabled: enhanced });
    const page = await context.newPage();
    page.setDefaultTimeout(2500);
    const response = await page.goto(origin);
    assert.equal(response.status(), 200, 'initial status');
    if (enhanced) {
        await page.waitForFunction(() => performance.getEntriesByType('resource').some(entry => entry.initiatorType === 'script'));
    }
    return { context, page };
}

async function add(page, title) {
    await page.getByLabel('New todo').fill(title);
    const post = page.waitForResponse(result => result.request().method() === 'POST');
    await page.getByRole('button', { name: 'Add todo' }).click();
    return (await post).status();
}

async function checkApp(page, check, count) {
    const rows = page.locator('#todo-board li');
    const title = 'Review <em> & report';
    switch (check) {
        case 'initial':
            assert.equal(await rows.count(), count, 'initial board size');
            break;
        case 'filter':
            await page.getByRole('link', { name: 'Completed' }).click();
            await page.waitForURL(`${origin}/?filter=completed`);
            assert.equal(await rows.count(), 0, 'completed view contains no active rows');
            break;
        case 'native-add':
            assert.equal(await add(page, title), 303, 'native valid POST');
            await page.getByText(title, { exact: true }).first().waitFor();
            break;
        case 'native-invalid':
            assert.equal(await add(page, '   '), 400, 'native invalid POST');
            assert.equal(await page.getByLabel('New todo').inputValue(), '   ');
            assert.equal(await rows.count(), count, 'invalid POST preserves rows');
            break;
        case 'enhanced-add': {
            const url = page.url();
            assert.equal(await add(page, title), 200, 'enhanced valid POST');
            await page.waitForFunction(total => document.querySelectorAll('#todo-board li').length === total, count + 1);
            assert.equal(page.url(), url, 'enhanced Add stays in the document');
            break;
        }
        case 'enhanced-invalid':
            await add(page, '   ');
            await page.getByRole('heading', { name: 'Check your todo' }).waitFor();
            await page.waitForFunction(() => document.activeElement?.id === 'todo-errors');
            assert.equal(await rows.count(), count, 'invalid enhanced POST preserves rows');
            break;
        case 'keys': {
            const keys = await rows.evaluateAll(items => items.map(row => row.dataset.todoId));
            assert.equal(keys.length, count);
            assert.ok(keys.every(Boolean), 'each row has an identity');
            assert.equal(new Set(keys).size, count, 'public row identities are distinct');
            break;
        }
        case 'output':
            await page.getByText('Todo <1> & Kotlin', { exact: true }).waitFor();
            assert.equal(await rows.first().locator('span').textContent(), 'Todo <1> & Kotlin');
            break;
    }
}

const results = {};
let count = 10;
try {
    for (const check of requested === 'all' ? checks : [requested]) {
        if (!checks.includes(check)) throw new Error(`Unknown check: ${check}`);
        try {
            const { context, page } = await prepare(check);
            try {
                await checkApp(page, check, count);
                results[check] = { passed: true };
                if (check.endsWith('-add')) count++;
            } finally {
                await context.close();
            }
        } catch (error) {
            results[check] = { passed: false, message: error.message };
        }
    }
    console.log(JSON.stringify({ browser: browser.version(), results }));
} finally {
    await browser.close();
}
