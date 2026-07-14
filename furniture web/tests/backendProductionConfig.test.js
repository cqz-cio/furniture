import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const backendResourceUrl = (path) =>
  new URL(`../../yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/${path}`, import.meta.url);

const readBackendResource = (path) => readFileSync(backendResourceUrl(path), "utf8").replace(/\r\n/g, "\n");

describe("backend production configuration", () => {
  it("does not hard-code the server to the local Spring profile", () => {
    const source = readBackendResource("application.yaml");

    expect(source).not.toMatch(/profiles:\s*\n\s*active:\s*local\b/);
    expect(source).toContain("active: ${SPRING_PROFILES_ACTIVE:}");
  });

  it("exposes a repeatable backend production config verifier", () => {
    const packageJson = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));

    expect(packageJson.scripts["verify:backend-production-config"]).toBe(
      "node scripts/verify-backend-production-config.mjs",
    );
  });

  it("provides a production profile that uses runtime environment values", () => {
    const prodUrl = backendResourceUrl("application-prod.yaml");

    expect(existsSync(prodUrl)).toBe(true);

    const source = readBackendResource("application-prod.yaml");
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
      expect(source).toContain(value);
    }
  });

  it("keeps production mock login and broad actuator exposure disabled", () => {
    const source = readBackendResource("application-prod.yaml");

    expect(source).toMatch(/mock-enable:\s*false\b/);
    expect(source).toMatch(/api-docs:\s*\n\s*enabled:\s*false\b/);
    expect(source).toMatch(/swagger-ui:\s*\n\s*enabled:\s*false\b/);
    expect(source).toMatch(/knife4j:\s*\n\s*enable:\s*false\b/);
    expect(source).toMatch(/exposure:\s*\n\s*include:\s*'health,info'/);
    expect(source).not.toContain("include: '*'");
  });

  it("does not keep local database defaults in the production profile", () => {
    const source = readBackendResource("application-prod.yaml");

    expect(source).not.toContain("jdbc:mysql://127.0.0.1");
    expect(source).not.toMatch(/username:\s*root\b/);
    expect(source).not.toMatch(/password:\s*123456\b/);
  });
});
