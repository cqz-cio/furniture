import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BACKEND_SQL_ROOT = "../yudao电商管理平台前后端/yudao-cloud/sql/mysql";

export const buildMigrationChecks = () => [
  {
    name: "product-favorite-sku-wishlist",
    relativePath: `${BACKEND_SQL_ROOT}/product-favorite-sku-wishlist.sql`,
    requiredTokens: [
      "ALTER TABLE `product_favorite`",
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
      "COMMENT 'Product SKU id'",
      "COMMENT 'Favorite quantity'",
      "COMMENT 'SPU name snapshot'",
      "COMMENT 'Product image snapshot'",
      "COMMENT 'Product price snapshot in cents'",
      "COMMENT 'Market price snapshot in cents'",
      "COMMENT 'Color snapshot'",
      "COMMENT 'Fabric snapshot'",
      "COMMENT 'Width snapshot'",
      "COMMENT 'Delivery note snapshot'",
      "COMMENT 'Dimensions snapshot'",
      "idx_product_favorite_user_spu_sku",
    ],
  },
  {
    name: "trade-order-address-verification",
    relativePath: `${BACKEND_SQL_ROOT}/trade-order-address-verification.sql`,
    requiredTokens: [
      "TABLE_NAME = 'trade_order'",
      "COLUMN_NAME = 'address_verification'",
      "ADD COLUMN `address_verification` json",
    ],
  },
  {
    name: "member-address-address-verification",
    relativePath: `${BACKEND_SQL_ROOT}/member-address-address-verification.sql`,
    requiredTokens: [
      "TABLE_NAME = 'member_address'",
      "COLUMN_NAME = 'address_verification'",
      "ADD COLUMN `address_verification` json",
    ],
  },
];

export const verifyDbMigrations = (checks = buildMigrationChecks()) => {
  const errors = [];
  const checked = [];

  for (const check of checks) {
    const absolutePath = resolve(process.cwd(), check.relativePath);
    if (!existsSync(absolutePath)) {
      errors.push(`${check.name}: missing migration file at ${absolutePath}`);
      continue;
    }

    const source = readFileSync(absolutePath, "utf8");
    const missingTokens = check.requiredTokens.filter((token) => !source.includes(token));
    if (missingTokens.length) {
      errors.push(`${check.name}: missing required token(s): ${missingTokens.join(", ")}`);
    }

    checked.push({
      ...check,
      absolutePath,
    });
  }

  return {
    ok: errors.length === 0,
    errors,
    checked,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const result = verifyDbMigrations();
  if (result.ok) {
    console.log(`Database migration check passed: ${result.checked.length} file(s)`);
    for (const check of result.checked) {
      console.log(`- ${check.name}: ${check.absolutePath}`);
    }
  } else {
    console.error("Database migration check failed:");
    result.errors.forEach((error) => console.error(`error: ${error}`));
    process.exitCode = 1;
  }
}
