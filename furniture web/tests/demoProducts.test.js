import { describe, expect, it } from "vitest";
import { demoProducts } from "../src/data/demoProducts.js";

describe("offline demo product catalog", () => {
  it("gives every product a distinct HTTPS cover and gallery", () => {
    expect(new Set(demoProducts.map((product) => product.cover)).size).toBe(demoProducts.length);
    demoProducts.forEach((product) => {
      expect(product.cover).toMatch(/^https:\/\//);
      expect(product.gallery.length).toBeGreaterThanOrEqual(2);
      expect(product.gallery).toContain(product.cover);
      expect(product.gallery.every((url) => /^https:\/\//.test(url))).toBe(true);
    });
  });

  it("keeps commercially coherent prices and inventory", () => {
    demoProducts.forEach((product) => {
      expect(product.price).toBeGreaterThan(0);
      expect(product.marketPrice).toBeGreaterThan(product.price);
      expect(product.stock).toBeGreaterThanOrEqual(0);
    });
  });
});
