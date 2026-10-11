import assert from "node:assert/strict";
import { test } from "node:test";
import { installWogeDpuDocument, recordWogeDpuCapability } from "../src/dpu.js";

test("DPU capability is host-only, short-lived and secure on HTTPS", () => {
  const root = {
    defaultView: {
      HTMLTemplateElement: { prototype: { htmlFor: "" } },
      location: { protocol: "https:" },
    },
  };
  assert.equal(recordWogeDpuCapability(root), true);
  assert.equal(root.cookie, "woge_dpu=1; Path=/; Max-Age=86400; SameSite=Lax; Secure");
});

test("unsupported browsers overwrite a previous affirmative signal", () => {
  const root = {
    cookie: "woge_dpu=1",
    defaultView: {
      HTMLTemplateElement: { prototype: {} },
      location: { protocol: "http:" },
    },
  };
  assert.equal(recordWogeDpuCapability(root), false);
  assert.equal(root.cookie, "woge_dpu=0; Path=/; Max-Age=86400; SameSite=Lax");
});

test("capability storage errors propagate rather than silently claiming support", () => {
  const root = {
    defaultView: {
      HTMLTemplateElement: { prototype: {} },
      location: { protocol: "http:" },
    },
    set cookie(value) { throw new Error(`Cookie storage blocked: ${value.length}`); },
  };
  assert.throws(() => recordWogeDpuCapability(root), /Cookie storage blocked/u);
});

test("a document ending without a terminal frame emits a safe truncation diagnostic", async () => {
  const root = Object.assign(new EventTarget(), {
    readyState: "complete",
    querySelectorAll: () => [],
    defaultView: {
      CustomEvent,
      MutationObserver: class {
        observe() {}
        disconnect() {}
      },
      addEventListener() {},
      removeEventListener() {},
    },
  });
  const errors = [];
  root.addEventListener("woge:dpu-error", (event) => errors.push(event.detail.code));
  installWogeDpuDocument(root);
  await Promise.resolve();
  assert.deepEqual(errors, ["WOGE_DPU_TRUNCATED"]);
});

test("repeated installation owns only one document reader", () => {
  let observed = 0;
  const root = Object.assign(new EventTarget(), {
    readyState: "loading",
    querySelectorAll: () => [],
    defaultView: {
      CustomEvent,
      MutationObserver: class {
        observe() { observed++; }
        disconnect() {}
      },
      addEventListener() {},
      removeEventListener() {},
    },
  });
  const installation = installWogeDpuDocument(root);
  assert.equal(installWogeDpuDocument(root), installation);
  assert.equal(observed, 1);
  installation.dispose();
});
