import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("generated baseline runtime compatibility", () => {
  const baseline = read(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql",
  );
  const v013 = read(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V013__statistics_commerce_dashboard.sql",
  );
  const v026 = read(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V026__seo_keyword_relevance_analysis.sql",
  );

  it("bounds first-install advisory lock names without changing V013", () => {
    expect(v013).toContain("CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2')");
    expect(baseline).not.toContain("CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2')");
    expect(baseline.match(/CONCAT\('oakved:stats:', LEFT\(SHA2\(DATABASE\(\), 256\), 40\)\)/g))
      .toHaveLength(2);
  });

  it("allocates V026 SEO menu IDs dynamically only in the first-install baseline", () => {
    expect(v026).toContain("SELECT 8112,'关键词分析'");
    expect(v026).toContain("SELECT 8110,'运行分析'");
    expect(v026).toContain("SELECT 8111,'分析查询'");
    expect(baseline).not.toContain("SELECT 8112,'关键词分析'");
    expect(baseline).not.toContain("SELECT 8110,'运行分析'");
    expect(baseline).not.toContain("SELECT 8111,'分析查询'");
    expect(baseline).toContain("SELECT '关键词分析'");
    expect(baseline).toContain("SELECT '运行分析'");
    expect(baseline).toContain("SELECT '分析查询'");
  });
});
