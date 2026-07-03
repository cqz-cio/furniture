import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const adminRoot = new URL("../../yudao电商管理平台前后端/yudao-ui-admin-vue3/", import.meta.url);
const readAdmin = (path) => readFileSync(new URL(path, adminRoot), "utf8");

describe("admin real-account module launch coverage", () => {
  it("keeps real-account admin modules visible in furniture-lite mode", () => {
    const furnitureLiteConfig = readAdmin("src/config/furnitureLite.ts");

    expect(furnitureLiteConfig).toContain("'/member/membership'");
    expect(furnitureLiteConfig).toContain("'/member/gift-registry'");
    expect(furnitureLiteConfig).toContain("'/member/trade-application'");
  });

  it("includes membership, gift registry, and trade admin wiring in the launch admin check", () => {
    const checkScript = readAdmin("scripts/check-furniture-lite-config.mjs");

    [
      "src/api/member/membership/index.ts",
      "src/views/member/membership/index.vue",
      "src/api/member/giftRegistry/index.ts",
      "src/views/member/gift-registry/index.vue",
      "src/api/member/trade/application/index.ts",
      "src/views/member/trade/application/index.vue",
      "/member/membership/page",
      "member:membership:update",
      "/member/gift-registry/page",
      "member:gift-registry:update",
      "/member/trade-application/page",
      "member:trade-application:review",
    ].forEach((token) => {
      expect(checkScript).toContain(token);
    });
  });
});
