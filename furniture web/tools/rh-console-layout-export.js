(() => {
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

  function clean(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

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
        if (sameTagSiblings.length > 1) {
          part += `:nth-of-type(${sameTagSiblings.indexOf(node) + 1})`;
        }
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
    if (authoringName) {
      return `${el.tagName.toLowerCase()}[authoringname="${authoringName.replaceAll('"', '\\"')}"]`;
    }
    const cls = Array.from(el.classList || [])
      .slice(0, 4)
      .map((name) => `.${CSS.escape(name)}`)
      .join("");
    return `${el.tagName.toLowerCase()}${cls}`;
  }

  function textFor(el) {
    const text = clean(el.innerText || el.textContent || "");
    return text.length > 240 ? `${text.slice(0, 240)}...` : text;
  }

  function isElementWorthKeeping(el, rect, computed) {
    if (el === document.body) return true;
    if (landmarkTags.has(el.tagName)) return true;
    if (el.id || el.getAttribute("role") || el.getAttribute("aria-label")) return true;
    if (el.getAttribute("authoringname") || el.getAttribute("data-testid")) return true;
    if (computed.backgroundImage && computed.backgroundImage !== "none") return true;
    if (clean(el.innerText || "").length > 0 && rect.width * rect.height > 100) return true;
    return false;
  }

  const elements = [];
  const images = [];

  for (const el of document.querySelectorAll("body, body *")) {
    const rect = el.getBoundingClientRect();
    const computed = getComputedStyle(el);
    const area = rect.width * rect.height;
    const visible =
      rect.width > 0 &&
      rect.height > 0 &&
      area > 4 &&
      computed.display !== "none" &&
      computed.visibility !== "hidden" &&
      computed.opacity !== "0";

    if (!visible || !isElementWorthKeeping(el, rect, computed)) continue;

    const styles = {};
    for (const key of styleKeys) styles[key] = computed[key];

    const record = {
      selector: selectorFor(el),
      cssPath: cssPath(el),
      tag: el.tagName.toLowerCase(),
      id: el.id || null,
      className: typeof el.className === "string" ? el.className : null,
      role: el.getAttribute("role"),
      ariaLabel: el.getAttribute("aria-label"),
      authoringName: el.getAttribute("authoringname"),
      dataTestId: el.getAttribute("data-testid"),
      href: el.getAttribute("href"),
      text: textFor(el),
      rect: {
        x: Math.round(rect.x * 100) / 100,
        y: Math.round(rect.y * 100) / 100,
        width: Math.round(rect.width * 100) / 100,
        height: Math.round(rect.height * 100) / 100,
      },
      viewportRect: {
        top: Math.round(rect.top * 100) / 100,
        right: Math.round(rect.right * 100) / 100,
        bottom: Math.round(rect.bottom * 100) / 100,
        left: Math.round(rect.left * 100) / 100,
      },
      styles,
    };

    elements.push(record);

    if (el.tagName === "IMG") {
      images.push({
        selector: record.selector,
        cssPath: record.cssPath,
        src: el.currentSrc || el.src,
        alt: el.getAttribute("alt"),
        naturalWidth: el.naturalWidth || null,
        naturalHeight: el.naturalHeight || null,
        renderedWidth: record.rect.width,
        renderedHeight: record.rect.height,
        objectFit: computed.objectFit,
        objectPosition: computed.objectPosition,
      });
    }

    if (computed.backgroundImage && computed.backgroundImage !== "none") {
      images.push({
        selector: record.selector,
        cssPath: record.cssPath,
        src: computed.backgroundImage,
        alt: null,
        naturalWidth: null,
        naturalHeight: null,
        renderedWidth: record.rect.width,
        renderedHeight: record.rect.height,
        objectFit: "background-image",
        backgroundSize: computed.backgroundSize,
        backgroundPosition: computed.backgroundPosition,
        backgroundRepeat: computed.backgroundRepeat,
      });
    }
  }

  const anchors = Array.from(document.querySelectorAll("a"))
    .map((a) => {
      const rect = a.getBoundingClientRect();
      return {
        selector: selectorFor(a),
        cssPath: cssPath(a),
        text: textFor(a),
        href: a.href || a.getAttribute("href"),
        ariaLabel: a.getAttribute("aria-label"),
        rect: {
          x: Math.round(rect.x * 100) / 100,
          y: Math.round(rect.y * 100) / 100,
          width: Math.round(rect.width * 100) / 100,
          height: Math.round(rect.height * 100) / 100,
        },
      };
    })
    .filter((a) => a.href || a.text || a.ariaLabel);

  const data = {
    url: location.href,
    title: document.title,
    capturedAt: new Date().toISOString(),
    viewport: {
      width: window.innerWidth,
      height: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio,
    },
    scroll: {
      x: window.scrollX,
      y: window.scrollY,
    },
    document: {
      scrollWidth: document.documentElement.scrollWidth,
      scrollHeight: document.documentElement.scrollHeight,
      clientWidth: document.documentElement.clientWidth,
      clientHeight: document.documentElement.clientHeight,
    },
    body: {
      brand: document.body.getAttribute("data-brand"),
      pagePath: document.body.getAttribute("data-page-path"),
      userType: document.body.getAttribute("data-user-type"),
    },
    summary: {
      elements: elements.length,
      images: images.length,
      anchors: anchors.length,
    },
    elements,
    images,
    anchors,
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json",
  });
  const a = document.createElement("a");
  const pathName = location.pathname.replace(/^\/+/, "") || "home";
  const viewportName = `${window.innerWidth}x${window.innerHeight}`;
  a.href = URL.createObjectURL(blob);
  a.download = `rh-layout-${fileSafe(pathName)}-${viewportName}-${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(a.href);

  console.log("RH layout exported:", data.summary, data.viewport, data.url);
})();
