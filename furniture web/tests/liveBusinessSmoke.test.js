import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  buildLiveBusinessSmokeSteps,
  parseLiveBusinessSmokeArgs,
} from "../scripts/live-business-smoke.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const liveBusinessSmokeScriptPath = new URL("../scripts/live-business-smoke.mjs", import.meta.url);

describe("live business smoke gate", () => {
  it("exposes a repeatable live business smoke command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:smoke:live-business"]).toBe("node scripts/live-business-smoke.mjs");
    expect(existsSync(liveBusinessSmokeScriptPath)).toBe(true);
  });

  it("maps production env into the live wishlist smoke step", () => {
    const options = parseLiveBusinessSmokeArgs(["--env-file", ".env.production.example"]);
    const steps = buildLiveBusinessSmokeSteps(options, {
      YUDAO_SMOKE_TOKEN: "launch-token",
    });

    expect(steps).toHaveLength(1);
    expect(steps[0]).toMatchObject({
      name: "wishlist-live-smoke",
      command: "npm",
      args: ["run", "test:smoke:wishlist"],
      env: {
        WISHLIST_SMOKE_MODE: "live",
        YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
        YUDAO_SMOKE_TENANT_ID: "121",
        YUDAO_SMOKE_TOKEN: "launch-token",
      },
    });
  });

  it("requires an explicit live smoke token", () => {
    const options = parseLiveBusinessSmokeArgs(["--env-file", ".env.production.example"]);

    expect(() => buildLiveBusinessSmokeSteps(options, {})).toThrow(/YUDAO_SMOKE_TOKEN/);
  });
});
