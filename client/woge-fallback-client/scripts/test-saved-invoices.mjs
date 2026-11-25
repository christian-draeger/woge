import assert from "node:assert/strict";
import { chromium } from "playwright";

const origin = process.argv[2];
assert(origin, "Provide the loopback test server origin");
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
    assert.equal(await page.locator("#saved-invoice-errors").count(), 0);
  }
  // Enhanced upload leaves the draft and file in place. A deliberate native retry shows server errors.
  await submit(page, 400, "document");
  await page.locator(`#${errorField}-error`).waitFor();
  assert.equal(await page.locator(`#${errorField}`).getAttribute("aria-errormessage"), `${errorField}-error`);
  assert.equal(await page.locator("#saved-invoice-errors a").getAttribute("href"), `#${errorField}`);
  assert.equal(await page.locator("#saved-receipt").inputValue(), "");
}

async function verify(browser, locale, enhanced) {
  const context = await browser.newContext({ javaScriptEnabled: enhanced, locale: locale.tag });
  try {
    const page = await context.newPage();
    await page.goto(`${origin}${invoicePath}`);
    const url = new RegExp(`${invoicePath}\\?invoice=\\d+$`);
    assert.equal(await page.locator("html").getAttribute("dir"), locale.dir);
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
    await submit(page, 303, enhanced ? "xhr" : "document");
    await page.waitForURL(editUrl);
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.edited);
    assert.equal(await page.locator("#saved-row-7-second").inputValue(), locale.amounts[1]);
    assert.equal(await page.locator("#saved-receipt").inputValue(), "");
    await page.reload();
    assert.equal(await page.locator("#saved-amount").inputValue(), locale.edited);
    console.log(`Saved invoice ${locale.tag} ${enhanced ? "enhanced" : "native"}: rejected, corrected, created, edited, reloaded`);
  } finally {
    await context.close();
  }
}

const browser = await chromium.launch();
try {
  for (const locale of locales) {
    for (const enhanced of [false, true]) await verify(browser, locale, enhanced);
  }
} finally {
  await browser.close();
}
