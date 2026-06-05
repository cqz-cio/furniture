const fallbackGallery = [
  { label: "Hero", kind: "Product front view", tone: "light" },
  { label: "Room", kind: "Styled room image", tone: "warm" },
  { label: "Detail", kind: "Construction detail", tone: "linen" },
  { label: "Material", kind: "Material texture", tone: "fabric" },
  { label: "Scale", kind: "Dimension guide", tone: "line" },
];

const stripHtml = (value = "") =>
  String(value)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n")
    .replace(/<[^>]*>/g, "")
    .replace(/\s+/g, " ")
    .trim();

const normalizeGallery = (product = {}) => {
  const urls = [product.cover, ...(Array.isArray(product.gallery) ? product.gallery : [])].filter(Boolean);
  const images = urls.map((src, index) => ({
    ...fallbackGallery[index % fallbackGallery.length],
    src,
  }));
  return [...images, ...fallbackGallery.slice(images.length)].slice(0, 5);
};

const swatches = {
  linen: [
    { label: "Ivory Performance Linen", swatch: "#ebe5db" },
    { label: "Warm Grey Basketweave", swatch: "#a29b91" },
    { label: "Sand Belgian Linen", swatch: "#c9b99d" },
    { label: "Camel Velvet", swatch: "#a56f3f" },
    { label: "Chocolate Chenille", swatch: "#4d3528" },
    { label: "Graphite Weave", swatch: "#2c2b29" },
  ],
  woodStone: [
    { label: "White Carrara Marble", swatch: "#eeeae2" },
    { label: "Travertine", swatch: "#c9b591" },
    { label: "Black Marble", swatch: "#2e2b28" },
    { label: "Smoked Oak", swatch: "#5a3e2c" },
    { label: "Natural Oak", swatch: "#c6a77c" },
    { label: "Charcoal Oak", swatch: "#24211f" },
  ],
  outdoor: [
    { label: "Sand Perennials", swatch: "#d7c7ae" },
    { label: "Fog Performance Weave", swatch: "#a9aaa4" },
    { label: "Charcoal Canvas", swatch: "#3d3d3a" },
    { label: "Weathered Teak", swatch: "#9d8060" },
    { label: "Natural Teak", swatch: "#b58b5d" },
    { label: "Black Aluminum", swatch: "#1f1f1f" },
  ],
  metal: [
    { label: "Lacquered Brass", swatch: "#b99a58" },
    { label: "Bronze", swatch: "#4f3a2b" },
    { label: "Polished Nickel", swatch: "#d4d5d2" },
    { label: "Matte Black", swatch: "#191919" },
    { label: "Antique Pewter", swatch: "#77736b" },
    { label: "Linen Shade", swatch: "#e7dfcf" },
  ],
};

const fabricSelector = {
  stockedCount: 26,
  specialOrderCount: 191,
  label: "SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS",
  swatches: swatches.linen,
};

const availability = {
  title: "VIEW IN STOCK ITEMS",
  readyToShip: "Ready to ship in 3-7 days",
  specialOrder: "Special order options ship by confirmed production window",
  cta: "View ready-to-ship configurations",
};

const relatedLinks = {
  bed: [
    { label: "ALSO AVAILABLE IN LEATHER", href: "#" },
    { label: "ALSO AVAILABLE FOR CUSTOM CONFIGURATION", href: "#" },
    { label: "EXPLORE THE LUXE BED COLLECTION", href: "#" },
  ],
  sofa: [
    { label: "ALSO AVAILABLE IN LEATHER", href: "#" },
    { label: "ALSO AVAILABLE FOR SECTIONAL CONFIGURATION", href: "#" },
    { label: "EXPLORE THE CLOUD MODULAR COLLECTION", href: "#" },
  ],
  diningTable: [
    { label: "ALSO AVAILABLE WITH WOOD TOP", href: "#" },
    { label: "ALSO AVAILABLE FOR CUSTOM LENGTH", href: "#" },
    { label: "EXPLORE THE MARBLE DINING COLLECTION", href: "#" },
  ],
  chair: [
    { label: "ALSO AVAILABLE AS A DINING CHAIR", href: "#" },
    { label: "ALSO AVAILABLE WITH CUSTOM CUSHIONS", href: "#" },
    { label: "EXPLORE THE OUTDOOR LOUNGE COLLECTION", href: "#" },
  ],
  lighting: [
    { label: "ALSO AVAILABLE AS A SCONCE", href: "#" },
    { label: "ALSO AVAILABLE IN CUSTOM FINISHES", href: "#" },
    { label: "EXPLORE THE ARCHITECTURAL LIGHTING COLLECTION", href: "#" },
  ],
};

