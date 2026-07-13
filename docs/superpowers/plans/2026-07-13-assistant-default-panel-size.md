# Assistant Default Panel Size Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the desktop furniture assistant open at the approved 656px-wide, near-full-height size while preserving existing resizing and mobile behavior.

**Architecture:** Keep the existing `FurnitureAssistantPanel.vue` state and `getDefaultPanelState` viewport clamping. Change only the desktop default constants so the helper receives a 656px width and the current viewport's available height; preserve the 24px margin and the existing mobile CSS override.

**Tech Stack:** Vue 3, JavaScript, Vitest, Vite

## Global Constraints

- Desktop default width is `656px`.
- Desktop default right, top, and bottom margins are `24px`.
- Desktop default height uses the available viewport height, equivalent to `calc(100dvh - 48px)`.
- Width and height remain clamped to the viewport.
- Existing drag and eight-direction resize behavior remains unchanged.
- Existing mobile bottom-sheet behavior at `640px` and below remains unchanged.
- No chat, product, API, launcher, typography, color, or mobile composition changes.

---

### Task 1: Update the desktop default panel geometry

**Files:**
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`

**Interfaces:**
- Consumes: `getDefaultPanelState(viewport, { width, height, margin })` from `src/utils/assistantFloatingPosition.js`.
- Produces: `defaultPanelState()` with a preferred width of `656` and preferred height equal to `viewport.height - PANEL_MARGIN * 2`, still clamped by `getDefaultPanelState`.

- [ ] **Step 1: Write the failing regression assertions**

Add a focused test to `furniture web/tests/furnitureAssistantPanel.test.js`:

```js
it("opens at the approved desktop concierge size", () => {
  const source = readSource("../src/components/FurnitureAssistantPanel.vue");

  expect(source).toContain("const PANEL_DEFAULT_WIDTH = 656;");
  expect(source).toContain("height: viewport.height - PANEL_MARGIN * 2");
  expect(source).toContain("const PANEL_MARGIN = 24;");
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
npm test -- --run tests/furnitureAssistantPanel.test.js
```

Expected: FAIL because the component still contains `const PANEL_DEFAULT_WIDTH = 460;` and passes the fixed `PANEL_DEFAULT_HEIGHT` value.

- [ ] **Step 3: Implement the approved default dimensions**

In `furniture web/src/components/FurnitureAssistantPanel.vue`, replace the fixed desktop height constant and update `defaultPanelState()`:

```js
const PANEL_DEFAULT_WIDTH = 656;
const PANEL_MIN_WIDTH = 320;
const PANEL_MIN_HEIGHT = 420;
const PANEL_MARGIN = 24;

const defaultPanelState = () => {
  const viewport = viewportSize();

  return getDefaultPanelState(viewport, {
    width: PANEL_DEFAULT_WIDTH,
    height: viewport.height - PANEL_MARGIN * 2,
    margin: PANEL_MARGIN,
  });
};
```

Remove `PANEL_DEFAULT_HEIGHT` because the default height is now derived from the live viewport.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
npm test -- --run tests/furnitureAssistantPanel.test.js
```

Expected: all tests in `furnitureAssistantPanel.test.js` PASS.

- [ ] **Step 5: Run full regression verification**

Run:

```powershell
npm test
npm run build
```

Expected: the complete Vitest suite passes and Vite produces a successful production build.

- [ ] **Step 6: Verify repository scope and commit**

Run:

```powershell
git diff --check
git status --short
git add "furniture web/tests/furnitureAssistantPanel.test.js" "furniture web/src/components/FurnitureAssistantPanel.vue"
git commit -m "fix(ui): enlarge default assistant panel"
```

Expected: only the test and component implementation are included in the implementation commit; `git diff --check` reports no whitespace errors.
