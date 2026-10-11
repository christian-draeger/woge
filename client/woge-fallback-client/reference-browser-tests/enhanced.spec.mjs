import { expect, test } from "../test-support/strict-csp.mjs";

test("automatic reload recovery remains exhausted after the server allocates a fresh epoch", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const firstEpoch = await page.locator('meta[name="woge-page-epoch"]').getAttribute("content");
  expect(await page.evaluate(async () => {
    const { createWogeRecoveryBudget } = await import("/assets/woge/index.js");
    return createWogeRecoveryBudget().tryReload(document.querySelector('meta[name="woge-page-epoch"]').content);
  })).toBe(true);
  await page.reload();
  expect(await page.locator('meta[name="woge-page-epoch"]').getAttribute("content")).not.toBe(firstEpoch);
  expect(await page.evaluate(async () => {
    const { createWogeRecoveryBudget } = await import("/assets/woge/index.js");
    return createWogeRecoveryBudget().tryReload(document.querySelector('meta[name="woge-page-epoch"]').content);
  })).toBe(false);
});

test("safe region GETs preserve latest search intent and bound recovery across hosts", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const result = await page.evaluate(async () => {
    const { createWogePatchRuntime, classifyWogeFailure } = await import("/assets/woge/index.js");
    const observations = [];
    const runtime = createWogePatchRuntime(document, { observer: (event) => observations.push(event) });
    const region = document.querySelector("#board-tasks").closest("[data-woge-region]");
    const target = region.dataset.wogeRegion;
    const url = (context, query = "") => {
      const parts = [context.pageEpoch, target, context.targets[0].baseRevision, context.interactionSequence];
      return `/projects/woge/tasks/regions/${parts.map(encodeURIComponent).join("/")}?query=${encodeURIComponent(query)}`;
    };
    const load = async (context, signal, query) => {
      const response = await fetch(url(context, query), {
        headers: { Accept: "application/vnd.woge.patch-stream; version=1" }, signal,
      });
      if (!response.ok || !response.body) throw new Error(`Refresh failed: ${response.status}`);
      return response.body;
    };
    const older = runtime.beginInteraction([target]);
    const olderBody = await load(older, undefined, "no-matching-task");
    const newer = runtime.beginInteraction([target]);
    const newerBody = await load(newer, undefined, "Review");
    await runtime.applyPatchStream(newerBody);
    const stale = await runtime.applyPatchStream(olderBody);
    const current = region.textContent;
    const refreshed = await runtime.refetchRegion(target, (context, { signal }) => load(context, signal, ""));
    let attempts = 0;
    const failingLoader = async () => { attempts += 1; throw new Error("Safe loader unavailable"); };
    try { await runtime.refetchRegion(target, failingLoader); } catch {}
    let exhausted;
    try { await runtime.refetchRegion(target, failingLoader); } catch (problem) {
      exhausted = classifyWogeFailure(problem);
    }
    const response = await fetch(url(runtime.beginInteraction([target])), { redirect: "manual" });
    return {
      current, stale: stale.stalePatchCount, refreshed: refreshed.patchCount, attempts, exhausted,
      staleDiagnostic: observations.some((event) => event.code === "WOGE_STALE_PATCH"),
      nativeRedirect: response.type === "opaqueredirect",
      revision: region.dataset.wogeRevision,
    };
  });
  expect(result.current).toContain("Review the project");
  expect(result.stale).toBe(1);
  expect(result.staleDiagnostic).toBe(true);
  expect(result.refreshed).toBe(1);
  expect(result.revision).toBe("2");
  expect(result.attempts).toBe(1);
  expect(result.exhausted.code).toBe("WOGE_RESYNC_EXHAUSTED");
  expect(result.nativeRedirect).toBe(true);
});

