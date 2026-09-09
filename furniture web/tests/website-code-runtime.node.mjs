import test from "node:test";
import assert from "node:assert/strict";
import { snippetRequirements, hasSnippetConsent, createWebsiteCodeRuntime } from "../src/services/websiteCodeRuntime.js";
const element = (tagName, attrs = {}) => ({ nodeType: 1, tagName, getAttribute: key => attrs[key] ?? null });
test("tracking code waits for both categories unless explicitly categorized", () => {
  const defaults = snippetRequirements(element("SCRIPT"));
  assert.equal(hasSnippetConsent(defaults, { analytics: true }), false);
  assert.equal(hasSnippetConsent(defaults, { analytics: true, marketing: true }), true);
  assert.deepEqual(snippetRequirements(element("SCRIPT", { "data-cms-consent": "analytics" })), ["analytics"]);
  assert.deepEqual(snippetRequirements(element("SCRIPT", { "data-cms-consent": "unknown" })), ["analytics", "marketing"]);
});
test("verification and inert metadata can load without tracking consent", () => {
  for (const node of [element("META"), element("STYLE"), element("SCRIPT", { type: "application/ld+json" })]) {
    assert.deepEqual(snippetRequirements(node), []);
  }
  assert.deepEqual(snippetRequirements(element("SCRIPT", { "data-cms-consent": "necessary" })), []);
});
test("an unavailable configuration cannot break the site", async () => {
  const errors = [];
  const runtime = createWebsiteCodeRuntime({ document: {}, loadPublished: () => { throw new Error("offline"); }, onError: e => errors.push(e.message) });
  await runtime.ready;
  assert.deepEqual(errors, ["offline"]);
  await runtime.updateConsent({ analytics: true, marketing: true });
});
test("repeated setup for the same document loads once, documents stay independent", async () => {
  const document = {};
  let calls = 0;
  const options = { document, loadPublished: () => { calls++; return { version: null }; } };
  const first = createWebsiteCodeRuntime(options);
  const second = createWebsiteCodeRuntime(options);
  assert.equal(first, second);
  await first.ready;
  assert.equal(calls, 1);
  const other = createWebsiteCodeRuntime({ ...options, document: {} });
  await other.ready;
  assert.notEqual(first, other);
  assert.equal(calls, 2);
});
