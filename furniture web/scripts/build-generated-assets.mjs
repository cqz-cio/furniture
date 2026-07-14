import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const projectRoot = path.resolve(import.meta.dirname, "..");
const generatedDir = path.join(
  process.env.USERPROFILE || process.env.HOME,
  ".codex",
  "generated_images",
  "019eba9f-24de-7390-bb89-d59b3475ea18",
);
const outputDir = path.join(projectRoot, "public", "assets", "generated-furniture");
const contactSheetPath = path.join(projectRoot, "captures", "local", "generated-furniture-contact-sheet.webp");

const sourceFiles = fs
  .readdirSync(generatedDir)
  .filter((name) => name.endsWith(".png"))
  .map((name) => path.join(generatedDir, name))
  .sort((a, b) => fs.statSync(a).mtimeMs - fs.statSync(b).mtimeMs);

if (sourceFiles.length < 11) {
  throw new Error(`Expected 11 generated source images, found ${sourceFiles.length}`);
}

const source = {
  living: sourceFiles[0],
  bedroom: sourceFiles[1],
  dining: sourceFiles[2],
  outdoor: sourceFiles[3],
  bath: sourceFiles[4],
  sofa: sourceFiles[5],
  bed: sourceFiles[6],
  table: sourceFiles[7],
  chair: sourceFiles[8],
  pendant: sourceFiles[9],
  rug: sourceFiles[10],
};

const assets = [
  ["home-hero-desktop.webp", source.living, 1600, 1076, "cover", 0.82],
  ["home-hero-mobile.webp", source.living, 780, 1200, "cover", 0.78],
  ["home-module-002-bedroom-desktop.webp", source.bedroom, 1600, 1076, "cover", 0.8],
  ["home-module-002-bedroom-mobile.webp", source.bedroom, 780, 1200, "cover", 0.76],
  ["home-module-003-dining-desktop.webp", source.dining, 1600, 1076, "cover", 0.8],
  ["home-module-003-dining-mobile.webp", source.dining, 780, 934, "cover", 0.76],
  ["home-module-004-outdoor-desktop.webp", source.outdoor, 1600, 1077, "cover", 0.8],
  ["home-module-004-outdoor-mobile.webp", source.outdoor, 780, 934, "cover", 0.76],
  ["home-module-005-sourcebook-desktop.webp", source.living, 1600, 1150, "cover", 0.78],
  ["home-module-005-sourcebook-mobile.webp", source.living, 780, 561, "cover", 0.74],
  ["sale-hero-desktop.webp", source.outdoor, 1600, 688, "cover", 0.78],
  ["sale-hero-mobile.webp", source.outdoor, 780, 490, "cover", 0.74],
  ["sale-membership-desktop.webp", source.living, 1600, 842, "cover", 0.78],
  ["sale-membership-mobile.webp", source.living, 780, 1047, "cover", 0.74],
  ["sale-category-living-desktop.webp", source.living, 1200, 700, "cover", 0.72],
  ["sale-category-living-mobile.webp", source.living, 780, 456, "cover", 0.7],
  ["sale-category-sofas-desktop.webp", source.sofa, 1200, 700, "contain", 0.72],
  ["sale-category-sofas-mobile.webp", source.sofa, 780, 456, "contain", 0.7],
  ["sale-category-dining-desktop.webp", source.dining, 1200, 700, "cover", 0.72],
  ["sale-category-dining-mobile.webp", source.dining, 780, 456, "cover", 0.7],
  ["sale-category-bedroom-desktop.webp", source.bedroom, 1200, 700, "cover", 0.72],
  ["sale-category-bedroom-mobile.webp", source.bedroom, 780, 456, "cover", 0.7],
  ["sale-category-bath-desktop.webp", source.bath, 1200, 700, "cover", 0.72],
  ["sale-category-bath-mobile.webp", source.bath, 780, 456, "cover", 0.7],
  ["sale-category-outdoor-desktop.webp", source.outdoor, 1200, 700, "cover", 0.72],
  ["sale-category-outdoor-mobile.webp", source.outdoor, 780, 456, "cover", 0.7],
  ["sale-category-rugs-desktop.webp", source.rug, 1200, 700, "cover", 0.72],
  ["sale-category-rugs-mobile.webp", source.rug, 780, 456, "cover", 0.7],
  ["sale-category-lighting-desktop.webp", source.pendant, 1200, 700, "contain", 0.72],
  ["sale-category-lighting-mobile.webp", source.pendant, 780, 456, "contain", 0.7],
  ["sale-category-bedding-desktop.webp", source.bedroom, 1200, 700, "cover", 0.72],
  ["sale-category-bedding-mobile.webp", source.bedroom, 780, 456, "cover", 0.7],
  ["sale-category-bath-towels-desktop.webp", source.bath, 1200, 700, "cover", 0.72],
  ["sale-category-bath-towels-mobile.webp", source.bath, 780, 456, "cover", 0.7],
  ["product-sofa-cover.webp", source.sofa, 900, 1125, "contain", 0.78, { cropTop: 0.18 }],
  ["product-sofa-gallery.webp", source.sofa, 1254, 1222, "contain", 0.82, { cropTop: 0.18 }],
  ["product-bed-cover.webp", source.bed, 900, 1125, "contain", 0.78],
  ["product-bed-gallery.webp", source.bed, 1254, 1222, "contain", 0.82],
  ["product-table-cover.webp", source.table, 900, 1125, "contain", 0.78],
  ["product-table-gallery.webp", source.table, 1254, 1222, "contain", 0.82],
  ["product-chair-cover.webp", source.chair, 900, 1125, "contain", 0.78],
  ["product-chair-gallery.webp", source.chair, 1254, 1222, "contain", 0.82],
  ["product-pendant-cover.webp", source.pendant, 900, 1125, "contain", 0.78],
  ["product-pendant-gallery.webp", source.pendant, 1254, 1222, "contain", 0.82],
];

