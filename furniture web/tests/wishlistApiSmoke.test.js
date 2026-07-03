import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const wishlistSmokeScriptPath = new URL("../scripts/wishlist-api-smoke.mjs", import.meta.url);

const runWishlistSmokeFailure = (env) => {
  try {
    execFileSync("node", ["scripts/wishlist-api-smoke.mjs"], {
      cwd: process.cwd(),
      encoding: "utf8",
      env: { ...process.env, ...env },
      stdio: ["ignore", "pipe", "pipe"],
      timeout: 5000,
    });
    throw new Error("wishlist smoke unexpectedly passed");
  } catch (error) {
    return `${error.stdout || ""}${error.stderr || ""}${error.message || ""}`;
  }
};

describe("wishlist API smoke test entry", () => {
  it("exposes a repeatable wishlist API smoke command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:smoke:wishlist"]).toBe("node scripts/wishlist-api-smoke.mjs");
    expect(existsSync(wishlistSmokeScriptPath)).toBe(true);
  });

  it("covers the launch-critical favorite API sequence", () => {
    const source = readFileSync(wishlistSmokeScriptPath, "utf8");

    expect(source).toContain("WISHLIST_SMOKE_MODE");
    expect(source).toContain("YUDAO_SMOKE_TOKEN");
    expect(source).toContain("/product/favorite/create");
    expect(source).toContain("/product/favorite/page");
    expect(source).toContain("/product/favorite/update-count");
    expect(source).toContain("/product/favorite/delete");
    expect(source).toContain("spuId: 910001");
    expect(source).toContain("skuId: 91000101");
  });

  it("runs in mock mode without a live Yudao backend", () => {
    const output = execFileSync("node", ["scripts/wishlist-api-smoke.mjs"], {
      cwd: process.cwd(),
      encoding: "utf8",
      env: { ...process.env, WISHLIST_SMOKE_MODE: "mock" },
      stdio: ["ignore", "pipe", "pipe"],
    });

    expect(output).toContain("Wishlist API smoke passed");
    expect(output).toContain("mode=mock");
  });

  it("rejects non-launch base URLs in live mode before making requests", () => {
    const localhostOutput = runWishlistSmokeFailure({
      WISHLIST_SMOKE_MODE: "live",
      YUDAO_SMOKE_BASE_URL: "http://127.0.0.1:48080/app-api",
      YUDAO_SMOKE_TOKEN: "launch-token",
    });
    expect(localhostOutput).toContain("YUDAO_SMOKE_BASE_URL must not point to localhost");

    const exampleOutput = runWishlistSmokeFailure({
      WISHLIST_SMOKE_MODE: "live",
      YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      YUDAO_SMOKE_TOKEN: "launch-token",
    });
    expect(exampleOutput).toContain("YUDAO_SMOKE_BASE_URL must not use a documentation/example domain");
  });

  it("rejects placeholder tokens in live mode before making requests", () => {
    const output = runWishlistSmokeFailure({
      WISHLIST_SMOKE_MODE: "live",
      YUDAO_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_SMOKE_TOKEN: "<real-app-user-token>",
    });

    expect(output).toContain("YUDAO_SMOKE_TOKEN must be a real live token");
  });
});
