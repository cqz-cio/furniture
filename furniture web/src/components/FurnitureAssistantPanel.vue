<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "../i18n.js";
import { createMockAssistantResponse, sendFurnitureAssistantMessage } from "../services/furnitureAssistant.js";

const emit = defineEmits(["add-to-cart"]);

const { t } = useI18n();
const LAUNCHER_STORAGE_KEY = "furniture-assistant-position";
const LAUNCHER_SIZE = 60;
const LAUNCHER_MARGIN = 20;
const PANEL_DEFAULT_WIDTH = 520;
const PANEL_DEFAULT_HEIGHT = 640;
const PANEL_MIN_WIDTH = 320;
const PANEL_MIN_HEIGHT = 420;
const PANEL_MARGIN = 16;
const resizeDirections = ["top", "right", "bottom", "left", "top-left", "top-right", "bottom-right", "bottom-left"];

const open = ref(false);
const launcherPosition = ref({ left: 0, top: 0 });
const panelState = ref({ left: 0, top: 0, width: PANEL_DEFAULT_WIDTH, height: PANEL_DEFAULT_HEIGHT });
const launcherDragState = ref(null);
const panelDragState = ref(null);
const panelResizeState = ref(null);
const draftMessage = ref("");
const chatMessages = ref([
  {
    id: "assistant-welcome",
    sender: "assistant",
    content: t("assistant.welcome"),
  },
]);
const assistantResponse = ref(createMockAssistantResponse(""));
const isSubmitting = ref(false);
const assistantError = ref("");
const activeProductIndex = ref(0);
const previousProductIndex = ref(null);
const productTransitionDirection = ref("forward");
const productAnimationTimer = ref(null);

const assistantProducts = computed(() => assistantResponse.value?.products || []);
const assistantSources = computed(() => assistantResponse.value?.sources || []);
const productCount = computed(() => assistantProducts.value.length);
const assistantDisplayName = "AI小导购";
const userDisplayName = "我";
const assistantPresenceLabel = "在线服务";
const composerModeLabel = "家具顾问";
const messageTimeLabel = "刚刚";

const launcherStyle = computed(() => ({
  left: `${launcherPosition.value.left}px`,
  top: `${launcherPosition.value.top}px`,
}));

const panelStyle = computed(() => ({
  left: `${panelState.value.left}px`,
  top: `${panelState.value.top}px`,
  width: `${panelState.value.width}px`,
  height: `${panelState.value.height}px`,
}));

const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

const viewportSize = () => ({
  width: globalThis.window?.innerWidth || 1280,
  height: globalThis.window?.innerHeight || 720,
});

const launcherBounds = () => {
  const viewport = viewportSize();

  return {
    maxLeft: Math.max(LAUNCHER_MARGIN, viewport.width - LAUNCHER_SIZE - LAUNCHER_MARGIN),
    maxTop: Math.max(LAUNCHER_MARGIN, viewport.height - LAUNCHER_SIZE - LAUNCHER_MARGIN),
  };
};

const panelBounds = (width = panelState.value.width, height = panelState.value.height) => {
  const viewport = viewportSize();

  return {
    maxLeft: Math.max(PANEL_MARGIN, viewport.width - width - PANEL_MARGIN),
    maxTop: Math.max(PANEL_MARGIN, viewport.height - height - PANEL_MARGIN),
    maxWidth: Math.max(PANEL_MIN_WIDTH, viewport.width - PANEL_MARGIN * 2),
    maxHeight: Math.max(PANEL_MIN_HEIGHT, viewport.height - PANEL_MARGIN * 2),
  };
};

const safeJsonRead = (key) => {
  try {
    return JSON.parse(globalThis.localStorage?.getItem(key) || "null");
  } catch {
    return null;
  }
};

const safeJsonWrite = (key, value) => {
  try {
    globalThis.localStorage?.setItem(key, JSON.stringify(value));
  } catch {
    // Floating UI should continue to work when storage is unavailable.
  }
};

const setLauncherPosition = (left, top) => {
  const bounds = launcherBounds();
  launcherPosition.value = {
    left: clamp(left, LAUNCHER_MARGIN, bounds.maxLeft),
    top: clamp(top, LAUNCHER_MARGIN, bounds.maxTop),
  };
};

const setPanelState = ({ left, top, width, height }) => {
  const sizeBounds = panelBounds();
  const nextWidth = clamp(width, PANEL_MIN_WIDTH, sizeBounds.maxWidth);
  const nextHeight = clamp(height, PANEL_MIN_HEIGHT, sizeBounds.maxHeight);
  const positionBounds = panelBounds(nextWidth, nextHeight);

  panelState.value = {
    left: clamp(left, PANEL_MARGIN, positionBounds.maxLeft),
    top: clamp(top, PANEL_MARGIN, positionBounds.maxTop),
    width: nextWidth,
    height: nextHeight,
  };
};

