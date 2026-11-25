import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { chromium } from "playwright";

const [target, launcher] = process.argv.slice(2);
assert(target && (target !== "--distribution" || launcher), "Provide an origin or --distribution <launcher>");
const invoicePath = "/localized-invoices";
const postPath = "/woge-actions/save-localized-invoice";
const locales = [
  { tag: "de-DE", dir: "ltr", date: "18.11.2026", amounts: ["1,20", "2,30", "3,40", "4,50"], total: "90071992547409,93", edited: "500,31" },
  { tag: "en-US", dir: "ltr", date: "11/18/2026", amounts: ["1.20", "2.30", "3.40", "4.50"], total: "90071992547409.93", edited: "500.31" },
  { tag: "ar-EG", dir: "rtl", date: "١٨/١١/٢٠٢٦", amounts: ["١٫٢٠", "٢٫٣٠", "٣٫٤٠", "٤٫٥٠"], total: "٩٠٠٧١٩٩٢٥٤٧٤٠٩٫٩٣", edited: "٥٠٠٫٣١" },
];
const receipt = Buffer.from("Beleg + إيصال ñ\n");
const badReceipt = Buffer.from([0x80]);

async function selectReceipt(page, buffer) {
  await page.locator("#saved-receipt").setInputFiles({ name: "receipt.txt", mimeType: "text/plain", buffer });
}

async function submit(page, status, resource) {
  const post = page.waitForResponse(
    (response) => new URL(response.url()).pathname === postPath && response.request().method() === "POST",
  );
  await page.locator("form button:not([type=button])").click();
  const response = await post;
  assert.equal(response.status(), status);
  assert.equal(response.request().resourceType(), resource);
}

async function reject(page, enhanced, errorField) {
  if (enhanced) {
    const original = page.url();
    await submit(page, 400, "xhr");
    await page.waitForFunction(() => !document.querySelector("form").hasAttribute("data-woge-upload"));
    assert.equal(page.url(), original);
    assert((await page.locator("#saved-receipt").inputValue()).endsWith("receipt.txt"));
    assert((await page.locator("#saved-invoice-upload [role=status]").textContent()).trim());
    assert.equal(await page.locator("#saved-invoice-errors").count(), 0);
  }
  // Enhanced upload leaves the draft and file in place. A deliberate native retry shows server errors.
  await submit(page, 400, "document");
  await page.locator(`#${errorField}-error`).waitFor();
  assert.equal(await page.locator(`#${errorField}`).getAttribute("aria-errormessage"), `${errorField}-error`);
  assert.equal(await page.locator("#saved-invoice-errors a").getAttribute("href"), `#${errorField}`);
  assert.equal(await page.locator("#saved-receipt").inputValue(), "");
}

async function verify(browser, origin, locale, enhanced) {
  const context = await browser.newContext({
    javaScriptEnabled: enhanced,
    locale: locale.tag,
    viewport: locale.dir === "rtl" ? { width: 390, height: 844 } : undefined,
  });
  try {
    const page = await context.newPage();
    const response = await page.goto(`${origin}${invoicePath}`);
    assert.equal(response.headers()["cache-control"], "no-store");
    const url = new RegExp(`${invoicePath}\\?invoice=\\d+$`);
    assert.equal(await page.locator("html").getAttribute("dir"), locale.dir);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    for (const [id, value] of Object.entries({
      "saved-amount": locale.total,
      "saved-service-day": "2026-10-11",
      "saved-due-day": locale.date,
      "saved-payment-time": "2026-10-25T03:30",
      "saved-row-7-first": locale.amounts[0],
      "saved-row-7-second": locale.amounts[1],
      "saved-row-42-first": locale.amounts[2],
      "saved-row-42-second": `${locale.amounts[3]} + & <ñ>`,
    })) {
      await page.locator(`#${id}`).fill(value);
    }
    await selectReceipt(page, receipt);
    await reject(page, enhanced, "saved-row-42-first");
    assert.equal(await page.locator("#saved-row-42-second").inputValue(), `${locale.amounts[3]} + & <ñ>`);
    await page.locator("#saved-row-42-second").fill(locale.amounts[3]);
    await selectReceipt(page, receipt);
    await submit(page, 303, enhanced ? "xhr" : "document");
    await page.waitForURL(url);
    await page.locator("#stored-receipt").waitFor();
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.total);
    assert.equal(await page.locator("#saved-due-day").inputValue(), locale.date);
    const editUrl = page.url();

    await page.locator("#saved-amount").fill(locale.edited);
    await selectReceipt(page, badReceipt);
    await reject(page, enhanced, "saved-receipt");
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.edited);
    await Promise.all([
      page.waitForEvent("domcontentloaded"),
      submit(page, 303, enhanced ? "xhr" : "document"),
    ]);
    assert.equal(page.url(), editUrl);
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.edited);
    assert.equal(await page.locator("#saved-row-7-second").inputValue(), locale.amounts[1]);
    assert.equal(await page.locator("#saved-receipt").inputValue(), "");
    await page.reload();
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.edited);
    const storedReceipt = await page.locator("#stored-receipt").innerText();
    const localizedBytes = new Intl.NumberFormat(locale.tag, { useGrouping: false }).format(receipt.length);
    assert(storedReceipt.includes(localizedBytes));
    console.log(`Saved invoice ${locale.tag} ${enhanced ? "enhanced" : "native"}: rejected, corrected, created, edited, reloaded`);
    const { pathname, search } = new URL(editUrl);
    return { path: pathname + search, locale, amount: locale.edited, storedReceipt };
  } finally {
    await context.close();
  }
}

