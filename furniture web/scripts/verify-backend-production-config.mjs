import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BACKEND_RESOURCE_DIR = resolve(
  process.cwd(),
  "..",
  "yudao电商管理平台前后端",
  "yudao-cloud",
  "yudao-server",
  "src",
  "main",
  "resources",
);

const readResource = (name) => readFileSync(resolve(BACKEND_RESOURCE_DIR, name), "utf8").replace(/\r\n/g, "\n");

const has = (source, value) => source.includes(value);

export const verifyBackendProductionConfig = () => {
  const errors = [];
  const appPath = resolve(BACKEND_RESOURCE_DIR, "application.yaml");
  const prodPath = resolve(BACKEND_RESOURCE_DIR, "application-prod.yaml");

  if (!existsSync(appPath)) errors.push(`Missing backend application.yaml: ${appPath}`);
  if (!existsSync(prodPath)) errors.push(`Missing backend production profile: ${prodPath}`);
  if (errors.length) return { ok: false, errors };

  const appSource = readResource("application.yaml");
  const prodSource = readResource("application-prod.yaml");

  if (/profiles:\s*\n\s*active:\s*local\b/.test(appSource)) {
    errors.push("Backend application.yaml must not hard-code spring.profiles.active=local.");
  }
  if (!has(appSource, "active: ${SPRING_PROFILES_ACTIVE:}")) {
    errors.push("Backend application.yaml must read spring.profiles.active from SPRING_PROFILES_ACTIVE.");
  }

  const requiredRuntimeValues = [
    "${YUDAO_DB_URL",
    "${YUDAO_DB_USERNAME",
    "${YUDAO_DB_PASSWORD",
    "${YUDAO_REDIS_HOST",
    "${YUDAO_REDIS_PORT",
    "${YUDAO_PAY_ORDER_NOTIFY_URL",
    "${YUDAO_PAY_REFUND_NOTIFY_URL",
    "${YUDAO_PAY_TRANSFER_NOTIFY_URL",
    "${YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY",
  ];

  for (const value of requiredRuntimeValues) {
    if (!has(prodSource, value)) errors.push(`application-prod.yaml must use runtime value ${value}.`);
  }

  const requiredProductionGuards = [
    /mock-enable:\s*false\b/,
    /api-docs:\s*\n\s*enabled:\s*false\b/,
    /swagger-ui:\s*\n\s*enabled:\s*false\b/,
    /knife4j:\s*\n\s*enable:\s*false\b/,
    /exposure:\s*\n\s*include:\s*'health,info'/,
  ];

  for (const guard of requiredProductionGuards) {
    if (!guard.test(prodSource)) errors.push(`application-prod.yaml is missing production guard ${guard}.`);
  }

  const forbiddenProdValues = [
    "jdbc:mysql://127.0.0.1",
    "include: '*'",
  ];

  for (const value of forbiddenProdValues) {
    if (has(prodSource, value)) errors.push(`application-prod.yaml must not contain ${value}.`);
  }
  if (/username:\s*root\b/.test(prodSource)) errors.push("application-prod.yaml must not use root database user.");
  if (/password:\s*123456\b/.test(prodSource)) errors.push("application-prod.yaml must not use default database password.");

  return {
    ok: errors.length === 0,
    errors,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const result = verifyBackendProductionConfig();
  if (result.ok) {
    console.log("Backend production config check passed.");
  } else {
    console.error("Backend production config check failed:");
    result.errors.forEach((error) => console.error(`- ${error}`));
    process.exitCode = 1;
  }
}