const defaultPanelState = () => {
  const viewport = viewportSize();
  const width = Math.min(PANEL_DEFAULT_WIDTH, viewport.width - PANEL_MARGIN * 2);
  const height = Math.min(PANEL_DEFAULT_HEIGHT, viewport.height - PANEL_MARGIN * 2);
  const nextLeft = Math.min(launcherPosition.value.left, viewport.width - width - PANEL_MARGIN);
  const nextTop = Math.min(launcherPosition.value.top, viewport.height - height - PANEL_MARGIN);

  return {
    left: Math.max(PANEL_MARGIN, nextLeft),
    top: Math.max(PANEL_MARGIN, nextTop),
    width,
    height,
  };
};

const loadLauncherPosition = () => {
  const bounds = launcherBounds();
  const savedPosition = safeJsonRead(LAUNCHER_STORAGE_KEY);

  if (savedPosition && Number.isFinite(savedPosition.left) && Number.isFinite(savedPosition.top)) {
    setLauncherPosition(savedPosition.left, savedPosition.top);
    return;
  }

  launcherPosition.value = {
    left: bounds.maxLeft,
    top: bounds.maxTop,
  };
};

const persistLauncherPosition = () => {
  safeJsonWrite(LAUNCHER_STORAGE_KEY, launcherPosition.value);
};

const startDrag = (event) => {
  launcherDragState.value = {
    startX: event.clientX,
    startY: event.clientY,
    left: launcherPosition.value.left,
    top: launcherPosition.value.top,
    moved: false,
  };
  event.currentTarget.setPointerCapture?.(event.pointerId);
};

const moveDrag = (event) => {
  if (!launcherDragState.value) return;

  const deltaX = event.clientX - launcherDragState.value.startX;
  const deltaY = event.clientY - launcherDragState.value.startY;
  if (Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3) launcherDragState.value.moved = true;

  setLauncherPosition(launcherDragState.value.left + deltaX, launcherDragState.value.top + deltaY);
};

const endDrag = () => {
  if (!launcherDragState.value) return;

  persistLauncherPosition();
  window.setTimeout(() => {
    launcherDragState.value = null;
  }, 0);
};

const startPanelDrag = (event) => {
  if (event.target.closest?.("button")) return;

  event.preventDefault();
  panelDragState.value = {
    startX: event.clientX,
    startY: event.clientY,
    left: panelState.value.left,
    top: panelState.value.top,
  };
  event.currentTarget.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", movePanelDrag);
  window.addEventListener("pointerup", endPanelDrag);
  window.addEventListener("pointercancel", endPanelDrag);
};

const movePanelDrag = (event) => {
  if (!panelDragState.value) return;

  setPanelState({
    ...panelState.value,
    left: panelDragState.value.left + event.clientX - panelDragState.value.startX,
    top: panelDragState.value.top + event.clientY - panelDragState.value.startY,
  });
};

const endPanelDrag = () => {
  if (!panelDragState.value) return;

  panelDragState.value = null;
  window.removeEventListener("pointermove", movePanelDrag);
  window.removeEventListener("pointerup", endPanelDrag);
  window.removeEventListener("pointercancel", endPanelDrag);
};

const startPanelResize = (direction, event) => {
  event.preventDefault();
  event.stopPropagation();
  panelResizeState.value = {
    direction,
    startX: event.clientX,
    startY: event.clientY,
    left: panelState.value.left,
    top: panelState.value.top,
    width: panelState.value.width,
    height: panelState.value.height,
  };
  event.currentTarget.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", movePanelResize);
  window.addEventListener("pointerup", endPanelResize);
  window.addEventListener("pointercancel", endPanelResize);
};

