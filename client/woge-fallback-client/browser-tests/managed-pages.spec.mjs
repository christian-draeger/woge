import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

const metadata = '<meta name="woge-page-epoch" content="managed-epoch"><meta name="woge-render-session" content="1">';
const module = '<script type="module" src="/woge-fallback.js"></script>';
const mediaType = "application/vnd.woge.patch-stream; version=1";

test("one page URL and the shipped module connect SSE and refresh without application JavaScript", async ({ page }) => {
  const requests = [];
  await page.route("**/managed-page**", async (route) => {
    const url = new URL(route.request().url());
    requests.push(url);
    if (url.searchParams.has("_woge_live")) {
      await route.fulfill({ contentType: "text/event-stream", body: "retry: 60000\n\nevent: invalidate\ndata: summary-1\n\n" });
    } else if (url.searchParams.has("_woge_refresh")) {
      await route.fulfill({ headers: { "content-type": mediaType }, body: Buffer.from(encodeStream([
        patchFrame({
          epoch: url.searchParams.get("_woge_epoch"), target: url.searchParams.get("_woge_target"),
          baseRevision: Number(url.searchParams.get("_woge_revision")),
          nextRevision: Number(url.searchParams.get("_woge_revision")) + 1,
          interactionSequence: Number(url.searchParams.get("_woge_interaction")), html: "Current live data",
        }), completeFrame(),
      ])) });
    } else {
      await route.fulfill({ contentType: "text/html", body: `<!doctype html><html><head>${metadata}${module}</head><body>
        <section data-woge-region="summary-1" data-woge-revision="0" data-woge-live-url="/managed-page"
          data-woge-live-epoch="managed-epoch">Initial data</section></body></html>` });
    }
  });
  await page.goto("/managed-page");
  await expect(page.locator("section")).toHaveText("Current live data");
  await expect(page.locator("section")).toHaveAttribute("data-woge-revision", "1");
  const subscription = requests.find((url) => url.searchParams.has("_woge_live"));
  expect(subscription.searchParams.get("_woge_protocol_version")).toBe("1");
  const refresh = requests.find((url) => url.searchParams.has("_woge_refresh"));
  expect(refresh.searchParams.get("_woge_epoch")).toBe("managed-epoch");
  expect(refresh.searchParams.get("_woge_interaction")).toBe("1");
});

test("managed action forms automatically send updated ordering headers without hidden command fields", async ({ page }) => {
  const submissions = [];
  await page.route("**/managed-form", (route) => route.fulfill({ contentType: "text/html", body:
    `<!doctype html><html><head>${metadata}${module}</head><body>
      <section data-woge-region="summary-1" data-woge-revision="0">Initial</section>
      <p id="status" role="status" data-woge-region="status-1" data-woge-revision="0"></p>
      <p id="alert" role="alert"></p>
      <form method="post" action="/managed-action" data-woge-action data-woge-status="status"
        data-woge-alert="alert" data-woge-failure-message="Not saved">
        <input name="text" value="Note"><button>Save</button></form></body></html>` }));
  await page.route("**/managed-action", async (route) => {
    const request = route.request();
    submissions.push(request);
    await route.continue();
  });
  await page.goto("/managed-form");
  for (const count of [1, 2]) {
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.locator("section")).toHaveText(`Notes: ${count}`);
    await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  }
  expect(submissions.map((request) => request.headers()["woge-interaction"])).toEqual(["1", "2"]);
  expect(submissions[1].headers()["woge-region-revisions"]).toContain("summary-1:1");
  expect(submissions.map((request) => request.postData())).toEqual(["text=Note", "text=Note"]);
  await expect(page.getByRole("alert")).toBeEmpty();
});
