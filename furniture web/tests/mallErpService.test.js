import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const root = new URL(
  "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-erp/yudao-module-erp-server/src/main/java/cn/iocoder/yudao/module/erp/",
  import.meta.url,
);
const repositoryRoot = new URL("../../", import.meta.url);

const file = (path) => new URL(path, root);

describe("mall ERP product synchronization service", () => {
  it("persists tenant-safe mappings and uses stable SKU product codes", () => {
    const serviceUrl = file("service/integration/MallErpProductSyncServiceImpl.java");
    expect(existsSync(serviceUrl)).toBe(true);
    const service = readFileSync(serviceUrl, "utf8");
    expect(service).toContain("MallErpProductCodeGenerator productCodeGenerator");
    expect(service).toContain("TenantContextHolder.getRequiredTenantId(), sku.getId()");
    expect(service).toContain("productSkuApi.getSku(mallSkuId).getCheckedData()");
    expect(service).toContain("productSpuApi.getSpu(mallSpuId).getCheckedData()");
    expect(service).toContain("mappingMapper.selectByMallSkuId(mallSkuId)");
    expect(service).toContain("erpStockMapper.selectSumByProductId");
    expect(service).not.toContain("erpProductMapper.insert(");
    expect(service).not.toContain("resolveErpCategory(spu)");
    expect(service).not.toMatch(/getName\(\).*select|select.*getName\(\)/);
  });

  it("exposes stable mall category identity to both storefront and ERP synchronization", () => {
    const controller = readFileSync(new URL(
      "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuController.java",
      repositoryRoot,
    ), "utf8");
    const dto = readFileSync(new URL(
      "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-api/src/main/java/cn/iocoder/yudao/module/product/api/spu/dto/ProductSpuRespDTO.java",
      repositoryRoot,
    ), "utf8");

    expect(controller).toContain("overlayCategoryDetails");
    expect(controller).toContain("setCategoryName");
    expect(controller).toContain("setCategoryCode");
    expect(controller).toContain("setCategoryParentId");
    expect(dto).toContain("private String categoryName;");
  });

  it("defines mapping and sanitized sync-log persistence", () => {
    [
      "dal/dataobject/integration/MallErpProductMappingDO.java",
      "dal/dataobject/integration/MallErpSyncLogDO.java",
      "dal/mysql/integration/MallErpProductMappingMapper.java",
      "dal/mysql/integration/MallErpSyncLogMapper.java",
      "api/integration/MallErpProductApiImpl.java",
    ].forEach((path) => expect(existsSync(file(path)), path).toBe(true));
  });
});
