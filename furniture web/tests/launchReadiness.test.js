import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  buildDockerArgs,
  buildLaunchReadinessSteps,
  parseLaunchReadinessArgs,
  resolveStepProcess,
} from "../scripts/verify-launch-readiness.mjs";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("launch readiness gate", () => {
  it("exposes a single npm script for launch readiness checks", () => {
    const packageJson = JSON.parse(readSource("../package.json"));

    expect(packageJson.scripts["verify:launch-readiness"]).toBe("node scripts/verify-launch-readiness.mjs");
  });

  it("runs env, audit, test, and build checks by default", () => {
    const options = parseLaunchReadinessArgs(["--env-file", ".env.production.example"]);
    const steps = buildLaunchReadinessSteps(options);

    expect(steps.map((step) => step.name)).toEqual([
      "production-env",
      "npm-audit",
      "unit-tests",
      "production-build",
    ]);
    expect(steps[0].command).toBe("node");
    expect(steps[0].args).toEqual(["scripts/verify-production-env.mjs", "--env-file", ".env.production.example"]);
    expect(steps[1].args).toEqual(["audit", "--audit-level=low"]);
    expect(steps[2].args).toEqual(["test"]);
    expect(steps[3].args).toEqual(["run", "build"]);
    expect(steps[3].env).toMatchObject({
      VITE_YUDAO_PAY_CHANNEL_CODE: "alipay_pc",
    });
  });

  it("can include a Docker image build with Vite env build args", () => {
    const options = parseLaunchReadinessArgs([
      "--env-file=.env.production.example",
      "--include-docker",
      "--docker-tag",
      "oakved-storefront:launch-smoke",
    ]);
    const steps = buildLaunchReadinessSteps(options);
    const dockerStep = steps.find((step) => step.name === "docker-build");

    expect(dockerStep.command).toBe("docker");
    expect(dockerStep.args).toContain("build");
    expect(dockerStep.args).toContain("-t");
    expect(dockerStep.args).toContain("oakved-storefront:launch-smoke");
    expect(dockerStep.args).toContain("--build-arg");
    expect(dockerStep.args).toContain("VITE_YUDAO_APP_API_BASE=https://api.oakved.example/app-api");
    expect(dockerStep.args.at(-1)).toBe(".");
  });

  it("can include wishlist smoke in launch mode or mock mode", () => {
    const liveOptions = parseLaunchReadinessArgs(["--include-wishlist-smoke"]);
    const liveStep = buildLaunchReadinessSteps(liveOptions).find((step) => step.name === "wishlist-smoke");

    expect(liveStep.command).toBe("npm");
    expect(liveStep.args).toEqual(["run", "test:smoke:wishlist"]);
    expect(liveStep.env).toMatchObject({ WISHLIST_SMOKE_MODE: "live" });

    const mockOptions = parseLaunchReadinessArgs(["--include-wishlist-smoke", "--wishlist-smoke-mode=mock"]);
    const mockStep = buildLaunchReadinessSteps(mockOptions).find((step) => step.name === "wishlist-smoke");

    expect(mockStep.env).toMatchObject({ WISHLIST_SMOKE_MODE: "mock" });
  });

  it("can include the live business smoke gate with the selected env file", () => {
    const options = parseLaunchReadinessArgs(["--env-file=.env.production.example", "--include-live-business-smoke"]);
    const liveStep = buildLaunchReadinessSteps(options).find((step) => step.name === "live-business-smoke");

    expect(liveStep.command).toBe("npm");
    expect(liveStep.args).toEqual(["run", "test:smoke:live-business", "--", "--env-file", ".env.production.example"]);
  });

  it("can pass a separate smoke env file to live smoke gates", () => {
    const options = parseLaunchReadinessArgs([
      "--env-file=.env.production.example",
      "--smoke-env-file=.env.launch-smoke",
      "--include-live-business-smoke",
      "--include-order-live-smoke",
      "--include-real-account-smoke",
    ]);
    const steps = buildLaunchReadinessSteps(options);
    const liveStep = steps.find((step) => step.name === "live-business-smoke");
    const orderStep = steps.find((step) => step.name === "order-live-smoke");
    const realAccountStep = steps.find((step) => step.name === "real-account-smoke");

    expect(steps.map((step) => step.name)).toEqual(
      expect.arrayContaining(["launch-smoke-env", "live-business-smoke", "order-live-smoke", "real-account-smoke"]),
    );
    expect(steps.findIndex((step) => step.name === "launch-smoke-env")).toBeLessThan(
      steps.findIndex((step) => step.name === "live-business-smoke"),
    );
    expect(steps.find((step) => step.name === "launch-smoke-env")?.args).toEqual([
      "run",
      "verify:launch-smoke-env",
      "--",
      "--env-file",
      ".env.launch-smoke",
    ]);
    expect(liveStep.args).toEqual(["run", "test:smoke:live-business", "--", "--env-file", ".env.launch-smoke"]);
    expect(orderStep.args).toEqual(["run", "test:smoke:order-live", "--", "--env-file", ".env.launch-smoke"]);
    expect(realAccountStep.args).toEqual(["run", "test:smoke:real-account", "--", "--env-file", ".env.launch-smoke"]);
  });

  it("can include the order live smoke gate with the selected env file", () => {
    const options = parseLaunchReadinessArgs(["--env-file=.env.production.example", "--include-order-live-smoke"]);
    const orderStep = buildLaunchReadinessSteps(options).find((step) => step.name === "order-live-smoke");

    expect(orderStep.command).toBe("npm");
    expect(orderStep.args).toEqual(["run", "test:smoke:order-live", "--", "--env-file", ".env.production.example"]);
  });

  it("can include the real account smoke gate with optional order detail checking", () => {
    const options = parseLaunchReadinessArgs([
      "--env-file=.env.production.example",
      "--include-real-account-smoke",
      "--real-account-check-order",
    ]);
    const realAccountStep = buildLaunchReadinessSteps(options).find((step) => step.name === "real-account-smoke");

    expect(realAccountStep.command).toBe("npm");
    expect(realAccountStep.args).toEqual([
      "run",
      "test:smoke:real-account",
      "--",
      "--env-file",
      ".env.production.example",
      "--check-order",
    ]);
  });

  it("can include admin and backend launch gates from the monorepo workspace", () => {
    const options = parseLaunchReadinessArgs([
      "--env-file=.env.production.example",
      "--include-admin-check",
      "--include-admin-build",
      "--include-backend-build",
    ]);
    const steps = buildLaunchReadinessSteps(options);
    const adminCheck = steps.find((step) => step.name === "admin-furniture-lite-check");
    const adminBuild = steps.find((step) => step.name === "admin-production-build");
    const backendBuild = steps.find((step) => step.name === "backend-server-build");

    expect(adminCheck).toMatchObject({
      command: "pnpm",
      args: ["run", "check:furniture-lite"],
    });
    expect(adminCheck.cwd).toContain("yudao-ui-admin-vue3");

    expect(adminBuild).toMatchObject({
      command: "pnpm",
      args: ["run", "build:prod"],
    });
    expect(adminBuild.cwd).toContain("yudao-ui-admin-vue3");

    expect(backendBuild).toMatchObject({
      command: "mvn",
      args: ["-pl", "yudao-server", "-am", "-DskipTests", "package"],
    });
    expect(backendBuild.cwd).toContain("yudao-cloud");
  });

  it("can include the backend production configuration gate", () => {
    const options = parseLaunchReadinessArgs(["--include-backend-prod-config"]);
    const backendProdConfig = buildLaunchReadinessSteps(options).find(
      (step) => step.name === "backend-production-config",
    );

    expect(backendProdConfig).toMatchObject({
      command: "npm",
      args: ["run", "verify:backend-production-config"],
    });
  });

  it("can include the backend production runtime env gate", () => {
    const options = parseLaunchReadinessArgs([
      "--include-backend-prod-env",
      "--backend-env-file",
      ".env.backend-production.example",
      "--backend-env-allow-placeholders",
    ]);
    const backendProdEnv = buildLaunchReadinessSteps(options).find((step) => step.name === "backend-production-env");

    expect(backendProdEnv).toMatchObject({
      command: "npm",
      args: [
        "run",
        "verify:backend-production-env",
        "--",
        "--env-file",
        ".env.backend-production.example",
        "--allow-placeholders",
      ],
    });
  });

  it("can include cross-file launch env alignment", () => {
    const options = parseLaunchReadinessArgs([
      "--include-launch-env-alignment",
      "--env-file",
      ".env.production.example",
      "--smoke-env-file",
      ".env.launch-smoke.example",
      "--backend-env-file",
      ".env.backend-production.example",
      "--base-url",
      "https://shop.oakved.example",
      "--launch-env-alignment-allow-placeholders",
    ]);
    const alignment = buildLaunchReadinessSteps(options).find((step) => step.name === "launch-env-alignment");

    expect(alignment).toMatchObject({
      command: "npm",
      args: [
        "run",
        "verify:launch-env-alignment",
        "--",
        "--env-file",
        ".env.production.example",
        "--smoke-env-file",
        ".env.launch-smoke.example",
        "--backend-env-file",
        ".env.backend-production.example",
        "--base-url",
        "https://shop.oakved.example",
        "--allow-placeholders",
      ],
    });
  });

  it("only forwards launch-safe Vite values to Docker build args", () => {
    const args = buildDockerArgs({
      VITE_YUDAO_APP_API_BASE: "https://api.example/app-api",
      VITE_YUDAO_APP_TENANT_ID: "121",
      VITE_YUDAO_US_DEFAULT_AREA_ID: "100200",
      VITE_YUDAO_PAY_CHANNEL_CODE: "alipay_pc",
      VITE_ADDRESS_VERIFICATION_PATH: "/member/address/verify",
      VITE_ADDRESS_VERIFICATION_STATUS_PATH: "/member/address/verification-status",
      VITE_SHOW_AUTH_TOKEN_PANEL: "true",
    });

    expect(args).toContain("VITE_YUDAO_APP_API_BASE=https://api.example/app-api");
    expect(args).not.toContain("VITE_SHOW_AUTH_TOKEN_PANEL=true");
  });

  it("uses the npm CLI path on Windows without enabling shell execution", () => {
    expect(
      resolveStepProcess(
        { command: "npm", args: ["test"] },
        { npm_execpath: "C:\\node\\node_modules\\npm\\bin\\npm-cli.js" },
        "win32",
      ),
    ).toMatchObject({
      command: process.execPath,
      args: ["C:\\node\\node_modules\\npm\\bin\\npm-cli.js", "test"],
    });
    expect(resolveStepProcess({ command: "node", args: ["x.js"] }, {}, "win32")).toEqual({
      command: "node",
      args: ["x.js"],
    });
    expect(resolveStepProcess({ command: "npm", args: ["test"] }, {}, "linux")).toEqual({
      command: "npm",
      args: ["test"],
    });
  });
});