const productTypeTemplates = {
  bed: {
    collection: "LUXE BED COLLECTION",
    heroNote: "Shown in Ivory Performance Linen with standard platform base.",
    selector: fabricSelector,
    relatedLinks: relatedLinks.bed,
    highlights: [
      "Hand upholstered in premium performance fabric",
      "Kiln-dried hardwood frame with engineered support",
      "Platform support works with mattress-only or box-spring setups",
      "Designed as a bedroom collection template for later SKU expansion",
    ],
    optionGroups: [
      { key: "size", label: "Size", helper: "Choose the bed frame size.", values: ["Queen 1.5m", "King 1.8m", "California King 2.0m"] },
      { key: "fabric", label: "Fabric", helper: "Stocked and special order upholstery options.", values: swatches.linen.slice(0, 4) },
      { key: "finish", label: "Finish", helper: "Visible frame or leg finish.", values: swatches.woodStone.slice(3, 6) },
      { key: "configuration", label: "Configuration", helper: "Controls base and headboard setup.", values: ["Standard bed", "Storage bed", "Tall headboard", "Bedroom set"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Low, tailored upholstered bed with soft proportions"], ["Structure", "Kiln-dried hardwood and engineered support"], ["Comfort", "Padded headboard designed for relaxed leaning"], ["Compatibility", "Works with common mattress and foundation setups"]] },
      { title: "DIMENSIONS", rows: [["Queen 1.5m", "168W x 214D x 112H cm"], ["King 1.8m", "198W x 214D x 112H cm"], ["California King 2.0m", "218W x 224D x 112H cm"], ["Floor to platform", "28 cm"], ["Headboard depth", "12 cm"]] },
      { title: "MATERIALS", rows: [["Frame", "Kiln-dried hardwood, engineered wood and slat support"], ["Upholstery", "Performance linen, velvet, leather or custom textile"], ["Fill", "High-density foam and soft fiber wrap"], ["Feet", "Recessed wood or adjustable metal glides"]] },
      { title: "CARE", rows: [["Fabric care", "Vacuum with a soft brush attachment"], ["Spills", "Blot immediately with a clean damp cloth"], ["Sunlight", "Avoid prolonged direct sunlight"]] },
      { title: "DELIVERY", rows: [["Delivery", "Scheduled furniture delivery"], ["Assembly", "Bed frame assembly recommended on site"], ["Lead time", "Stocked options ship first; custom options follow confirmed window"]] },
    ],
  },
  sofa: {
    collection: "CLOUD MODULAR COLLECTION",
    heroNote: "Shown in Sand Performance Linen with classic depth and down-blend cushions.",
    selector: fabricSelector,
    relatedLinks: relatedLinks.sofa,
    highlights: [
      "Low, deep modular profile for relaxed living rooms",
      "Down-blend cushions with foam core support",
      "Available in stocked fabrics, leather, sectional pieces and custom layouts",
      "Designed for later expansion into sofa, sectional and chaise SKUs",
    ],
    optionGroups: [
      { key: "configuration", label: "Configuration", helper: "Choose the seating layout before fabric and depth.", values: ["Sofa", "Left-arm sectional", "Right-arm sectional", "Sofa with chaise"] },
      { key: "fabric", label: "Fabric", helper: "Stocked and special order upholstery options.", values: swatches.linen.slice(0, 4) },
      { key: "depth", label: "Depth", helper: "Controls seat depth and room footprint.", values: ["Classic depth", "Luxe depth", "Petite depth"] },
      { key: "fill", label: "Cushion fill", helper: "Defines the sit and maintenance level.", values: ["Down blend", "Foam core", "Performance fiber"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Low modular frame with broad arms and loose back cushions"], ["Frame", "Kiln-dried hardwood frame with reinforced corner blocks"], ["Comfort", "Deep seat with layered cushion support"], ["Configuration", "Single sofa template can expand into sectional pieces later"]] },
      { title: "DIMENSIONS", rows: [["Overall width", "220 / 260 / 300 cm"], ["Overall depth", "105 / 115 cm"], ["Overall height", "78 cm"], ["Seat height", "46 cm"], ["Arm height", "62 cm"]] },
      { title: "MATERIALS", rows: [["Upholstery", "Performance linen, velvet, leather or custom textile"], ["Cushions", "Foam core wrapped in down-blend fiber"], ["Frame", "Hardwood and engineered wood support structure"], ["Feet", "Recessed wood feet in dark finish"]] },
      { title: "CARE", rows: [["Fabric care", "Vacuum with soft brush and spot clean promptly"], ["Cushions", "Rotate and fluff cushions to maintain shape"], ["Sunlight", "Avoid prolonged direct sunlight"]] },
      { title: "DELIVERY", rows: [["Stocked fabric", "Ready to ship in 3-7 days"], ["Custom order", "Ships by confirmed production window"], ["Installation", "Large modules are delivered by appointment"]] },
    ],
  },
  "dining-table": {
    collection: "MARBLE DINING COLLECTION",
    heroNote: "Shown in White Carrara marble top with smoked oak pedestal base.",
    selector: { stockedCount: 8, specialOrderCount: 6, label: "SELECT FROM 8 STONE TOPS AND 6 WOOD FINISHES", swatches: swatches.woodStone },
    relatedLinks: relatedLinks.diningTable,
    highlights: [
      "Statement dining table with stone or wood top options",
      "Pedestal base keeps seating flexible around the table",
      "Available in round, rectangular and extension-ready proportions",
      "Template supports material, shape and seating capacity parameters",
    ],
    optionGroups: [
      { key: "shape", label: "Shape", helper: "Select the dining room footprint.", values: ["Rectangular", "Round", "Oval", "Extension"] },
      { key: "size", label: "Size", helper: "Controls seating capacity and room clearance.", values: ["180 cm", "220 cm", "260 cm", "300 cm"] },
      { key: "top", label: "Top material", helper: "Stone and wood top options inspired by RH dining filters.", values: swatches.woodStone.slice(0, 4) },
      { key: "base", label: "Base finish", helper: "Base finish can map to SKU attributes later.", values: swatches.woodStone.slice(3, 6) },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Sculptural pedestal dining table with quiet geometric base"], ["Top", "Stone or wood top options with softened edges"], ["Base", "Engineered pedestal base designed for stability"], ["Use", "Dining room, breakfast room or hospitality suite"]] },
      { title: "DIMENSIONS", rows: [["Overall width", "180 / 220 / 260 / 300 cm"], ["Overall depth", "95 / 105 / 115 cm"], ["Overall height", "76 cm"], ["Top thickness", "3 cm"], ["Seating capacity", "6 / 8 / 10 seats"]] },
      { title: "MATERIALS", rows: [["Stone", "Marble, travertine or quartz-style finish"], ["Wood", "Oak veneer and engineered wood support core"], ["Base", "Wood pedestal or metal-reinforced base"]] },
      { title: "CARE", rows: [["Stone care", "Use coasters and wipe spills immediately"], ["Wood care", "Clean with soft dry cloth"], ["Heat", "Use pads under hot cookware"]] },
      { title: "DELIVERY", rows: [["Delivery", "White-glove delivery recommended"], ["Assembly", "Base and top require on-site placement"], ["Lead time", "Stocked finishes ship first; special stone follows confirmed window"]] },
    ],
  },
  chair: {
    collection: "OUTDOOR LOUNGE COLLECTION",
    heroNote: "Shown in Weathered Teak with Sand Perennials performance cushion.",
    selector: { stockedCount: 12, specialOrderCount: 48, label: "SELECT FROM 12 STOCKED AND 48 SPECIAL ORDER OUTDOOR FABRICS", swatches: swatches.outdoor },
    relatedLinks: relatedLinks.chair,
    highlights: [
      "Outdoor lounge chair with weather-ready frame and cushions",
      "Performance fabric is selected for outdoor use and easy cleaning",
      "Frame finish, cushion fill and orientation are fixed template parameters",
      "Can later expand into dining chair, lounge chair and swivel chair variants",
    ],
    optionGroups: [
      { key: "frame", label: "Frame", helper: "Choose weathered wood or metal frame finish.", values: swatches.outdoor.slice(3, 6) },
      { key: "fabric", label: "Fabric", helper: "Outdoor stocked and special order cushion fabrics.", values: swatches.outdoor.slice(0, 3) },
      { key: "cushion", label: "Cushion", helper: "Controls cushion profile and comfort.", values: ["Standard cushion", "Luxe cushion", "Quick-dry cushion"] },
      { key: "orientation", label: "Orientation", helper: "Useful for lounge chair or modular outdoor layouts.", values: ["Stationary", "Swivel", "Left arm", "Right arm"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Relaxed outdoor lounge chair with angled back"], ["Frame", "Teak or powder-coated metal frame options"], ["Cushion", "Performance cushion with outdoor fabric cover"], ["Use", "Terrace, patio, sunroom or garden room"]] },
      { title: "DIMENSIONS", rows: [["Overall width", "82 cm"], ["Overall depth", "90 cm"], ["Overall height", "78 cm"], ["Seat height", "43 cm"], ["Arm height", "60 cm"]] },
      { title: "MATERIALS", rows: [["Frame", "Weathered teak or powder-coated aluminum"], ["Fabric", "Outdoor performance fabric"], ["Fill", "Quick-dry foam and fiber wrap"]] },
      { title: "CARE", rows: [["Outdoor care", "Cover or store cushions during heavy weather"], ["Frame care", "Clean with mild soap and water"], ["Fabric care", "Spot clean and air dry"]] },
      { title: "DELIVERY", rows: [["Delivery", "Ships assembled or with minimal setup"], ["Stocked fabrics", "Ready to ship in 3-7 days"], ["Custom cushions", "Ships by confirmed production window"]] },
    ],
  },
  lighting: {
    collection: "ARCHITECTURAL LIGHTING COLLECTION",
    heroNote: "Shown in Lacquered Brass with linen shade and warm dimmable bulb.",
    selector: { stockedCount: 6, specialOrderCount: 4, label: "SELECT FROM 6 METAL FINISHES AND 4 SHADE OPTIONS", swatches: swatches.metal },
    relatedLinks: relatedLinks.lighting,
    highlights: [
      "Architectural fixture template for pendant, sconce and chandelier products",
      "Finish, shade, bulb and canopy are fixed lighting parameters",
      "Designed for warm residential ambience with dimmable setup",
      "Installation notes are included for admin-to-web completeness",
    ],
    optionGroups: [
      { key: "size", label: "Size", helper: "Choose fixture diameter or drop length.", values: ["Small", "Medium", "Large", "Linear"] },
      { key: "finish", label: "Finish", helper: "Metal finish for fixture body and canopy.", values: swatches.metal.slice(0, 5) },
      { key: "shade", label: "Shade", helper: "Shade options for light diffusion.", values: ["Linen shade", "Glass shade", "Metal shade", "No shade"] },
      { key: "bulb", label: "Bulb", helper: "Defines light source and compatibility.", values: ["E26 LED", "G9 LED", "Integrated LED", "Dimmable warm LED"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Clean architectural lighting fixture with warm residential tone"], ["Mounting", "Ceiling or wall template depending on final SKU"], ["Dimming", "Compatible with dimmable LED setup where specified"], ["Use", "Dining room, bedroom, entry or hospitality suite"]] },
      { title: "DIMENSIONS", rows: [["Overall width", "32 / 48 / 72 cm"], ["Overall height", "38 / 52 / 68 cm"], ["Canopy", "13 cm diameter"], ["Cord length", "Adjustable up to 180 cm"], ["Weight", "4-12 kg by size"]] },
      { title: "MATERIALS", rows: [["Body", "Steel or brass with plated finish"], ["Shade", "Linen, glass or metal shade options"], ["Canopy", "Matching metal canopy"]] },
      { title: "CARE", rows: [["Cleaning", "Dust with a soft dry cloth"], ["Shade care", "Use lint roller or low suction for fabric shade"], ["Electrical", "Disconnect power before bulb replacement"]] },
      { title: "DELIVERY", rows: [["Delivery", "Small parcel or scheduled delivery by size"], ["Installation", "Professional installation recommended"], ["Lead time", "Stocked finishes ship first; custom finishes follow confirmed window"]] },
    ],
  },
};

const inferProductType = (product = {}) => {
  const rawType = String(product.detailConfig?.productType || product.productType || product.type || product.category || "").toLowerCase();
  const text = `${rawType} ${product.name || ""} ${product.subtitle || ""}`.toLowerCase();

  if (text.includes("dining") || text.includes("table")) return "dining-table";
  if (text.includes("chair")) return "chair";
  if (text.includes("lighting") || text.includes("lamp") || text.includes("light")) return "lighting";
  if (text.includes("sofa") || text.includes("sectional")) return "sofa";
  if (text.includes("bed")) return "bed";
  return "bed";
};

export const buildProductDetailModel = (product = {}) => {
  const productType = inferProductType(product);
  const template = productTypeTemplates[productType] || productTypeTemplates.bed;
  const detailConfig = product.detailConfig || {};
  const description =
    stripHtml(product.description || product.subtitle) ||
    "A polished furniture detail template with fixed product information fields, ready for final parameters and product images.";

  return {
    id: product.id,
    skuId: product.skuId,
    source: product.source || "demo",
    productType,
    collection: detailConfig.collection || product.collection || template.collection,
    name: product.name || "Luxury Furniture",
    description,
    heroNote: detailConfig.heroNote || product.heroNote || template.heroNote,
    gallery: normalizeGallery(product),
    price: {
      prefix: "Starting at",
      member: Number(product.price) || 888,
      sale: Number(product.salePrice) || 932,
      regular: Number(product.marketPrice) || 999,
      memberLabel: "Member",
      saleLabel: "Sale",
      regularLabel: "Regular",
      savingsLabel: "SAVE 30% ON SELECT ITEMS",
      context: "Starting at price reflects the displayed size and stocked finish.",
    },
    stock: {
      label: "Inventory",
      value: Number(product.stock ?? 20),
      status: Number(product.stock ?? 20) > 0 ? "In stock" : "Made to order",
    },
    fabricSelector: detailConfig.fabricSelector || product.fabricSelector || template.selector,
    availability: detailConfig.availability || product.availability || availability,
    highlights: detailConfig.highlights || product.highlights || template.highlights,
    relatedLinks: detailConfig.relatedLinks || product.relatedLinks || template.relatedLinks,
    optionGroups: detailConfig.optionGroups || product.optionGroups || template.optionGroups,
    accordions: detailConfig.accordions || product.accordions || template.accordions,
  };
};
