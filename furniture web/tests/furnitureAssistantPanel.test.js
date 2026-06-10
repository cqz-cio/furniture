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

    expect(source).toContain("createMockAssistantResponse");
    expect(source).toContain("sendFurnitureAssistantMessage");
    expect(source).toContain("furniture-assistant-position");
    expect(source).not.toContain("furniture-assistant-panel-state");
    expect(source).toContain("assistant-avatar-icon");
    expect(source).toContain("pointerdown");
    expect(source).toContain("pointermove");
    expect(source).toContain("setPointerCapture");
    expect(source).toContain("startPanelDrag");
    expect(source).toContain("movePanelDrag");
    expect(source).toContain("startPanelResize");
    expect(source).toContain("movePanelResize");
    expect(source).toContain("resizeDirections");
    expect(source).toContain('"top", "right", "bottom", "left", "top-left", "top-right", "bottom-right", "bottom-left"');
    expect(source).toContain("direction,");
    expect(source).toContain(':data-resize-direction="direction"');
    expect(source).toContain('@pointerdown="startPanelResize(direction, $event)"');
    expect(source).toContain("panelStyle");
    expect(source).toContain('role="dialog"');
    expect(source).toContain('aria-labelledby="furniture-assistant-title"');
    expect(source).toContain("assistantResponse");
    expect(source).toContain('emit("add-to-cart"');
  });

  it("tracks user prompts, loading, error, and structured assistant results", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("createMockAssistantResponse");
    expect(source).toContain("const chatMessages = ref([");
    expect(source).toContain("const assistantResponse = ref(createMockAssistantResponse(\"\"))");
    expect(source).toContain("const isSubmitting = ref(false)");
    expect(source).toContain("const assistantError = ref(\"\")");
    expect(source).toContain("await sendFurnitureAssistantMessage(message)");
    expect(source).toContain("chatMessages.value.push({");
    expect(source).toContain('sender: "user"');
    expect(source).toContain('sender: "assistant"');
    expect(source).toContain("assistantResponse.value = response");
    expect(source).toContain("assistantError.value = caught.message");
    expect(source).toContain("assistantSources");
    expect(source).toContain('v-if="isSubmitting"');
    expect(source).toContain('v-if="assistantError"');
    expect(source).toContain('v-for="message in chatMessages"');
    expect(source).toContain('v-for="(product, index) in assistantProducts"');
    expect(source).toContain('v-for="source in assistantSources"');
    expect(source).toContain(':href="product.detailUrl"');
    expect(source).toContain(':disabled="isSubmitting || !draftMessage.trim()"');
  });

  it("keeps product recommendations as the assistant's automatic second message", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("assistantProducts.length");
    expect(source).toContain("assistant-chat-recommendations");
    expect(source).toContain("assistant-product-carousel");
    expect(source).toContain("assistant-product-list");
    expect(source).toContain("assistant-product-slide");
    expect(source).toContain("assistant-product-slide-active");
    expect(source).toContain("assistant-product-card");
    expect(source).toContain("formatAssistantPrice(product.price)");
    expect(source).toContain("activeProductIndex");
    expect(source).toContain("previousProductIndex");
    expect(source).toContain("productTransitionDirection");
    expect(source).toContain("productAnimationTimer");
    expect(source).toContain("assistant-carousel-forward");
    expect(source).toContain("assistant-carousel-backward");
    expect(source).toContain("assistant-product-slide-exiting");
    expect(source).toContain("showPreviousProduct");
    expect(source).toContain("showNextProduct");
    expect(source).toContain("assistant-carousel-indicators");
  });

  it("uses polished semi-transparent carousel arrow controls", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("assistant-carousel-arrow-icon");
    expect(source).toContain('viewBox="0 0 24 24"');
    expect(source).toContain('d="M14.5 6.5 9 12l5.5 5.5"');
    expect(source).toContain('d="M9.5 6.5 15 12l-5.5 5.5"');
    expect(styles).toContain("border-color: rgba(47, 49, 46, 0.24);");
    expect(styles).toContain("background: rgba(255, 255, 255, 0.62);");
    expect(styles).toContain("backdrop-filter: blur(8px);");
    expect(styles).toContain(".assistant-carousel-arrow-icon");
    expect(styles).toContain(".assistant-carousel-control:hover");
    expect(styles).toContain(".assistant-carousel-control:focus-visible");
  });

  it("renders assistant answer sources for knowledge-backed replies", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");
    const styles = readSource("../src/styles.css");

    expect(source).toContain("assistantSources");
    expect(source).toContain("assistant-source-list");
    expect(source).toContain("assistant-source-chip");
    expect(source).toContain("source.type");
    expect(source).toContain("source.name");
    expect(source).toContain('t("assistant.sourcesLabel")');
    expect(styles).toContain(".assistant-source-list");
    expect(styles).toContain(".assistant-source-chip");
  });


  it("resets the dialog to its default size whenever it is opened", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("const openPanel = () => {");
    expect(source).toContain("setPanelState(defaultPanelState());");
    expect(source).not.toContain("persistPanelState");
    expect(source).not.toContain("safeJsonWrite(PANEL_STORAGE_KEY");
  });

  it("renders the assistant as a social chat with avatars, names, and user replies", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("chatMessages");
    expect(source).toContain('sender: "assistant"');
    expect(source).toContain('sender: "user"');
    expect(source).toContain("AI小导购");
    expect(source).toContain('const userDisplayName = "我"');
    expect(source).toContain("assistant-chat-row");
    expect(source).toContain("assistant-chat-avatar");
    expect(source).toContain("assistant-chat-name");
    expect(source).toContain("assistant-chat-bubble");
    expect(source).toContain("assistant-chat-user");
    expect(source).toContain("assistant-chat-recommendations");
    expect(source).toContain("draftMessage.trim()");
  });

  it("presents the panel as a refined shopping chat surface", () => {
    const source = readSource("../src/components/FurnitureAssistantPanel.vue");

    expect(source).toContain("assistantPresenceLabel");
    expect(source).toContain("messageTimeLabel");
    expect(source).toContain("composerModeLabel");
    expect(source).toContain("assistant-panel-identity");
    expect(source).toContain("assistant-panel-avatar");
    expect(source).toContain("assistant-panel-presence");
    expect(source).toContain("assistant-chat-meta");
    expect(source).toContain("assistant-message-time");
    expect(source).toContain("assistant-composer-shell");
    expect(source).toContain("assistant-send-icon");
    expect(source).toContain("assistant-carousel-counter");
    expect(source).toContain("activeProductIndex + 1");
  });

  it("styles the floating assistant affordance and every resize surface", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".furniture-assistant-launcher");
    expect(source).toContain(".furniture-assistant-panel");
    expect(source).toContain(".furniture-assistant-resize-handle");
    expect(source).toContain('.furniture-assistant-resize-handle[data-resize-direction="top"]');
    expect(source).toContain('.furniture-assistant-resize-handle[data-resize-direction="right"]');
    expect(source).toContain('.furniture-assistant-resize-handle[data-resize-direction="bottom"]');
    expect(source).toContain('.furniture-assistant-resize-handle[data-resize-direction="left"]');
    expect(source).toContain("cursor: nwse-resize;");
    expect(source).toContain("cursor: ns-resize;");
    expect(source).toContain("cursor: ew-resize;");
    expect(source).toContain("cursor: move;");
    expect(source).toContain("min-width: 320px;");
    expect(source).toContain("max-height: calc(100dvh - 32px);");
  });

  it("styles the assistant thread as left and right chat bubbles", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".assistant-chat-row");
    expect(source).toContain(".assistant-chat-user");
    expect(source).toContain(".assistant-chat-avatar");
    expect(source).toContain(".assistant-chat-name");
    expect(source).toContain(".assistant-chat-bubble");
    expect(source).toContain(".assistant-chat-user .assistant-chat-bubble");
    expect(source).toContain(".assistant-chat-recommendations");
    expect(source).toContain(".assistant-product-carousel");
    expect(source).toContain(".assistant-product-slide-active");
    expect(source).toContain(".assistant-carousel-control");
    expect(source).toContain(".assistant-carousel-indicator-active");
    expect(source).toContain(".assistant-carousel-forward .assistant-product-slide-active");
    expect(source).toContain(".assistant-carousel-backward .assistant-product-slide-active");
    expect(source).toContain(".assistant-product-slide-exiting");
    expect(source).toContain("@keyframes assistantSlideInFromRight");
    expect(source).toContain("@keyframes assistantSlideOutToLeft");
    expect(source).toContain("@keyframes assistantSlideInFromLeft");
    expect(source).toContain("@keyframes assistantSlideOutToRight");
    expect(source).toContain("--assistant-product-card-width: 408px;");
    expect(source).toContain("--assistant-product-card-height: 410px;");
    expect(source).toContain(".assistant-chat-recommendations .assistant-chat-content");
    expect(source).toContain("flex-basis: min(var(--assistant-product-card-width), calc(100% - 44px));");
    expect(source).toContain("width: min(var(--assistant-product-card-width), 100%);");
    expect(source).toContain("height: var(--assistant-product-card-height);");
    expect(source).toContain("inline-size: 100%;");
    expect(source).toContain("block-size: 100%;");
    expect(source).toContain("grid-template-rows: 190px minmax(0, 1fr) 92px;");
  });

  it("styles the assistant window like a complete chat product", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".assistant-panel-identity");
    expect(source).toContain(".assistant-panel-avatar");
    expect(source).toContain(".assistant-panel-presence");
    expect(source).toContain(".assistant-presence-dot");
    expect(source).toContain(".assistant-chat-meta");
    expect(source).toContain(".assistant-message-time");
    expect(source).toContain(".assistant-chat-bubble::after");
    expect(source).toContain(".assistant-composer-shell");
    expect(source).toContain(".assistant-composer-mode");
    expect(source).toContain(".assistant-send-button");
    expect(source).toContain(".assistant-send-icon");
    expect(source).toContain(".assistant-carousel-counter");
  });

  it("adds assistant copy to every supported locale", () => {
    const source = readSource("../src/i18n.js");

    expect(source).toContain("assistantMessages");
    expect(source).toContain("assistant: {");
    expect(source).toContain("launcherLabel:");
    expect(source).toContain("askPlaceholder:");
    expect(source).toContain("loading:");
    expect(source).toContain("error:");
    expect(source).toContain("viewDetails:");
    expect(source).toContain("sourcesLabel:");
  });
});
