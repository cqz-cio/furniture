import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");

const cloud =
  "yudao电商管理平台前后端/yudao-cloud/";
const admin =
  "yudao电商管理平台前后端/yudao-ui-admin-vue3/src/";

describe("tenant business mode ERP contract", () => {
  it("migrates all tenants to B2C by default and initializes the two known tenants", () => {
    const migration = read(
      `${cloud}sql/mysql/migrations/V025__tenant_business_mode.sql`,
    );

    expect(migration).toContain("ADD COLUMN `business_mode` varchar(16) NOT NULL DEFAULT 'B2C'");
    expect(migration).toMatch(/SET `business_mode` = 'B2B'\s+WHERE `id` = 162/);
    expect(migration).toMatch(/SET `business_mode` = 'B2C'\s+WHERE `id` = 121/);
  });

  it("persists and validates businessMode across the tenant model and VOs", () => {
    const tenantDo = read(
      `${cloud}yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/tenant/TenantDO.java`,
    );
    const saveReq = read(
      `${cloud}yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/controller/admin/tenant/vo/tenant/TenantSaveReqVO.java`,
    );
    const resp = read(
      `${cloud}yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/controller/admin/tenant/vo/tenant/TenantRespVO.java`,
    );

    expect(tenantDo).toContain("private String businessMode;");
    expect(saveReq).toContain("private String businessMode;");
    expect(saveReq).toContain("@InEnum(value = TenantBusinessModeEnum.class");
    expect(resp).toContain("private String businessMode;");
  });

  it("derives the current profile exclusively from the effective tenant context", () => {
    const controller = read(
      `${cloud}yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/controller/admin/tenant/TenantController.java`,
    );
    const start = controller.indexOf('@GetMapping("/current-business-profile")');
    const end = controller.indexOf('@PostMapping("/create")', start);
    const profileEndpoint = controller.slice(start, end);

    expect(profileEndpoint).toContain("TenantContextHolder.getRequiredTenantId()");
    expect(profileEndpoint).toContain("TenantBusinessModeEnum.of");
    expect(profileEndpoint).not.toContain("@RequestParam");
    expect(profileEndpoint).not.toContain("@TenantIgnore");
    expect(profileEndpoint).not.toContain("@PermitAll");
  });

  it("uses inventoryEnabled for product columns and the B2B tab set", () => {
    const view = read(`${admin}views/mall/product/spu/index.vue`);

    expect(view.match(/v-if="inventoryEnabled"/g)).toHaveLength(2);
    expect(view).toContain("inventoryEnabled.value ? [0, 1, 2, 3, 4] : [0, 1, 4]");
    expect(view).toContain("'展示中'");
    expect(view).toContain("'未展示'");
    expect(view).toContain("'展示状态'");
  });

  it("keeps SkuList stock-visible by default and protects hidden stock in every mode", () => {
    const skuList = read(`${admin}views/mall/product/spu/components/SkuList.vue`);

    expect(skuList).toContain("showStock: propTypes.bool.def(true)");
    expect(skuList.match(/v-if="showStock"/g)).toHaveLength(3);
    expect(skuList).toContain("if (!props.showStock)");
    expect(skuList).toContain("delete batchValues.stock");
    expect(skuList).toContain("copyValueToTarget(item, batchValues)");
  });

  it("loads one profile at each product top level and passes showStock down", () => {
    const list = read(`${admin}views/mall/product/spu/index.vue`);
    const form = read(`${admin}views/mall/product/spu/form/index.vue`);
    const skuForm = read(`${admin}views/mall/product/spu/form/SkuForm.vue`);
    const hook = read(`${admin}hooks/web/useTenantBusinessProfile.ts`);

    expect(list).toContain("loadTenantBusinessProfile");
    expect(form).toContain("loadTenantBusinessProfile");
    expect(form).toContain(':show-stock="inventoryEnabled"');
    expect(skuForm.match(/:show-stock="showStock"/g)).toHaveLength(3);
    expect(skuForm).toContain("rule.name !== 'stock'");
    expect(hook).toContain("getCurrentTenantBusinessProfile");
    expect(hook).not.toMatch(/\b(121|162)\b/);
  });

  it("does not hardcode known tenant IDs in product frontend business logic", () => {
    const productSource = [
      read(`${admin}views/mall/product/spu/index.vue`),
      read(`${admin}views/mall/product/spu/form/index.vue`),
      read(`${admin}views/mall/product/spu/form/SkuForm.vue`),
      read(`${admin}views/mall/product/spu/components/SkuList.vue`),
      read(`${admin}hooks/web/useTenantBusinessProfile.ts`),
    ].join("\n");

    expect(productSource).not.toMatch(/\b(121|162)\b/);
  });
});
