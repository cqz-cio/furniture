export const primaryNavigation = [
  { label: "Bedroom Furniture", href: "/products?room=bedroom" },
  { label: "Storage Cabinets", href: "/products?category=storage" },
  { label: "Desks & Tables", href: "/products?category=desk-table" },
  { label: "Seating & Benches", href: "/products?category=seating" },
  { label: "Room Sets", href: "/products?collection=bedroom-room" },
  { label: "Woodcraft", href: "/products?material=wood" },
  { label: "New & Sale", href: "/sale" },
];

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

export const woodFurnitureMegaMenus = {
  "Bedroom Furniture": [
    { label: "\u5e8a\u5934\u67dc", href: "/products?category=nightstand" },
    { label: "\u5e8a\u5c3e\u957f\u51f3", href: "/products?category=bed-bench" },
    { label: "\u6597\u67dc", href: "/products?category=dresser" },
    { label: "\u5316\u5986\u684c", href: "/products?category=vanity" },
    { label: "\u5367\u5ba4\u5957\u88c5", href: "/products?collection=bedroom-set" },
    { label: "\u67e5\u770b\u5168\u90e8\u5367\u5ba4\u5bb6\u5177", href: "/products?room=bedroom", accent: true },
  ],
  "Storage Cabinets": [
    { label: "\u6597\u67dc", href: "/products?category=dresser" },
    { label: "\u62bd\u5c49\u67dc", href: "/products?category=drawer-chest" },
    { label: "\u8fb9\u67dc", href: "/products?category=side-cabinet" },
    { label: "\u9ad8\u67dc", href: "/products?category=tall-cabinet" },
    { label: "\u5367\u5ba4\u6536\u7eb3\u7ec4\u5408", href: "/products?collection=storage-set" },
    { label: "\u67e5\u770b\u5168\u90e8\u67dc\u7c7b\u6536\u7eb3", href: "/products?category=storage", accent: true },
  ],
  "Desks & Tables": [
    { label: "\u4e66\u684c", href: "/products?category=desk" },
    { label: "\u5316\u5986\u684c", href: "/products?category=vanity" },
    { label: "\u5706\u684c", href: "/products?category=round-table" },
    { label: "\u4e66\u684c\u6905", href: "/products?category=desk-chair" },
    { label: "\u4e66\u623f\u7ec4\u5408", href: "/products?collection=study-set" },
    { label: "\u67e5\u770b\u5168\u90e8\u684c\u6905\u4e66\u623f", href: "/products?category=desk-table", accent: true },
  ],
  "Seating & Benches": [
    { label: "\u5355\u4eba\u5ea7\u6c99\u53d1", href: "/products?category=single-sofa" },
    { label: "\u4f11\u95f2\u6905", href: "/products?category=accent-chair" },
    { label: "\u5e8a\u5c3e\u51f3", href: "/products?category=bed-bench" },
    { label: "\u5316\u5986\u6905", href: "/products?category=vanity-chair" },
    { label: "\u6362\u978b\u51f3", href: "/products?category=bench" },
    { label: "\u67e5\u770b\u5168\u90e8\u6905\u51f3\u6c99\u53d1", href: "/products?category=seating", accent: true },
  ],
  "Room Sets": [
    { label: "\u4e3b\u5367\u6574\u5c4b", href: "/products?collection=master-bedroom" },
    { label: "\u5ba2\u5367\u6574\u5c4b", href: "/products?collection=guest-bedroom" },
    { label: "\u9152\u5e97\u98ce\u5367\u5ba4", href: "/products?style=hotel-bedroom" },
    { label: "\u6cd5\u5f0f\u6728\u8d28\u5367\u5ba4", href: "/products?style=french-wood" },
    { label: "\u80e1\u6843\u6728\u5367\u5ba4", href: "/products?material=walnut" },
    { label: "\u67e5\u770b\u5168\u90e8\u6574\u5c4b\u65b9\u6848", href: "/products?collection=room", accent: true },
  ],
  Woodcraft: [
    { label: "\u80e1\u6843\u6728", href: "/products?material=walnut" },
    { label: "\u6a61\u6728", href: "/products?material=oak" },
    { label: "\u6a31\u6843\u6728", href: "/products?material=cherry" },
    { label: "\u767d\u8721\u6728", href: "/products?material=ash" },
    { label: "\u96d5\u523b\u5de5\u827a", href: "/products?craft=carved" },
    { label: "\u67e5\u770b\u5168\u90e8\u6728\u6750\u5de5\u827a", href: "/products?material=wood", accent: true },
  ],
  "New & Sale": [
    { label: "\u65b0\u54c1\u4e0a\u67b6", href: "/products?tag=new" },
    { label: "\u70ed\u9500\u5355\u54c1", href: "/products?tag=best-seller" },
    { label: "\u73b0\u8d27\u5bb6\u5177", href: "/products?tag=in-stock" },
    { label: "\u9650\u65f6\u7279\u60e0", href: "/sale" },
    { label: "\u67e5\u770b\u5168\u90e8\u7279\u60e0", href: "/sale", accent: true },
  ],
};

export const woodFurnitureDropdownLabels = primaryNavigation.map((item) => item.label);
export const livingMegaMenu = woodFurnitureMegaMenus["Bedroom Furniture"];
export const livingSeatingMegaMenu = [];
export const livingMegaSubmenus = {};
export const saleMegaMenu = woodFurnitureMegaMenus["New & Sale"];
export const mobileDrawerNavigation = primaryNavigation.map((item) => ({ ...item, accent: item.label === "New & Sale" }));

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
