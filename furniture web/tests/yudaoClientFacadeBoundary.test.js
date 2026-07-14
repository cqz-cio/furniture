import { readdirSync, readFileSync, statSync } from "node:fs";
import { relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const testsRoot = fileURLToPath(new URL(".", import.meta.url));
const allowedFacadeTests = new Set(["yudaoClientFacade.test.js", "yudaoClientFacadeBoundary.test.js"]);

const collectTestFiles = (directory) =>
  readdirSync(directory).flatMap((entry) => {
    const path = resolve(directory, entry);
    if (statSync(path).isDirectory()) return collectTestFiles(path);
    return /\.test\.js$/.test(entry) ? [path] : [];
  });

describe("Yudao client facade test boundary", () => {
  it("keeps direct yudaoClient imports isolated to the facade compatibility test", () => {
    const offenders = collectTestFiles(testsRoot)
      .filter((file) => !allowedFacadeTests.has(relative(testsRoot, file).replace(/\\/g, "/")))
      .filter((file) => readFileSync(file, "utf8").includes("../src/services/yudaoClient.js"))
      .map((file) => relative(testsRoot, file).replace(/\\/g, "/"));

    expect(offenders).toEqual([]);
  });
});
