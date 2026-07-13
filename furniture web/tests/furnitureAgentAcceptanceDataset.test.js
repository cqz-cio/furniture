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
const listFields = ["preconditions", "finalAssertions", "forbidden", "evidence", "manualSteps"];

function expectNonEmptyStrings(values) {
  expect(Array.isArray(values)).toBe(true);
  expect(values.length).toBeGreaterThan(0);
  for (const value of values) {
    expect(typeof value).toBe("string");
    expect(value.trim()).not.toBe("");
  }
}

function manualSections(guide) {
  const matches = [...guide.matchAll(/^### ([A-Z]+-\d{3}) (.+)$/gm)];
  return matches.map((match, index) => ({
    id: match[1],
    title: match[2].trim(),
    body: guide.slice(match.index, matches[index + 1]?.index ?? guide.length),
  }));
}

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
      for (const field of listFields) expectNonEmptyStrings(scenario[field]);
      expect(Array.isArray(scenario.turns)).toBe(true);
      expect(scenario.turns.length).toBeGreaterThan(0);
      for (const turn of scenario.turns) {
        expect(typeof turn.user).toBe("string");
        expect(turn.user.trim()).not.toBe("");
        expectNonEmptyStrings(turn.assertions);
      }
    }

    const recommendationScenarios = dataset.scenarios.filter(
      (scenario) => scenario.category === "single-turn-recommendation",
    );
    expect(recommendationScenarios.every((scenario) => scenario.turns.length === 1)).toBe(true);

    const behaviorSignatures = dataset.scenarios.map((scenario) =>
      JSON.stringify({
        assertions: scenario.turns.map((turn) => turn.assertions),
        finalAssertions: scenario.finalAssertions,
        forbidden: scenario.forbidden,
        evidence: scenario.evidence,
        manualSteps: scenario.manualSteps,
      }),
    );
    expect(new Set(behaviorSignatures).size).toBe(dataset.scenarios.length);
  });

  it("maps every JSON scenario into the manual guide", () => {
    const dataset = JSON.parse(readFileSync(fixtureUrl, "utf8"));
    const guide = readFileSync(guideUrl, "utf8");
    const sections = manualSections(guide);

    expect(sections).toHaveLength(dataset.scenarios.length);
    expect(sections.map((section) => section.id)).toEqual([
      ...dataset.scenarios.filter((scenario) => scenario.priority === "P0").map((scenario) => scenario.id),
      ...dataset.scenarios.filter((scenario) => scenario.priority === "P1").map((scenario) => scenario.id),
      ...dataset.scenarios.filter((scenario) => scenario.priority === "P2").map((scenario) => scenario.id),
    ]);

    for (const scenario of dataset.scenarios) {
      const matches = sections.filter((section) => section.id === scenario.id);
      expect(matches).toHaveLength(1);
      const section = matches[0];
      expect(section.title).toBe(scenario.title);
      expect(section.body).toContain(`**优先级：** ${scenario.priority}`);
      for (const turn of scenario.turns) {
        expect(section.body).toContain(`> ${turn.user}`);
        for (const assertion of turn.assertions) expect(section.body).toContain(`- ${assertion}`);
      }
      for (const field of listFields) {
        for (const value of scenario[field]) expect(section.body).toContain(`- ${value}`);
      }
    }
  });
});
