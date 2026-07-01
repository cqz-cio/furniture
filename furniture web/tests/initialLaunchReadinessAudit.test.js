import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { createLaunchEvidenceBundle } from "../scripts/create-launch-evidence.mjs";
import {
  auditInitialLaunchReadiness,
  parseInitialLaunchReadinessArgs,
} from "../scripts/audit-initial-launch-readiness.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const productionEnv = `VITE_YUDAO_APP_API_BASE=https://api.oakved.example/app-api
VITE_YUDAO_APP_TENANT_ID=121
VITE_YUDAO_US_DEFAULT_AREA_ID=100200
VITE_YUDAO_PAY_CHANNEL_CODE=alipay_pc
VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify
VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status
VITE_SHOW_AUTH_TOKEN_PANEL=false
`;

const smokeEnv = `YUDAO_SMOKE_BASE_URL=https://api.oakved.example/app-api
YUDAO_SMOKE_TENANT_ID=121
YUDAO_SMOKE_TOKEN=launch-token
YUDAO_ORDER_SMOKE_SKU_ID=5001
YUDAO_ORDER_SMOKE_CART_ID=7001
YUDAO_ORDER_SMOKE_ADDRESS_ID=8101
YUDAO_ORDER_SMOKE_COUNT=1
YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE=alipay_pc
YUDAO_ORDER_SMOKE_RETURN_ORIGIN=https://shop.oakved.example
YUDAO_ORDER_SMOKE_CREATE_ORDER=false
`;

const backendEnv = `SPRING_PROFILES_ACTIVE=prod
YUDAO_DB_URL=jdbc:mysql://mysql.internal:3306/oakved?useSSL=true&serverTimezone=Asia/Shanghai
YUDAO_DB_USERNAME=oakved_app
YUDAO_DB_PASSWORD=replace-with-strong-password
YUDAO_REDIS_HOST=redis.internal
YUDAO_REDIS_PORT=6379
YUDAO_ADMIN_UI_URL=https://admin.oakved.example
YUDAO_APP_UI_URL=https://shop.oakved.example
YUDAO_PAY_ORDER_NOTIFY_URL=https://api.oakved.example/admin-api/pay/notify/order
YUDAO_PAY_REFUND_NOTIFY_URL=https://api.oakved.example/admin-api/pay/notify/refund
YUDAO_PAY_TRANSFER_NOTIFY_URL=https://api.oakved.example/admin-api/pay/notify/transfer
YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY=replace-with-real-google-address-validation-key
`;

const successOutputByFile = {
  "production-env.txt": "Production env check passed: .env.production\n",
  "launch-smoke-env.txt": "Launch smoke env check passed: .env.launch-smoke\n",
  "backend-production-env.txt": "Backend production env check passed: .env.backend-production\n",
  "backend-production-config.txt": "Backend production config check passed.\n",
  "launch-env-alignment.txt": "Launch env alignment check passed.\n",
  "db-migrations.txt": "Database migration check passed: 3 file(s)\n",
  "launch-readiness.txt": "Launch readiness check passed.\n",
  "order-create-smoke.txt": "Order live smoke passed: orderId=1001, payOrderId=2001\n",
  "post-deploy-health.txt": "Post-deploy health check passed: 5 check(s)\n",
};

const tinyPng = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01,
]);

const createCompleteLaunchFixture = () => {
  const tempRoot = mkdtempSync(join(tmpdir(), "oakved-initial-launch-"));
  const envFile = join(tempRoot, ".env.production");
  const smokeEnvFile = join(tempRoot, ".env.launch-smoke");
  const backendEnvFile = join(tempRoot, ".env.backend-production");
  const evidenceDir = join(tempRoot, "evidence");
  writeFileSync(envFile, productionEnv, "utf8");
  writeFileSync(smokeEnvFile, smokeEnv, "utf8");
  writeFileSync(backendEnvFile, backendEnv, "utf8");

  const bundle = createLaunchEvidenceBundle({
    dir: evidenceDir,
    commitSha: "abc123",
    imageTag: "oakved-storefront:abc123",
    imageDigest: "sha256:123",
    baseUrl: "https://shop.oakved.example",
    envFile,
    smokeEnvFile,
    backendEnvFile,
    createdAt: "2026-07-01T00:00:00.000Z",
  });

  for (const file of bundle.manifest.requiredEvidenceFiles) {
    writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
  }

  writeFileSync(join(evidenceDir, "browser-home.png"), tinyPng);

  return { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir };
};

describe("initial launch readiness audit", () => {
  it("exposes a repeatable initial launch readiness audit command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["audit:initial-launch-readiness"]).toBe("node scripts/audit-initial-launch-readiness.mjs");
  });

  it("parses env and evidence directory flags", () => {
    expect(
      parseInitialLaunchReadinessArgs([
        "--env-file",
        ".env.production",
        "--smoke-env-file",
        ".env.launch-smoke",
        "--backend-env-file",
        ".env.backend-production",
        "--base-url",
        "https://shop.oakved.example",
        "--evidence-dir",
        "launch-evidence/2026-07-01",
      ]),
    ).toEqual({
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      baseUrl: "https://shop.oakved.example",
      evidenceDir: "launch-evidence/2026-07-01",
    });
  });

  it("passes only when env files align, backend production config, DB migration files, and evidence audit are complete", () => {
    const { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir } = createCompleteLaunchFixture();

    try {
      const result = auditInitialLaunchReadiness({
        envFile,
        smokeEnvFile,
        backendEnvFile,
        baseUrl: "https://shop.oakved.example",
        evidenceDir,
      });

      expect(result).toMatchObject({ ok: true, blockers: [] });
      expect(result.checks.map((check) => check.name)).toEqual([
        "production-env",
        "launch-smoke-env",
        "backend-production-env",
        "launch-env-alignment",
        "backend-production-config",
        "db-migrations",
        "launch-evidence",
      ]);
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("reports concrete blockers when required external launch evidence is missing", () => {
    const result = auditInitialLaunchReadiness({
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      baseUrl: "https://shop.oakved.example",
      evidenceDir: "",
    });

    expect(result.ok).toBe(false);
    expect(result.blockers.join("\n")).toContain("Production env file not found");
    expect(result.blockers.join("\n")).toContain("Launch smoke env file not found");
    expect(result.blockers.join("\n")).toContain("Backend production env file not found");
    expect(result.blockers.join("\n")).toContain("env file not found");
    expect(result.blockers.join("\n")).toContain("Launch evidence directory is required");
  });
});
