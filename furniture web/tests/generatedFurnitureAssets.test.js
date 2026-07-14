import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { describe, expect, it } from "vitest";
import { generatedFurnitureAssets } from "../src/data/generatedFurnitureAssets.js";

const projectRoot = dirname(fileURLToPath(new URL("../package.json", import.meta.url)));

const collectAssetPaths = (value) => {
  if (typeof value === "string") return value.startsWith("/assets/") ? [value] : [];
  if (!value || typeof value !== "object") return [];
  return Object.values(value).flatMap(collectAssetPaths);
};

describe("generated furniture assets", () => {
  it("points every generated visual reference at a public asset", () => {
    const assetPaths = collectAssetPaths(generatedFurnitureAssets);

    expect(assetPaths).toContain("/assets/generated-furniture/home-hero-desktop.webp");
    assetPaths.forEach((assetPath) => {
      expect(existsSync(join(projectRoot, "public", assetPath))).toBe(true);
    });
    expect(existsSync(join(projectRoot, "public", "/assets/brand/oakved-logo-black.png"))).toBe(true);
    expect(existsSync(join(projectRoot, "public", "/assets/brand/oakved-logo-white.png"))).toBe(true);
  });
});
