import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import { buildMigrationChecks, verifyDbMigrations } from "../scripts/verify-db-migrations.mjs";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("database migration readiness", () => {
  it("exposes a repeatable db migration verification command", () => {
    const packageJson = JSON.parse(readSource("../package.json"));

    expect(packageJson.scripts["verify:db-migrations"]).toBe("node scripts/verify-db-migrations.mjs");
  });

  it("checks every launch-critical backend migration file", () => {
    const checks = buildMigrationChecks();

    expect(checks.map((check) => check.name)).toEqual([
      "product-favorite-sku-wishlist",
      "trade-order-address-verification",
      "member-address-address-verification",
      "member-trade-application",
      "member-membership",
      "member-gift-registry",
      "trade-gift-registry-context",
    ]);
    expect(checks[0].relativePath).toContain("product-favorite-sku-wishlist.sql");
    expect(checks[1].relativePath).toContain("trade-order-address-verification.sql");
    expect(checks[2].relativePath).toContain("member-address-address-verification.sql");
    expect(checks[3].relativePath).toContain("member-trade-application.sql");
    expect(checks[4].relativePath).toContain("member-membership.sql");
    expect(checks[5].relativePath).toContain("member-gift-registry.sql");
    expect(checks[6].relativePath).toContain("trade-gift-registry-context.sql");
  });

  it("verifies required table and column tokens in migration scripts", () => {
    const result = verifyDbMigrations();

    expect(result.ok).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.checked).toHaveLength(7);
    expect(result.checked[0]).toMatchObject({
      name: "product-favorite-sku-wishlist",
      requiredTokens: expect.arrayContaining(["ADD COLUMN `sku_id`", "idx_product_favorite_user_spu_sku"]),
    });
    expect(result.checked[1]).toMatchObject({
      name: "trade-order-address-verification",
      requiredTokens: expect.arrayContaining(["TABLE_NAME = 'trade_order'", "ADD COLUMN `address_verification` json"]),
    });
    expect(result.checked[2]).toMatchObject({
      name: "member-address-address-verification",
      requiredTokens: expect.arrayContaining(["TABLE_NAME = 'member_address'", "ADD COLUMN `address_verification` json"]),
    });
    expect(result.checked[3]).toMatchObject({
      name: "member-trade-application",
      requiredTokens: expect.arrayContaining([
        "CREATE TABLE IF NOT EXISTS `member_trade_application`",
        "trade_id",
        "member:trade-application:query",
        "member:trade-application:review",
        "member/trade/application/index",
      ]),
      infraReferences: expect.arrayContaining(["member-trade-application.sql"]),
    });
    expect(result.checked[4]).toMatchObject({
      name: "member-membership",
      requiredTokens: expect.arrayContaining([
        "CREATE TABLE IF NOT EXISTS `member_membership`",
        "source_order_id",
        "source_pay_order_id",
        "member:membership:query",
        "member:membership:update",
        "member/membership/index",
      ]),
      infraReferences: expect.arrayContaining(["member-membership.sql"]),
    });
    expect(result.checked[5]).toMatchObject({
      name: "member-gift-registry",
      requiredTokens: expect.arrayContaining([
        "CREATE TABLE IF NOT EXISTS `member_gift_registry`",
        "CREATE TABLE IF NOT EXISTS `member_gift_registry_item`",
        "public_code",
        "quantity_purchased",
        "member:gift-registry:query",
        "member:gift-registry:update",
        "member/gift-registry/index",
      ]),
      infraReferences: expect.arrayContaining(["member-gift-registry.sql"]),
    });
    expect(result.checked[6]).toMatchObject({
      name: "trade-gift-registry-context",
      requiredTokens: expect.arrayContaining([
        "TABLE_NAME = 'trade_cart'",
        "TABLE_NAME = 'trade_order_item'",
        "ADD COLUMN `registry_id`",
        "ADD COLUMN `registry_item_id`",
      ]),
      infraReferences: expect.arrayContaining(["trade-gift-registry-context.sql"]),
    });
  });

  it("lets launch readiness include the database migration gate", async () => {
    const { buildLaunchReadinessSteps, parseLaunchReadinessArgs } = await import("../scripts/verify-launch-readiness.mjs");

    const options = parseLaunchReadinessArgs(["--include-db-migrations"]);
    const dbStep = buildLaunchReadinessSteps(options).find((step) => step.name === "db-migrations");

    expect(dbStep.command).toBe("npm");
    expect(dbStep.args).toEqual(["run", "verify:db-migrations"]);
  });
});
