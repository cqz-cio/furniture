const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const [url, viewportArg, outputJson, outputPng] = process.argv.slice(2);

if (!url || !viewportArg || !outputJson || !outputPng) {
  console.error("Usage: node scripts/measure-layout.cjs <url> <width>x<height> <output.json> <output.png>");
  process.exit(1);
}

const match = viewportArg.match(/^(\d+)x(\d+)$/);
if (!match) {
  console.error("Viewport must look like 1440x900");
  process.exit(1);
}

const viewport = {
  width: Number(match[1]),
  height: Number(match[2]),
};

function ensureDir(filePath) {
  return fs.mkdir(path.dirname(filePath), { recursive: true });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport });

  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 45000 });
  await page.waitForTimeout(3500);

  await ensureDir(outputPng);
  await page.screenshot({ path: outputPng, fullPage: true });

  const data = await page.evaluate(() => {
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
      "fontFamily",
      "fontSize",
      "fontWeight",
      "lineHeight",
      "letterSpacing",
      "textTransform",
      "color",
      "backgroundColor",
      "backgroundImage",
      "borderTopWidth",
      "borderRightWidth",
      "borderBottomWidth",
      "borderLeftWidth",
      "borderRadius",
      "boxShadow",
      "objectFit",
      "objectPosition",
      "overflow",
      "zIndex",
    ];

    const interestingTags = new Set([
      "BODY",
      "HEADER",
      "NAV",
      "MAIN",
      "FOOTER",
      "SECTION",
      "ARTICLE",
      "ASIDE",
      "IMG",
      "A",
      "BUTTON",
      "H1",
      "H2",
      "H3",
      "P",
      "UL",
      "LI",
      "FORM",
      "INPUT",
    ]);

    function selectorFor(el) {
      if (el.id) return `${el.tagName.toLowerCase()}#${CSS.escape(el.id)}`;
      const testId = el.getAttribute("data-testid");
      if (testId) return `${el.tagName.toLowerCase()}[data-testid="${testId}"]`;
      const authoringName = el.getAttribute("authoringname");
      if (authoringName) return `${el.tagName.toLowerCase()}[authoringname="${authoringName.replaceAll('"', '\\"')}"]`;
      const cls = Array.from(el.classList || []).slice(0, 3).map((name) => `.${CSS.escape(name)}`).join("");
      return `${el.tagName.toLowerCase()}${cls}`;
    }

    function textFor(el) {
      const text = (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
      return text.length > 180 ? `${text.slice(0, 180)}...` : text;
    }

    const elements = [];
    const images = [];
    const all = Array.from(document.querySelectorAll("body, body *"));

    for (const el of all) {
      const rect = el.getBoundingClientRect();
      const area = rect.width * rect.height;
      const computed = getComputedStyle(el);
      const hasBackgroundImage = computed.backgroundImage && computed.backgroundImage !== "none";
      const isMeaningful =
        interestingTags.has(el.tagName) ||
        el.id ||
        el.getAttribute("role") ||
        el.getAttribute("authoringname") ||
        hasBackgroundImage;

      if (!isMeaningful || rect.width <= 0 || rect.height <= 0 || area < 16) continue;

      const styles = {};
      for (const key of styleKeys) {
        styles[key] = computed[key];
      }

      const record = {
        selector: selectorFor(el),
        tag: el.tagName.toLowerCase(),
        id: el.id || null,
        className: typeof el.className === "string" ? el.className : null,
        role: el.getAttribute("role"),
        authoringName: el.getAttribute("authoringname"),
        ariaLabel: el.getAttribute("aria-label"),
        href: el.getAttribute("href"),
        text: textFor(el),
        rect: {
          x: Math.round(rect.x * 100) / 100,
          y: Math.round(rect.y * 100) / 100,
          width: Math.round(rect.width * 100) / 100,
          height: Math.round(rect.height * 100) / 100,
        },
        styles,
      };

      elements.push(record);

      if (el.tagName === "IMG") {
        images.push({
          selector: record.selector,
          src: el.currentSrc || el.src,
          alt: el.getAttribute("alt"),
          naturalWidth: el.naturalWidth || null,
          naturalHeight: el.naturalHeight || null,
          renderedWidth: record.rect.width,
          renderedHeight: record.rect.height,
          objectFit: computed.objectFit,
          objectPosition: computed.objectPosition,
        });
      } else if (hasBackgroundImage) {
        images.push({
          selector: record.selector,
          src: computed.backgroundImage,
          alt: null,
          naturalWidth: null,
          naturalHeight: null,
          renderedWidth: record.rect.width,
          renderedHeight: record.rect.height,
          objectFit: "background-image",
          objectPosition: computed.backgroundPosition,
        });
      }
    }

    return {
      url: location.href,
      title: document.title,
      capturedAt: new Date().toISOString(),
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
        devicePixelRatio: window.devicePixelRatio,
      },
      document: {
        scrollWidth: document.documentElement.scrollWidth,
        scrollHeight: document.documentElement.scrollHeight,
      },
      body: {
        brand: document.body.getAttribute("data-brand"),
        pagePath: document.body.getAttribute("data-page-path"),
        userType: document.body.getAttribute("data-user-type"),
      },
      summary: {
        elements: elements.length,
        images: images.length,
      },
      elements,
      images,
    };
  });

  await ensureDir(outputJson);
  await fs.writeFile(outputJson, JSON.stringify(data, null, 2), "utf8");
  await browser.close();

  console.log(`Wrote ${outputJson}`);
  console.log(`Wrote ${outputPng}`);
  console.log(`Elements: ${data.summary.elements}`);
  console.log(`Images: ${data.summary.images}`);
})();
