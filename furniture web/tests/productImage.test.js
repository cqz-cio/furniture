import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/components/ProductImage.vue", import.meta.url), "utf8");

describe("ProductImage external image fallback", () => {
  it("tracks load failures and hides a broken image", () => {
    expect(source).toContain("const failed = ref(false)");
    expect(source).toContain("watch(() => props.src");
    expect(source).toContain('@error="failed = true"');
    expect(source).toContain('v-if="src && !failed"');
  });
});
