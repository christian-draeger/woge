import assert from "node:assert/strict";
import test from "node:test";
import { classifyWogeFailure } from "../src/recovery.js";
import { parsePatchHtml } from "../src/trusted-html.js";

function documentWith(trustedTypes) {
  const inputs = [];
  return {
    inputs,
    defaultView: { trustedTypes },
    createElement(name) {
      assert.equal(name, "template");
      return { set innerHTML(value) { inputs.push(value); } };
    },
  };
}

test("browsers without Trusted Types parse the original patch payload", () => {
  const document = documentWith(undefined);
  parsePatchHtml(document, "<p>Server output</p>");
  assert.deepEqual(document.inputs, ["<p>Server output</p>"]);
});

test("one lazy named policy wraps only patch strings across runtimes in a realm", () => {
  const creations = [];
  const payloads = [];
  const factory = {
    createPolicy(name, rules) {
      creations.push(name);
      assert.deepEqual(Object.keys(rules), ["createHTML"]);
      return { createHTML(html) {
        payloads.push(html);
        return Object.freeze({ trustedHTML: rules.createHTML(html) });
      } };
    },
  };
  const first = documentWith(factory);
  const second = documentWith(factory);
  assert.deepEqual(creations, []);
  parsePatchHtml(first, "<p>Replace</p>");
  parsePatchHtml(second, "<li>Append</li>");
  assert.deepEqual(creations, ["woge"]);
  assert.deepEqual(payloads, ["<p>Replace</p>", "<li>Append</li>"]);
  assert.deepEqual(first.inputs, [{ trustedHTML: "<p>Replace</p>" }]);
  assert.deepEqual(second.inputs, [{ trustedHTML: "<li>Append</li>" }]);
});

test("policy denial fails closed without attempting the HTML sink or a default policy", () => {
  const document = documentWith({ createPolicy(name) {
    assert.equal(name, "woge");
    throw new TypeError("Policy denied");
  } });
  assert.throws(() => parsePatchHtml(document, "<p>Denied</p>"), { code: "WOGE_TRUSTED_TYPES_POLICY" });
  assert.deepEqual(document.inputs, []);
});

test("a policy from another document realm is not reused", () => {
  for (let i = 0; i < 2; i++) {
    let creations = 0;
    const document = documentWith({ createPolicy() {
      creations++;
      return { createHTML: (html) => html };
    } });
    parsePatchHtml(document, "<p>Own realm</p>");
    assert.equal(creations, 1);
  }
});

test("non-string inputs cannot reach policy creation or the HTML sink", () => {
  const document = documentWith({ createPolicy() { assert.fail("Unexpected policy creation"); } });
  assert.throws(() => parsePatchHtml(document, { toString: () => "<p>bad</p>" }), { code: "WOGE_INVALID_HTML" });
  assert.deepEqual(document.inputs, []);
});

test("TrustedHTML creation and sink errors are security failures, never network retries", () => {
  for (const stage of ["createHTML", "sink"]) {
    const document = documentWith({ createPolicy() {
      return { createHTML(html) {
        if (stage === "createHTML") throw new TypeError("TrustedHTML failed");
        return html;
      } };
    } });
    if (stage === "sink") document.createElement = () => ({
      set innerHTML(_) { throw new TypeError("TrustedHTML required"); },
    });
    assert.throws(() => parsePatchHtml(document, "<p>Rejected</p>"), (problem) => {
      assert.deepEqual(classifyWogeFailure(problem, { safeRequest: true }), {
        code: "WOGE_TRUSTED_TYPES_POLICY", category: "security", outcome: "fail-closed",
      });
      return true;
    });
  }
});
