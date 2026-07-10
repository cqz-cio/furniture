import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");

const readCssBlock = (selector, source = css) => {
  const start = source.indexOf(`${selector} {`);
  const end = source.indexOf("\n}", start);
  return source.slice(start, end + 2);
};

describe("product detail gallery layout", () => {
  it("caps the shared desktop gallery between 420px and 520px", () => {
    const gallery = readCssBlock(".product-gallery-main");

    expect(gallery).toContain("height: clamp(420px, 36vw, 520px);");
    expect(gallery).toContain("aspect-ratio: 4 / 3;");
  });

  it("keeps the existing mobile gallery height", () => {
    const mobile = css.slice(css.indexOf("@media (max-width: 900px)"));
    const gallery = readCssBlock(".product-gallery-main", mobile);

    expect(gallery).toContain("height: 360px;");
    expect(gallery).toContain("aspect-ratio: auto;");
  });
});
