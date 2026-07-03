import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BACKEND_SQL_ROOT = "../yudao电商管理平台前后端/yudao-cloud/sql/mysql";

const BACKEND_DOCKER_ROOT = BACKEND_SQL_ROOT.replace("/sql/mysql", "/script/docker");
const INFRA_REFERENCE_PATHS = [
  `${BACKEND_DOCKER_ROOT}/docker-compose-local-infra.yml`,
  `${BACKEND_DOCKER_ROOT}/start-local-infra.ps1`,
  `${BACKEND_DOCKER_ROOT}/README-local-infra.md`,
];

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
  {
    name: "member-trade-application",
    relativePath: `${BACKEND_SQL_ROOT}/member-trade-application.sql`,
    requiredTokens: [
      "CREATE TABLE IF NOT EXISTS `member_trade_application`",
      "trade_id",
      "member:trade-application:query",
      "member:trade-application:review",
      "member/trade/application/index",
    ],
    infraReferences: ["member-trade-application.sql"],
  },
  {
    name: "member-membership",
    relativePath: `${BACKEND_SQL_ROOT}/member-membership.sql`,
    requiredTokens: [
      "CREATE TABLE IF NOT EXISTS `member_membership`",
      "source_order_id",
      "source_pay_order_id",
      "member:membership:query",
      "member:membership:update",
      "member/membership/index",
    ],
    infraReferences: ["member-membership.sql"],
  },
  {
    name: "member-gift-registry",
    relativePath: `${BACKEND_SQL_ROOT}/member-gift-registry.sql`,
    requiredTokens: [
      "CREATE TABLE IF NOT EXISTS `member_gift_registry`",
      "CREATE TABLE IF NOT EXISTS `member_gift_registry_item`",
      "public_code",
      "quantity_purchased",
      "member:gift-registry:query",
      "member:gift-registry:update",
      "member/gift-registry/index",
    ],
    infraReferences: ["member-gift-registry.sql"],
  },
  {
    name: "trade-gift-registry-context",
    relativePath: `${BACKEND_SQL_ROOT}/trade-gift-registry-context.sql`,
    requiredTokens: [
      "TABLE_NAME = 'trade_cart'",
      "TABLE_NAME = 'trade_order_item'",
      "COLUMN_NAME = 'registry_id'",
      "COLUMN_NAME = 'registry_item_id'",
      "ADD COLUMN `registry_id`",
      "ADD COLUMN `registry_item_id`",
    ],
    infraReferences: ["trade-gift-registry-context.sql"],
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

    const infraReferencePaths = [];
    if (check.infraReferences?.length) {
      const infraSources = INFRA_REFERENCE_PATHS.map((relativePath) => {
        const infraPath = resolve(process.cwd(), relativePath);
        infraReferencePaths.push(infraPath);

        if (!existsSync(infraPath)) {
          errors.push(`${check.name}: missing infra reference file at ${infraPath}`);
          return "";
        }

        return readFileSync(infraPath, "utf8");
      });

      const missingInfraReferences = check.infraReferences.filter((reference) =>
        infraSources.some((source) => !source.includes(reference)),
      );
      if (missingInfraReferences.length) {
        errors.push(`${check.name}: missing local infra reference(s): ${missingInfraReferences.join(", ")}`);
      }
    }

    checked.push({
      ...check,
      absolutePath,
      infraReferencePaths,
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