test("repeated submission while a response is held never replays the mutation", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const title = `Single ${test.info().project.name}`;
  await page.evaluate(() => {
    const originalFetch = window.fetch.bind(window);
    const gate = new Promise((resolve) => { window.releaseAction = resolve; });
    window.actionRequests = 0;
    window.fetch = async (...args) => {
      const response = await originalFetch(...args);
      if (String(args[0]).endsWith("/woge-actions/add-board-task")) {
        window.actionRequests += 1;
        await gate;
      }
      return response;
    };
  });
  const input = page.getByLabel("Task title");
  await input.fill(title);
  await input.press("Enter");
  await expect(page.locator("#board-form")).toHaveAttribute("aria-busy", "true");
  await expect.poll(() => page.evaluate(() => window.actionRequests)).toBe(1);
  await input.press("Enter");
  expect(await page.evaluate(() => window.actionRequests)).toBe(1);
  await page.evaluate(() => window.releaseAction());
  await expect(page.locator("#board-form")).not.toHaveAttribute("aria-busy");
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
  await expect(input).toBeFocused();
  await page.reload();
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
});

test("a new navigation rejects the previous document's deferred stream", async ({ page }) => {
  await page.goto("/projects/woge");
  await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
  const firstEpoch = await page.locator('meta[name="woge-page-epoch"]').getAttribute("content");
  const patchUrl = await page.locator("body").getAttribute("data-woge-patch-url");
  const response = await page.request.get(patchUrl);
  const previousStream = Array.from(await response.body());
  await page.reload();
  await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
  expect(await page.locator('meta[name="woge-page-epoch"]').getAttribute("content")).not.toBe(firstEpoch);
  const error = await page.evaluate(async (bytes) => {
    const { createWogePatchRuntime, classifyWogeFailure } = await import("/assets/woge/index.js");
    const stream = new ReadableStream({
      start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
    });
    try {
      await createWogePatchRuntime(document).applyPatchStream(stream);
      return null;
    } catch (problem) {
      return classifyWogeFailure(problem);
    }
  }, previousStream);
  expect(error).toEqual({ code: "WOGE_STALE_PAGE_EPOCH", category: "stale", outcome: "reload-page" });
  await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
});

test("renders the useful shell before applying every deferred region", async ({ page }) => {
  const browserProblems = [];
  page.on("console", (message) => {
    if (message.type() === "error") browserProblems.push(message.text());
  });

  page.on("pageerror", (problem) => browserProblems.push(problem.message));

  let releasePatchRequest;
  const patchRequestGate = new Promise((resolve) => {
    releasePatchRequest = resolve;
  });
  await page.route("**/projects/woge/woge-patches/*", async (route) => {
    await patchRequestGate;
    await route.continue();
  });

  const response = await page.goto("/projects/woge");
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("text/html");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Woge");
  await expect(page.locator('[data-woge-region][data-woge-revision="0"]')).toHaveCount(3);
  await expect(page.getByText("Loading from the server…")).toHaveCount(3);
  await expect(page.locator('link[rel="stylesheet"]')).toHaveAttribute("href", "/assets/application.css");
  await expect(page.locator('script[type="module"]')).toHaveAttribute("src", "/assets/application.js");

  releasePatchRequest();

  await expect(page.locator('[data-woge-region][data-woge-revision="1"]')).toHaveCount(3);
  await expect(page.getByRole("table", { name: "Current tasks for Woge" })).toBeVisible();
  await expect(page.getByText("Server adapter contract verified")).toBeVisible();
  await expect(page.getByText("Loading from the server…")).toHaveCount(0);
  await expect(page.locator(".is-loading")).toHaveCount(0);
  expect(browserProblems).toEqual([]);
});

