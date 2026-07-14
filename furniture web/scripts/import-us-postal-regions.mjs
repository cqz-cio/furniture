import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const [, , inputArg, outputArg] = process.argv;

if (!inputArg || !outputArg) {
  console.error("Usage: node scripts/import-us-postal-regions.mjs <input.csv> <output.js>");
  process.exit(1);
}

const clean = (value) => String(value || "").trim();
const normalizeZip = (value) => clean(value).match(/\d{5}/)?.[0] || "";
const normalizeState = (value) => clean(value).toUpperCase();
const titleizeCity = (value) =>
  clean(value)
    .toLowerCase()
    .replace(/\b[a-z]/g, (letter) => letter.toUpperCase());

const parseCsvLine = (line) => {
  const cells = [];
  let cell = "";
  let quoted = false;

  for (let index = 0; index < line.length; index += 1) {
    const character = line[index];
    const next = line[index + 1];

    if (character === '"' && quoted && next === '"') {
      cell += '"';
      index += 1;
    } else if (character === '"') {
      quoted = !quoted;
    } else if (character === "," && !quoted) {
      cells.push(cell);
      cell = "";
    } else {
      cell += character;
    }
  }

  cells.push(cell);
  return cells.map(clean);
};

const normalizeHeader = (value) => clean(value).replace(/\s+/g, "_").toLowerCase();
const firstValue = (row, keys) => keys.map((key) => row[key]).find((value) => clean(value));

const parseCsv = (source) => {
  const lines = source.replace(/^\uFEFF/, "").split(/\r?\n/).filter((line) => clean(line));
  const headers = parseCsvLine(lines[0] || "").map(normalizeHeader);

  return lines.slice(1).map((line) => {
    const cells = parseCsvLine(line);
    return headers.reduce((row, header, index) => {
      row[header] = cells[index] || "";
      return row;
    }, {});
  });
};

const regions = parseCsv(readFileSync(resolve(inputArg), "utf8"))
  .map((row) => ({
    postalCode: normalizeZip(firstValue(row, ["postalcode", "postal_code", "zip", "zipcode", "zip_code"])),
    city: titleizeCity(firstValue(row, ["city", "primary_city", "placename", "place_name"])),
    state: normalizeState(firstValue(row, ["state", "state_id", "statecode", "state_code"])),
  }))
  .filter((region) => region.postalCode && region.city && region.state)
  .sort((left, right) => left.postalCode.localeCompare(right.postalCode));

const uniqueRegions = Array.from(new Map(regions.map((region) => [region.postalCode, region])).values());
const lines = uniqueRegions.map(
  (region) => `  { postalCode: "${region.postalCode}", city: "${region.city}", state: "${region.state}" },`,
);
const output = `export const US_POSTAL_REGIONS = [\n${lines.join("\n")}\n];\n`;
const outputPath = resolve(outputArg);

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, output, "utf8");
console.log(`Imported ${uniqueRegions.length} US postal regions into ${outputPath}`);
