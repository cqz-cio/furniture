import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("cart recovery notices", () => {
  it("passes cart recovery notices from App into the cart drawer", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("cartNoticeKey = ref(\"\")");
    expect(source).toContain("cartNoticeDetail = ref(\"\")");
    expect(source).toContain("cartDebugInfo = ref(\"\")");
    expect(source).toContain("getYudaoCartErrorDetail");
    expect(source).toContain("getYudaoCartDebugInfo");
    expect(source).toContain(':notice-key="cartNoticeKey"');
    expect(source).toContain(':notice-detail="cartNoticeDetail"');
    expect(source).toContain(':debug-info="cartDebugInfo"');
    expect(source).toContain('@resync="loadRemoteCart"');
    expect(source).toContain("clearYudaoSession");
    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("switchToAuthRequiredCart");
    expect(source).toContain('noticeKey: "cart.remoteAuthRequired"');
    expect(source).toContain('noticeKey: "cart.remoteUnavailable"');
    expect(source).toContain('noticeKey: "cart.remoteMutationUnavailable"');
  });

  it("renders the notice inside the cart drawer", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain("noticeKey");
    expect(source).toContain("debugInfo");
    expect(source).toContain("cart-drawer-notice");
    expect(source).toContain("cart-debug-info");
    expect(source).toContain("t(noticeKey)");
    expect(source).toContain('const emit = defineEmits(["checkout", "close", "resync", "update-quantity", "remove", "wishlist", "add-membership"])');
    expect(source).toContain("canResyncCart");
    expect(source).toContain('t("cart.retrySync")');
    expect(source).toContain('@click="emit(\'resync\')"');
  });

  it("shows problem markers on invalid remote cart items", () => {
    const drawerSource = readSource("../src/components/CartDrawer.vue");
    const mapperSource = readSource("../src/services/yudaoMappers.js");

    expect(mapperSource).toContain('cartProblemKey: "cart.itemUnavailable"');
    expect(drawerSource).toContain("item.cartProblemKey");
    expect(drawerSource).toContain("cart-item-problem");
    expect(drawerSource).toContain("t(item.cartProblemKey)");
  });

  it("marks failed remote cart mutations as local preview instead of real Yudao cart rows", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("localPreviewProduct");
    expect(source).toContain('source: "local-preview"');
    expect(source).toContain("addLocalCartItem(cartItems.value, localPreviewProduct, quantity)");
  });

  it("styles the cart drawer notice", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".cart-drawer-notice");
    expect(source).toContain(".cart-debug-info");
    expect(source).toContain(".cart-drawer-notice button");
    expect(source).toContain(".cart-item-problem");
  });
});
