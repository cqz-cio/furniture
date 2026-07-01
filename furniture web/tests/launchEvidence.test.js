import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import {
  buildLaunchEvidenceManifest,
  createLaunchEvidenceBundle,
  parseLaunchEvidenceArgs,
} from "../scripts/create-launch-evidence.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

describe("launch evidence bundle", () => {
  it("exposes a repeatable launch evidence command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["create:launch-evidence"]).toBe("node scripts/create-launch-evidence.mjs");
  });

  it("parses launch metadata flags", () => {
    expect(
      parseLaunchEvidenceArgs([
        "--dir",
        "launch-evidence/test",
        "--image-tag",
        "oakved-storefront:abc123",
        "--image-digest",
        "sha256:123",
        "--base-url",
        "https://shop.example.com",
        "--launch-env-file",
        ".env.production",
        "--launch-smoke-env-file",
        ".env.launch-smoke",
        "--backend-env-file",
        ".env.backend-production",
      ]),
    ).toMatchObject({
      dir: "launch-evidence/test",
      imageTag: "oakved-storefront:abc123",
      imageDigest: "sha256:123",
      baseUrl: "https://shop.example.com",
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
    });
  });

  it("builds a manifest with launch commands and required evidence files", () => {
    const manifest = buildLaunchEvidenceManifest({
      commitSha: "abc123",
      imageTag: "oakved-storefront:abc123",
      imageDigest: "sha256:123",
      baseUrl: "https://shop.example.com",
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      createdAt: "2026-07-01T00:00:00.000Z",
    });

    expect(manifest).toMatchObject({
      commitSha: "abc123",
      imageTag: "oakved-storefront:abc123",
      imageDigest: "sha256:123",
      baseUrl: "https://shop.example.com",
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
    });
    expect(manifest.commands).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: "production-env", command: "npm.cmd run verify:production-env -- --env-file .env.production" }),
        expect.objectContaining({ name: "backend-production-env", command: "npm.cmd run verify:backend-production-env -- --env-file .env.backend-production" }),
        expect.objectContaining({ name: "backend-production-config", command: "npm.cmd run verify:backend-production-config" }),
        expect.objectContaining({
          name: "launch-env-alignment",
          command: "npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com",
        }),
        expect.objectContaining({ name: "launch-readiness", command: expect.stringContaining("--smoke-env-file .env.launch-smoke") }),
        expect.objectContaining({ name: "launch-readiness", command: expect.stringContaining("--include-backend-prod-env --backend-env-file .env.backend-production") }),
        expect.objectContaining({ name: "post-deploy-health", command: "npm.cmd run test:deploy:health -- --base-url https://shop.example.com" }),
      ]),
    );
    expect(manifest.requiredEvidenceFiles).toContain("production-env.txt");
    expect(manifest.requiredEvidenceFiles).toContain("backend-production-env.txt");
    expect(manifest.requiredEvidenceFiles).toContain("backend-production-config.txt");
    expect(manifest.requiredEvidenceFiles).toContain("launch-env-alignment.txt");
    expect(manifest.requiredEvidenceFiles).toContain("post-deploy-health.txt");
  });

  it("creates manifest, README, and evidence placeholders", () => {
    const tempRoot = mkdtempSync(join(tmpdir(), "oakved-launch-evidence-"));
    const evidenceDir = join(tempRoot, "launch");

    try {
      const result = createLaunchEvidenceBundle({
        dir: evidenceDir,
        commitSha: "abc123",
        imageTag: "oakved-storefront:abc123",
        imageDigest: "sha256:123",
        baseUrl: "https://shop.example.com",
        envFile: ".env.production",
        smokeEnvFile: ".env.launch-smoke",
        backendEnvFile: ".env.backend-production",
        createdAt: "2026-07-01T00:00:00.000Z",
      });

      expect(result.manifestPath).toContain("launch-manifest.json");
      const manifest = JSON.parse(readFileSync(result.manifestPath, "utf8"));
      const readme = readFileSync(join(evidenceDir, "README.md"), "utf8");
      const placeholder = readFileSync(join(evidenceDir, "production-env.txt"), "utf8");

      expect(manifest.commitSha).toBe("abc123");
      expect(readme).toContain("Do not commit real launch evidence");
      expect(placeholder).toContain("Paste command output here");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });
});
