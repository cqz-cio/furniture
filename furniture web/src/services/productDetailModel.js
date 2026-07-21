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

const membershipPrompt = {
  title: "Member pricing available",
  copy: "Sign in or join the Members Program to review eligible savings before checkout.",
  href: "/membership",
  linkLabel: "Learn More",
};

const purchaseAssurance = [
  { title: "Delivery", copy: "Delivery windows are shown before checkout and confirmed after order review." },
  { title: "Installation", copy: "Large furniture can be scheduled with room-of-choice placement." },
  { title: "Returns", copy: "Review eligible returns, exchanges and custom-order terms before purchase." },
];

const companionProducts = {
  sofa: [
    { title: "Round Oak Table", href: "/product?id=1003", image: "/assets/generated-furniture/product-table-cover.webp" },
    { title: "Bedroom Side Chair", href: "/product?id=1008", image: "/assets/generated-furniture/product-chair-cover.webp" },
  ],
  bed: [
    { title: "Oak Nightstand", href: "/product?id=1002", image: "/assets/generated-furniture/product-bed-cover.webp" },
    { title: "End-of-Bed Bench", href: "/product?id=1004", image: "/assets/generated-furniture/product-chair-cover.webp" },
  ],
  "dining-table": [
    { title: "Bedroom Side Chair", href: "/product?id=1008", image: "/assets/generated-furniture/product-chair-cover.webp" },
    { title: "Walnut Writing Desk", href: "/product?id=1007", image: "/assets/generated-furniture/teen-study.webp" },
  ],
  chair: [
    { title: "Walnut Single Sofa", href: "/product?id=1001", image: "/assets/generated-furniture/product-sofa-cover.webp" },
    { title: "Round Oak Table", href: "/product?id=1003", image: "/assets/generated-furniture/product-table-cover.webp" },
  ],
  lighting: [
    { title: "Round Oak Table", href: "/product?id=1003", image: "/assets/generated-furniture/product-table-cover.webp" },
    { title: "Oak Nightstand", href: "/product?id=1002", image: "/assets/generated-furniture/product-bed-cover.webp" },
  ],
};

const roomInspiration = {
  bed: [
    {
      title: "Bedroom layers",
      copy: "Pair upholstered softness with quiet oak, linen and warm bedside lighting.",
      image: "/assets/generated-furniture/home-module-002-bedroom-desktop.webp",
    },
    {
      title: "Material close-up",
      copy: "Use woven textiles and natural wood finishes to keep the room calm.",
      image: "/assets/generated-furniture/product-bed-gallery.webp",
    },
  ],
  sofa: [
    {
      title: "Style the room",
      copy: "Anchor the seating area with deep upholstery, low tables and tonal textiles.",
      image: "/assets/generated-furniture/home-module-002-bedroom-desktop.webp",
    },
    {
      title: "Living room texture",
      copy: "Layer performance linen with warm wood and sculptural lighting.",
      image: "/assets/generated-furniture/product-sofa-gallery.webp",
    },
  ],
  "dining-table": [
    {
      title: "Dining composition",
      copy: "Balance stone, oak and softened seating for a grounded dining room.",
      image: "/assets/generated-furniture/home-module-003-dining-desktop.webp",
    },
    {
      title: "Stone detail",
      copy: "Let the table surface carry the room while surrounding UI stays quiet.",
      image: "/assets/generated-furniture/product-table-gallery.webp",
    },
  ],
  chair: [
    {
      title: "Outdoor room",
      copy: "Use weathered teak, performance cushions and open-air spacing.",
      image: "/assets/generated-furniture/outdoor-landing-hero-desktop.webp",
    },
    {
      title: "Frame and cushion",
      copy: "Keep outdoor options visual so finish and fabric decisions feel natural.",
      image: "/assets/generated-furniture/product-chair-gallery.webp",
    },
  ],
  lighting: [
    {
      title: "Warm dining light",
      copy: "Use brass and linen shade details to soften architectural rooms.",
      image: "/assets/generated-furniture/home-module-003-dining-desktop.webp",
    },
    {
      title: "Fixture detail",
      copy: "Finish, shade and bulb choices stay close to the purchase decision.",
      image: "/assets/generated-furniture/product-pendant-gallery.webp",
    },
  ],
};

