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
  "real-account-smoke.txt": "Real account readiness smoke passed.",
  "order-create-smoke.txt": "Order live smoke passed:",
  "post-deploy-health.txt": "Post-deploy health check passed",
};

const REQUIRED_REAL_ACCOUNT_MODULES = [
  "productCatalog",
  "cart",
  "checkout",
  "orders",
  "billing",
  "accountProfile",
  "addressBook",
  "wishlist",
  "membership",
  "giftRegistry",
  "tradeProgram",
];

const REQUIRED_REAL_ACCOUNT_STEPS = [
  "product-catalog-page",
  "product-detail",
  "cart-list",
  "order-page",
  "member-profile",
  "member-address-list",
  "wishlist-page",
  "membership-profile",
  "gift-registry-my",
  "membership-admin-page",
  "gift-registry-admin-page",
  "gift-registry-admin-detail",
  "trade-application-admin-page",
];

const REQUIRED_REAL_ACCOUNT_IDENTIFIERS = [
  "userId",
  "cartId",
  "skuId",
  "addressId",
  "orderId",
  "giftRegistryPublicCode",
  "tradeId",
  "tradeEmail",
  "membershipStatus",
  "membershipPlanCode",
  "giftRegistryItemSpuId",
  "giftRegistryItemSkuId",
];
const NUMERIC_REAL_ACCOUNT_IDENTIFIERS = [
  "userId",
  "cartId",
  "skuId",
  "addressId",
  "orderId",
  "giftRegistryItemSpuId",
  "giftRegistryItemSkuId",
];

const REQUIRED_LAUNCH_EVIDENCE_FILES = ["real-account-smoke.txt"];

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

const isDocumentationDomainUrl = (value) => {
  try {
    const hostname = new URL(value).hostname.toLowerCase();
    return hostname === "example.com" || hostname.endsWith(".example.com") || hostname.endsWith(".example");
  } catch {
    return false;
  }
};

const requireNonDocumentationUrl = (errors, manifest, key) => {
  const value = String(manifest[key] || "").trim();
  if (value && isDocumentationDomainUrl(value)) {
    errors.push(`${key} must not use a documentation/example domain`);
  }
};

const requireSuccessMarker = (errors, file, content) => {
  const marker = SUCCESS_MARKERS_BY_FILE[file];
  if (marker && !String(content || "").includes(marker)) {
    errors.push(`${file} must contain success marker: ${marker}`);
  }
};

const isPlaceholderValue = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  return !normalized || normalized.includes("<") || normalized.includes(">") || normalized.includes("replace-me");
};

const isPositiveInteger = (value) => /^[1-9]\d*$/.test(String(value || "").trim());

const isEmailAddress = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || "").trim());

const requireRealAccountReadySnapshot = (errors, file, content) => {
  if (file !== "real-account-smoke.txt") return;
  const source = String(content || "");
  const normalized = source.toLowerCase();

  if (normalized.includes("optional readiness step skipped")) {
    errors.push(`${file} must not include skipped readiness steps`);
  }
  if (normalized.includes("real account readiness failed") || normalized.includes("error:")) {
    errors.push(`${file} must not include failure output`);
  }

  for (const moduleName of REQUIRED_REAL_ACCOUNT_MODULES) {
    const readyPattern = new RegExp(`"${moduleName}"\\s*:\\s*"ready"`);
    const nonReadyPattern = new RegExp(`"${moduleName}"\\s*:\\s*"(partial|blocked)"`);
    if (!readyPattern.test(source)) {
      errors.push(`${file} must include ready module snapshot for ${moduleName}`);
    }
    if (nonReadyPattern.test(source)) {
      errors.push(`${file} must not include non-ready module snapshot for ${moduleName}`);
    }
  }

  const seededAccountMatch = source.match(/"seededAccount"\s*:\s*\{([\s\S]*?)\}/);
  if (!seededAccountMatch) {
    errors.push(`${file} must include seededAccount block`);
  }

  const seededAccountSource = seededAccountMatch?.[1] || "";
  const seededIdentifierValues = new Map();
  for (const identifier of REQUIRED_REAL_ACCOUNT_IDENTIFIERS) {
    const identifierMatch = seededAccountSource.match(new RegExp(`"${identifier}"\\s*:\\s*"([^"]+)"`));
    if (!identifierMatch) {
      errors.push(`${file} must include seeded real-account identifier: ${identifier}`);
    } else {
      const value = identifierMatch[1].trim();
      seededIdentifierValues.set(identifier, value);
      if (isPlaceholderValue(value)) {
        errors.push(`${file} seeded ${identifier} must not be a placeholder`);
      }
    }
  }

  for (const identifier of NUMERIC_REAL_ACCOUNT_IDENTIFIERS) {
    const value = seededIdentifierValues.get(identifier);
    if (value && !isPlaceholderValue(value) && !isPositiveInteger(value)) {
      errors.push(`${file} seeded ${identifier} must be a positive integer`);
    }
  }

  const tradeEmail = seededIdentifierValues.get("tradeEmail");
  if (tradeEmail && !isPlaceholderValue(tradeEmail) && !isEmailAddress(tradeEmail)) {
    errors.push(`${file} seeded tradeEmail must be a valid email address`);
  }
  if (tradeEmail && /@example\.com$/i.test(tradeEmail)) {
    errors.push(`${file} seeded tradeEmail must not use example.com`);
  }
};

