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
    ]);
    expect(checks[0].relativePath).toContain("product-favorite-sku-wishlist.sql");
    expect(checks[1].relativePath).toContain("trade-order-address-verification.sql");
    expect(checks[2].relativePath).toContain("member-address-address-verification.sql");
  });

  it("verifies required table and column tokens in migration scripts", () => {
    const result = verifyDbMigrations();

    expect(result.ok).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.checked).toHaveLength(3);
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
  });

  it("lets launch readiness include the database migration gate", async () => {
    const { buildLaunchReadinessSteps, parseLaunchReadinessArgs } = await import("../scripts/verify-launch-readiness.mjs");

    const options = parseLaunchReadinessArgs(["--include-db-migrations"]);
    const dbStep = buildLaunchReadinessSteps(options).find((step) => step.name === "db-migrations");

    expect(dbStep.command).toBe("npm");
    expect(dbStep.args).toEqual(["run", "verify:db-migrations"]);
  });
});
