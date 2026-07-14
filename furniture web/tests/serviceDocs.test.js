import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readServicesReadme = () => readFileSync(new URL("../src/services/README.md", import.meta.url), "utf8");
const envExamplePath = new URL("../.env.example", import.meta.url);
const yudaoRequestPath = new URL("../src/services/yudaoRequest.js", import.meta.url);
const backendTradeAddressVerificationSqlPath = new URL(
  "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/trade-order-address-verification.sql",
  import.meta.url,
);
const backendMemberAddressVerificationSqlPath = new URL(
  "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/member-address-address-verification.sql",
  import.meta.url,
);
const backendAddressVerificationConfigPaths = [
  new URL(
    "../../yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml",
    import.meta.url,
  ),
  new URL(
    "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/resources/application-local.yaml",
    import.meta.url,
  ),
];

describe("service module documentation", () => {
  it("documents the Yudao service import boundaries", () => {
    const source = readServicesReadme();

    [
      "yudaoAuthApi.js",
      "yudaoCartApi.js",
      "yudaoMemberApi.js",
      "yudaoOrderApi.js",
      "yudaoPaymentApi.js",
      "yudaoProductApi.js",
      "yudaoRequest.js",
      "yudaoMappers.js",
      "yudaoClient.js",
      "checkoutErrors.js",
      "checkoutRecovery.js",
      "checkoutPayment.js",
      "checkoutAddressConfirmation.js",
      "orderAddressVerification.js",
      "usAddress.js",
      "usPostalRegions.js",
      "import-us-postal-regions.mjs",
      "member-address-address-verification.sql",
      "trade-order-address-verification.sql",
    ].forEach((moduleName) => {
      expect(source).toContain(moduleName);
    });

    expect(source).toContain("New application code should import from the domain module");
    expect(source).toContain("Use yudaoClient.js only for backwards compatibility");
    expect(source).toContain("VITE_YUDAO_APP_API_BASE");
    expect(source).toContain("VITE_YUDAO_APP_TENANT_ID");
    expect(source).toContain("VITE_YUDAO_US_DEFAULT_AREA_ID");
    expect(source).toContain("sourceWarningKey");
    expect(source).toContain("local ZIP, city, and state");
    expect(source).toContain("not a carrier deliverability confirmation");
    expect(source).toContain("backend-address-verification");
    expect(source).toContain("yudao.member.address-verification.google.api-key");
    expect(source).toContain("yudao.member.address-verification.google.enable-usps-cass");
    expect(source).toContain("VITE_ADDRESS_VERIFICATION_PATH");
    expect(source).toContain("VITE_ADDRESS_VERIFICATION_STATUS_PATH");
    expect(source).toContain("VITE_YUDAO_PAY_CHANNEL_CODE");
    expect(source).toContain("Yudao `url` display responses");
    expect(source).toContain("Yudao `form` display responses");
    expect(source).toContain("displayMode: `url`");
    expect(source).toContain("absolute `http` or `https` return URL");
  });

  it("provides a storefront environment example for checkout integrations", () => {
    expect(existsSync(envExamplePath)).toBe(true);

    const source = readFileSync(envExamplePath, "utf8");

    const runtimeEnvNames = [
      "VITE_YUDAO_APP_API_BASE",
      "VITE_YUDAO_APP_TENANT_ID",
      "VITE_YUDAO_US_DEFAULT_AREA_ID",
      "VITE_YUDAO_PAY_CHANNEL_CODE",
      "VITE_ADDRESS_VERIFICATION_PATH",
      "VITE_ADDRESS_VERIFICATION_STATUS_PATH",
    ];
    const requestSource = readFileSync(yudaoRequestPath, "utf8");

    runtimeEnvNames.forEach((envName) => {
      expect(source).toContain(envName);
    });
    expect(requestSource).toContain("VITE_YUDAO_APP_API_BASE");
    expect(requestSource).toContain("VITE_YUDAO_APP_TENANT_ID");
    expect(source).not.toContain("VITE_YUDAO_API_BASE_URL");
    expect(source).not.toContain("VITE_YUDAO_TENANT_ID");
  });

  it("documents backend address verification runtime configuration", () => {
    backendAddressVerificationConfigPaths.forEach((configPath) => {
      expect(existsSync(configPath)).toBe(true);
      const source = readFileSync(configPath, "utf8");

      expect(source).toContain("address-verification:");
      expect(source).toContain("api-key: ${YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY:}");
      expect(source).toContain("enable-usps-cass: true");
      expect(source).toContain("connect-timeout-millis: 3000");
      expect(source).toContain("read-timeout-millis: 5000");
    });
  });

  it("keeps backend address verification migration scripts available", () => {
    [
      {
        path: backendTradeAddressVerificationSqlPath,
        table: "trade_order",
      },
      {
        path: backendMemberAddressVerificationSqlPath,
        table: "member_address",
      },
    ].forEach(({ path, table }) => {
      expect(existsSync(path)).toBe(true);
      const source = readFileSync(path, "utf8");

      expect(source).toContain(`TABLE_NAME = '${table}'`);
      expect(source).toContain("COLUMN_NAME = 'address_verification'");
      expect(source).toContain("ADD COLUMN `address_verification` json");
    });
  });
});
