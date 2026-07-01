import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_ENV_FILE = ".env.production";
const DEFAULT_SMOKE_ENV_FILE = ".env.launch-smoke";
const DEFAULT_BACKEND_ENV_FILE = ".env.backend-production";

const safeTimestamp = (iso) => String(iso || new Date().toISOString()).replace(/[:.]/g, "-");

const getGitCommitSha = () => {
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], {
      cwd: process.cwd(),
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return "";
  }
};

export const parseLaunchEvidenceArgs = (argv = [], env = process.env) => {
  const createdAt = env.LAUNCH_EVIDENCE_CREATED_AT || new Date().toISOString();
  const options = {
    dir: env.LAUNCH_EVIDENCE_DIR || `launch-evidence/${safeTimestamp(createdAt)}`,
    commitSha: env.LAUNCH_COMMIT_SHA || "",
    imageTag: env.LAUNCH_IMAGE_TAG || "",
    imageDigest: env.LAUNCH_IMAGE_DIGEST || "",
    baseUrl: env.LAUNCH_HEALTH_BASE_URL || "",
    envFile: env.LAUNCH_ENV_FILE || DEFAULT_ENV_FILE,
    smokeEnvFile: env.LAUNCH_SMOKE_ENV_FILE || DEFAULT_SMOKE_ENV_FILE,
    backendEnvFile: env.LAUNCH_BACKEND_ENV_FILE || DEFAULT_BACKEND_ENV_FILE,
    createdAt,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    const readValue = () => {
      index += 1;
      return argv[index] || "";
    };

    if (arg === "--dir") options.dir = readValue();
    else if (arg.startsWith("--dir=")) options.dir = arg.slice("--dir=".length);
    else if (arg === "--commit-sha") options.commitSha = readValue();
    else if (arg.startsWith("--commit-sha=")) options.commitSha = arg.slice("--commit-sha=".length);
    else if (arg === "--image-tag") options.imageTag = readValue();
    else if (arg.startsWith("--image-tag=")) options.imageTag = arg.slice("--image-tag=".length);
    else if (arg === "--image-digest") options.imageDigest = readValue();
    else if (arg.startsWith("--image-digest=")) options.imageDigest = arg.slice("--image-digest=".length);
    else if (arg === "--base-url") options.baseUrl = readValue();
    else if (arg.startsWith("--base-url=")) options.baseUrl = arg.slice("--base-url=".length);
    else if (arg === "--launch-env-file" || arg === "--env-file") options.envFile = readValue() || DEFAULT_ENV_FILE;
    else if (arg.startsWith("--launch-env-file=")) options.envFile = arg.slice("--launch-env-file=".length);
    else if (arg.startsWith("--env-file=")) options.envFile = arg.slice("--env-file=".length);
    else if (arg === "--launch-smoke-env-file" || arg === "--smoke-env-file") options.smokeEnvFile = readValue() || DEFAULT_SMOKE_ENV_FILE;
    else if (arg.startsWith("--launch-smoke-env-file=")) options.smokeEnvFile = arg.slice("--launch-smoke-env-file=".length);
    else if (arg.startsWith("--smoke-env-file=")) options.smokeEnvFile = arg.slice("--smoke-env-file=".length);
    else if (arg === "--backend-env-file") options.backendEnvFile = readValue() || DEFAULT_BACKEND_ENV_FILE;
    else if (arg.startsWith("--backend-env-file=")) options.backendEnvFile = arg.slice("--backend-env-file=".length);
  }

  return options;
};

const command = (name, commandText, outputFile) => ({
  name,
  command: commandText,
  outputFile,
});

