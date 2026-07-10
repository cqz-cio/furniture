import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");
const page = readFileSync(new URL("../src/pages/SofaPdpPage.vue", import.meta.url), "utf8").replace(/\r\n/g, "\n");

const readCssBlock = (selector, source = css) => {
  const start = source.indexOf(`${selector} {`);
  if (start < 0) return "";

  const openingBrace = source.indexOf("{", start);
  let depth = 0;

  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === "{") depth += 1;
    if (source[index] === "}") depth -= 1;
    if (depth === 0) return source.slice(start, index + 1);
  }

  return "";
};

describe("product detail gallery layout", () => {
  it("caps the shared desktop gallery between 420px and 520px", () => {
    const gallery = readCssBlock(".product-gallery-main");

    expect(gallery).toContain("height: clamp(420px, 36vw, 520px);");
    expect(gallery).toContain("aspect-ratio: 4 / 3;");
  });

  it("keeps the existing mobile gallery height", () => {
    const mobile = readCssBlock("@media (max-width: 900px)");
    const gallery = readCssBlock(".product-gallery-main", mobile);

    expect(gallery).toContain("height: 360px;");
    expect(gallery).toContain("aspect-ratio: auto;");
  });

  it("applies the shared gallery selector in the common product detail page", () => {
    expect(page).toContain('class="product-gallery-main"');
  });
});
