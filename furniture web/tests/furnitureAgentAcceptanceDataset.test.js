import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const fixtureUrl = new URL("../fixtures/furniture-agent-acceptance.json", import.meta.url);
const guideUrl = new URL("../../docs/testing/furniture-agent-comprehensive-manual.md", import.meta.url);
const categoryPrefixes = new Map([
  ["single-turn-recommendation", "REC"],
  ["multi-turn-requirements", "REQ"],
  ["conversation-memory", "MEM"],
  ["product-and-erp-truth", "ERP"],
  ["model-and-fallback", "MOD"],
  ["language-and-input-boundaries", "INP"],
  ["security-and-privacy", "SEC"],
  ["unsupported-capabilities", "CAP"],
]);

describe("furniture Agent acceptance dataset", () => {
  it("has comprehensive, uniquely identified and executable scenarios", () => {
    const dataset = JSON.parse(readFileSync(fixtureUrl, "utf8"));
    const ids = dataset.scenarios.map((scenario) => scenario.id);
    const turnCount = dataset.scenarios.reduce((sum, scenario) => sum + scenario.turns.length, 0);

    expect(dataset.version).toBe("1.0.0");
    expect(dataset.generatedAt).toBe("2026-07-13");
    expect(dataset.scope).toBe("current-furniture-agent");
    expect(dataset.scenarios.length).toBeGreaterThanOrEqual(40);
    expect(turnCount).toBeGreaterThanOrEqual(100);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(dataset.scenarios.map((scenario) => scenario.category))).toEqual(
      new Set(categoryPrefixes.keys()),
    );

    for (const scenario of dataset.scenarios) {
      expect(scenario.id.trim()).not.toBe("");
      expect(scenario.id).toMatch(/^[A-Z]+-\d{3}$/);
      expect(scenario.id.startsWith(`${categoryPrefixes.get(scenario.category)}-`)).toBe(true);
      expect(scenario.category.trim()).not.toBe("");
      expect(["P0", "P1", "P2"]).toContain(scenario.priority);
      expect(scenario.title.trim()).not.toBe("");
      for (const field of ["preconditions", "turns", "finalAssertions", "forbidden", "evidence", "manualSteps"]) {
        expect(scenario[field].length).toBeGreaterThan(0);
      }
      for (const turn of scenario.turns) {
        expect(turn.user.trim()).not.toBe("");
        expect(turn.assertions.length).toBeGreaterThan(0);
      }
    }
  });

  it("maps every JSON scenario into the manual guide", () => {
    const dataset = JSON.parse(readFileSync(fixtureUrl, "utf8"));
    const guide = readFileSync(guideUrl, "utf8");

    for (const scenario of dataset.scenarios) {
      expect(guide).toContain(`### ${scenario.id} `);
      for (const turn of scenario.turns) expect(guide).toContain(`> ${turn.user}`);
    }
  });
});
