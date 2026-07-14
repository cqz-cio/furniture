import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout preview smoke wrapper", () => {
  it("exposes a command that starts preview before running checkout E2E smoke", () => {
    const packageJson = JSON.parse(readSource("../package.json"));
    const source = readSource("../scripts/checkout-preview-smoke.mjs");

    expect(packageJson.scripts["test:e2e:checkout:preview"]).toBe("node scripts/checkout-preview-smoke.mjs");
    expect(source).toContain("CHECKOUT_PREVIEW_PORT");
    expect(source).toContain("CHECKOUT_PREVIEW_ENV_FILE");
    expect(source).toContain("CHECKOUT_PREVIEW_SKIP_BUILD");
    expect(source).toContain("CHECKOUT_E2E_BASE_URL");
    expect(source).toContain("npm");
    expect(source).toContain('["run", "build"]');
    expect(source).toContain("preview");
    expect(source).toContain("scripts/checkout-e2e-smoke.mjs");
    expect(source).toContain("waitForPreview");
    expect(source).toContain("previewProcess.kill");
  });

  it("lets launch readiness include the checkout preview smoke after build", async () => {
    const { buildLaunchReadinessSteps, parseLaunchReadinessArgs } = await import("../scripts/verify-launch-readiness.mjs");

    const options = parseLaunchReadinessArgs(["--include-checkout-smoke"]);
    const steps = buildLaunchReadinessSteps(options);
    const checkoutStep = steps.find((step) => step.name === "checkout-preview-smoke");

    expect(steps.map((step) => step.name)).toContain("production-build");
    expect(checkoutStep.command).toBe("npm");
    expect(checkoutStep.args).toEqual(["run", "test:e2e:checkout:preview"]);
    expect(checkoutStep.env).toMatchObject({
      CHECKOUT_PREVIEW_ENV_FILE: ".env.production",
      CHECKOUT_PREVIEW_SKIP_BUILD: "true",
    });
  });
});