export const buildLaunchEvidenceManifest = (options = {}) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const smokeEnvFile = options.smokeEnvFile || DEFAULT_SMOKE_ENV_FILE;
  const backendEnvFile = options.backendEnvFile || DEFAULT_BACKEND_ENV_FILE;
  const baseUrl = options.baseUrl || "https://shop.example.com";

  const commands = [
    command("production-env", `npm.cmd run verify:production-env -- --env-file ${envFile}`, "production-env.txt"),
    command("launch-smoke-env", `npm.cmd run verify:launch-smoke-env -- --env-file ${smokeEnvFile}`, "launch-smoke-env.txt"),
    command("backend-production-env", `npm.cmd run verify:backend-production-env -- --env-file ${backendEnvFile}`, "backend-production-env.txt"),
    command("backend-production-config", "npm.cmd run verify:backend-production-config", "backend-production-config.txt"),
    command(
      "launch-env-alignment",
      `npm.cmd run verify:launch-env-alignment -- --env-file ${envFile} --smoke-env-file ${smokeEnvFile} --backend-env-file ${backendEnvFile} --base-url ${baseUrl}`,
      "launch-env-alignment.txt",
    ),
    command("db-migrations", "npm.cmd run verify:db-migrations", "db-migrations.txt"),
    command(
      "launch-readiness",
      `npm.cmd run verify:launch-readiness -- --env-file ${envFile} --smoke-env-file ${smokeEnvFile} --include-db-migrations --include-backend-prod-config --include-backend-prod-env --backend-env-file ${backendEnvFile} --include-launch-env-alignment --base-url ${baseUrl} --include-live-business-smoke --include-order-live-smoke`,
      "launch-readiness.txt",
    ),
    command("order-create-smoke", `npm.cmd run test:smoke:order-live -- --env-file ${smokeEnvFile} --create-order`, "order-create-smoke.txt"),
    command("post-deploy-health", `npm.cmd run test:deploy:health -- --base-url ${baseUrl}`, "post-deploy-health.txt"),
  ];

  return {
    createdAt: options.createdAt || new Date().toISOString(),
    commitSha: options.commitSha || "",
    imageTag: options.imageTag || "",
    imageDigest: options.imageDigest || "",
    baseUrl,
    envFile,
    smokeEnvFile,
    backendEnvFile,
    commands,
    requiredEvidenceFiles: commands.map((item) => item.outputFile),
    notes: [
      "Paste command output into the matching .txt file.",
      "Attach browser screenshots and registry digest evidence alongside this manifest.",
      "Do not commit real launch evidence, tokens, screenshots, or production identifiers.",
    ],
  };
};

const readmeFor = (manifest) => `# Launch Evidence

Do not commit real launch evidence, tokens, screenshots, or production identifiers.

Commit SHA: ${manifest.commitSha || "(record before launch)"}
Image tag: ${manifest.imageTag || "(record before deploy)"}
Image digest: ${manifest.imageDigest || "(record after push)"}
Base URL: ${manifest.baseUrl}
Backend env file: ${manifest.backendEnvFile}

## Required Command Outputs

${manifest.commands.map((item) => `- ${item.outputFile}: \`${item.command}\``).join("\n")}

Paste command output here in the matching files. Add screenshots and registry evidence to this folder before launch sign-off.
`;

export const createLaunchEvidenceBundle = (options = {}) => {
  const absoluteDir = resolve(process.cwd(), options.dir || `launch-evidence/${safeTimestamp(options.createdAt)}`);
  const manifest = buildLaunchEvidenceManifest({
    ...options,
    commitSha: options.commitSha || getGitCommitSha(),
  });

  mkdirSync(absoluteDir, { recursive: true });
  const manifestPath = join(absoluteDir, "launch-manifest.json");
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  writeFileSync(join(absoluteDir, "README.md"), readmeFor(manifest), "utf8");

  for (const file of manifest.requiredEvidenceFiles) {
    const outputPath = join(absoluteDir, file);
    if (!existsSync(outputPath)) {
      writeFileSync(outputPath, `Paste command output here for ${file}.\n`, "utf8");
    }
  }

  return {
    dir: absoluteDir,
    manifestPath,
    manifest,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  try {
    const options = parseLaunchEvidenceArgs(process.argv.slice(2));
    const result = createLaunchEvidenceBundle(options);
    console.log(`Launch evidence bundle created: ${result.dir}`);
    console.log(`Manifest: ${result.manifestPath}`);
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
