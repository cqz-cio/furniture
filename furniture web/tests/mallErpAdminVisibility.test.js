import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");

describe("mall ERP admin visibility", () => {
  it("exposes permission-protected product mapping and sync operations", () => {
    const controller = read("yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/admin/spu/ProductSpuController.java");
    expect(controller).toContain("getErpIntegration");
    expect(controller).toContain("syncErpIntegration");
    expect(controller).toContain("syncAllErpIntegrations");
    expect(controller).toContain("@PreAuthorize");
  });

  it("shows ERP code, state, stock and last sync with manual sync actions", () => {
    const api = read("yudao电商管理平台前后端/yudao-ui-admin-vue3/src/api/mall/product/spu.ts");
    const view = read("yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/product/spu/index.vue");
    ["erpProductCode", "syncStatus", "sellableStock", "lastSyncedAt"].forEach((field) =>
      expect(api + view).toContain(field));
    expect(api).toContain("/product/spu/erp-integration");
    expect(view).toContain("handleErpSync");
    expect(view).toContain("handleErpSyncAll");
  });
});
