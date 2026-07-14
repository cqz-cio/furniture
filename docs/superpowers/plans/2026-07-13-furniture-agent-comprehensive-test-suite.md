# Furniture Agent Comprehensive Test Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a validated JSON acceptance dataset and matching Chinese manual test guide for the current furniture Agent.

**Architecture:** Treat the JSON dataset as the canonical scenario source and keep the manual guide synchronized through stable scenario IDs. Add one Vitest contract test that validates schema, coverage, uniqueness, priorities, turn counts, and manual-guide parity without introducing a new runtime dependency or test runner.

**Tech Stack:** JSON, Markdown, JavaScript, Vitest

## Global Constraints

- Produce both machine-readable JSON and a Chinese manual guide.
- Target approximately 40 scenarios and 100 user turns.
- Cover real model, Redis memory, product search, ERP truth, fallback, safety, multilingual, edge, and unsupported-capability paths.
- Use only `P0`, `P1`, and `P2` priorities.
- Validate live product and ERP facts dynamically instead of hard-coding names, prices, stock, or IDs.
- Do not implement a runner, modify Agent behavior, seed policies, add image upload, or implement RAG.

---

### Task 1: Define and validate the canonical JSON dataset

**Files:**
- Create: `furniture web/tests/furnitureAgentAcceptanceDataset.test.js`
- Create: `furniture web/fixtures/furniture-agent-acceptance.json`

**Interfaces:**
- Produces: JSON root fields `version`, `generatedAt`, `scope`, and `scenarios`.
- Produces: scenario fields `id`, `category`, `priority`, `title`, `preconditions`, `turns`, `finalAssertions`, `forbidden`, `evidence`, and `manualSteps`.
- Consumes: Vitest and Node.js `readFileSync`; no new dependency.

- [ ] **Step 1: Write the failing dataset contract test**

Create `furniture web/tests/furnitureAgentAcceptanceDataset.test.js`:

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const fixtureUrl = new URL("../fixtures/furniture-agent-acceptance.json", import.meta.url);
const guideUrl = new URL("../../docs/testing/furniture-agent-comprehensive-manual.md", import.meta.url);
const requiredCategories = [
  "single-turn-recommendation",
  "multi-turn-requirements",
  "conversation-memory",
  "product-and-erp-truth",
  "model-and-fallback",
  "language-and-input-boundaries",
  "security-and-privacy",
  "unsupported-capabilities",
];

