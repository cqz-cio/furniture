export const CART_ANIMATION_TARGET_SELECTOR = "[data-cart-animation-target]";

const CART_ANIMATION_SOURCE_SELECTOR = [
  ".product-gallery-main img",
  ".product-card-media img",
  ".product-image-frame img",
].join(", ");

const PRODUCT_CONTEXT_SELECTOR = ".product-card, .product-detail-page";
const CART_FLY_ICON_MAX_SIZE = 52;
const CART_FLY_ICON_MIN_SIZE = 34;

const isReducedMotion = (win) => Boolean(win?.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches);

const getVisibleRect = (element) => {
  const rect = element?.getBoundingClientRect?.();
  if (!rect || rect.width <= 0 || rect.height <= 0) return null;
  return rect;
};

const getViewportCenter = (win, doc) => {
  const viewportHeight = win.innerHeight || doc?.documentElement?.clientHeight || 0;

  return {
    viewportCenterY: viewportHeight / 2,
  };
};

const findSourceImage = (trigger, doc) => {
  const context = trigger?.closest?.(PRODUCT_CONTEXT_SELECTOR);
  return context?.querySelector?.(CART_ANIMATION_SOURCE_SELECTOR) || null;
};

const cleanupClone = (sourceClone) => {
  if (sourceClone?.parentNode) sourceClone.parentNode.removeChild(sourceClone);
};

const getFlyIconSize = (sourceRect) => {
  const sourceAspectRatio = sourceRect.width / sourceRect.height;
  const longEdge = Math.min(
    CART_FLY_ICON_MAX_SIZE,
    Math.max(CART_FLY_ICON_MIN_SIZE, Math.min(sourceRect.width, sourceRect.height) * 0.22),
  );

  if (sourceAspectRatio >= 1) {
    return {
      thumbnailWidth: longEdge,
      thumbnailHeight: longEdge / sourceAspectRatio,
    };
  }

  return {
    thumbnailWidth: longEdge * sourceAspectRatio,
    thumbnailHeight: longEdge,
  };
};

const pulseCartTarget = (target, win) => {
  target.classList.remove("cart-bag-bump");
  win.requestAnimationFrame?.(() => target.classList.add("cart-bag-bump"));
  win.setTimeout?.(() => target.classList.remove("cart-bag-bump"), 620);
};

export const playAddToCartFlyAnimation = ({
  trigger,
  source,
  target,
  doc = globalThis.document,
  win = globalThis.window,
} = {}) => {
  if (!doc || !win || isReducedMotion(win)) return false;

  const sourceImage = source || findSourceImage(trigger, doc);
  const cartTarget = target || doc.querySelector(CART_ANIMATION_TARGET_SELECTOR);
  const sourceRect = getVisibleRect(sourceImage);
  const targetRect = getVisibleRect(cartTarget);

  if (!sourceImage?.src || !sourceRect || !targetRect || typeof sourceImage.cloneNode !== "function") {
    return false;
  }

  const { thumbnailWidth, thumbnailHeight } = getFlyIconSize(sourceRect);
  const sourceCenterX = sourceRect.left + sourceRect.width / 2;
  const { viewportCenterY } = getViewportCenter(win, doc);
  const startCenterX = sourceCenterX;
  const startCenterY = viewportCenterY;
  const sourceClone = sourceImage.cloneNode(true);
  sourceClone.className = "cart-fly-clone";
  sourceClone.alt = "";
  sourceClone.removeAttribute("loading");
  Object.assign(sourceClone.style, {
    left: `${startCenterX - thumbnailWidth / 2}px`,
    top: `${startCenterY - thumbnailHeight / 2}px`,
    width: `${thumbnailWidth}px`,
    height: `${thumbnailHeight}px`,
    transform: "translate3d(0, 0, 0)",
  });

  doc.body.appendChild(sourceClone);

  const targetCenterX = targetRect.left + targetRect.width / 2;
  const targetCenterY = targetRect.top + targetRect.height / 2;
  const travelX = targetCenterX - startCenterX;
  const travelY = targetCenterY - startCenterY;
  const travelDistance = Math.hypot(travelX, travelY) || 1;
  const curveBend = Math.min(56, Math.max(22, travelDistance * 0.08));
  const curveX = (travelY / travelDistance) * curveBend;
  const curveY = (-travelX / travelDistance) * curveBend;

  const runFlyAnimation = () => {
    const animation = sourceClone.animate(
      [
        { opacity: 0, transform: "translate3d(0, 6px, 0)" },
        { offset: 0.16, opacity: 0.62, transform: "translate3d(0, 0, 0)" },
        {
          offset: 0.5,
          opacity: 0.6,
          transform: `translate3d(${travelX * 0.2 + curveX}px, ${travelY * 0.2 + curveY - 4}px, 0)`,
        },
        {
          offset: 0.76,
          opacity: 0.48,
          transform: `translate3d(${travelX * 0.62 + curveX * 0.7}px, ${
            travelY * 0.62 + curveY * 0.7 - 12
          }px, 0)`,
        },
        { opacity: 0, transform: `translate3d(${travelX}px, ${travelY}px, 0)` },
      ],
      {
        duration: 1000,
        easing: "cubic-bezier(0.5, 0, 0.85, 0.35)",
        fill: "forwards",
      },
    );

    pulseCartTarget(cartTarget, win);
    animation.finished?.then(() => cleanupClone(sourceClone)).catch(() => cleanupClone(sourceClone));
  };

  if (typeof win.requestAnimationFrame === "function") {
    win.requestAnimationFrame(runFlyAnimation);
  } else {
    runFlyAnimation();
  }

  return true;
};
