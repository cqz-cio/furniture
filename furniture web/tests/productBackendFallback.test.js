import { describe, expect, it } from "vitest";
import {
  canUseProductDemoFallback,
  resolveProductBackendFailure,
} from "../src/services/productBackendFallback.js";

describe("product backend fallback policy", () => {
  it("allows demo catalog fallback only outside production", () => {
    expect(canUseProductDemoFallback({ DEV: true, PROD: false })).toBe(true);
    expect(canUseProductDemoFallback({ DEV: false, PROD: false })).toBe(true);
    expect(canUseProductDemoFallback({ DEV: false, PROD: true })).toBe(false);
  });

  it("keeps demo products in development but returns an error state in production", () => {
    const demoProducts = [{ id: 1, name: "Demo Sofa" }];

    expect(resolveProductBackendFailure({ env: { DEV: true, PROD: false }, demoProducts })).toEqual({
      source: "demo",
      products: demoProducts,
      error: false,
    });

    expect(resolveProductBackendFailure({ env: { DEV: false, PROD: true }, demoProducts })).toEqual({
      source: "error",
      products: [],
      error: true,
    });
  });
});
