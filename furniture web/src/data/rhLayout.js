export const primaryNavigation = [
  { key: "new", labelKey: "navigation.storefront.primary.new", href: "/products?tag=new" },
  { key: "collections", labelKey: "navigation.storefront.primary.collections", href: "/products?collection=all" },
  { key: "bedroom", labelKey: "navigation.storefront.primary.bedroom", href: "/products?room=bedroom" },
  { key: "living", labelKey: "navigation.storefront.primary.living", href: "/products?room=living" },
  { key: "dining", labelKey: "navigation.storefront.primary.dining", href: "/products?room=dining" },
  { key: "bespoke", labelKey: "navigation.storefront.primary.bespoke", href: "/products?collection=bespoke" },
  { key: "decor", labelKey: "navigation.storefront.primary.decor", href: "/products?category=decor" },
  { key: "sale", labelKey: "navigation.storefront.primary.sale", href: "/sale", accent: true },
];

export const CATALOG_HREF = "/catalog";

const storefrontItem = (key, href) => ({
  key,
  labelKey: `navigation.storefront.submenu.${key}`,
  href,
});

const catalogItem = () => storefrontItem("catalog", CATALOG_HREF);
const withCatalog = (items) => [catalogItem(), ...items];

export const storefrontDropdownMenus = {
  collections: withCatalog([
    storefrontItem("solstice", "/products?collection=solstice"),
    storefrontItem("halcyon", "/products?collection=halcyon"),
    storefrontItem("kindred", "/products?collection=kindred"),
  ]),
  bedroom: withCatalog([
    storefrontItem("beds", "/products?category=bed"),
    storefrontItem("headboard", "/products?category=headboard"),
    storefrontItem("nightstands", "/products?category=nightstand"),
    storefrontItem("benches", "/products?category=bench"),
    storefrontItem("dressers", "/products?category=dresser"),
    storefrontItem("chairs", "/products?category=chair"),
    storefrontItem("sideTables", "/products?category=side-table"),
    storefrontItem("fabricCare", "/products?group=fabric-care"),
    storefrontItem("materialsCraftsmanship", "/products?group=materials-craftsmanship"),
    storefrontItem("sales", "/sale"),
  ]),
  living: withCatalog([
    storefrontItem("sofas", "/products?category=sofa"),
    storefrontItem("tables", "/products?category=table"),
    storefrontItem("consoles", "/products?category=console"),
    storefrontItem("sideboards", "/products?category=sideboard"),
    storefrontItem("cabinets", "/products?category=cabinet"),
    storefrontItem("benches", "/products?category=bench"),
    storefrontItem("chairs", "/products?category=chair"),
    storefrontItem("stools", "/products?category=stool"),
    storefrontItem("fabricCare", "/products?group=fabric-care"),
    storefrontItem("materialsCraftsmanship", "/products?group=materials-craftsmanship"),
    storefrontItem("sales", "/sale"),
  ]),
  dining: withCatalog([
    storefrontItem("rectangularTables", "/products?category=rectangular-table"),
    storefrontItem("roundOvalTables", "/products?category=round-oval-table"),
    storefrontItem("bistroTables", "/products?category=bistro-table"),
    storefrontItem("fabricChairs", "/products?category=fabric-chair"),
    storefrontItem("woodWovenChairs", "/products?category=wood-woven-chair"),
    storefrontItem("barCounterStools", "/products?category=bar-counter-stool"),
    storefrontItem("upholsterySwatches", "/products?group=upholstery-swatches"),
    storefrontItem("sales", "/sale"),
  ]),
};

export const storefrontDropdownKeys = Object.keys(storefrontDropdownMenus);

export const babyChildNavigation = [
  { label: "Furniture", href: "/baby-child/furniture" },
  { label: "Bedding", href: "/baby-child/bedding" },
  { label: "Nursery", href: "/baby-child/nursery" },
  { label: "Decor", href: "/baby-child/decor" },
  { label: "Lighting", href: "/baby-child/lighting" },
  { label: "Rugs", href: "/baby-child/rugs" },
  { label: "Windows", href: "/baby-child/windows" },
  { label: "Storage", href: "/baby-child/storage" },
  { label: "Playroom", href: "/baby-child/playroom" },
  { label: "Gifts", href: "/baby-child/gifts" },
  { label: "Teen", href: "/teen" },
  { label: "Sale", href: "/sale" },
  { label: "Registry", href: "/gift-registry" },
];

export const babyChildCollections = [
  { title: "Nursery", summary: "Cribs, beds, dressers and changing tables.", href: "/baby-child/nursery" },
  { title: "Bedroom", summary: "Foundational pieces for first rooms.", href: "/baby-child/bedroom" },
  { title: "Bedding", summary: "Sheets, quilts and layered textiles.", href: "/baby-child/bedding" },
  { title: "Playroom", summary: "Tables, seating and storage for play.", href: "/baby-child/playroom" },
];