const movePanelResize = (event) => {
  if (!panelResizeState.value) return;

  const { direction, startX, startY, left, top, width, height } = panelResizeState.value;
  const deltaX = event.clientX - startX;
  const deltaY = event.clientY - startY;
  const viewport = viewportSize();
  const resizesLeft = direction.includes("left");
  const resizesRight = direction.includes("right");
  const resizesTop = direction.includes("top");
  const resizesBottom = direction.includes("bottom");
  const right = left + width;
  const bottom = top + height;
  let nextLeft = left;
  let nextTop = top;
  let nextWidth = width;
  let nextHeight = height;

  if (resizesLeft) {
    nextLeft = clamp(left + deltaX, PANEL_MARGIN, right - PANEL_MIN_WIDTH);
    nextWidth = right - nextLeft;
  }

  if (resizesRight) {
    nextWidth = clamp(width + deltaX, PANEL_MIN_WIDTH, viewport.width - left - PANEL_MARGIN);
  }

  if (resizesTop) {
    nextTop = clamp(top + deltaY, PANEL_MARGIN, bottom - PANEL_MIN_HEIGHT);
    nextHeight = bottom - nextTop;
  }

  if (resizesBottom) {
    nextHeight = clamp(height + deltaY, PANEL_MIN_HEIGHT, viewport.height - top - PANEL_MARGIN);
  }

  setPanelState({
    left: nextLeft,
    top: nextTop,
    width: nextWidth,
    height: nextHeight,
  });
};

const endPanelResize = () => {
  if (!panelResizeState.value) return;

  panelResizeState.value = null;
  window.removeEventListener("pointermove", movePanelResize);
  window.removeEventListener("pointerup", endPanelResize);
  window.removeEventListener("pointercancel", endPanelResize);
};

const openPanel = () => {
  setPanelState(defaultPanelState());
  open.value = true;
};

const togglePanel = () => {
  if (launcherDragState.value?.moved) return;
  if (open.value) {
    open.value = false;
    return;
  }
  openPanel();
};

const addProduct = (product) => {
  emit("add-to-cart", product, 1);
};

const formatAssistantPrice = (price) => `$${Number(price || 0).toLocaleString()}`;

const clearProductAnimationTimer = () => {
  if (!productAnimationTimer.value) return;

  window.clearTimeout(productAnimationTimer.value);
  productAnimationTimer.value = null;
};

const finishProductAnimation = () => {
  previousProductIndex.value = null;
  productAnimationTimer.value = null;
};

const setActiveProduct = (index, direction = "forward") => {
  if (!productCount.value) return;

  const nextIndex = (index + productCount.value) % productCount.value;
  if (nextIndex === activeProductIndex.value) return;

  clearProductAnimationTimer();
  previousProductIndex.value = activeProductIndex.value;
  productTransitionDirection.value = direction;
  activeProductIndex.value = nextIndex;
  productAnimationTimer.value = window.setTimeout(finishProductAnimation, 280);
};

const showPreviousProduct = () => {
  setActiveProduct(activeProductIndex.value - 1, "backward");
};

const showNextProduct = () => {
  setActiveProduct(activeProductIndex.value + 1, "forward");
};

const selectProduct = (index) => {
  const direction = index < activeProductIndex.value ? "backward" : "forward";
  setActiveProduct(index, direction);
};

const submitDraft = async () => {
  const message = draftMessage.value.trim();
  if (!message || isSubmitting.value) return;

  chatMessages.value.push({
    id: `user-${Date.now()}`,
    sender: "user",
    content: message,
  });
  draftMessage.value = "";
  assistantError.value = "";
  isSubmitting.value = true;

  try {
    const response = await sendFurnitureAssistantMessage(message);
    assistantResponse.value = response;
    clearProductAnimationTimer();
    activeProductIndex.value = 0;
    previousProductIndex.value = null;
    if (response.answer) {
      chatMessages.value.push({
        id: `assistant-${Date.now()}`,
        sender: "assistant",
        content: response.answer,
      });
    }
  } catch (caught) {
    assistantError.value = caught.message || t("assistant.error");
  } finally {
    isSubmitting.value = false;
  }
};

const handleViewportResize = () => {
  setLauncherPosition(launcherPosition.value.left, launcherPosition.value.top);
  setPanelState(panelState.value);
};

onMounted(() => {
  loadLauncherPosition();
  setPanelState(defaultPanelState());
  window.addEventListener("resize", handleViewportResize);
});

onBeforeUnmount(() => {
  clearProductAnimationTimer();
  window.removeEventListener("resize", handleViewportResize);
  window.removeEventListener("pointermove", movePanelDrag);
  window.removeEventListener("pointerup", endPanelDrag);
  window.removeEventListener("pointercancel", endPanelDrag);
  window.removeEventListener("pointermove", movePanelResize);
  window.removeEventListener("pointerup", endPanelResize);
  window.removeEventListener("pointercancel", endPanelResize);
});
</script>

