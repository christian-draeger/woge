import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { fileURLToPath } from "node:url";
import { test, expect } from "@playwright/test";

const fixtureDirectory = fileURLToPath(new URL("../fixtures/", import.meta.url));
const html = await readFile(`${fixtureDirectory}/modern.html`);
const css = await readFile(`${fixtureDirectory}/modern.css`);

let server;
let origin;

test.beforeAll(async () => {
  server = createServer((request, response) => {
    if (request.url === "/") {
      response.writeHead(200, { "content-type": "text/html; charset=utf-8" });
      response.end(html);
      return;
    }
    if (request.url === "/modern.css") {
      response.writeHead(200, { "content-type": "text/css; charset=utf-8", "cache-control": "public, max-age=31536000, immutable" });
      response.end(css);
      return;
    }
    response.writeHead(404);
    response.end();
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
});

test.afterAll(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

test("applies current platform CSS from external, page and declaration sources", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(origin);

  const result = await page.locator(".card").evaluate((element) => {
    const style = getComputedStyle(element);
    const owner = getComputedStyle(element.parentElement);
    return {
      boxSizing: style.boxSizing,
      backgroundColor: style.backgroundColor,
      paddingBlockStart: style.paddingBlockStart,
      borderStartStartRadius: style.borderStartStartRadius,
      outlineColor: style.outlineColor,
      gridTemplateColumns: style.gridTemplateColumns,
      modernSupports: style.getPropertyValue("--woge-supports-modern-color").trim(),
      inlineCustomProperty: style.getPropertyValue("--inline-accent").trim(),
      caretColor: style.caretColor,
      animationDurationSeconds: style.animationDuration.endsWith("ms")
        ? Number.parseFloat(style.animationDuration) / 1000
        : Number.parseFloat(style.animationDuration),
      containerType: owner.containerType,
      marginInlineStart: owner.marginInlineStart,
      viewTransitionName: owner.viewTransitionName
    };
  });

  expect(result.boxSizing).toBe("border-box");
  expect(result.backgroundColor).toBe("rgb(1, 2, 3)");
  expect(result.paddingBlockStart).toBe("9px");
  expect(result.borderStartStartRadius).toBe("7px");
  expect(result.outlineColor).toBe("rgb(0, 0, 255)");
  expect(result.gridTemplateColumns).not.toBe("none");
  expect(result.modernSupports).toBe("yes");
  expect(result.inlineCustomProperty).toBe("rgb(4 5 6)");
  expect(result.caretColor).toBe("rgb(4, 5, 6)");
  expect(result.animationDurationSeconds).toBeCloseTo(0.000001, 9);
  expect(result.containerType).toBe("inline-size");
  expect(result.marginInlineStart).toBe("12px");
  expect(result.viewTransitionName).toBe("woge-dashboard");
});

test("keeps Tailwind-like utility classes, semantic HTML and scope attributes independent", async ({ page }) => {
  await page.goto(origin);

  const result = await page.locator("article").evaluate((element) => ({
    tag: element.localName,
    classes: [...element.classList],
    scope: element.getAttribute("data-woge-scope"),
    region: element.closest("[data-woge-region]")?.getAttribute("data-woge-region") ?? null
  }));

  expect(result).toEqual({
    tag: "article",
    classes: ["card", "tw-bg", "page-literal"],
    scope: "w-demo",
    region: null
  });
});