export const babyChildInspiration = [];

export const rhFooter = {
  newsletter: {
    title: "INSPIRATION, DELIVERED.",
    subtitle: "Receive new collection notes, room edits and Oakved updates.",
    inputLabel: "Email address",
    action: "SIGN UP",
  },
  columns: [
    { title: "RESOURCES", links: ["REQUEST A SOURCEBOOK", "OAKVED MEMBERS PROGRAM", "MEMBERSHIP FAQS"] },
    { title: "CUSTOMER EXPERIENCE", links: ["CONTACT US", "DELIVERY", "RETURNS"] },
    { title: "OUR COMPANY", links: ["ABOUT OAKVED", "CAREERS", "TRADE"] },
    { title: "LEGAL", links: ["PRIVACY NOTICE", "TERMS OF USE", "PRODUCT REGISTRATION"] },
  ],
  region: "United States ($) / English",
  copyright: "© 2026 Oakved",
};

const footerHrefMap = {
  "OAKVED MEMBERS PROGRAM": "/membership",
  "RH MEMBERS PROGRAM": "/membership",
  "MEMBERSHIP FAQS": "/membership/faqs",
  "GIFT REGISTRY": "/gift-registry",
  "REQUEST A SOURCEBOOK": "/products",
  "CONTACT US": "/account",
  DELIVERY: "/checkout",
  RETURNS: "/membership/terms",
  "ABOUT OAKVED": "/",
  CAREERS: "/account",
  "PRIVACY NOTICE": "/membership/terms",
  "TERMS OF USE": "/membership/terms",
  "PRODUCT REGISTRATION": "/account",
  TRADE: "/trade/sign-in",
};

export const footerLinkHref = (label) => footerHrefMap[label] || "#";

const globalMenuHrefMap = {
  RH: "/",
  Oakved: "/",
  "RH Outdoor": "/outdoor",
  "RH Baby & Child": "/baby-child",
  "RH Teen": "/teen",
  "RH Members Program": "/membership",
  "Oakved Members Program": "/membership",
  "All Furniture": "/products",
  "Bedroom Furniture": "/products?room=bedroom",
  "Storage Cabinets": "/products?category=storage",
  "Desks & Tables": "/products?category=desk-table",
  "Seating & Benches": "/products?category=seating",
  "Room Sets": "/products?room=bedroom",
  "Bedroom Sets": "/products?room=bedroom",
  "Study Rooms": "/products?room=study",
  "Living Corners": "/products?room=living",
  "Interior Design": "/interior-design",
  "Trade Program": "/trade/sign-in",
  "Membership FAQ": "/membership/faqs",
  "Gift Registry": "/gift-registry",
  "New Arrivals": "/products?tag=new",
  "Best Sellers": "/products?tag=best-seller",
  "In-stock Furniture": "/products?tag=in-stock",
  Sale: "/sale",
};

export const globalMenuLinkHref = (label) => globalMenuHrefMap[label] || "#";

const babyChildModuleKeys = [
  "hero", "video", "sourcebookBg", "sourcebookCover", "sourcebookLogo", "cordelia1", "cordeliaLogo1",
  "cordelia2", "kalleLogo", "cordelia3", "cordeliaLogo2", "genevieve1", "genevieveLogo1", "genevieve2",
  "genevieveLogo2", "genevieveReeded", "genevieveReededLogo", "miyu1", "miyuLogo1", "miyu2",
  "miyuLogo2", "miyu3", "miyuLogo3", "designGalleries", "rhid", "rhidLogo", "greenguardImage",
  "greenguardBg", "registryImage", "registryBg", "pinterest", "emailSignup",
];

export const babyChildPageSpecs = {
  desktop: {
    viewport: "1365 x 953",
    documentHeight: "14341",
    modules: babyChildModuleKeys.map((key) => ({
      key,
      type: key === "video" ? "video" : "image",
      rendered: key === "hero" ? "1350 x 900" : key === "cordeliaLogo1" ? "406.08 x 106.27" : "1350 x 759.66",
      natural: key === "emailSignup" ? "08212024_RH_BC_EmailSignup_Module" : `${key} natural`,
      mobileRendered: key === "miyu3" ? "390 x 219.45" : "390 x 219.45",
    })),
  },
  mobile: { viewport: "390 x 844", documentHeight: "6945" },
  groups: Array.from({ length: 17 }, (_, index) => ({ key: `group-${index + 1}` })),
};

export const mobileDrawerNavigation = primaryNavigation.map((item) => ({
  ...item,
  items: storefrontDropdownMenus[item.key] || [],
}));