test("one action updates the authoritative board regions repeatedly without moving focus", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const title = `Enhanced ${test.info().project.name}`;
  const count = Number((await page.locator("#task-count").textContent()).split(" ")[0]);
  const input = page.getByLabel("Task title");
  await input.fill(title);
  await input.press("Enter");
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
  await expect(page.locator("#task-count")).toHaveText(`${count + 1} tasks`);
  await expect(input).toBeFocused();
  await expect(input).toHaveValue(title);
  await expect(page.locator("#board-status")).toHaveText("Task added");
  await expect(page.locator("[data-woge-revision='1']:not(#board-activity)")).toHaveCount(4);
  await input.fill(`${title} again`);
  await input.press("Enter");
  await expect(page.locator("#task-count")).toHaveText(`${count + 2} tasks`);
  await expect(page.locator("[data-woge-revision='2']:not(#board-activity)")).toHaveCount(4);
  await expect(input).toBeFocused();
  await page.reload();
  await expect(page.locator("#task-count")).toHaveText(`${count + 2} tasks`);
  await expect(page.locator("#board-tasks li", { hasText: `${title} again` })).toHaveCount(1);
});

test("the board rejects absent and foreign Origin before command execution", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const before = await page.locator("#task-count").textContent();
  const values = await page.locator('input[type="hidden"]').evaluateAll((fields) =>
    Object.fromEntries(fields.map((field) => [field.name, field.value])));
  for (const origin of [undefined, "https://foreign.invalid"]) {
    const response = await page.request.post("/woge-actions/add-board-task", {
      form: { ...values, title: "Forged task" },
      headers: origin ? { Origin: origin } : {},
    });
    expect(response.status()).toBe(403);
  }
  await page.reload();
  await expect(page.locator("#task-count")).toHaveText(before);
  await expect(page.locator("#board-tasks li", { hasText: "Forged task" })).toHaveCount(0);
});

test("stale board versions fail without replay or losing the editable input", async ({ page }) => {
  await page.goto("/projects/woge/tasks");
  const before = await page.locator("#task-count").textContent();
  await page.locator('input[name="version"]').evaluate((field) => { field.value = "-1"; });
  const input = page.getByLabel("Task title");
  await input.fill("Stale task");
  const response = page.waitForResponse((response) =>
    response.request().method() === "POST" && response.url().endsWith("/woge-actions/add-board-task"));
  await input.press("Enter");
  expect((await response).status()).toBe(409);
  await expect(page.locator("#board-alert")).toHaveText(
    "Task was not added. Reload the board before trying again.");
  await expect(input).toBeFocused();
  await expect(input).toHaveValue("Stale task");
  await expect(page.locator("#board-form")).not.toHaveAttribute("aria-busy");
  await expect(page.locator("#task-count")).toHaveText(before);
  await page.reload();
  await expect(page.locator("#task-count")).toHaveText(before);
});

test("a task from another visitor shows one silent live notice without moving focus", async ({ page, context }) => {
  const connected = page.waitForResponse((response) => response.url().includes("/projects/woge/tasks/live/"));
  await page.goto("/projects/woge/tasks");
  expect((await connected).headers()["content-type"]).toContain("text/event-stream");
  const input = page.getByLabel("Task title");
  await input.focus();
  await input.fill("Draft stays");
  const other = await context.newPage();
  await other.goto("/projects/woge/tasks");
  const title = `Live ${test.info().project.name}`;
  const otherInput = other.getByLabel("Task title");
  await otherInput.fill(title);
  await otherInput.press("Enter");
  await expect(other.locator("#board-status")).toHaveText("Task added");

  const notice = page.locator("#board-activity");
  await expect(notice).toHaveAttribute("role", "status");
  await expect(notice).toHaveText("1 new task was added. Show the latest board");
  await expect(input).toBeFocused();
  await expect(input).toHaveValue("Draft stays");
  await expect(other.locator("#board-activity")).toHaveText("");

  await notice.getByRole("link", { name: "Show the latest board" }).click();
  await expect(page.locator("#board-tasks li", { hasText: title })).toHaveCount(1);
  await expect(page.locator("#board-activity")).toHaveText("");
});