describe("furniture Agent acceptance dataset", () => {
  it("has comprehensive, uniquely identified and executable scenarios", () => {
    const dataset = JSON.parse(readFileSync(fixtureUrl, "utf8"));
    const ids = dataset.scenarios.map((scenario) => scenario.id);
    const turnCount = dataset.scenarios.reduce((sum, scenario) => sum + scenario.turns.length, 0);

    expect(dataset.version).toBe("1.0.0");
    expect(dataset.scope).toBe("current-furniture-agent");
    expect(dataset.scenarios.length).toBeGreaterThanOrEqual(40);
    expect(turnCount).toBeGreaterThanOrEqual(100);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(dataset.scenarios.map((scenario) => scenario.category))).toEqual(new Set(requiredCategories));

    for (const scenario of dataset.scenarios) {
      expect(scenario.id).toMatch(/^[A-Z]+-\d{3}$/);
      expect(["P0", "P1", "P2"]).toContain(scenario.priority);
      expect(scenario.title.trim()).not.toBe("");
      expect(scenario.preconditions.length).toBeGreaterThan(0);
      expect(scenario.turns.length).toBeGreaterThan(0);
      expect(scenario.finalAssertions.length).toBeGreaterThan(0);
      expect(scenario.forbidden.length).toBeGreaterThan(0);
      expect(scenario.evidence.length).toBeGreaterThan(0);
      expect(scenario.manualSteps.length).toBeGreaterThan(0);
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
```

- [ ] **Step 2: Run the contract test and verify RED**

Run from `furniture web`:

```powershell
npm.cmd test -- --run tests/furnitureAgentAcceptanceDataset.test.js
```

Expected: FAIL with `ENOENT` because the fixture and manual guide do not exist.

- [ ] **Step 3: Create the canonical JSON dataset**

Create `furniture web/fixtures/furniture-agent-acceptance.json` with:

- Root values `version: "1.0.0"`, `generatedAt: "2026-07-13"`, and `scope: "current-furniture-agent"`.
- At least 40 scenarios and 100 total turns.
- Exactly the eight categories asserted by the contract test.
- Stable prefixes: `REC`, `REQ`, `MEM`, `ERP`, `MOD`, `INP`, `SEC`, and `CAP`.
- Copy-ready Chinese, English, and mixed-language user prompts.
- Per-turn behavioral assertions and final live-system evidence assertions.
- Explicit forbidden claims for invented products, prices, stock, policies, secrets, and unsupported capabilities.

- [ ] **Step 4: Parse and inspect the JSON**

Run from `furniture web`:

```powershell
node -e "const d=require('./fixtures/furniture-agent-acceptance.json'); console.log({scenarios:d.scenarios.length,turns:d.scenarios.reduce((n,s)=>n+s.turns.length,0),categories:[...new Set(d.scenarios.map(s=>s.category))]})"
```

Expected: valid JSON, at least 40 scenarios, at least 100 turns, and all eight required categories.

### Task 2: Produce the synchronized Chinese manual guide

**Files:**
- Create: `docs/testing/furniture-agent-comprehensive-manual.md`
- Test: `furniture web/tests/furnitureAgentAcceptanceDataset.test.js`

**Interfaces:**
- Consumes: every scenario ID and user turn from `furniture-agent-acceptance.json`.
- Produces: one `### <ID> <title>` section per scenario with copy-ready blockquoted prompts.

- [ ] **Step 1: Create the environment and evidence checklist**

Start the guide with:

```markdown
# 家具 Agent 综合人工测试手册

## 执行前准备

- 前端以 API 模式运行，禁止使用 mock。
- 后端、Redis、商品服务和 ERP 均可访问。
- 准备可以核对商品名称、SKU、价格和可售库存的只读接口或后台。
- 记录每个场景的 conversationId、响应 sources、商品结果和必要日志。
- P0 任一失败均阻断发布；P1 失败需评估；P2 记录为健壮性缺陷。
```

- [ ] **Step 2: Add every scenario in P0, P1, P2 order**

For every JSON scenario, write a section using this exact shape:

```markdown
### MEM-001 保留预算和材质条件

**优先级：** P0  
**前置条件：** 后端、Redis、商品服务和 ERP 可用。

**输入：**

> 我想要 8000 元以内的布艺沙发

> 奶油风，家里有猫

**预期：** 保留预算和材质，新增风格和宠物条件；推荐商品均来自真实商品系统。

**禁止：** 编造商品、价格、库存或内部 ID。

**证据：** Agent 响应、商品 API、ERP 库存、Redis 会话状态。
```

- [ ] **Step 3: Run the focused contract test and verify GREEN**

Run:

```powershell
npm.cmd test -- --run tests/furnitureAgentAcceptanceDataset.test.js
```

Expected: both dataset structure and manual parity tests PASS.

- [ ] **Step 4: Run full verification**

Run:

```powershell
npm.cmd test
git diff --check
git status --short
```

Expected: all frontend tests pass, no whitespace errors, and only the JSON dataset, manual guide, and dataset contract test are uncommitted.

- [ ] **Step 5: Commit the test suite**

Run:

```powershell
git add "furniture web/fixtures/furniture-agent-acceptance.json" "furniture web/tests/furnitureAgentAcceptanceDataset.test.js" "docs/testing/furniture-agent-comprehensive-manual.md"
git commit -m "test: add comprehensive furniture agent acceptance suite"
```

Expected: one commit containing the synchronized dataset, validator, and manual guide.
