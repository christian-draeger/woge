import { fail } from "./protocol.js";

const policies = new WeakMap();

// Private to patch parsing: never expose a general-purpose HTML or default policy.
export function parsePatchHtml(document, html) {
  if (typeof html !== "string") fail("WOGE_INVALID_HTML", "Patch HTML must be a string");
  const template = document.createElement("template");
  const factory = document.defaultView?.trustedTypes;
  if (factory) {
    try {
      let policy = policies.get(factory);
      if (!policy) {
        policy = factory.createPolicy("woge", { createHTML: (payload) => payload });
        policies.set(factory, policy);
      }
      template.innerHTML = policy.createHTML(html);
    } catch {
      fail("WOGE_TRUSTED_TYPES_POLICY", "Trusted Types patch parsing requires the named woge HTML policy");
    }
  } else {
    template.innerHTML = html;
  }
  return template;
}
