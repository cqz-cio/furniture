import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readServicesReadme = () => readFileSync(new URL("../src/services/README.md", import.meta.url), "utf8");

describe("service module documentation", () => {
  it("documents the Yudao service import boundaries", () => {
    const source = readServicesReadme();

    [
      "yudaoAuthApi.js",
      "yudaoCartApi.js",
      "yudaoMemberApi.js",
      "yudaoOrderApi.js",
      "yudaoProductApi.js",
      "yudaoRequest.js",
      "yudaoMappers.js",
      "yudaoClient.js",
      "checkoutErrors.js",
      "checkoutRecovery.js",
    ].forEach((moduleName) => {
      expect(source).toContain(moduleName);
    });

    expect(source).toContain("New application code should import from the domain module");
    expect(source).toContain("Use yudaoClient.js only for backwards compatibility");
  });
});
