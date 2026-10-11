import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

const STYLES = {
  css: ["form", "navigation", "data", "complex"].map((name) => `/component-${name}.css`),
  tailwind: ["/component-tailwind.css"],
};
const WCAG = ["wcag2a", "wcag2aa", "wcag21aa"];

async function apply(page, patch) {
  const bytes = encodeStream([patchFrame(patch), completeFrame()]);
  return page.evaluate((bytes) => runtime.applyPatchStream(byteStream(Uint8Array.from(bytes))), Array.from(bytes));
}

async function load(page, variant, path) {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  await page.evaluate(async (path) => {
    const doc = new DOMParser().parseFromString(await (await fetch(path)).text(), "text/html");
    document.head.innerHTML = '<title>Data components</title><meta name="woge-page-epoch" content="epoch-a">';
    document.body.innerHTML = doc.body.innerHTML;
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  }, path);
  for (const href of STYLES[variant]) await page.addStyleTag({ url: href });
}

const fragment = (page, url) => page.evaluate(async (url) => (await fetch(url)).text(), url);
const rowIds = (page) => page.locator("tbody tr").evaluateAll((rows) => rows.map((row) => row.dataset.row));

for (const variant of ["css", "tailwind"]) test.describe(`${variant} data components`, () => {
  test.describe("patches", () => {
    test.beforeEach(async ({ page }) => load(page, variant, `/data/${variant}`));

    test("sort patch keeps selection and focus and never duplicates rows", async ({ page }) => {
      await page.getByLabel("Select Grace Hopper").check();
      await page.getByRole("link", { name: "Role" }).focus();
      await apply(page, { target: "people", html: await fragment(page, `/data-fragment/${variant}?sort=role`) });
      expect(await rowIds(page)).toEqual(["grace", "ada", "linus"]);
      await expect(page.getByLabel("Select Grace Hopper")).toBeChecked();
      await expect(page.getByRole("link", { name: "Role" })).toBeFocused();
      await expect(page.locator('th[aria-sort="ascending"]')).toHaveText("Role");
      await expect(page.locator("[aria-sort]")).toHaveCount(1);
    });

    test("stale replays are ignored and a dropped selected row rejects the whole patch", async ({ page }) => {
      const sorted = await fragment(page, `/data-fragment/${variant}?sort=role`);
      await apply(page, { target: "people", html: sorted });
      await apply(page, { target: "people", html: await fragment(page, `/data-fragment/${variant}?dir=desc`) });
      expect(await rowIds(page)).toEqual(["grace", "ada", "linus"]);
      await expect(page.locator("#people")).toHaveAttribute("data-woge-revision", "1");
      await page.getByLabel("Select Ada Lovelace").check();
      const filtered = await fragment(page, `/data-fragment/${variant}?q=gra`);
      await expect(apply(page, { target: "people", baseRevision: 1, nextRevision: 2, html: filtered })).rejects.toThrow();
      expect(await rowIds(page)).toEqual(["grace", "ada", "linus"]);
      await expect(page.getByLabel("Select Ada Lovelace")).toBeChecked();
      await page.getByLabel("Select Ada Lovelace").uncheck();
      await apply(page, { target: "people", baseRevision: 1, nextRevision: 2, html: filtered });
      expect(await rowIds(page)).toEqual(["grace"]);
    });

    test("loading, stale, partial-error and optimistic states have explicit semantics", async ({ page }) => {
      const panel = page.locator('[data-part="panel"]');
      await expect(panel).toHaveAttribute("data-state", "ready");
      await expect(page.locator('[data-part="notice"]')).toHaveCount(0);
      let revision = 0;
      for (const state of ["loading", "stale", "partial-error", "optimistic"]) {
        const html = await fragment(page, `/data-state/${variant}?state=${state}`);
        await apply(page, { target: "people", baseRevision: revision, nextRevision: ++revision, html });
        await expect(panel).toHaveAttribute("data-state", state);
        await expect(page.locator('[data-part="notice"]')).toHaveCount(1);
        await expect(page.locator('#people [role="alert"], #people [role="status"], #people [aria-live]')).toHaveCount(0);
        await expect(page.getByRole("link", { name: "Refresh" }))
          .toHaveCount(["stale", "partial-error"].includes(state) ? 1 : 0);
        await expect(page.locator('tr[aria-busy="true"]')).toHaveCount(state === "optimistic" ? 1 : 0);
        expect(await rowIds(page)).toEqual(["ada", "alan", "grace"]);
      }
      await expect(page.locator('tr[data-pending="true"]')).toHaveText(/Grace Hopper/);
      const ready = await fragment(page, `/data-fragment/${variant}`);
      await apply(page, { target: "people", baseRevision: revision, nextRevision: ++revision, html: ready });
      await expect(panel).toHaveAttribute("data-state", "ready");
      await expect(panel).not.toHaveAttribute("aria-busy", "true");
      await expect(page.locator('tr[aria-busy="true"]')).toHaveCount(0);
    });

    test("focus on a removed retry link falls back to the stable region root", async ({ page }) => {
      const stale = await fragment(page, `/data-state/${variant}?state=stale`);
      await apply(page, { target: "people", html: stale });
      await page.getByRole("link", { name: "Refresh" }).focus();
      await apply(page, { target: "people", baseRevision: 1, nextRevision: 2, html: await fragment(page, `/data-fragment/${variant}`) });
      await expect(page.locator("#people")).toBeFocused();
    });
  });

  test.describe("semantics and keyboard", () => {
    test.beforeEach(async ({ page }) => load(page, variant, `/data/${variant}`));

    test("table keeps native semantics with row headers, one aria-sort and a focusable scroll region", async ({ page }) => {
      await expect(page.getByRole("table", { name: "People" })).toBeVisible();
      await expect(page.getByRole("rowheader")).toHaveCount(3);
      await expect(page.getByRole("columnheader", { name: "Name" })).toHaveAttribute("aria-sort", "ascending");
      await expect(page.locator("[aria-sort]")).toHaveCount(1);
      await expect(page.getByRole("region", { name: "People" }).last()).toHaveAttribute("tabindex", "0");
    });

    test("keyboard sorts through a link and toggles selection with Space", async ({ page }) => {
      const box = page.getByLabel("Select Ada Lovelace");
      await box.focus();
      await page.keyboard.press("Space");
      await expect(box).toBeChecked();
      await page.getByRole("link", { name: "Name" }).focus();
      await page.keyboard.press("Enter");
      await expect(page).toHaveURL(/\/data\/[a-z]+\?sort=name&dir=desc$/);
    });

    test("mobile viewport scrolls the table region instead of the page", async ({ page }) => {
      await page.setViewportSize({ width: 320, height: 640 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await expect(page.getByRole("link", { name: "Role" })).toBeVisible();
    });

    test("RTL text and logical layout stay correct", async ({ page }) => {
      await page.evaluate(() => { document.documentElement.dir = "rtl"; });
      await expect(page.getByLabel("Select Ada Lovelace")).toBeVisible();
      const [first, last] = await page.locator("thead th").evaluateAll((cells) =>
        [cells[0], cells.at(-1)].map((cell) => cell.getBoundingClientRect().x));
      expect(first).toBeGreaterThan(last);
    });

    test("data pages pass automated accessibility checks", async ({ page }) => {
      expect((await new AxeBuilder({ page }).withTags(WCAG).analyze()).violations).toEqual([]);
      await apply(page, { target: "people", html: await fragment(page, `/data-state/${variant}?state=partial-error`) });
      expect((await new AxeBuilder({ page }).withTags(WCAG).analyze()).violations).toEqual([]);
    });
  });

  test.describe("complex inputs", () => {
    test.beforeEach(async ({ page }) => load(page, variant, `/data-extra/${variant}?kind=complex`));

    test("combobox and date input are native controls", async ({ page }) => {
      const city = page.getByLabel("City");
      expect(await city.evaluate((input) => [input.list.id, input.list.options.length])).toEqual(["city-options", 2]);
      await city.fill("Anywhere else");
      expect(await city.evaluate((input) => input.validity.valid)).toBe(true);
      const day = page.getByLabel("Date");
      await expect(day).toHaveAttribute("type", "date");
      await day.fill("2027-01-01");
      expect(await day.evaluate((input) => input.validity.rangeOverflow)).toBe(true);
      await day.fill("2026-05-01");
      expect(await day.evaluate((input) => input.validity.valid)).toBe(true);
    });

    test("checkbox group and multiple select submit repeated names", async ({ page }) => {
      await page.getByLabel("CSS").check();
      await page.getByLabel("Roles").selectOption(["admin", "user"]);
      const submitted = await page.locator("form").evaluate((form) => {
        const data = new FormData(form);
        return [data.getAll("tags"), data.getAll("roles")];
      });
      expect(submitted).toEqual([["html", "css"], ["admin", "user"]]);
      await expect(page.getByRole("group", { name: "Topics" })).toBeVisible();
      await page.getByLabel("CSS").focus();
      await page.keyboard.press("Space");
      await expect(page.getByLabel("CSS")).not.toBeChecked();
    });

    test("RTL labels and automated accessibility checks", async ({ page }) => {
      await page.evaluate(() => { document.documentElement.dir = "rtl"; });
      const [box, label] = await Promise.all([
        page.getByLabel("كوتلن").boundingBox(), page.getByText("كوتلن").boundingBox(),
      ]);
      expect(box.x).toBeGreaterThan(label.x);
      expect((await new AxeBuilder({ page }).withTags(WCAG).analyze()).violations).toEqual([]);
    });
  });

  test("cards, badges, avatars and empty states pass automated accessibility checks", async ({ page }) => {
    await load(page, variant, `/data-extra/${variant}?kind=cards`);
    await expect(page.getByRole("list", { name: "Team" }).getByRole("listitem")).toHaveCount(5);
    await expect(page.getByRole("img", { name: "Ada Lovelace" }).first()).toBeVisible();
    expect((await new AxeBuilder({ page }).withTags(WCAG).analyze()).violations).toEqual([]);
  });
});

test.describe("no JavaScript", () => {
  test.use({ javaScriptEnabled: false });

  for (const variant of ["css", "tailwind"]) test(`${variant} sort, filter and paging work as links and a GET form`, async ({ page }) => {
    const requests = [];
    page.on("request", (request) => requests.push(request.url()));
    await page.goto(`/data/${variant}`);
    await page.getByRole("link", { name: "Role" }).click();
    await expect(page).toHaveURL(/\/data\/[a-z]+\?sort=role&dir=asc$/);
    expect(await rowIds(page)).toEqual(["grace", "ada", "linus"]);
    await page.getByRole("link", { name: "2", exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
    expect(await rowIds(page)).toEqual(["alan", "katherine"]);
    await page.getByLabel("Name", { exact: true }).fill("gra");
    await page.getByRole("button", { name: "Filter" }).click();
    await expect(page).toHaveURL(/q=gra&sort=role&dir=asc$/);
    expect(await rowIds(page)).toEqual(["grace"]);
    await page.getByLabel("Name", { exact: true }).fill("zzz");
    await page.getByLabel("Name", { exact: true }).press("Enter");
    await expect(page.getByRole("heading", { name: "No people found" })).toBeVisible();
    await page.getByRole("link", { name: "Clear" }).click();
    expect(await rowIds(page)).toEqual(["ada", "alan", "grace"]);
    expect(requests.some((url) => url.endsWith(".js"))).toBe(false);
  });
});
