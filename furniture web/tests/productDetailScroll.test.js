import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const readSource = (relativePath) => fs.readFileSync(path.resolve(process.cwd(), relativePath), "utf8");

describe("product detail scroll restoration", () => {
  it("starts each in-app product detail navigation at the top of the page", () => {
    const source = readSource("src/App.vue");

    expect(source).toContain('if (page !== "sofa-pdp") return;');
    expect(source).toContain('window.scrollTo({ top: 0, left: 0, behavior: "auto" });');
    expect(source).toContain("resetProductDetailScroll(nextPage);");
  });
});
