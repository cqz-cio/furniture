import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (absolutePath) => readFileSync(absolutePath, "utf8").replace(/\r\n/g, "\n");

describe("backend product favorite SQL migration", () => {
  it("adds SKU-level wishlist snapshot columns to product_favorite", () => {
    const sql = readSource(
      "D:/code/yudao电商管理平台前后端/yudao-cloud/sql/mysql/product-favorite-sku-wishlist.sql",
    );

    for (const column of [
      "ADD COLUMN `sku_id`",
      "ADD COLUMN `count`",
      "ADD COLUMN `spu_name`",
      "ADD COLUMN `pic_url`",
      "ADD COLUMN `price`",
      "ADD COLUMN `market_price`",
      "ADD COLUMN `color`",
      "ADD COLUMN `fabric`",
      "ADD COLUMN `width`",
      "ADD COLUMN `delivery`",
      "ADD COLUMN `dimensions`",
    ]) {
      expect(sql).toContain(column);
    }
    expect(sql).toContain("idx_product_favorite_user_spu_sku");
  });
});
