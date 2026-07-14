import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { spawn } from "node:child_process";
import { chromium } from "playwright";

const root = path.resolve(import.meta.dirname, "..");
const port = 5176;
const baseUrl = `http://127.0.0.1:${port}`;
const captureDir = path.join(root, "captures", "local");

fs.mkdirSync(captureDir, { recursive: true });

const server = spawn("cmd.exe", ["/d", "/s", "/c", `npm.cmd run dev -- --port ${port}`], {
  cwd: root,
  stdio: ["ignore", "pipe", "pipe"],
  windowsHide: true,
});

let stdout = "";
let stderr = "";
server.stdout.on("data", (chunk) => {
  stdout += chunk.toString();
});
server.stderr.on("data", (chunk) => {
  stderr += chunk.toString();
});

const waitForServer = async () => {
  const started = Date.now();
  while (Date.now() - started < 30_000) {
    const ok = await new Promise((resolve) => {
      const req = http.get(baseUrl, (res) => {
        res.resume();
        resolve(res.statusCode >= 200 && res.statusCode < 500);
      });
      req.on("error", () => resolve(false));
      req.setTimeout(1000, () => {
        req.destroy();
        resolve(false);
      });
    });
    if (ok) return;
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  throw new Error(`Vite server did not start.\nSTDOUT:\n${stdout}\nSTDERR:\n${stderr}`);
};

const verifyPage = async (page, route, fileName) => {
  await page.goto(`${baseUrl}${route}`);
  await page.waitForLoadState("networkidle");
  await page.evaluate(async () => {
    const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    for (let y = 0; y <= document.documentElement.scrollHeight; y += 700) {
      window.scrollTo(0, y);
      await pause(80);
    }
    window.scrollTo(0, 0);
    await pause(200);
  });
  await page.screenshot({ path: path.join(captureDir, fileName), fullPage: false });

  return page.evaluate(() => {
    const images = [...document.querySelectorAll("img")].map((img) => ({
      src: img.getAttribute("src"),
      complete: img.complete,
      naturalWidth: img.naturalWidth,
      naturalHeight: img.naturalHeight,
      visible: img.getBoundingClientRect().width > 0 && img.getBoundingClientRect().height > 0,
    }));
    const imageSpecs = [...document.querySelectorAll(".image-spec")].map((el) => ({
      hasImage: Boolean(el.querySelector("img")),
      visible: el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0,
    }));
    return {
      url: location.pathname + location.search,
      imageCount: images.length,
      brokenImages: images
        .filter((img) => img.visible && (!img.complete || img.naturalWidth === 0))
        .map((img) => img.src),
      loadedGeneratedImages: images.filter(
        (img) => img.src?.includes("/assets/generated-furniture/") && img.complete && img.naturalWidth > 0,
      ).length,
      visibleGeneratedImages: images.filter((img) => img.visible && img.src?.includes("/assets/generated-furniture/")).length,
      visibleImageSpecsWithImages: imageSpecs.filter((item) => item.visible && item.hasImage).length,
      visibleImageSpecsWithoutImages: imageSpecs.filter((item) => item.visible && !item.hasImage).length,
    };
  });
};

let browser;
try {
  await waitForServer();
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1365, height: 953 } });
  const desktopChecks = [
    await verifyPage(page, "/", "generated-assets-home-1365.png"),
    await verifyPage(page, "/sale", "generated-assets-sale-1365.png"),
    await verifyPage(page, "/sofas-plp", "generated-assets-plp-1365.png"),
    await verifyPage(page, "/sofa-pdp?id=1001", "generated-assets-pdp-1365.png"),
  ];

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileSale = await verifyPage(page, "/sale", "generated-assets-sale-390.png");
  const checks = [...desktopChecks, mobileSale];
  const broken = checks.flatMap((check) => check.brokenImages.map((src) => `${check.url}: ${src}`));
  if (broken.length > 0) {
    throw new Error(`Broken images found:\n${broken.join("\n")}`);
  }

  console.log(JSON.stringify(checks, null, 2));
} finally {
  if (browser) await browser.close();
  if (server.pid) {
    const killer = spawn("taskkill", ["/PID", String(server.pid), "/T", "/F"], {
      stdio: "ignore",
      windowsHide: true,
      detached: true,
    });
    killer.unref();
    server.stdout.destroy();
    server.stderr.destroy();
  } else {
    server.kill();
  }
}

process.exit(0);
