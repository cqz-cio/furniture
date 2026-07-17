import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Oakved 2026 Logo asset", () => {
  it("ships an optimized RGBA PNG for high-density web displays", () => {
    const png = readFileSync(new URL("../public/assets/brand/oakved-logo-2026-white.png", import.meta.url));
    expect(png.subarray(1, 4).toString("ascii")).toBe("PNG");
    expect(png.readUInt32BE(16)).toBe(2112);
    expect(png.readUInt32BE(20)).toBeGreaterThan(550);
    expect(png.readUInt32BE(20)).toBeLessThan(700);
    expect(png[25]).toBe(6);
    expect(png.byteLength).toBeLessThan(250_000);
  });
});
