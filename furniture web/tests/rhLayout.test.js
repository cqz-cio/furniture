import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  babyChildCollections,
  babyChildNavigation,
  babyChildPageSpecs,
  homeHighlights,
  homeFullPageModules,
  homeHeroAssets,
  globalMenuPanels,
  livingMegaMenu,
  mobileDrawerNavigation,
  primaryNavigation,
  rhFooter,
  outdoorCollectionSpecs,
  sofaPdpSpecs,
  sofasPlpSpecs,
  saleCategories,
  saleQuickLinks,
  saleMegaMenu,
  saleHeroSpecs,
  saleMembershipSpec,
  teenHeroSpecs,
  teenPageSpecs,
} from "../src/data/rhLayout.js";

describe("RH layout extraction data", () => {
  it("keeps the primary RH navigation order used by the header", () => {
    expect(primaryNavigation.map((item) => item.label)).toEqual([
      "Living",
      "Dining",
      "Bed",
      "Bath",
      "Outdoor",
      "Lighting",
      "Textiles",
      "Rugs",
      "Décor",
      "Baby & Child",
      "Teen",
      "Sale",
      "Interior Design",
    ]);
  });

  it("defines the global hamburger menu panels from the source menu", () => {
    expect(globalMenuPanels.map((panel) => panel.heading)).toEqual([
      "Products",
      "Places",
      "Services",
      "Spaces",
    ]);
    expect(globalMenuPanels[0].links).toContain("RH Interiors");
    expect(globalMenuPanels[0].image).toBeUndefined();
    expect(globalMenuPanels[0].spec).toMatchObject({
      rendered: "409 x 216",
      recommended2x: "818 x 432",
    });
    expect(globalMenuPanels[1].links).toContain("Galleries");
    expect(globalMenuPanels[2].groups.map((group) => group.heading)).toContain("Trade");
    expect(globalMenuPanels[3].links).toContain("RH Three Expedition Yacht");
  });

  it("defines the Living dropdown and mobile drawer navigation", () => {
    expect(livingMegaMenu.map((item) => item.label)).toEqual([
      "Fabric Seating",
      "Leather Seating",
      "The Cloud® Collection",
      "Shelving & Cabinets",
      "Sideboards",
      "Media",
      "Tables",
      "Consoles",
      "Office",
      "Shop By Room",
      "Sale",
    ]);
    expect(mobileDrawerNavigation.at(-1).label).toBe("Interior Design");
    expect(mobileDrawerNavigation.find((item) => item.label === "Sale").accent).toBe(true);
  });

  it("defines Baby & Child navigation and collection modules", () => {
    expect(babyChildNavigation.map((item) => item.label)).toEqual([
      "Furniture",
      "Bedding",
      "Nursery",
      "Décor",
      "Lighting",
      "Rugs",
      "Windows",
      "Storage",
      "Playroom",
      "Gifts",
      "Teen",
      "Sale",
      "Registry",
    ]);
    expect(babyChildCollections.map((item) => item.title)).toEqual(["Nursery", "Bedroom", "Bedding", "Playroom"]);
  });

  it("defines the shared RH footer text and link groups", () => {
    expect(rhFooter.newsletter.title).toBe("INSPIRATION, DELIVERED.");
    expect(rhFooter.columns.map((column) => column.title)).toEqual([
      "RESOURCES",
      "CUSTOMER EXPERIENCE",
      "OUR COMPANY",
      "LEGAL",
    ]);
    expect(rhFooter.columns[0].links).toContain("REQUEST A SOURCEBOOK");
    expect(rhFooter.columns.at(-1).links).toContain("PRODUCT REGISTRATION");
    expect(rhFooter.region).toBe("United States ($) / English");
  });

  it("keeps extracted Baby & Child homepage media slots", () => {
    expect(babyChildPageSpecs.desktop.viewport).toBe("1365 x 953");
    expect(babyChildPageSpecs.desktop.documentHeight).toBe("14341");
    expect(babyChildPageSpecs.mobile.viewport).toBe("390 x 844");
    expect(babyChildPageSpecs.mobile.documentHeight).toBe("6945");
    expect(babyChildPageSpecs.desktop.modules.map((module) => module.key)).toEqual([
      "hero",
      "video",
      "sourcebookBg",
      "sourcebookCover",
      "sourcebookLogo",
      "cordelia1",
      "cordeliaLogo1",
      "cordelia2",
      "kalleLogo",
      "cordelia3",
      "cordeliaLogo2",
      "genevieve1",
      "genevieveLogo1",
      "genevieve2",
      "genevieveLogo2",
      "genevieveReeded",
      "genevieveReededLogo",
      "miyu1",
      "miyuLogo1",
      "miyu2",
      "miyuLogo2",
      "miyu3",
      "miyuLogo3",
      "designGalleries",
      "rhid",
      "rhidLogo",
      "greenguardImage",
      "greenguardBg",
      "registryImage",
      "registryBg",
      "pinterest",
      "emailSignup",
    ]);
    expect(babyChildPageSpecs.desktop.modules[0].rendered).toBe("1350 x 900");
    expect(babyChildPageSpecs.desktop.modules[1].type).toBe("video");
    expect(babyChildPageSpecs.desktop.modules.find((module) => module.key === "cordeliaLogo1").rendered).toBe("406.08 x 106.27");
    expect(babyChildPageSpecs.desktop.modules.find((module) => module.key === "miyu3").mobileRendered).toBe("390 x 219.45");
    expect(babyChildPageSpecs.desktop.modules.at(-1).natural).toContain("08212024_RH_BC_EmailSignup_Module");
    expect(babyChildPageSpecs.groups).toHaveLength(17);
  });

  it("includes the extracted Sale category tiles from the layout JSON", () => {
    expect(saleCategories).toHaveLength(10);
    expect(saleCategories[0]).toMatchObject({
      title: "Living",
      href: expect.stringContaining("cat29830020"),
      imageId: "SALE-IMG-003",
      desktopSrc: expect.stringContaining("03062026_RH_Core_Sale1"),
    });
    expect(saleCategories.at(-1)).toMatchObject({
      title: "Bath Towels",
      imageId: "SALE-IMG-012",
    });
  });

  it("keeps the Sale quick links aligned with the source desktop header", () => {
    expect(saleQuickLinks.map((item) => item.title)).toEqual([
      "Living",
      "Dining",
      "Bedroom",
      "Bath",
      "Outdoor",
      "Rugs",
      "Lighting",
      "Bedding",
      "Bath Towels",
    ]);
  });

  it("defines the Sale hover dropdown from the source menu screenshot", () => {
    expect(saleMegaMenu.map((item) => item.label)).toEqual([
      "Living",
      "Dining",
      "Bed",
      "Bath",
      "Outdoor",
      "Lighting",
      "Textiles",
      "Rugs",
      "Décor",
      "Baby & Child",
      "Teen",
    ]);
  });

  it("exposes measured placeholder specs for the Sale hero assets", () => {
    expect(saleHeroSpecs.desktop.rendered).toBe("1350 x 580.5");
    expect(saleHeroSpecs.mobile.rendered).toBe("390 x 244.77");
    expect(saleHeroSpecs.desktop.src).toContain("05212026_RH_Sale_US%20CA%20Sale");
    expect(saleHeroSpecs.mobile.src).toContain("Sale%20Mobile");
  });

  it("keeps the Sale mobile hero hidden on desktop with a specific CSS rule", () => {
    const css = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");
    expect(css).toContain(".sale-hero .sale-hero-image-mobile {\n  display: none;");
    expect(css).toContain(".sale-hero .sale-hero-image-mobile {\n    display: flex;");
  });

  it("defines measured homepage image slots from desktop and mobile exports", () => {
    expect(homeHighlights.map((item) => item.title)).toEqual([
      "首页第二屏图位",
      "首页第三屏图位",
      "首页第四屏图位",
      "首页 Sourcebook 图位",
    ]);
    expect(homeHighlights[0].desktopRendered).toBe("1350 x 907.88");
    expect(homeHighlights.at(-1).mobileRendered).toBe("390 x 280.31");
  });

  it("extends the homepage structure with modules observed in the full mobile screenshot", () => {
    expect(homeFullPageModules.map((item) => item.title)).toEqual([
      "首页第二屏图位",
      "首页第三屏图位",
      "首页第四屏图位",
      "首页 Sourcebook 图位",
      "RH Milan 室内图位",
      "Sourcebooks 双封面模块",
      "RH Interiors 入口模块",
      "Outdoor 休闲场景模块",
      "室内系列入口模块",
      "Sourcebooks 多封面矩阵",
      "RH Members Program 文案模块",
      "Chairman / 创始人文案模块",
      "建筑外立面模块",
      "餐厅 / 酒吧模块",
      "泳池 / Guesthouse 模块",
      "私人飞机模块",
      "游艇模块",
      "Footer 前服务链接区域",
    ]);
    expect(homeFullPageModules.slice(4).every((item) => item.sourceLevel === "完整手机长截图推断，待首页 JSON 精确复核")).toBe(true);
  });

  it("uses measured homepage hero assets for desktop and mobile", () => {
    expect(homeHeroAssets.desktop.rendered).toBe("1350 x 907.88");
    expect(homeHeroAssets.mobile.rendered).toBe("390 x 600.08");
  });

  it("keeps measured Sale membership and Teen hero slots", () => {
    expect(saleMembershipSpec.desktop.rendered).toBe("1350 x 710.63");
    expect(saleMembershipSpec.mobile.rendered).toBe("390 x 523.42");
    expect(teenHeroSpecs.desktop.rendered).toBe("1350 x 900");
    expect(teenHeroSpecs.desktop.natural).toBe("3600 x 2400");
    expect(teenPageSpecs.desktop.collection.count).toBe(10);
    expect(teenPageSpecs.desktop.video.rendered).toBe("1350 x 905.58");
  });

  it("keeps measured Outdoor, PLP, and PDP extraction groups", () => {
    expect(outdoorCollectionSpecs.desktop.banner.count).toBe(24);
    expect(outdoorCollectionSpecs.mobile.banner.rendered).toBe("358 x 175.55");
    expect(sofasPlpSpecs.desktop.product.count).toBe(16);
    expect(sofasPlpSpecs.mobile.badge.count).toBe(72);
    expect(sofaPdpSpecs.desktop.main.rendered).toBe("627 x 611.14");
    expect(sofaPdpSpecs.mobile.main.rendered).toBe("358 x 348.94");
  });
});
