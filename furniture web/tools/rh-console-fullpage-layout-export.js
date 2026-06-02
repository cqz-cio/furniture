// Paste this whole file into Chrome DevTools Console on the target RH page.
// It scrolls through the full page first, waits for lazy media, then downloads JSON.
(async () => {
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const round = (value) => Math.round(Number(value || 0) * 100) / 100;
  const clean = (value) => String(value || "").replace(/\s+/g, " ").trim();

  const styleKeys = [
    "display",
    "position",
    "boxSizing",
    "width",
    "height",
    "marginTop",
    "marginRight",
    "marginBottom",
    "marginLeft",
    "paddingTop",
    "paddingRight",
    "paddingBottom",
    "paddingLeft",
    "gap",
    "rowGap",
    "columnGap",
    "fontFamily",
    "fontSize",
    "fontWeight",
    "lineHeight",
    "letterSpacing",
    "textAlign",
    "textTransform",
    "whiteSpace",
    "color",
    "backgroundColor",
    "backgroundImage",
    "backgroundSize",
    "backgroundPosition",
    "backgroundRepeat",
    "borderTopWidth",
    "borderRightWidth",
    "borderBottomWidth",
    "borderLeftWidth",
    "borderTopColor",
    "borderRightColor",
    "borderBottomColor",
    "borderLeftColor",
    "borderRadius",
    "boxShadow",
    "objectFit",
    "objectPosition",
    "overflow",
    "overflowX",
    "overflowY",
    "zIndex",
    "opacity",
    "transform",
    "visibility",
  ];

  const landmarkTags = new Set([
    "BODY",
    "HEADER",
    "NAV",
    "MAIN",
    "FOOTER",
    "SECTION",
    "ARTICLE",
    "ASIDE",
    "IMG",
    "PICTURE",
    "VIDEO",
    "CANVAS",
    "IFRAME",
    "A",
    "BUTTON",
    "H1",
    "H2",
    "H3",
    "H4",
    "P",
    "UL",
    "OL",
    "LI",
    "FORM",
    "INPUT",
    "SELECT",
    "TEXTAREA",
    "LABEL",
  ]);

  function fileSafe(value) {
    return clean(value)
      .toLowerCase()
      .replace(/^https?:\/\//, "")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 90);
  }

  function cssPath(el) {
    if (!el || el.nodeType !== 1) return "";
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && node !== document.body) {
      let part = node.tagName.toLowerCase();
      if (node.id) {
        part += `#${CSS.escape(node.id)}`;
        parts.unshift(part);
        break;
      }

      const testId = node.getAttribute("data-testid");
      const authoringName = node.getAttribute("authoringname");
      if (testId) {
        part += `[data-testid="${testId.replaceAll('"', '\\"')}"]`;
      } else if (authoringName) {
        part += `[authoringname="${authoringName.replaceAll('"', '\\"')}"]`;
      } else {
        const classes = Array.from(node.classList || [])
          .filter((name) => !/^css-|^Mui/.test(name))
          .slice(0, 2);
        if (classes.length) part += classes.map((name) => `.${CSS.escape(name)}`).join("");
      }

      const parent = node.parentElement;
      if (parent) {
        const sameTagSiblings = Array.from(parent.children).filter((child) => child.tagName === node.tagName);
        if (sameTagSiblings.length > 1) part += `:nth-of-type(${sameTagSiblings.indexOf(node) + 1})`;
      }

      parts.unshift(part);
      node = parent;
    }
    return parts.length ? `body > ${parts.join(" > ")}` : "body";
  }

  function selectorFor(el) {
    if (el.id) return `${el.tagName.toLowerCase()}#${CSS.escape(el.id)}`;
    const testId = el.getAttribute("data-testid");
    if (testId) return `${el.tagName.toLowerCase()}[data-testid="${testId.replaceAll('"', '\\"')}"]`;
    const authoringName = el.getAttribute("authoringname");
    if (authoringName) return `${el.tagName.toLowerCase()}[authoringname="${authoringName.replaceAll('"', '\\"')}"]`;
    const cls = Array.from(el.classList || [])
      .slice(0, 4)
      .map((name) => `.${CSS.escape(name)}`)
      .join("");
    return `${el.tagName.toLowerCase()}${cls}`;
  }

  function textFor(el) {
    const text = clean(el.innerText || el.textContent || "");
    return text.length > 260 ? `${text.slice(0, 260)}...` : text;
  }

  function absoluteRect(el) {
    const rect = el.getBoundingClientRect();
    return {
      x: round(rect.left + window.scrollX),
      y: round(rect.top + window.scrollY),
      top: round(rect.top + window.scrollY),
      left: round(rect.left + window.scrollX),
      width: round(rect.width),
      height: round(rect.height),
      bottom: round(rect.bottom + window.scrollY),
      right: round(rect.right + window.scrollX),
    };
  }

  function viewportRect(el) {
    const rect = el.getBoundingClientRect();
    return {
      top: round(rect.top),
      right: round(rect.right),
      bottom: round(rect.bottom),
      left: round(rect.left),
      width: round(rect.width),
      height: round(rect.height),
    };
  }

  function isVisible(el, computed) {
    const rect = el.getBoundingClientRect();
    return (
      rect.width > 0 &&
      rect.height > 0 &&
      rect.width * rect.height > 4 &&
      computed.display !== "none" &&
      computed.visibility !== "hidden" &&
      computed.opacity !== "0"
    );
  }

  function isElementWorthKeeping(el, computed) {
    if (el === document.body) return true;
    if (landmarkTags.has(el.tagName)) return true;
    if (el.id || el.getAttribute("role") || el.getAttribute("aria-label")) return true;
    if (el.getAttribute("authoringname") || el.getAttribute("data-testid")) return true;
    if (computed.backgroundImage && computed.backgroundImage !== "none") return true;
    if (clean(el.innerText || "").length > 0) return true;
    return false;
  }

  function extractUrlsFromCssText(cssText) {
    return Array.from(cssText.matchAll(/url\((['"]?)(.*?)\1\)/g))
      .map((match) => match[2])
      .filter(Boolean)
      .map((url) => new URL(url, location.href).href);
  }

  async function autoScrollFullPage() {
    const startY = window.scrollY;
    const step = Math.max(500, Math.floor(window.innerHeight * 0.75));
    let lastHeight = 0;
    let stableRounds = 0;

    window.scrollTo(0, 0);
    await sleep(800);

    while (stableRounds < 3) {
      const height = document.documentElement.scrollHeight;
      for (let y = window.scrollY; y < height; y += step) {
        window.scrollTo(0, y);
        await sleep(260);
      }
      window.scrollTo(0, document.documentElement.scrollHeight);
      await sleep(900);

      const newHeight = document.documentElement.scrollHeight;
      if (Math.abs(newHeight - lastHeight) < 2) stableRounds += 1;
      else stableRounds = 0;
      lastHeight = newHeight;
    }

    window.scrollTo(0, startY);
    await sleep(500);
  }

  async function waitForImages() {
    const imgs = Array.from(document.images || []);
    await Promise.allSettled(
      imgs.map((img) => {
        if (img.complete) return Promise.resolve();
        return new Promise((resolve) => {
          img.addEventListener("load", resolve, { once: true });
          img.addEventListener("error", resolve, { once: true });
          setTimeout(resolve, 2500);
        });
      }),
    );
  }

  console.log("RH full-page extraction: scrolling full page...");
  await autoScrollFullPage();
  await waitForImages();

  const elements = [];
  const media = [];

  for (const el of document.querySelectorAll("body, body *")) {
    const computed = getComputedStyle(el);
    if (!isVisible(el, computed) || !isElementWorthKeeping(el, computed)) continue;

    const styles = {};
    for (const key of styleKeys) styles[key] = computed[key];

    const record = {
      index: elements.length,
      tag: el.tagName.toLowerCase(),
      selector: selectorFor(el),
      cssPath: cssPath(el),
      id: el.id || "",
      className: typeof el.className === "string" ? el.className : "",
      role: el.getAttribute("role") || "",
      ariaLabel: el.getAttribute("aria-label") || "",
      authoringName: el.getAttribute("authoringname") || "",
      dataTestId: el.getAttribute("data-testid") || "",
      href: el.href || el.getAttribute("href") || "",
      src: el.currentSrc || el.src || el.getAttribute("src") || "",
      alt: el.getAttribute("alt") || "",
      text: textFor(el),
      rect: absoluteRect(el),
      viewportRect: viewportRect(el),
      style: styles,
    };

    elements.push(record);

    if (el.tagName === "IMG") {
      media.push({
        sourceType: "img",
        index: record.index,
        selector: record.selector,
        authoringName: record.authoringName,
        src: el.currentSrc || el.src,
        alt: el.getAttribute("alt") || "",
        naturalWidth: el.naturalWidth || null,
        naturalHeight: el.naturalHeight || null,
        rendered: `${record.rect.width} x ${record.rect.height}`,
        rect: record.rect,
        objectFit: computed.objectFit,
        objectPosition: computed.objectPosition,
      });
    }

    if (el.tagName === "VIDEO") {
      media.push({
        sourceType: "video",
        index: record.index,
        selector: record.selector,
        authoringName: record.authoringName,
        src: el.currentSrc || el.src,
        poster: el.getAttribute("poster") || "",
        ariaLabel: record.ariaLabel,
        rendered: `${record.rect.width} x ${record.rect.height}`,
        rect: record.rect,
        objectFit: computed.objectFit,
        objectPosition: computed.objectPosition,
      });
    }

    if (computed.backgroundImage && computed.backgroundImage !== "none") {
      media.push({
        sourceType: "computed-background",
        index: record.index,
        selector: record.selector,
        authoringName: record.authoringName,
        src: computed.backgroundImage,
        rendered: `${record.rect.width} x ${record.rect.height}`,
        rect: record.rect,
        backgroundSize: computed.backgroundSize,
        backgroundPosition: computed.backgroundPosition,
        backgroundRepeat: computed.backgroundRepeat,
      });
    }
  }

  const cssBackgroundRules = [];
  for (const sheet of Array.from(document.styleSheets)) {
    let rules;
    try {
      rules = sheet.cssRules;
    } catch {
      continue;
    }
    for (const rule of Array.from(rules || [])) {
      if (!rule.cssText || !/background|url\(/i.test(rule.cssText)) continue;
      const urls = extractUrlsFromCssText(rule.cssText);
      if (!urls.length) continue;
      cssBackgroundRules.push({
        selectorText: rule.selectorText || "",
        cssText: rule.cssText,
        urls,
      });
    }
  }

  const anchors = Array.from(document.querySelectorAll("a"))
    .map((a) => ({
      selector: selectorFor(a),
      cssPath: cssPath(a),
      text: textFor(a),
      href: a.href || a.getAttribute("href") || "",
      ariaLabel: a.getAttribute("aria-label") || "",
      rect: absoluteRect(a),
    }))
    .filter((a) => a.href || a.text || a.ariaLabel);

  const data = {
    exportedAt: new Date().toISOString(),
    url: location.href,
    title: document.title,
    viewport: {
      width: window.innerWidth,
      height: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio,
    },
    document: {
      width: document.documentElement.scrollWidth,
      height: document.documentElement.scrollHeight,
      bodyHeight: document.body.scrollHeight,
    },
    pageMeta: {
      pagePath: document.body.getAttribute("data-page-path") || "",
      brand: document.body.getAttribute("data-brand") || "",
      userType: document.body.getAttribute("data-user-type") || "",
    },
    scroll: {
      x: window.scrollX,
      y: window.scrollY,
    },
    summary: {
      elements: elements.length,
      media: media.length,
      images: media.filter((item) => item.sourceType === "img").length,
      videos: media.filter((item) => item.sourceType === "video").length,
      computedBackgrounds: media.filter((item) => item.sourceType === "computed-background").length,
      cssBackgroundRules: cssBackgroundRules.length,
      anchors: anchors.length,
    },
    media,
    cssBackgroundRules,
    anchors,
    elements,
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const a = document.createElement("a");
  const pathName = location.pathname.replace(/^\/+/, "") || "home";
  const viewportName = `${window.innerWidth}x${window.innerHeight}`;
  a.href = URL.createObjectURL(blob);
  a.download = `rh-fullpage-layout-${fileSafe(pathName)}-${viewportName}-${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(a.href);

  console.log("RH full-page layout exported:", data.summary, data.viewport, data.url);
})();
