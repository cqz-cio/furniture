import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("furniture assistant panel", () => {
  it("mounts the assistant from the app shell", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain('import FurnitureAssistantPanel from "./components/FurnitureAssistantPanel.vue";');
    expect(source).toContain("<FurnitureAssistantPanel");
    expect(source).toContain('@add-to-cart="addToCart"');
  });

  it("defines a polished draggable launcher plus draggable and resizable dialog", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("furniture-assistant-position");
    expect(source).toContain("furniture-assistant-panel-state");
    expect(source).toContain("assistant-avatar-icon");
    expect(source).toContain("pointerdown");
    expect(source).toContain("pointermove");
    expect(source).toContain("setPointerCapture");
    expect(source).toContain("startPanelDrag");
    expect(source).toContain("movePanelDrag");
    expect(source).toContain("startPanelResize");
    expect(source).toContain("movePanelResize");
    expect(source).toContain("panelStyle");
    expect(source).toContain('role="dialog"');
    expect(source).toContain('aria-labelledby="furniture-assistant-title"');
    expect(source).toContain("assistantProductCards");
    expect(source).toContain('emit("add-to-cart"');
  });

  it("styles the floating assistant affordance and resize surface", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".furniture-assistant-launcher");
    expect(source).toContain(".furniture-assistant-panel");
    expect(source).toContain(".furniture-assistant-resize-handle");
    expect(source).toContain("cursor: nwse-resize;");
    expect(source).toContain("cursor: move;");
    expect(source).toContain("min-width: 320px;");
    expect(source).toContain("max-height: calc(100dvh - 32px);");
  });

  it("adds assistant copy to every supported locale", () => {
    const source = readSource("../src/i18n.js");

    expect(source).toContain("assistantMessages");
    expect(source).toContain("assistant: {");
    expect(source).toContain("launcherLabel:");
    expect(source).toContain("askPlaceholder:");
  });
});