export const globalMenuPanels = [
  {
    heading: "Products",
    links: ["All Furniture", "Bedroom Furniture", "Storage Cabinets", "Desks & Tables", "Seating & Benches"],
    spec: { rendered: "409 x 216", recommended2x: "818 x 432" },
  },
  { heading: "Rooms", links: ["Room Sets", "Bedroom Sets", "Study Rooms", "Living Corners"], spec: { rendered: "409 x 216" } },
  { heading: "Services", links: ["Interior Design", "Trade Program", "Oakved Members Program"], groups: [{ heading: "Support", links: ["Membership FAQ", "Gift Registry"] }] },
  { heading: "New & Sale", links: ["New Arrivals", "Best Sellers", "In-stock Furniture", "Sale"] },
];

export const homeHeroAssets = { desktop: { rendered: "1350 x 907.88" }, mobile: { rendered: "390 x 600.08" } };
export const homeHighlights = [
  { key: "home2", title: "home module 2", desktopRendered: "1350 x 907.88", mobileRendered: "390 x 600.08" },
  { key: "home3", title: "home module 3", desktopRendered: "1350 x 907.88", mobileRendered: "390 x 600.08" },
  { key: "home4", title: "home module 4", desktopRendered: "1350 x 907.88", mobileRendered: "390 x 600.08" },
  { key: "sourcebook", title: "sourcebook module", desktopRendered: "1350 x 280.31", mobileRendered: "390 x 280.31" },
];
export const homeFullPageModules = [
  "home2", "home3", "home4", "sourcebook", "milan", "sourcebooksCover", "interiorsEntry", "outdoorScene",
  "interiorSeries", "sourcebooksGrid", "membersProgram", "founder", "architecture", "restaurant", "guesthouse",
  "aviation", "yachting", "serviceLinks",
].map((key, index) => ({ key, title: key === "membersProgram" ? "Oakved Members Program" : key, sourceLevel: index >= 4 ? "full mobile screenshot inferred" : "primary module" }));

export const saleHeroSpecs = { desktop: { rendered: "1350 x 580.5", src: "05212026_RH_Sale_US%20CA%20Sale" }, mobile: { rendered: "390 x 244.77", src: "Sale%20Mobile" } };
export const saleMembershipSpec = { desktop: { rendered: "1350 x 710.63" }, mobile: { rendered: "390 x 523.42" } };
export const saleCategories = [
  { title: "Living", href: "cat29830020", imageId: "SALE-IMG-003", desktopSrc: "03062026_RH_Core_Sale1" },
  { title: "Sofas", href: "cat160024", imageId: "SALE-IMG-004", desktopSrc: "sale-sofas" },
  { title: "Dining", href: "cat29830024", imageId: "SALE-IMG-005", desktopSrc: "sale-dining" },
  { title: "Bedroom", href: "cat10250052", imageId: "SALE-IMG-006", desktopSrc: "sale-bedroom" },
  { title: "Outdoor", href: "outdoor", imageId: "SALE-IMG-007", desktopSrc: "sale-outdoor" },
  { title: "Rugs", href: "rugs", imageId: "SALE-IMG-008", desktopSrc: "sale-rugs" },
  { title: "Lighting", href: "lighting", imageId: "SALE-IMG-009", desktopSrc: "sale-lighting" },
  { title: "Bedding", href: "bedding", imageId: "SALE-IMG-010", desktopSrc: "sale-bedding" },
  { title: "Decor", href: "decor", imageId: "SALE-IMG-011", desktopSrc: "sale-decor" },
  { title: "Bath Towels", href: "bath", imageId: "SALE-IMG-012", desktopSrc: "sale-bath" },
];
export const saleQuickLinks = [saleCategories[0], saleCategories[2], saleCategories[3], { ...saleCategories[9], title: "Bath" }, saleCategories[4], saleCategories[5], saleCategories[6], saleCategories[7], saleCategories[9]];
const saleCategoryRouteMap = { Living: "/products", Sofas: "/products", Outdoor: "/outdoor" };
export const saleCategoryLinkHref = (category) => saleCategoryRouteMap[category.title] || "/missing";

export const categoryImageSpec = { rendered: "300 x 360" };
export const teenHeroSpecs = { desktop: { rendered: "1350 x 900", natural: "3600 x 2400" } };
export const teenPageSpecs = { desktop: { collection: { count: 10 }, video: { rendered: "1350 x 905.58" } } };
export const outdoorCollectionSpecs = { desktop: { banner: { count: 24 } }, mobile: { banner: { rendered: "358 x 175.55" } } };
export const sofasPlpSpecs = { desktop: { product: { count: 16 } }, mobile: { badge: { count: 72 } } };
export const sofaPdpSpecs = { desktop: { main: { rendered: "627 x 611.14" } }, mobile: { main: { rendered: "358 x 348.94" } } };
export const extractionBacklog = [{ title: "Legacy extraction route", href: "/missing", status: "archived" }];