const requireRealAccountOrderDetailStep = (errors, file, content, manifest) => {
  if (file !== "real-account-smoke.txt") return;
  const commands = Array.isArray(manifest.commands) ? manifest.commands : [];
  const command = commands.find((item) => item?.name === "real-account-smoke");
  if (String(command?.command || "").includes("--check-order") && !String(content || "").includes("==> order-detail")) {
    errors.push(`${file} must include the order-detail step when --check-order is used`);
  }
};

const requireRealAccountStepLogs = (errors, file, content) => {
  if (file !== "real-account-smoke.txt") return;
  const source = String(content || "");
  for (const step of REQUIRED_REAL_ACCOUNT_STEPS) {
    if (!source.includes(`==> ${step}`)) {
      errors.push(`${file} must include the real-account smoke step: ${step}`);
    }
  }
};

const requireManifestCommandIncludes = (errors, manifest, commandName, requiredFragments = []) => {
  const commands = Array.isArray(manifest.commands) ? manifest.commands : [];
  const command = commands.find((item) => item?.name === commandName);
  if (!command || !String(command.command || "").trim()) {
    errors.push(`${commandName} command is required in launch-manifest.json`);
    return;
  }

  for (const fragment of requiredFragments) {
    if (!command.command.includes(fragment)) {
      errors.push(`${commandName} command must include ${fragment}`);
    }
  }
};

const rejectManifestCommandFragments = (errors, manifest, forbiddenFragments = []) => {
  const commands = Array.isArray(manifest.commands) ? manifest.commands : [];
  for (const command of commands) {
    const commandText = String(command?.command || "");
    for (const fragment of forbiddenFragments) {
      if (commandText.includes(fragment)) {
        errors.push(`${command?.name || "unnamed"} command must not include ${fragment}`);
      }
    }
  }
};

const requireCommandOutputFilesInEvidenceList = (errors, manifest, requiredEvidenceFiles) => {
  const commands = Array.isArray(manifest.commands) ? manifest.commands : [];
  for (const command of commands) {
    const outputFile = String(command?.outputFile || "").trim();
    if (outputFile && !requiredEvidenceFiles.includes(outputFile)) {
      errors.push(`requiredEvidenceFiles must include outputFile ${outputFile} from ${command?.name || "unnamed"} command`);
    }
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
  requireNonDocumentationUrl(errors, manifest, "baseUrl");

  const requiredEvidenceFiles = Array.isArray(manifest.requiredEvidenceFiles) ? manifest.requiredEvidenceFiles : [];
  if (!requiredEvidenceFiles.length) {
    errors.push("requiredEvidenceFiles must list launch command output files");
  }
  for (const file of REQUIRED_LAUNCH_EVIDENCE_FILES) {
    if (!requiredEvidenceFiles.includes(file)) {
      errors.push(`requiredEvidenceFiles must include ${file}`);
    }
  }
  requireCommandOutputFilesInEvidenceList(errors, manifest, requiredEvidenceFiles);

  requireManifestCommandIncludes(errors, manifest, "launch-readiness", [
    "--include-db-migrations",
    "--include-backend-prod-config",
    "--include-backend-prod-env",
    "--include-launch-env-alignment",
    "--include-admin-check",
    "--include-admin-build",
    "--include-backend-build",
    "--include-live-business-smoke",
    "--include-order-live-smoke",
    "--include-real-account-smoke",
    "--real-account-check-order",
  ]);
  requireManifestCommandIncludes(errors, manifest, "real-account-smoke", [
    "test:smoke:real-account",
    "--env-file",
    "--check-order",
  ]);
  rejectManifestCommandFragments(errors, manifest, ["--allow-placeholders"]);

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
    requireRealAccountReadySnapshot(errors, file, content);
    requireRealAccountStepLogs(errors, file, content);
    requireRealAccountOrderDetailStep(errors, file, content, manifest);
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