const relatedLinks = {
  bed: [
    { label: "ALSO AVAILABLE IN LEATHER", href: "/products?material=leather" },
    { label: "ALSO AVAILABLE FOR CUSTOM CONFIGURATION", href: "/products?collection=bedroom-set" },
    { label: "EXPLORE THE LUXE BED COLLECTION", href: "/products?room=bedroom" },
  ],
  sofa: [
    { label: "ALSO AVAILABLE IN LEATHER", href: "/products?material=leather" },
    { label: "ALSO AVAILABLE WITH CUSTOM FABRIC", href: "/products?category=single-sofa" },
    { label: "EXPLORE THE BEDROOM LOUNGE COLLECTION", href: "/products?category=single-sofa" },
  ],
  diningTable: [
    { label: "ALSO AVAILABLE WITH WOOD TOP", href: "/products?material=wood" },
    { label: "ALSO AVAILABLE FOR CUSTOM LENGTH", href: "/products?category=desk-table" },
    { label: "EXPLORE THE MARBLE DINING COLLECTION", href: "/products?category=round-table" },
  ],
  chair: [
    { label: "ALSO AVAILABLE AS A DINING CHAIR", href: "/products?category=chair" },
    { label: "ALSO AVAILABLE WITH CUSTOM CUSHIONS", href: "/products?category=seating" },
    { label: "EXPLORE THE OUTDOOR LOUNGE COLLECTION", href: "/outdoor" },
  ],
  lighting: [
    { label: "ALSO AVAILABLE AS A SCONCE", href: "/products?category=lighting" },
    { label: "ALSO AVAILABLE IN CUSTOM FINISHES", href: "/products?material=metal" },
    { label: "EXPLORE THE ARCHITECTURAL LIGHTING COLLECTION", href: "/products?category=lighting" },
  ],
};

const companyRelatedLinks = {
  nightstand: [
    { label: "PAIR WITH DRESSERS", href: "/products?category=dresser" },
    { label: "VIEW BEDROOM SETS", href: "/products?collection=bedroom-set" },
    { label: "EXPLORE WOOD FINISHES", href: "/products?material=wood" },
  ],
  dresser: [
    { label: "PAIR WITH NIGHTSTANDS", href: "/products?category=nightstand" },
    { label: "VIEW STORAGE CABINETS", href: "/products?category=storage" },
    { label: "EXPLORE CARVED WOOD DETAILS", href: "/products?craft=carved" },
  ],
  vanity: [
    { label: "PAIR WITH VANITY CHAIRS", href: "/products?category=vanity-chair" },
    { label: "VIEW BEDROOM SETS", href: "/products?collection=bedroom-set" },
    { label: "EXPLORE WOOD FINISHES", href: "/products?material=wood" },
  ],
  desk: [
    { label: "PAIR WITH DESK CHAIRS", href: "/products?category=desk-chair" },
    { label: "VIEW STUDY SETS", href: "/products?collection=study-set" },
    { label: "EXPLORE WOOD FINISHES", href: "/products?material=wood" },
  ],
  "bed-bench": [
    { label: "PAIR WITH NIGHTSTANDS", href: "/products?category=nightstand" },
    { label: "VIEW BEDROOM SETS", href: "/products?collection=bedroom-set" },
    { label: "EXPLORE BENCHES", href: "/products?category=bench" },
  ],
  "round-table": [
    { label: "PAIR WITH SIDE CHAIRS", href: "/products?category=chair" },
    { label: "VIEW DESKS & TABLES", href: "/products?category=desk-table" },
    { label: "EXPLORE WOOD FINISHES", href: "/products?material=wood" },
  ],
  "single-sofa": [
    { label: "PAIR WITH ROUND TABLES", href: "/products?category=round-table" },
    { label: "VIEW CHAIRS & BENCHES", href: "/products?category=seating" },
    { label: "EXPLORE BEDROOM LOUNGE PIECES", href: "/products?collection=bedroom-set" },
  ],
};

