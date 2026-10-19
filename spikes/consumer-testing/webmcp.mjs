// Test-only prototype: this file is never served or imported by the application.
import { chromium, expect } from "@playwright/test";
import { writeFileSync, mkdirSync } from "node:fs";
const browser = await chromium.launch({ args: ["--enable-blink-features=WebMCP,WebMCPTesting", "--enable-features=WebMCPTesting"] });
try {
  const page = await browser.newPage();
  await page.goto(process.env.WOGE_TODO_URL);
  const capabilities = await page.evaluate(() => {
    const mc = document.modelContext ?? navigator.modelContext;
    return { secureContext: isSecureContext, modelContext: !!mc, members: mc ? Object.getOwnPropertyNames(Object.getPrototypeOf(mc)) : [],
      testing: navigator.modelContextTesting ? Object.getOwnPropertyNames(Object.getPrototypeOf(navigator.modelContextTesting)) : [] };
  });
  console.log(JSON.stringify({ browser: browser.version(), ...capabilities }));
  if (!capabilities.modelContext) throw new Error("No native WebMCP producer API; do not substitute a polyfill for browser evidence");
  await page.evaluate(() => {
    function annotate() {
      const form = document.querySelector("form#add-todo");
      if (!form || new URL(form.action).pathname !== "/woge-actions/add-todo") return;
      // One explicitly approved business action. No metadata is inferred from all actions.
      if (form.getAttribute("toolname") !== "addTodo") {
        form.setAttribute("toolname", "addTodo");
        form.setAttribute("tooldescription", "Prepare a new shared, process-local Todo. The user reviews and submits the visible form.");
      }
    }
    annotate();
    new MutationObserver(annotate).observe(document.body, { childList: true, subtree: true });
  });
  async function tools() {
    return page.evaluate(async () => {
      const mc = document.modelContext ?? navigator.modelContext;
      const list = mc.getTools ? await mc.getTools() : navigator.modelContextTesting.listTools();
      return list.map(t => ({ name: t.name, inputSchema: typeof t.inputSchema === "string" ? JSON.parse(t.inputSchema) : t.inputSchema }));
    });
  }
  await expect.poll(async () => (await tools()).filter(t => t.name === "addTodo").length).toBe(1);
  const registered = await tools();
  const tool = registered.find(t => t.name === "addTodo");
  if (JSON.stringify(Object.keys(tool.inputSchema.properties).sort()) !== '["title"]') {
    throw new Error(`Unexpected business schema: ${JSON.stringify(tool)}`);
  }
  const title = `WebMCP ${Date.now()}`;
  await page.evaluate(async title => {
    const mc = document.modelContext ?? navigator.modelContext;
    const tool = (await mc.getTools()).find(t => t.name === "addTodo");
    window.wogeInvocation = { state: "pending" };
    mc.executeTool(tool, JSON.stringify({ title })).then(
      value => window.wogeInvocation = { state: "resolved", value },
      error => window.wogeInvocation = { state: "rejected", name: error.name, message: error.message },
    );
  }, title);
  await expect(page.getByLabel("New todo")).toHaveValue(title);
  // The prototype deliberately keeps the documented human-review submission default.
  await page.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("[data-todo-id]").filter({ hasText: title })).toHaveCount(1);
  await expect.poll(async () => (await tools()).filter(t => t.name === "addTodo").length).toBe(1);
  const invocation = await page.evaluate(() => window.wogeInvocation);
  if (invocation.state !== "rejected" || !invocation.message.includes("respondWith")) {
    throw new Error(`Re-evaluate the recorded WebMCP conclusion: ${JSON.stringify(invocation)}`);
  }
  // Exercise real Woge form replacement, then verify registration did not duplicate or become stale.
  await page.getByLabel("New todo").fill("   ");
  await page.getByRole("button", { name: "Add todo", exact: true }).click();
  await expect(page.locator("#todo-errors")).toBeVisible();
  await expect.poll(async () => (await tools()).filter(t => t.name === "addTodo").length).toBe(1);
  await page.locator("form#add-todo").evaluate(form => form.remove());
  await expect.poll(async () => (await tools()).filter(t => t.name === "addTodo").length).toBe(0);
  const result = { browser: browser.version(), capabilities, registered, invocation, mutationVisible: true, replacement: "one tool", removal: "zero tools", actualModelTrials: false };
  mkdirSync("build/evidence", { recursive: true });
  writeFileSync("build/evidence/webmcp.json", JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} finally { await browser.close(); }
