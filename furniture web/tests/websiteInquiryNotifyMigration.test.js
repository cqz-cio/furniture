import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("V026 website inquiry notify migration", () => {
  it("widens notify params and installs the VANZ inquiry template", () => {
    const migration = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V026__website_inquiry_notify.sql",
    );

    expect(migration).toMatch(
      /MODIFY COLUMN `template_params` text[\s\S]*NOT NULL/i,
    );
    expect(migration).toContain("'vanz_website_inquiry'");
    expect(migration).toContain("'VANZ Website'");
    expect(migration).toContain("{submittedAt}");
    expect(migration).toContain("{message}");
    expect(migration).toMatch(/WHERE NOT EXISTS[\s\S]*`system_notify_template`/i);
  });

  it("keeps the generated baseline section byte-equivalent to V026", () => {
    const migration = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V026__website_inquiry_notify.sql",
    ).replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql",
    ).replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V026__website_inquiry_notify.sql\n";
    const start = baseline.indexOf(marker);
    const sectionStart = start + marker.length;
    const nextMarkerOffset = baseline.slice(sectionStart).search(
      /\n-- BEGIN (?:V\d{3}__|Oakved demo catalog)/,
    );
    const end = nextMarkerOffset < 0 ? -1 : sectionStart + nextMarkerOffset;

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(sectionStart, end).replace(/\s+$/, "") + "\n")
      .toBe(migration);
  });
});