const productTypeTemplates = {
  furniture: {
    collection: "FURNITURE COLLECTION",
    heroNote: "Shown in the selected finish and current stocked configuration.",
    selector: { stockedCount: 6, specialOrderCount: 12, label: "SELECT FROM STOCKED AND SPECIAL ORDER FINISHES", swatches: swatches.woodStone },
    relatedLinks: [
      { label: "ALSO AVAILABLE IN LEATHER", href: "/products?material=leather" },
      { label: "EXPLORE COORDINATING FURNITURE", href: "/products" },
      { label: "VIEW AVAILABLE FINISHES", href: "/products?filter=finish" },
    ],
    highlights: [
      "Designed for practical storage, display or everyday room use",
      "Material and finish options vary by stocked configuration",
      "Balanced proportions suit residential interiors",
      "Product-specific dimensions and care details are shown below",
    ],
    optionGroups: [
      { key: "finish", label: "Finish", helper: "Choose a stocked or special order finish.", values: swatches.woodStone.slice(3, 6) },
      { key: "size", label: "Size", helper: "Select the footprint that fits the room.", values: ["Compact", "Standard", "Large"] },
      { key: "configuration", label: "Configuration", helper: "Available configurations vary by product.", values: ["Standard", "With storage", "Extended"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Purpose-built furniture with balanced residential proportions"], ["Construction", "Wood, metal, glass or textile components as shown"], ["Finish", "Selected stocked finish"], ["Use", "Indoor residential use unless otherwise noted"]] },
      { title: "DIMENSIONS", rows: [["Size", "See selected product configuration"], ["Clearance", "Allow space for access and daily use"], ["Weight", "Varies by material and size"]] },
      { title: "MATERIALS", rows: [["Primary material", "As described in the product name and specification"], ["Finish", "Protective furniture finish suitable for normal indoor use"], ["Hardware", "Product-appropriate concealed or finished hardware"]] },
      { title: "CARE", rows: [["Routine care", "Dust with a soft dry cloth"], ["Spills", "Wipe promptly and avoid abrasive cleaners"], ["Placement", "Avoid prolonged moisture and direct heat"]] },
      { title: "DELIVERY", rows: [["Delivery", "Parcel or scheduled furniture delivery by size"], ["Assembly", "Assembly requirements vary by configuration"], ["Lead time", "Confirmed at checkout for the selected item"]] },
    ],
  },
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
    collection: "BEDROOM LOUNGE COLLECTION",
    heroNote: "Shown in Sand Performance Linen with warm wood legs.",
    selector: fabricSelector,
    relatedLinks: relatedLinks.sofa,
    highlights: [
      "Single-seat profile for bedroom reading corners",
      "Down-blend cushions with foam core support",
      "Available in stocked fabrics, leather and custom upholstery",
      "Sized to pair with nightstands, desks and round tables",
    ],
    optionGroups: [
      { key: "configuration", label: "Configuration", helper: "Choose the seating profile before fabric and depth.", values: ["Single seat", "Wide single seat", "Lounge chair", "Reading corner set"] },
      { key: "fabric", label: "Fabric", helper: "Stocked and special order upholstery options.", values: swatches.linen.slice(0, 4) },
      { key: "depth", label: "Depth", helper: "Controls seat depth and room footprint.", values: ["Classic depth", "Luxe depth", "Petite depth"] },
      { key: "fill", label: "Cushion fill", helper: "Defines the sit and maintenance level.", values: ["Down blend", "Foam core", "Performance fiber"] },
    ],
    accordions: [
      { title: "DETAILS", rows: [["Design", "Tailored single-seat lounge profile with broad arms"], ["Frame", "Kiln-dried hardwood frame with reinforced corner blocks"], ["Comfort", "Deep seat with layered cushion support"], ["Configuration", "Single sofa template pairs with bedroom tables and chairs"]] },
      { title: "DIMENSIONS", rows: [["Overall width", "92 / 108 cm"], ["Overall depth", "96 / 105 cm"], ["Overall height", "78 cm"], ["Seat height", "46 cm"], ["Arm height", "62 cm"]] },
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
      { key: "top", label: "Top material", helper: "Stone and wood top options for Oakved dining filters.", values: swatches.woodStone.slice(0, 4) },
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

const productTypeTemplateAliases = {
  nightstand: "bed",
  dresser: "bed",
  vanity: "bed",
  desk: "bed",
  "bed-bench": "bed",
  "round-table": "dining-table",
  "single-sofa": "sofa",
};

const companyCollectionNames = {
  nightstand: "BEDSIDE STORAGE COLLECTION",
  dresser: "CARVED STORAGE COLLECTION",
  vanity: "VANITY & DRESSING COLLECTION",
  desk: "BEDROOM STUDY COLLECTION",
  "bed-bench": "END-OF-BED BENCH COLLECTION",
  "round-table": "ROUND WOOD TABLE COLLECTION",
  "single-sofa": "BEDROOM LOUNGE COLLECTION",
  chair: "BEDROOM CHAIR COLLECTION",
};

const companyHeroNotes = {
  nightstand: "Shown in smoked oak with two-drawer bedside storage.",
  dresser: "Shown in carved walnut with full-width bedroom storage.",
  vanity: "Shown in natural oak with drawer storage for dressing or writing.",
  desk: "Shown in walnut with compact proportions for bedroom study zones.",
  "bed-bench": "Shown with timber frame and upholstered seat at the foot of the bed.",
  "round-table": "Shown in natural oak with a compact round footprint.",
  "single-sofa": "Shown as a single-seat bedroom lounge piece with warm wood legs.",
  chair: "Shown as a wood-framed bedroom chair for desk, vanity or lounge pairing.",
};

const inferProductType = (productInput = {}) => {
  const product = productInput || {};
  const rawType = String(product.detailConfig?.productType || product.productType || product.type || product.category || "").toLowerCase();
  const knownCompanyTypes = new Set([
    "nightstand",
    "bed-bench",
    "dresser",
    "vanity",
    "desk",
    "round-table",
    "single-sofa",
    "chair",
    "lighting",
    "bed",
    "furniture",
  ]);
  if (knownCompanyTypes.has(rawType)) return rawType;
  const text = `${rawType} ${product.name || ""} ${product.subtitle || ""}`.toLowerCase();

  if (text.includes("nightstand") || text.includes("bedside")) return "nightstand";
  if (text.includes("bed-bench") || text.includes("bed bench") || text.includes("end-of-bed") || text.includes("bench")) return "bed-bench";
  if (text.includes("dresser") || text.includes("chest") || text.includes("cabinet")) return "dresser";
  if (text.includes("vanity")) return "vanity";
  if (text.includes("desk")) return "desk";
  if (text.includes("round-table") || text.includes("round table")) return "round-table";
  if (text.includes("single-sofa") || text.includes("single sofa")) return "single-sofa";
  if (/\bdining-table\b|\bdining\b.*\btable\b/.test(text)) return "round-table";
  if (text.includes("chair")) return "chair";
  if (text.includes("lighting") || text.includes("lamp") || text.includes("light")) return "lighting";
  if (text.includes("sofa") || text.includes("sectional")) return "single-sofa";
  if (text.includes("bed")) return "bed";
  return "furniture";
};

export const buildProductDetailModel = (productInput = {}) => {
  const product = productInput || {};
  const productType = inferProductType(product);
  const templateKey = productTypeTemplateAliases[productType] || productType;
  const template = productTypeTemplates[templateKey] || productTypeTemplates.furniture;
  const detailConfig = product.detailConfig || {};
  const description =
    stripHtml(product.description || product.subtitle) ||
    "A polished furniture detail template with fixed product information fields, ready for final parameters and product images.";

  return {
    id: product.id,
    skuId: product.skuId,
    source: product.source || "demo",
    productType,
    collection: detailConfig.collection || product.collection || companyCollectionNames[productType] || template.collection,
    name: product.name || "Luxury Furniture",
    description,
    heroNote: detailConfig.heroNote || product.heroNote || companyHeroNotes[productType] || template.heroNote,
    gallery: normalizeGallery(product),
    price: {
      prefix: "Starting at",
      member: Number(product.price) || 888,
      sale: Number(product.salePrice) || 932,
      regular: Number(product.marketPrice) || 999,
      memberLabel: "Member",
      saleLabel: "Sale",
      regularLabel: "Regular",
      savingsLabel: "ANNUAL 5% FIRST ORDER / WHOLE-ROOM 15%",
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
    relatedLinks: detailConfig.relatedLinks || product.relatedLinks || companyRelatedLinks[productType] || template.relatedLinks,
    membershipPrompt: detailConfig.membershipPrompt || product.membershipPrompt || membershipPrompt,
    roomInspiration: detailConfig.roomInspiration || product.roomInspiration || roomInspiration[productType] || roomInspiration[templateKey],
    purchaseAssurance: detailConfig.purchaseAssurance || product.purchaseAssurance || purchaseAssurance,
    companionProducts: detailConfig.companionProducts || product.companionProducts || companionProducts[productType] || companionProducts[templateKey],
    optionGroups: detailConfig.optionGroups || product.optionGroups || template.optionGroups,
    accordions: detailConfig.accordions || product.accordions || template.accordions,
  };
};