const toFileUrl = (filePath) => `file:///${filePath.replace(/\\/g, "/")}`;
const assetUrl = (filePath) => `/assets/generated-furniture/${filePath}`;
const toPngDataUrl = (filePath) => `data:image/png;base64,${fs.readFileSync(filePath).toString("base64")}`;

fs.mkdirSync(outputDir, { recursive: true });
fs.mkdirSync(path.dirname(contactSheetPath), { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage();

await page.setContent("<!doctype html><html><body></body></html>");

const renderedAssets = [];

for (const [name, input, width, height, fit, quality, options = {}] of assets) {
  const webpBase64 = await page.evaluate(
    async ({ src, width, height, fit, quality, cropTop }) => {
      const img = new Image();
      img.decoding = "async";
      img.src = src;
      await img.decode();

      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#f3f1ec";
      ctx.fillRect(0, 0, width, height);

      const sourceY = img.naturalHeight * cropTop;
      const sourceHeight = img.naturalHeight - sourceY;
      const scale =
        fit === "contain"
          ? Math.min(width / img.naturalWidth, height / sourceHeight)
          : Math.max(width / img.naturalWidth, height / sourceHeight);
      const drawWidth = img.naturalWidth * scale;
      const drawHeight = sourceHeight * scale;
      const dx = (width - drawWidth) / 2;
      const dy = (height - drawHeight) / 2;
      ctx.drawImage(img, 0, sourceY, img.naturalWidth, sourceHeight, dx, dy, drawWidth, drawHeight);

      return canvas.toDataURL("image/webp", quality).split(",")[1];
    },
    { src: toPngDataUrl(input), width, height, fit, quality, cropTop: options.cropTop || 0 },
  );

  const outPath = path.join(outputDir, name);
  fs.writeFileSync(outPath, Buffer.from(webpBase64, "base64"));
  renderedAssets.push({ name, path: outPath, bytes: fs.statSync(outPath).size });
}

const contactSheetBase64 = await page.evaluate(
  async ({ items }) => {
    const thumbWidth = 260;
    const thumbHeight = 180;
    const gap = 18;
    const labelHeight = 34;
    const cols = 4;
    const rows = Math.ceil(items.length / cols);
    const canvas = document.createElement("canvas");
    canvas.width = cols * thumbWidth + (cols + 1) * gap;
    canvas.height = rows * (thumbHeight + labelHeight) + (rows + 1) * gap;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#f7f5f1";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.font = "12px Arial";
    ctx.fillStyle = "#2f2d29";

    for (let index = 0; index < items.length; index += 1) {
      const item = items[index];
      const img = new Image();
      img.decoding = "async";
      img.src = item.src;
      await img.decode();
      const col = index % cols;
      const row = Math.floor(index / cols);
      const x = gap + col * (thumbWidth + gap);
      const y = gap + row * (thumbHeight + labelHeight + gap);
      ctx.fillStyle = "#ebe7df";
      ctx.fillRect(x, y, thumbWidth, thumbHeight);
      const scale = Math.min(thumbWidth / img.naturalWidth, thumbHeight / img.naturalHeight);
      const dw = img.naturalWidth * scale;
      const dh = img.naturalHeight * scale;
      ctx.drawImage(img, x + (thumbWidth - dw) / 2, y + (thumbHeight - dh) / 2, dw, dh);
      ctx.fillStyle = "#2f2d29";
      ctx.fillText(item.label.slice(0, 34), x, y + thumbHeight + 20);
    }

    return canvas.toDataURL("image/webp", 0.74).split(",")[1];
  },
  {
    items: Object.entries(source).map(([label, filePath]) => ({
      label,
      src: toPngDataUrl(filePath),
    })),
  },
);

fs.writeFileSync(contactSheetPath, Buffer.from(contactSheetBase64, "base64"));

await browser.close();

console.table(
  renderedAssets.map((asset) => ({
    file: assetUrl(asset.name),
    kb: Math.round(asset.bytes / 1024),
  })),
);
console.log(`Contact sheet: ${contactSheetPath}`);