<template>
  <aside class="furniture-assistant" aria-live="polite">
    <button
      v-if="!open"
      class="furniture-assistant-launcher"
      type="button"
      :style="launcherStyle"
      :aria-label="t('assistant.launcherLabel')"
      @click="togglePanel"
      @pointerdown="startDrag"
      @pointermove="moveDrag"
      @pointerup="endDrag"
      @pointercancel="endDrag"
    >
      <span class="assistant-avatar">
        <svg
          class="assistant-avatar-icon"
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 8V5" />
          <path d="M9 5h6" />
          <rect width="14" height="11" x="5" y="8" rx="3" />
          <path d="M3 13h2" />
          <path d="M19 13h2" />
          <path d="M9.5 13.5h.01" />
          <path d="M14.5 13.5h.01" />
          <path d="M10 17c1.2.7 2.8.7 4 0" />
        </svg>
      </span>
    </button>

    <section
      v-if="open"
      class="furniture-assistant-panel"
      :style="panelStyle"
      role="dialog"
      aria-labelledby="furniture-assistant-title"
    >
      <header
        class="furniture-assistant-header"
        @pointerdown="startPanelDrag"
      >
        <div class="assistant-panel-identity">
          <div class="assistant-panel-avatar" aria-hidden="true">
            <svg
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
          </div>
          <div class="assistant-panel-copy">
            <p>{{ t("assistant.eyebrow") }}</p>
            <h2 id="furniture-assistant-title">{{ t("assistant.title") }}</h2>
            <span class="assistant-panel-presence">
              <span class="assistant-presence-dot" aria-hidden="true"></span>
              {{ assistantPresenceLabel }}
            </span>
          </div>
        </div>
        <button class="assistant-panel-close" type="button" :aria-label="t('common.close')" @click="open = false">×</button>
      </header>

      <div class="furniture-assistant-thread">
        <article
          v-for="message in chatMessages"
          :key="message.id"
          class="assistant-chat-row"
          :class="message.sender === 'user' ? 'assistant-chat-user' : 'assistant-chat-assistant'"
        >
          <div class="assistant-chat-avatar" aria-hidden="true">
            <svg
              v-if="message.sender === 'assistant'"
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
            <span v-else>{{ userDisplayName }}</span>
          </div>
          <div class="assistant-chat-content">
            <div class="assistant-chat-meta">
              <span class="assistant-chat-name">{{ message.sender === "user" ? userDisplayName : assistantDisplayName }}</span>
              <span class="assistant-message-time">{{ messageTimeLabel }}</span>
            </div>
            <p class="assistant-chat-bubble">{{ message.content }}</p>
          </div>
        </article>

        <article v-if="isSubmitting" class="assistant-chat-row assistant-chat-assistant assistant-chat-loading">
          <div class="assistant-chat-avatar" aria-hidden="true">
            <svg
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
          </div>
          <div class="assistant-chat-content">
            <div class="assistant-chat-meta">
              <span class="assistant-chat-name">{{ assistantDisplayName }}</span>
              <span class="assistant-message-time">{{ messageTimeLabel }}</span>
            </div>
            <p class="assistant-chat-bubble">{{ t("assistant.loading") }}</p>
          </div>
        </article>

        <article v-if="assistantError" class="assistant-chat-row assistant-chat-assistant assistant-chat-error">
          <div class="assistant-chat-avatar" aria-hidden="true">
            <svg
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
          </div>
          <div class="assistant-chat-content">
            <div class="assistant-chat-meta">
              <span class="assistant-chat-name">{{ t("assistant.errorTitle") }}</span>
              <span class="assistant-message-time">{{ messageTimeLabel }}</span>
            </div>
            <p class="assistant-chat-bubble">{{ assistantError }}</p>
          </div>
        </article>

        <article v-if="assistantSources.length" class="assistant-chat-row assistant-chat-assistant assistant-chat-sources">
          <div class="assistant-chat-avatar" aria-hidden="true">
            <svg
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
          </div>
          <div class="assistant-chat-content">
            <div class="assistant-chat-meta">
              <span class="assistant-chat-name">{{ t("assistant.sourcesLabel") }}</span>
              <span class="assistant-message-time">{{ messageTimeLabel }}</span>
            </div>
            <div class="assistant-chat-bubble assistant-source-list">
              <span
                v-for="source in assistantSources"
                :key="`${source.type}-${source.name}`"
                class="assistant-source-chip"
              >
                <span>{{ source.type }}</span>
                {{ source.name }}
              </span>
            </div>
          </div>
        </article>

        <article v-if="assistantProducts.length" class="assistant-chat-row assistant-chat-assistant assistant-chat-recommendations">
          <div class="assistant-chat-avatar" aria-hidden="true">
            <svg
              class="assistant-avatar-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 8V5" />
              <path d="M9 5h6" />
              <rect width="14" height="11" x="5" y="8" rx="3" />
              <path d="M3 13h2" />
              <path d="M19 13h2" />
              <path d="M9.5 13.5h.01" />
              <path d="M14.5 13.5h.01" />
              <path d="M10 17c1.2.7 2.8.7 4 0" />
            </svg>
          </div>
          <div class="assistant-chat-content">
            <div class="assistant-chat-meta">
              <span class="assistant-chat-name">{{ assistantDisplayName }}</span>
              <span class="assistant-message-time">{{ messageTimeLabel }}</span>
            </div>
            <div class="assistant-chat-bubble">
              <div
                class="assistant-product-carousel"
                :class="productTransitionDirection === 'forward' ? 'assistant-carousel-forward' : 'assistant-carousel-backward'"
                :aria-label="t('assistant.recommendationsLabel')"
              >
                <div v-if="productCount > 1" class="assistant-carousel-counter">
                  {{ activeProductIndex + 1 }} / {{ productCount }}
                </div>
                <div class="assistant-product-list">
                  <article
                    v-for="(product, index) in assistantProducts"
                    :key="product.skuId"
                    class="assistant-product-slide"
                    :class="{
                      'assistant-product-slide-active': index === activeProductIndex,
                      'assistant-product-slide-exiting': index === previousProductIndex,
                    }"
                    :aria-hidden="index !== activeProductIndex && index !== previousProductIndex"
                  >
                    <div class="assistant-product-card">
                      <div class="assistant-product-swatch">
                        <img v-if="product.cover" :src="product.cover" :alt="product.name" />
                      </div>
                      <div class="assistant-product-copy">
                        <p>{{ product.subtitle }}</p>
                        <h3>{{ product.name }}</h3>
                        <strong>{{ formatAssistantPrice(product.price) }}</strong>
                        <span>{{ product.reason }}</span>
                      </div>
                      <div class="assistant-product-actions">
                        <a class="assistant-product-link" :href="product.detailUrl">{{ t("assistant.viewDetails") }}</a>
                        <button type="button" @click="addProduct(product)">{{ t("assistant.addToBag") }}</button>
                      </div>
                    </div>
                  </article>
                </div>

                <button
                  v-if="productCount > 1"
                  class="assistant-carousel-control assistant-carousel-control-prev"
                  type="button"
                  aria-label="Previous recommendation"
                  @click="showPreviousProduct"
                >
                  <svg
                    class="assistant-carousel-arrow-icon"
                    aria-hidden="true"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M14.5 6.5 9 12l5.5 5.5" />
                  </svg>
                </button>
                <button
                  v-if="productCount > 1"
                  class="assistant-carousel-control assistant-carousel-control-next"
                  type="button"
                  aria-label="Next recommendation"
                  @click="showNextProduct"
                >
                  <svg
                    class="assistant-carousel-arrow-icon"
                    aria-hidden="true"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M9.5 6.5 15 12l-5.5 5.5" />
                  </svg>
                </button>

                <div v-if="productCount > 1" class="assistant-carousel-indicators">
                  <button
                    v-for="(_, index) in assistantProducts"
                    :key="`assistant-product-indicator-${index}`"
                    class="assistant-carousel-indicator"
                    :class="{ 'assistant-carousel-indicator-active': index === activeProductIndex }"
                    type="button"
                    :aria-label="`Show recommendation ${index + 1}`"
                    :aria-pressed="index === activeProductIndex"
                    @click="selectProduct(index)"
                  ></button>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>

      <form class="furniture-assistant-composer" @submit.prevent="submitDraft">
        <label class="sr-only" for="furniture-assistant-input">{{ t("assistant.askPlaceholder") }}</label>
        <div class="assistant-composer-shell">
          <span class="assistant-composer-mode">{{ composerModeLabel }}</span>
          <input id="furniture-assistant-input" v-model="draftMessage" type="text" :placeholder="t('assistant.askPlaceholder')" />
        </div>
        <button class="assistant-send-button" type="submit" :disabled="isSubmitting || !draftMessage.trim()">
          <span class="sr-only">{{ t("assistant.send") }}</span>
          <svg
            class="assistant-send-icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M5 12h13" />
            <path d="m13 6 6 6-6 6" />
          </svg>
        </button>
      </form>

      <button
        v-for="direction in resizeDirections"
        :key="direction"
        class="furniture-assistant-resize-handle"
        type="button"
        :data-resize-direction="direction"
        :aria-label="`Resize assistant ${direction}`"
        @pointerdown="startPanelResize(direction, $event)"
      ></button>
    </section>
  </aside>
</template>
