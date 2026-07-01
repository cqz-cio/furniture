import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { createLaunchEvidenceBundle } from "../scripts/create-launch-evidence.mjs";
import {
  auditLaunchEvidence,
  parseLaunchEvidenceAuditArgs,
} from "../scripts/audit-launch-evidence.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

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

const createCompleteEvidence = () => {
  const tempRoot = mkdtempSync(join(tmpdir(), "oakved-launch-audit-"));
  const evidenceDir = join(tempRoot, "launch");
  const bundle = createLaunchEvidenceBundle({
    dir: evidenceDir,
    commitSha: "abc123",
    imageTag: "oakved-storefront:abc123",
    imageDigest: "sha256:123",
    baseUrl: "https://shop.example.com",
    envFile: ".env.production",
    smokeEnvFile: ".env.launch-smoke",
    createdAt: "2026-07-01T00:00:00.000Z",
  });

  for (const file of bundle.manifest.requiredEvidenceFiles) {
    writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
  }

  writeFileSync(join(evidenceDir, "browser-home.png"), tinyPng);
  return { tempRoot, evidenceDir };
};

describe("launch evidence audit", () => {
  it("exposes a repeatable launch evidence audit command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["audit:launch-evidence"]).toBe("node scripts/audit-launch-evidence.mjs");
  });

  it("parses evidence directory flags", () => {
    expect(parseLaunchEvidenceAuditArgs(["--dir", "launch-evidence/2026-07-01"])).toEqual({
      dir: "launch-evidence/2026-07-01",
    });
    expect(parseLaunchEvidenceAuditArgs(["--dir=launch-evidence/latest"])).toEqual({
      dir: "launch-evidence/latest",
    });
  });

  it("passes when manifest metadata and required evidence files are complete", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result).toMatchObject({ ok: true, errors: [] });
      expect(result.checkedFiles).toEqual(
        expect.arrayContaining(["production-env.txt", "launch-readiness.txt", "post-deploy-health.txt"]),
      );
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when evidence files still contain placeholders", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "production-env.txt"), "Paste command output here for production-env.txt.\n", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("production-env.txt still contains placeholder text");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when a command output file does not contain the expected success marker", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "launch-readiness.txt"), "npm finished with exit code 0\n", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("launch-readiness.txt must contain success marker: Launch readiness check passed.");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when no browser screenshot evidence is present", () => {
    const tempRoot = mkdtempSync(join(tmpdir(), "oakved-launch-audit-no-shot-"));
    const evidenceDir = join(tempRoot, "launch");
    const bundle = createLaunchEvidenceBundle({
      dir: evidenceDir,
      commitSha: "abc123",
      imageTag: "oakved-storefront:abc123",
      imageDigest: "sha256:123",
      baseUrl: "https://shop.example.com",
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      createdAt: "2026-07-01T00:00:00.000Z",
    });

    for (const file of bundle.manifest.requiredEvidenceFiles) {
      writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
    }

    try {
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("At least one browser screenshot image is required");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when browser screenshot evidence still contains placeholder text", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "browser-home.png"), "screenshot-placeholder", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("browser-home.png still contains placeholder screenshot text");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when screenshot evidence is a text file renamed as an image", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "browser-home.png"), "PNG screenshot evidence for deployed home page", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("browser-home.png must be a valid PNG, JPEG, or WebP image.");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when launch metadata is missing", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.imageDigest = "";
      manifest.baseUrl = "";
      manifest.backendEnvFile = "";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("imageDigest is required");
      expect(result.errors.join("\n")).toContain("baseUrl is required");
      expect(result.errors.join("\n")).toContain("backendEnvFile is required");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });
});
