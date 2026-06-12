import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("header global menu assets", () => {
  it("renders buyer-facing menu images instead of extraction placeholders", () => {
    const header = readSource("../src/components/RhHeader.vue");
    const styles = readSource("../src/styles.css");

    expect(header).toContain("generatedGlobalMenuImage(index)");
    expect(header).toContain("global-menu-image");
    expect(header).not.toContain("ImageSpecPlaceholder");
    expect(header).not.toContain("panel.spec");
    expect(styles).toContain(".global-menu-image");
    expect(styles).not.toContain(".global-menu-spec");
  });
});
