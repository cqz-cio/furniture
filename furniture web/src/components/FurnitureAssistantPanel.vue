<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { demoProducts } from "../data/demoProducts.js";
import { useI18n } from "../i18n.js";

const emit = defineEmits(["add-to-cart"]);

const { t } = useI18n();
const LAUNCHER_STORAGE_KEY = "furniture-assistant-position";
const PANEL_STORAGE_KEY = "furniture-assistant-panel-state";
const LAUNCHER_SIZE = 60;
const LAUNCHER_MARGIN = 20;
const PANEL_DEFAULT_WIDTH = 420;
const PANEL_DEFAULT_HEIGHT = 560;
const PANEL_MIN_WIDTH = 320;
const PANEL_MIN_HEIGHT = 420;
const PANEL_MARGIN = 16;

const open = ref(false);
const launcherPosition = ref({ left: 0, top: 0 });
const panelState = ref({ left: 0, top: 0, width: PANEL_DEFAULT_WIDTH, height: PANEL_DEFAULT_HEIGHT });
const launcherDragState = ref(null);
const panelDragState = ref(null);
const panelResizeState = ref(null);
const draftMessage = ref("");

const assistantProductCards = computed(() =>
  demoProducts.slice(0, 3).map((product, index) => ({
    ...product,
    assistantReason: [t("assistant.reasonSofa"), t("assistant.reasonBed"), t("assistant.reasonTable")][index],
  }))
);

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

const loadPanelState = () => {
  const savedPanelState = safeJsonRead(PANEL_STORAGE_KEY);

  if (
    savedPanelState &&
    Number.isFinite(savedPanelState.left) &&
    Number.isFinite(savedPanelState.top) &&
    Number.isFinite(savedPanelState.width) &&
    Number.isFinite(savedPanelState.height)
  ) {
    setPanelState(savedPanelState);
    return;
  }

  setPanelState(defaultPanelState());
};

const persistLauncherPosition = () => {
  safeJsonWrite(LAUNCHER_STORAGE_KEY, launcherPosition.value);
};

const persistPanelState = () => {
  safeJsonWrite(PANEL_STORAGE_KEY, panelState.value);
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

  persistPanelState();
  panelDragState.value = null;
  window.removeEventListener("pointermove", movePanelDrag);
  window.removeEventListener("pointerup", endPanelDrag);
  window.removeEventListener("pointercancel", endPanelDrag);
};

const startPanelResize = (event) => {
  event.preventDefault();
  event.stopPropagation();
  panelResizeState.value = {
    startX: event.clientX,
    startY: event.clientY,
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

  setPanelState({
    ...panelState.value,
    width: panelResizeState.value.width + event.clientX - panelResizeState.value.startX,
    height: panelResizeState.value.height + event.clientY - panelResizeState.value.startY,
  });
};

const endPanelResize = () => {
  if (!panelResizeState.value) return;

  persistPanelState();
  panelResizeState.value = null;
  window.removeEventListener("pointermove", movePanelResize);
  window.removeEventListener("pointerup", endPanelResize);
  window.removeEventListener("pointercancel", endPanelResize);
};

const openPanel = () => {
  loadPanelState();
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

const submitDraft = () => {
  draftMessage.value = "";
};

const handleViewportResize = () => {
  setLauncherPosition(launcherPosition.value.left, launcherPosition.value.top);
  setPanelState(panelState.value);
};

onMounted(() => {
  loadLauncherPosition();
  loadPanelState();
  window.addEventListener("resize", handleViewportResize);
});

onBeforeUnmount(() => {
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
        <div>
          <p>{{ t("assistant.eyebrow") }}</p>
          <h2 id="furniture-assistant-title">{{ t("assistant.title") }}</h2>
        </div>
        <button type="button" :aria-label="t('common.close')" @click="open = false">x</button>
      </header>

      <div class="furniture-assistant-thread">
        <article class="assistant-message assistant-message-agent">
          <span>{{ t("assistant.agentLabel") }}</span>
          <p>{{ t("assistant.welcome") }}</p>
        </article>

        <div class="assistant-product-list" :aria-label="t('assistant.recommendationsLabel')">
          <article v-for="product in assistantProductCards" :key="product.skuId" class="assistant-product-card">
            <div class="assistant-product-swatch"></div>
            <div>
              <p>{{ product.subtitle }}</p>
              <h3>{{ product.name }}</h3>
              <strong>${{ product.price.toLocaleString() }}</strong>
              <span>{{ product.assistantReason }}</span>
            </div>
            <button type="button" @click="addProduct(product)">{{ t("assistant.addToBag") }}</button>
          </article>
        </div>
      </div>

      <form class="furniture-assistant-composer" @submit.prevent="submitDraft">
        <label class="sr-only" for="furniture-assistant-input">{{ t("assistant.askPlaceholder") }}</label>
        <input id="furniture-assistant-input" v-model="draftMessage" type="text" :placeholder="t('assistant.askPlaceholder')" />
        <button type="submit">{{ t("assistant.send") }}</button>
      </form>

      <button
        class="furniture-assistant-resize-handle"
        type="button"
        aria-label="Resize assistant"
        @pointerdown="startPanelResize"
      ></button>
    </section>
  </aside>
</template>
