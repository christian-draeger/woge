import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { runInNewContext } from "node:vm";
import test from "node:test";

const source = await readFile(new URL(
  "../../../modules/woge-ui-headless/src/main/resources/static/assets/woge-ui/upload.js", import.meta.url,
), "utf8");

function fixture() {
  const attrs = (initial = {}) => ({
    attributes: new Map(Object.entries(initial)),
    getAttribute(name) { return this.attributes.get(name) ?? null; },
    setAttribute(name, value) { this.attributes.set(name, value); },
    removeAttribute(name) { this.attributes.delete(name); },
    hasAttribute(name) { return this.attributes.has(name); },
  });
  const status = { textContent: "" };
  const progress = attrs();
  const cancel = { ...attrs(), hidden: true,
    addEventListener(_, listener) { this.click = listener; },
    removeEventListener() { this.click = undefined; },
  };
  const panel = { hidden: true, dataset: {
    uploadStart: "Uploading", uploadCancelled: "Cancelled", uploadFailed: "Failed", uploadComplete: "Done",
  }, querySelector(selector) {
    return selector === "progress" ? progress : selector === '[role="status"]' ? status : cancel;
  } };
  class Form {
    constructor() { Object.assign(this, attrs({
      "data-woge-upload": "progress", method: "post", enctype: "multipart/form-data", action: "/upload",
    })); }
    contains(node) { return node === panel; }
    closest() { return null; }
  }
  const form = new Form();
  const submitter = attrs();
  const handlers = {};
  const requests = [];
  let now = 0;
  const location = { origin: "https://example.test", assign(url) { this.destination = url; } };
  class Xhr {
    constructor() { this.upload = {}; requests.push(this); }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader(name, value) { this[name] = value; }
    send(data) { this.data = data; }
    abort() { this.onabort(); }
  }
  class Data { constructor(owner, button) { this.form = owner; this.submitter = button; } }
  runInNewContext(source, {
    document: {
      URL: "https://example.test/form", baseURI: "https://example.test/form",
      addEventListener(name, fn) { handlers[name] = fn; },
      getElementById() { return panel; }, querySelector() { return null; },
    },
    addEventListener(name, fn) { handlers[name] = fn; },
    HTMLFormElement: Form, XMLHttpRequest: Xhr, FormData: Data, URL, location,
    performance: { now: () => now },
  });
  const submit = () => {
    const event = { target: form, submitter, defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; } };
    handlers.submit(event);
    return event;
  };
  return { form, submitter, submit, panel, progress, status, cancel, requests, handlers, location,
    time(value) { now = value; } };
}

test("multipart progress is separate from Patch IR and polite speech is throttled", () => {
  const f = fixture();
  assert.equal(f.submit().defaultPrevented, true);
  const xhr = f.requests[0];
  assert.equal(xhr.method, "POST");
  assert.equal(xhr.Accept, "text/html");
  assert.equal(xhr.data.form, f.form);
  assert.equal(xhr.data.submitter, f.submitter);
  assert.equal(f.panel.hidden, false);
  assert.equal(f.status.textContent, "Uploading");
  assert.equal(f.form.getAttribute("aria-busy"), "true");
  assert.equal(f.submitter.getAttribute("aria-disabled"), "true");
  f.time(200);
  xhr.upload.onprogress({ lengthComputable: true, loaded: 2, total: 10 });
  assert.equal(f.progress.value, 20);
  assert.equal(f.status.textContent, "Uploading");
  f.time(1000);
  xhr.upload.onprogress({ lengthComputable: true, loaded: 5, total: 10 });
  assert.equal(f.status.textContent, "Uploading 50%");
  f.time(1500);
  xhr.upload.onprogress({ lengthComputable: true, loaded: 7, total: 10 });
  assert.equal(f.status.textContent, "Uploading 50%");
  f.submit();
  assert.equal(f.requests.length, 1);
  f.cancel.click();
  assert.equal(f.status.textContent, "Cancelled");
  assert.equal(f.form.getAttribute("aria-busy"), null);
  assert.equal(f.submitter.getAttribute("aria-disabled"), null);
  assert.equal(f.cancel.hidden, true);
  assert.equal(f.form.getAttribute("data-woge-upload"), null);
  assert.equal(f.submit().defaultPrevented, false);
  xhr.upload.onprogress({ lengthComputable: true, loaded: 10, total: 10 });
  assert.equal(f.status.textContent, "Cancelled");
});

test("failures restore a native form and success follows the normal redirect GET", () => {
  for (const outcome of ["error", "http", "success", "pagehide"]) {
    const f = fixture();
    f.submit();
    const xhr = f.requests[0];
    if (outcome === "error") xhr.onerror();
    else if (outcome === "pagehide") f.handlers.pagehide();
    else {
      xhr.status = outcome === "success" ? 200 : 400;
      xhr.responseURL = "https://example.test/complete";
      xhr.onload();
    }
    assert.equal(f.form.getAttribute("aria-busy"), null);
    assert.equal(f.form.getAttribute("enctype"), "multipart/form-data");
    assert.equal(f.form.getAttribute("action"), "/upload");
    assert.equal(f.requests.length, 1);
    if (outcome === "success") assert.equal(f.location.destination, "https://example.test/complete");
    else assert.equal(f.form.hasAttribute("data-woge-upload"), false);
  }
});

test("unsupported submission remains entirely native", () => {
  for (const [name, value] of [["method", "get"], ["enctype", "text/plain"],
    ["action", "https://other.test/upload"], ["target", "_blank"]]) {
    const f = fixture();
    f.form.setAttribute(name, value);
    assert.equal(f.submit().defaultPrevented, false);
    assert.equal(f.requests.length, 0);
    assert.equal(f.panel.hidden, true);
  }
});
