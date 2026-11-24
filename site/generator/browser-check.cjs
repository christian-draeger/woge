const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const navigation = [
  ['Overview', 'index.html'],
  ['Start', 'start.html'],
  ['Everyday tasks', 'tasks.html'],
  ['Hosts & deployment', 'hosts.html'],
  ['Demo & evidence', 'evidence.html'],
];

async function check(base) {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({
      javaScriptEnabled: false,
      viewport: { width: 390, height: 844 },
    });
    const response = await page.goto(base);
    assert.equal(response.status(), 200);
    const nav = page.getByRole('navigation', { name: 'Documentation' });
    assert.deepEqual(await nav.getByRole('link').allTextContents(), navigation.map(([name]) => name));

    await page.keyboard.press('Tab');
    assert.equal(await page.locator(':focus').textContent(), 'Skip to content');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator(':focus').getAttribute('id'), 'main');

    for (const [name, filename] of navigation) {
      const destination = new URL(filename, base).href;
      const link = nav.getByRole('link', { name, exact: true });
      const [loaded] = await Promise.all([
        page.waitForResponse((candidate) => candidate.url() === destination),
        link.click(),
      ]);
      assert.equal(loaded.status(), 200);
      assert.equal(await page.getByRole('heading', { level: 1 }).count(), 1);
      assert.equal(await nav.locator('[aria-current=page]').textContent(), name);
      const stylesheet = await page.locator('link[rel=stylesheet]').getAttribute('href');
      assert.match(new URL(stylesheet, base).pathname, /\/assets\/site-[a-f0-9]{64}[.]css$/);
      assert.equal((await page.request.get(new URL(stylesheet, base).href)).status(), 200);
    }

    await nav.getByRole('link', { name: 'Everyday tasks' }).click();
    assert.ok(await page.locator('pre[tabindex="0"] > code.language-kotlin').first().isVisible());
    assert.equal(await page.getByRole('button', { name: /copy/i }).count(), 0);
    console.log(`Static navigation, keyboard, stylesheet and snippets passed with JavaScript disabled at ${base}`);
  } finally {
    await browser.close();
  }
}

check(process.argv[2]).catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
