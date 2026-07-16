import { describe, expect, it } from "vitest";

import { buildMigrationChecks, verifyDbMigrations } from "../scripts/verify-db-migrations.mjs";

describe("database migration readiness", () => {
  it("exposes repeatable migration and baseline commands", async () => {
    const packageJson = (await import("../package.json", { with: { type: "json" } })).default;

    expect(packageJson.scripts["verify:db-migrations"]).toBe("node scripts/verify-db-migrations.mjs");
    expect(packageJson.scripts["build:db-baseline"]).toContain("build-oakved-baseline.mjs");
  });

  it("discovers the complete contiguous numbered catalog", () => {
    const checks = buildMigrationChecks();
    const expectedVersions = Array.from({ length: 20 }, (_, index) => index + 1);

    expect(checks).toHaveLength(expectedVersions.length);
    expect(checks[0].fileName).toBe("V001__module_tables.sql");
    expect(checks.slice(-2).map((check) => check.fileName)).toEqual([
      "V019__seo_foundation.sql",
      "V020__seo_active_record_uniqueness.sql",
    ]);
    expect(checks.map((check) => check.version)).toEqual(expectedVersions);
  });

  it("verifies migrations, baseline, runner, reset and compose wiring", () => {
    const result = verifyDbMigrations();

    expect(result.ok).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.checked.map((check) => check.version)).toEqual(
      Array.from({ length: 20 }, (_, index) => index + 1),
    );
  });

  it("lets launch readiness include the database migration gate", async () => {
    const { buildLaunchReadinessSteps, parseLaunchReadinessArgs } = await import("../scripts/verify-launch-readiness.mjs");
    const options = parseLaunchReadinessArgs(["--include-db-migrations"]);
    const dbStep = buildLaunchReadinessSteps(options).find((step) => step.name === "db-migrations");

    expect(dbStep.command).toBe("npm");
    expect(dbStep.args).toEqual(["run", "verify:db-migrations"]);
  });
});
