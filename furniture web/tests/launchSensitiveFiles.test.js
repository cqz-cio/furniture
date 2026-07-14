import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("launch-sensitive file protections", () => {
  it("keeps real launch env and evidence files out of git while allowing examples", () => {
    const rootGitignore = readSource("../../.gitignore");

    expect(rootGitignore).toContain("/furniture web/.env.production\n");
    expect(rootGitignore).toContain("/furniture web/.env.launch-smoke\n");
    expect(rootGitignore).toContain("/furniture web/.env.backend-production\n");
    expect(rootGitignore).toContain("/furniture web/launch-evidence/\n");
    expect(rootGitignore).toContain("!/furniture web/.env.production.example\n");
    expect(rootGitignore).toContain("!/furniture web/.env.launch-smoke.example\n");
    expect(rootGitignore).toContain("!/furniture web/.env.backend-production.example\n");
  });
});
