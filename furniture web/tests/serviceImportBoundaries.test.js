import { readdirSync, readFileSync, statSync } from "node:fs";
import { relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const srcRoot = fileURLToPath(new URL("../src", import.meta.url));

const collectSourceFiles = (directory) =>
  readdirSync(directory).flatMap((entry) => {
    const path = resolve(directory, entry);
    if (statSync(path).isDirectory()) return collectSourceFiles(path);
    return /\.(js|vue)$/.test(entry) ? [path] : [];
  });

describe("service import boundaries", () => {
  it("keeps application source imports on domain API modules instead of the yudaoClient facade", () => {
    const offenders = collectSourceFiles(srcRoot)
      .filter((file) => readFileSync(file, "utf8").includes("services/yudaoClient.js"))
      .map((file) => relative(srcRoot, file).replace(/\\/g, "/"));

    expect(offenders).toEqual([]);
  });
});
