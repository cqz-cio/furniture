import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

describe("US postal region import script", () => {
  it("converts a ZIP CSV export into a front-end data module", () => {
    const directory = mkdtempSync(join(tmpdir(), "us-postal-regions-"));
    const inputPath = join(directory, "regions.csv");
    const outputPath = join(directory, "usPostalRegions.js");

    writeFileSync(inputPath, "zip,city,state\n02116,Boston,MA\n94105,San Francisco,CA\n", "utf8");

    execFileSync("node", ["scripts/import-us-postal-regions.mjs", inputPath, outputPath], {
      cwd: process.cwd(),
      stdio: "pipe",
    });

    const output = readFileSync(outputPath, "utf8");
    expect(output).toContain("export const US_POSTAL_REGIONS =");
    expect(output).toContain('postalCode: "02116"');
    expect(output).toContain('city: "San Francisco"');
    expect(output).toContain('state: "CA"');

    rmSync(directory, { recursive: true, force: true });
  });
});
