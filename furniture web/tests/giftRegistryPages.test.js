import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);

describe("gift registry pages and routes", () => {
  it("adds create, find and manage registry pages", () => {
    ["GiftRegistryCreatePage.vue", "GiftRegistryFindPage.vue", "GiftRegistryManagePage.vue"].forEach((fileName) => {
      expect(existsSync(pagePath(fileName)), `${fileName} should exist`).toBe(true);
    });
  });

  it("registers gift registry routes in App.vue", () => {
    const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");

    expect(app).toContain('"gift-registry-create": "/gift-registry/create"');
    expect(app).toContain('"gift-registry-find": "/gift-registry/find"');
    expect(app).toContain('"gift-registry-manage": "/gift-registry/manage"');
  });

  it("uses the gift registry model from the create page", () => {
    const source = readFileSync(pagePath("GiftRegistryCreatePage.vue"), "utf8");

    expect(source).toContain("createGiftRegistryDraft");
    expect(source).toContain("getGiftRegistrySteps");
    expect(source).toContain("getRegistryShareState");
  });
});
