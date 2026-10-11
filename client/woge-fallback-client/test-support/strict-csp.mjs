import { expect, test as base } from "@playwright/test";

import { STRICT_CSP } from "./strict-policy.mjs";

export { STRICT_CSP };

// Auto fixture covers every journey, including violations before or across navigation.
export const test = base.extend({
  strictCsp: [async ({ page }, use) => {
    const policies = [];
    page.on("response", (response) => {
      // Redirects carry no rendered document, so their CSP header is irrelevant.
      const status = response.status();
      if (response.request().resourceType() === "document" && status >= 200 && (status < 300 || status >= 400)) {
        policies.push(response.headers()["content-security-policy"]);
      }
    });
    const violations = [];
    const consoleErrors = [];
    page.on("console", (message) => {
      if (message.type() === "error" && /content.security.policy|trusted.types|trustedhtml/i.test(message.text())) {
        consoleErrors.push(message.text());
      }
    });
    await page.exposeBinding("recordWogeCspViolation", (_, violation) => violations.push(violation));
    await page.addInitScript(() => {
      document.addEventListener("securitypolicyviolation", (event) => {
        window.recordWogeCspViolation(`${event.effectiveDirective}: ${event.blockedURI}`);
      });
    });
    await use();
    expect(policies.length, "at least one strict-policy document").toBeGreaterThan(0);
    for (const policy of policies) expect(policy, "document CSP header").toBe(STRICT_CSP);
    expect(violations, "securitypolicyviolation events").toEqual([]);
    expect(consoleErrors, "console CSP / Trusted Types errors").toEqual([]);
  }, { auto: true }],
});

export { expect };
