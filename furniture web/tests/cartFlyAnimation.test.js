import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const readCssBlock = (path, selector) => {
  const source = readSource(path);
  const start = source.indexOf(selector);
  const end = source.indexOf("\n}", start);
  return source.slice(start, end + 2);
};

describe("add to cart fly animation", () => {
  it("defines a reusable FLIP-style product image fly animation", () => {
    const source = readSource("../src/services/cartFlyAnimation.js");

    expect(source).toContain("CART_ANIMATION_TARGET_SELECTOR");
    expect(source).toContain("[data-cart-animation-target]");
    expect(source).toContain("prefers-reduced-motion: reduce");
    expect(source).toContain("cloneNode(true)");
    expect(source).toContain("sourceClone.animate");
    expect(source).toContain("cart-fly-clone");
    expect(source).toContain("cart-bag-bump");
    expect(source).toContain("CART_FLY_ICON_MAX_SIZE = 52");
    expect(source).toContain("thumbnailWidth");
    expect(source).toContain("thumbnailHeight");
    expect(source).toContain("getViewportCenter");
    expect(source).toContain("viewportCenterY");
    expect(source).toContain("win.innerHeight");
    expect(source).toContain("sourceCenterX");
    expect(source).toContain("const startCenterX = sourceCenterX");
    expect(source).toContain("const startCenterY = viewportCenterY");
    expect(source).toContain("opacity: 0.62");
    expect(source).toContain("duration: 1000");
    expect(source).toContain('easing: "cubic-bezier(0.5, 0, 0.85, 0.35)"');
    expect(source).toContain("Math.hypot");
    expect(source).toContain("curveX");
    expect(source).toContain("const curveX = (travelY / travelDistance) * curveBend");
    expect(source).toContain("const curveY = (-travelX / travelDistance) * curveBend");
    expect(source).toContain("offset: 0.5");
    expect(source).toContain("travelX * 0.2 + curveX");
    expect(source).toContain("travelX * 0.62 + curveX * 0.7");
    expect(source).toContain("requestAnimationFrame");
    expect(source).toContain('transform: "translate3d(0, 0, 0)"');
    expect(source).not.toContain("scale(0.16)");
    expect(source).not.toContain("scale(0.05)");
  });

  it("plays the animation after cart mutations receive a click trigger", () => {
    const appSource = readSource("../src/App.vue");
    const plpSource = readSource("../src/pages/SofasPlpPage.vue");
    const pdpSource = readSource("../src/pages/SofaPdpPage.vue");
    const headerSource = readSource("../src/components/RhHeader.vue");

    expect(appSource).toContain("playAddToCartFlyAnimation");
    expect(appSource).toContain("const addToCart = async (product, quantity = 1, options = {})");
    expect(appSource).toContain("trigger: options.trigger");
    expect(plpSource).toContain("trigger: $event.currentTarget");
    expect(pdpSource).toContain("const handleAddToCart = (event)");
    expect(pdpSource).toContain("handleAddToCart($event)");
    expect(headerSource).toContain("data-cart-animation-target");
  });

  it("styles the flying clone and cart landing feedback", () => {
    const source = readSource("../src/styles.css");
    const flyCloneStyles = readCssBlock("../src/styles.css", ".cart-fly-clone");

    expect(source).toContain(".cart-fly-clone");
    expect(flyCloneStyles).toContain("contain: layout paint style");
    expect(flyCloneStyles).toContain("backface-visibility: hidden");
    expect(flyCloneStyles).toContain("background: transparent");
    expect(flyCloneStyles).toContain("object-fit: cover");
    expect(flyCloneStyles).toContain("object-position: center");
    expect(flyCloneStyles).not.toContain("object-fit: contain");
    expect(flyCloneStyles).not.toContain("box-shadow: 0 10px 24px rgba(0, 0, 0, 0.08)");
    expect(source).toContain(".bag-icon.cart-bag-bump");
    expect(source).toContain("@keyframes cart-bag-bump");
  });
});