async function verifyCancelledUpload(page, url, amount) {
  const release = Promise.withResolvers();
  await page.route(`**${postPath}`, async route => {
    await release.promise;
    await route.abort().catch(() => {});
  }, { times: 1 });
  try {
    await page.locator("#saved-amount").fill("501.31");
    await selectReceipt(page, Buffer.from("Cancelled replacement receipt\n"));
    await Promise.all([
      page.waitForRequest(request => new URL(request.url()).pathname === postPath && request.method() === "POST"),
      page.locator("form button:not([type=button])").click(),
    ]);
    const cancel = page.getByRole("button", { name: "Cancel upload" });
    await cancel.click();
    await page.getByText("Upload cancelled.", { exact: false }).waitFor();
    assert.equal(await page.locator("#saved-amount").inputValue(), "501.31");
    assert((await page.locator("#saved-receipt").inputValue()).endsWith("receipt.txt"));
  } finally {
    release.resolve();
  }
  await page.goto(url);
  assert.equal(await page.locator("#saved-amount").inputValue(), amount);
  console.log("Cancelled upload: retained local draft and selected file before the request reached the server");
}

async function verifyConcurrentEdit(browser, origin, record) {
  const winner = await browser.newContext({ locale: record.locale.tag });
  const stale = await browser.newContext({ javaScriptEnabled: false, locale: record.locale.tag });
  try {
    const first = await winner.newPage();
    const second = await stale.newPage();
    const url = origin + record.path;
    await Promise.all([first.goto(url), second.goto(url)]);
    await verifyCancelledUpload(first, url, record.amount);
    await first.locator("#saved-amount").fill("601.31");
    await second.locator("#saved-amount").fill("702.31");
    await Promise.all([first.waitForEvent("domcontentloaded"), submit(first, 303, "xhr")]);
    await submit(second, 409, "document");
    await second.getByText("The invoice was changed.", { exact: false }).waitFor();
    assert.equal(await second.locator("#saved-amount").inputValue(), "702.31");
    await second.goto(url);
    assert.equal(await second.locator("#saved-amount").inputValue(), "601.31");
    console.log("Two editors: first save succeeds, stale edit receives 409 and keeps its draft");
    return { ...record, amount: "601.31" };
  } finally {
    await Promise.all([winner.close(), stale.close()]);
  }
}

async function stop(app) {
  if (app?.pid && app.exitCode === null && app.signalCode === null) {
    const exited = once(app, "exit");
    app.kill();
    await exited;
  }
}

async function start(executable, directory) {
  const app = spawn(executable, [], {
    env: { ...process.env, WOGE_INVOICE_DATA: directory, WOGE_INVOICE_PORT: "0" },
  });
  let log = "";
  try {
    const origin = await new Promise((ready, fail) => {
      const timeout = setTimeout(() => fail(new Error("Invoice desk startup timed out")), 30_000);
      app.once("error", error => { clearTimeout(timeout); fail(error); });
      app.once("exit", code => { clearTimeout(timeout); fail(new Error(`Invoice desk exited with ${code}`)); });
      for (const output of [app.stdout, app.stderr]) {
        output.on("data", chunk => {
          log = (log + chunk).slice(-8_000);
          const address = log.match(/Invoice desk ready at (http:\/\/127\.0\.0\.1:\d+)/)?.[1];
          if (address) { clearTimeout(timeout); ready(address); }
        });
      }
    });
    return { app, origin };
  } catch (error) {
    console.error(log);
    await stop(app);
    throw error;
  }
}

async function verifyRestart(browser, origin, records) {
  for (const { path, locale, amount, storedReceipt } of records) {
    const page = await browser.newPage({ locale: locale.tag, javaScriptEnabled: false });
    try {
      const response = await page.goto(origin + path);
      assert.equal(response.status(), 200);
      assert.equal(await page.locator("#saved-amount").inputValue(), amount);
      assert.equal(await page.locator("#stored-receipt").innerText(), storedReceipt);
    } finally {
      await page.close();
    }
  }
  console.log(`Packaged app restart: ${records.length} invoices retain their amounts and receipt metadata`);
}

let scratch, server, browser;
try {
  if (launcher) {
    scratch = await mkdtemp(join(tmpdir(), "woge-invoice-desk-"));
    server = await start(resolve(launcher), scratch);
    const home = await fetch(`${server.origin}/`, { redirect: "manual" });
    assert.equal(home.status, 302);
    assert.equal(home.headers.get("location"), invoicePath);
    const stylesheet = await fetch(`${server.origin}/assets/invoice-desk/invoice.css`);
    assert.equal(stylesheet.status, 200);
    assert.match(stylesheet.headers.get("content-type"), /^text\/css\b/u);
  }
  const origin = server?.origin ?? target;
  browser = await chromium.launch();
  const records = [];
  for (const locale of locales) {
    for (const enhanced of [false, true]) records.push(await verify(browser, origin, locale, enhanced));
  }
  if (server) {
    const index = records.findIndex(({ locale }) => locale.tag === "en-US");
    records[index] = await verifyConcurrentEdit(browser, origin, records[index]);
    await stop(server.app);
    server = await start(resolve(launcher), scratch);
    await verifyRestart(browser, server.origin, records);
  }
} finally {
  await browser?.close();
  await stop(server?.app);
  if (scratch) await rm(scratch, { recursive: true, force: true });
}
