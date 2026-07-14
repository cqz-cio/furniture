import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readBackendFile = (path) =>
  readFileSync(new URL(`../../yudao电商管理平台前后端/yudao-cloud/${path}`, import.meta.url), "utf8");

const tagValue = (source, tag) => source.match(new RegExp(`<${tag}>(.*?)</${tag}>`, "s"))?.[1].trim() || "";

const dependencyKeys = (pomSource) =>
  [...pomSource.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map(([, dependency]) => {
    const groupId = tagValue(dependency, "groupId");
    const artifactId = tagValue(dependency, "artifactId");
    const type = tagValue(dependency, "type") || "jar";
    const classifier = tagValue(dependency, "classifier");
    return `${groupId}:${artifactId}:${type}:${classifier}`;
  });

const duplicates = (items) => {
  const counts = new Map();
  for (const item of items) counts.set(item, (counts.get(item) || 0) + 1);
  return [...counts.entries()].filter(([, count]) => count > 1).map(([item]) => item);
};

describe("backend Maven POM quality gates", () => {
  it("does not declare duplicate dependencies in the Yudao gateway POM", () => {
    const keys = dependencyKeys(readBackendFile("yudao-gateway/pom.xml"));

    expect(duplicates(keys)).toEqual([]);
  });
});
