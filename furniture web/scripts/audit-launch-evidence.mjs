import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_EVIDENCE_DIR = "launch-evidence/latest";

const SUCCESS_MARKERS_BY_FILE = {
  "production-env.txt": "Production env check passed:",
  "launch-smoke-env.txt": "Launch smoke env check passed:",
  "backend-production-env.txt": "Backend production env check passed:",
  "backend-production-config.txt": "Backend production config check passed.",
  "launch-env-alignment.txt": "Launch env alignment check passed.",
  "db-migrations.txt": "Database migration check passed:",
  "launch-readiness.txt": "Launch readiness check passed.",
  "order-create-smoke.txt": "Order live smoke passed:",
  "post-deploy-health.txt": "Post-deploy health check passed",
};

export const parseLaunchEvidenceAuditArgs = (argv = [], env = process.env) => {
  const options = {
    dir: env.LAUNCH_EVIDENCE_DIR || DEFAULT_EVIDENCE_DIR,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--dir") {
      options.dir = argv[index + 1] || DEFAULT_EVIDENCE_DIR;
      index += 1;
    } else if (arg.startsWith("--dir=")) {
      options.dir = arg.slice("--dir=".length);
    }
  }

  return options;
};

const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));

const hasPlaceholderText = (content) => {
  const normalized = String(content || "").toLowerCase();
  return (
    !normalized.trim() ||
    normalized.includes("paste command output here") ||
    normalized.includes("<real-") ||
    normalized.includes("<commit") ||
    normalized.includes("placeholder")
  );
};

const isScreenshotFile = (file) => /\.(?:png|jpe?g|webp)$/i.test(file);

const isValidScreenshotImage = (file, content) => {
  const normalized = file.toLowerCase();
  if (normalized.endsWith(".png")) {
    return (
      content.length >= 8 &&
      content[0] === 0x89 &&
      content[1] === 0x50 &&
      content[2] === 0x4e &&
      content[3] === 0x47 &&
      content[4] === 0x0d &&
      content[5] === 0x0a &&
      content[6] === 0x1a &&
      content[7] === 0x0a
    );
  }
  if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
    return content.length >= 3 && content[0] === 0xff && content[1] === 0xd8 && content[2] === 0xff;
  }
  if (normalized.endsWith(".webp")) {
    return (
      content.length >= 12 &&
      content.toString("ascii", 0, 4) === "RIFF" &&
      content.toString("ascii", 8, 12) === "WEBP"
    );
  }
  return false;
};

const hasPlaceholderScreenshotText = (path) => {
  const content = readFileSync(path);
  const text = content.toString("utf8").toLowerCase();
  return !content.length || text.includes("screenshot-placeholder") || text.includes("placeholder screenshot");
};

const requireManifestValue = (errors, manifest, key) => {
  if (!String(manifest[key] || "").trim()) {
    errors.push(`${key} is required in launch-manifest.json`);
  }
};

const requireSuccessMarker = (errors, file, content) => {
  const marker = SUCCESS_MARKERS_BY_FILE[file];
  if (marker && !String(content || "").includes(marker)) {
    errors.push(`${file} must contain success marker: ${marker}`);
  }
};

export const auditLaunchEvidence = (options = {}) => {
  const dir = resolve(process.cwd(), options.dir || DEFAULT_EVIDENCE_DIR);
  const errors = [];
  const checkedFiles = [];
  const manifestPath = join(dir, "launch-manifest.json");

  if (!existsSync(manifestPath)) {
    return {
      ok: false,
      errors: [`launch-manifest.json is missing at ${manifestPath}`],
      checkedFiles,
      dir,
    };
  }

  let manifest;
  try {
    manifest = readJson(manifestPath);
  } catch (error) {
    return {
      ok: false,
      errors: [`launch-manifest.json is not valid JSON: ${error.message}`],
      checkedFiles,
      dir,
    };
  }

  for (const key of ["commitSha", "imageTag", "imageDigest", "baseUrl", "envFile", "smokeEnvFile", "backendEnvFile"]) {
    requireManifestValue(errors, manifest, key);
  }

  const requiredEvidenceFiles = Array.isArray(manifest.requiredEvidenceFiles) ? manifest.requiredEvidenceFiles : [];
  if (!requiredEvidenceFiles.length) {
    errors.push("requiredEvidenceFiles must list launch command output files");
  }

  const screenshotFiles = readdirSync(dir).filter(isScreenshotFile);
  if (!screenshotFiles.length) {
    errors.push("At least one browser screenshot image is required in the launch evidence directory.");
  }

  for (const file of screenshotFiles) {
    const screenshotPath = join(dir, file);
    if (!statSync(screenshotPath).isFile()) continue;
    const content = readFileSync(screenshotPath);
    if (hasPlaceholderScreenshotText(screenshotPath)) {
      errors.push(`${file} still contains placeholder screenshot text`);
    }
    if (!isValidScreenshotImage(file, content)) {
      errors.push(`${file} must be a valid PNG, JPEG, or WebP image.`);
    }
  }

  for (const file of requiredEvidenceFiles) {
    const evidencePath = join(dir, file);
    if (!existsSync(evidencePath)) {
      errors.push(`${file} is missing`);
      continue;
    }

    const content = readFileSync(evidencePath, "utf8");
    checkedFiles.push(file);
    if (hasPlaceholderText(content)) {
      errors.push(`${file} still contains placeholder text`);
    }
    requireSuccessMarker(errors, file, content);
  }

  return {
    ok: errors.length === 0,
    errors,
    checkedFiles,
    dir,
    manifest,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parseLaunchEvidenceAuditArgs(process.argv.slice(2));
  const result = auditLaunchEvidence(options);
  if (result.ok) {
    console.log(`Launch evidence audit passed: ${result.dir}`);
    result.checkedFiles.forEach((file) => console.log(`- ${file}`));
  } else {
    console.error(`Launch evidence audit failed: ${result.dir}`);
    result.errors.forEach((error) => console.error(`error: ${error}`));
    process.exitCode = 1;
  }
}
