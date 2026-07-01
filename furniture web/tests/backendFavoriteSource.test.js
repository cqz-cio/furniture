import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (absolutePath) => readFileSync(absolutePath, "utf8").replace(/\r\n/g, "\n");

const backendRoot =
  "D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product";

describe("backend product favorite SKU wishlist contract", () => {
  it("persists SKU-level wishlist row fields in ProductFavoriteDO", () => {
    const source = readSource(`${backendRoot}/dal/dataobject/favorite/ProductFavoriteDO.java`);

    for (const field of [
      "private Long skuId;",
      "private Integer count;",
      "private String spuName;",
      "private String picUrl;",
      "private Integer price;",
      "private Integer marketPrice;",
      "private String color;",
      "private String fabric;",
      "private String width;",
      "private String delivery;",
      "private String dimensions;",
    ]) {
      expect(source).toContain(field);
    }
  });

  it("accepts and returns SKU-level wishlist snapshot fields in app VO classes", () => {
    const req = readSource(`${backendRoot}/controller/app/favorite/vo/AppFavoriteReqVO.java`);
    const resp = readSource(`${backendRoot}/controller/app/favorite/vo/AppFavoriteRespVO.java`);

    for (const source of [req, resp]) {
      expect(source).toContain("private Long skuId;");
      expect(source).toContain("private Integer count;");
      expect(source).toContain("private String color;");
      expect(source).toContain("private String fabric;");
      expect(source).toContain("private String width;");
      expect(source).toContain("private String delivery;");
      expect(source).toContain("private String dimensions;");
    }
    expect(req).toContain("private String spuName;");
    expect(req).toContain("private String picUrl;");
    expect(req).toContain("private Integer price;");
    expect(req).toContain("private Integer marketPrice;");
  });

  it("creates favorites from the full request object while keeping delete by SPU compatible", () => {
    const controller = readSource(`${backendRoot}/controller/app/favorite/AppFavoriteController.java`);
    const service = readSource(`${backendRoot}/service/favorite/ProductFavoriteService.java`);
    const implementation = readSource(`${backendRoot}/service/favorite/ProductFavoriteServiceImpl.java`);
    const mapper = readSource(`${backendRoot}/dal/mysql/favorite/ProductFavoriteMapper.java`);

    expect(controller).toContain("productFavoriteService.createFavorite(getLoginUserId(), reqVO)");
    expect(service).toContain("Long createFavorite(Long userId, AppFavoriteReqVO reqVO);");
    expect(implementation).toContain("ProductFavoriteConvert.INSTANCE.convert(userId, reqVO)");
    expect(mapper).toContain("selectByUserIdAndSpuIdAndSkuId");
  });

  it("updates SKU-level wishlist quantities through app controller and service", () => {
    const controller = readSource(`${backendRoot}/controller/app/favorite/AppFavoriteController.java`);
    const service = readSource(`${backendRoot}/service/favorite/ProductFavoriteService.java`);
    const implementation = readSource(`${backendRoot}/service/favorite/ProductFavoriteServiceImpl.java`);

    expect(controller).toContain('@PutMapping(value = "/update-count")');
    expect(controller).toContain("productFavoriteService.updateFavoriteCount(getLoginUserId(), reqVO)");
    expect(service).toContain("void updateFavoriteCount(Long userId, AppFavoriteReqVO reqVO);");
    expect(implementation).toContain("favorite.setCount");
    expect(implementation).toContain("productFavoriteMapper.updateById(favorite)");
  });

  it("deletes SKU-level wishlist rows while keeping legacy SPU delete compatible", () => {
    const controller = readSource(`${backendRoot}/controller/app/favorite/AppFavoriteController.java`);
    const service = readSource(`${backendRoot}/service/favorite/ProductFavoriteService.java`);
    const implementation = readSource(`${backendRoot}/service/favorite/ProductFavoriteServiceImpl.java`);

    expect(controller).toContain("productFavoriteService.deleteFavorite(getLoginUserId(), reqVO)");
    expect(service).toContain("void deleteFavorite(Long userId, AppFavoriteReqVO reqVO);");
    expect(implementation).toContain("selectByUserIdAndSpuIdAndSkuId(userId, reqVO.getSpuId(), reqVO.getSkuId())");
    expect(implementation).toContain("deleteFavorite(userId, reqVO)");
  });
});
