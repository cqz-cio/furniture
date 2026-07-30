import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  CATALOG_HREF,
  babyChildCollections,
  babyChildNavigation,
  babyChildPageSpecs,
  homeHighlights,
  homeFullPageModules,
  homeHeroAssets,
  footerLinkHref,
  globalMenuLinkHref,
  globalMenuPanels,
  mobileDrawerNavigation,
  primaryNavigation,
  storefrontDropdownKeys,
  storefrontDropdownMenus,
  rhFooter,
  outdoorCollectionSpecs,
  sofaPdpSpecs,
  sofasPlpSpecs,
  saleCategories,
  saleCategoryLinkHref,
  saleQuickLinks,
  saleHeroSpecs,
  saleMembershipSpec,
  teenHeroSpecs,
  teenPageSpecs,
} from "../src/data/rhLayout.js";

describe("RH layout extraction data", () => {
  it("defines the approved storefront navigation order", () => {
    expect(primaryNavigation.map((item) => item.key)).toEqual([
      "new",
      "collections",
      "bedroom",
      "living",
      "dining",
      "bespoke",
      "decor",
      "sale",
    ]);
    expect(primaryNavigation.map((item) => item.labelKey)).toEqual([
      "navigation.storefront.primary.new",
      "navigation.storefront.primary.collections",
      "navigation.storefront.primary.bedroom",
      "navigation.storefront.primary.living",
      "navigation.storefront.primary.dining",
      "navigation.storefront.primary.bespoke",
      "navigation.storefront.primary.decor",
      "navigation.storefront.primary.sale",
    ]);
    expect(primaryNavigation.every((item) => item.href.startsWith("/products") || item.href === "/sale")).toBe(true);
  });

  it("defines the global hamburger menu panels from the source menu", () => {
    expect(globalMenuPanels.map((panel) => panel.heading)).toEqual([
      "Products",
      "Rooms",
      "Services",
      "New & Sale",
    ]);
    expect(globalMenuPanels[0].links).toContain("Bedroom Furniture");
    expect(globalMenuPanels[0].image).toBeUndefined();
    expect(globalMenuPanels[0].spec).toMatchObject({
      rendered: "409 x 216",
      recommended2x: "818 x 432",
    });
    expect(globalMenuPanels[1].links).toContain("Bedroom Sets");
    expect(globalMenuPanels[2].groups.map((group) => group.heading)).toContain("Support");
    expect(globalMenuPanels[3].links).toContain("Sale");
  });

  it("prepends the same catalog destination to every configured dropdown", () => {
    expect(storefrontDropdownKeys).toEqual(["collections", "bedroom", "living", "dining"]);

    storefrontDropdownKeys.forEach((key) => {
      expect(storefrontDropdownMenus[key][0]).toEqual({
        key: "catalog",
        labelKey: "navigation.storefront.submenu.catalog",
        href: CATALOG_HREF,
      });
    });

    expect(CATALOG_HREF).toBe("/catalog");
    expect(storefrontDropdownMenus.dining.map((item) => item.key)).toEqual([
      "catalog",
      "rectangularTables",
      "roundOvalTables",
      "bistroTables",
      "fabricChairs",
      "woodWovenChairs",
      "barCounterStools",
      "upholsterySwatches",
      "sales",
    ]);
  });

  it("derives mobile navigation from the same dropdown model", () => {
    expect(mobileDrawerNavigation.map((item) => item.key)).toEqual(primaryNavigation.map((item) => item.key));
    expect(mobileDrawerNavigation.find((item) => item.key === "dining").items)
      .toBe(storefrontDropdownMenus.dining);
    expect(mobileDrawerNavigation.find((item) => item.key === "sale").accent).toBe(true);
  });

  it("defines Baby & Child navigation and collection modules", () => {
    expect(babyChildNavigation.map((item) => item.label)).toEqual([
      "Furniture",
      "Bedding",
      "Nursery",
      "Decor",
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

  it("defines the shared Oakved footer text and link groups", () => {
    expect(rhFooter.newsletter.title).toBe("INSPIRATION, DELIVERED.");
    expect(rhFooter.columns.map((column) => column.title)).toEqual([
      "RESOURCES",
      "CUSTOMER EXPERIENCE",
      "OUR COMPANY",
      "LEGAL",
    ]);
    expect(rhFooter.columns[0].links).toContain("REQUEST A SOURCEBOOK");
    expect(rhFooter.columns[0].links).toContain("OAKVED MEMBERS PROGRAM");
    expect(rhFooter.columns[0].links).toContain("MEMBERSHIP FAQS");
    expect(rhFooter.columns.at(-1).links).toContain("PRODUCT REGISTRATION");
    expect(rhFooter.region).toBe("United States ($) / English");
  });

  it("maps membership and registry footer links to local routes", () => {
    expect(footerLinkHref("RH MEMBERS PROGRAM")).toBe("/membership");
    expect(footerLinkHref("MEMBERSHIP FAQS")).toBe("/membership/faqs");
    expect(footerLinkHref("GIFT REGISTRY")).toBe("/gift-registry");
    expect(rhFooter.columns.flatMap((column) => column.links).every((link) => footerLinkHref(link) !== "#")).toBe(true);
  });

  it("maps completed global menu entries to local routes", () => {
    expect(globalMenuLinkHref("RH")).toBe("/");
    expect(globalMenuLinkHref("RH Outdoor")).toBe("/outdoor");
    expect(globalMenuLinkHref("RH Baby & Child")).toBe("/baby-child");
    expect(globalMenuLinkHref("Oakved Members Program")).toBe("/membership");
    expect(globalMenuLinkHref("New Arrivals")).toBe("/products?tag=new");
    expect(globalMenuLinkHref("Bedroom Furniture")).toBe("/products?room=bedroom");
    expect(globalMenuLinkHref("Storage Cabinets")).toBe("/products?category=storage");
    expect(globalMenuLinkHref("Desks & Tables")).toBe("/products?category=desk-table");
    expect(globalMenuLinkHref("Seating & Benches")).toBe("/products?category=seating");
    expect(globalMenuPanels.flatMap((panel) => panel.links).every((link) => globalMenuLinkHref(link) !== "#")).toBe(true);
  });

  it("maps sale category modules to local prototype routes", () => {
    const salePageSource = readFileSync(new URL("../src/pages/SalePage.vue", import.meta.url), "utf8");
    const saleTileSource = readFileSync(new URL("../src/components/SaleCategoryTile.vue", import.meta.url), "utf8");

    expect(saleCategoryLinkHref(saleCategories.find((item) => item.title === "Living"))).toBe("/products");
    expect(saleCategoryLinkHref(saleCategories.find((item) => item.title === "Sofas"))).toBe("/products");
    expect(saleCategoryLinkHref(saleCategories.find((item) => item.title === "Outdoor"))).toBe("/outdoor");
    expect(saleCategoryLinkHref(saleCategories.find((item) => item.title === "Dining"))).toBe("/missing");
    expect(salePageSource).toContain("saleCategoryLinkHref(category)");
    expect(saleTileSource).toContain('saleCategoryLinkHref(category)');
    expect(saleTileSource).not.toContain('target="_blank"');
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

  it("keeps storefront navigation on the shared model only", () => {
    const source = readFileSync(new URL("../src/data/rhLayout.js", import.meta.url), "utf8");

    expect(source).not.toContain("woodFurnitureMegaMenus");
    expect(source).not.toContain("woodFurnitureDropdownLabels");
    expect(source).not.toContain("livingMegaMenu");
    expect(source).not.toContain("livingSeatingMegaMenu");
    expect(source).not.toContain("livingMegaSubmenus");
    expect(source).not.toContain("saleMegaMenu");
  });

  it("exposes measured placeholder specs for the Sale hero assets", () => {
    expect(saleHeroSpecs.desktop.rendered).toBe("1350 x 580.5");
    expect(saleHeroSpecs.mobile.rendered).toBe("390 x 244.77");
    expect(saleHeroSpecs.desktop.src).toContain("05212026_RH_Sale_US%20CA%20Sale");
    expect(saleHeroSpecs.mobile.src).toContain("Sale%20Mobile");
  });

  it("keeps the Sale hero responsive with picture-based image rules", () => {
    const css = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");
    const salePageSource = readFileSync(new URL("../src/pages/SalePage.vue", import.meta.url), "utf8");

    expect(salePageSource).toContain('<picture class="sale-hero-picture">');
    expect(salePageSource).toContain('media="(max-width: 760px)"');
    expect(css).toContain(".sale-hero-picture {\n  display: block;");
    expect(css).toContain("height: clamp(760px, 54vw, 1120px);");
    expect(css).toContain(".sale-hero-image {\n  display: block;\n  width: 100%;\n  height: clamp(760px, 54vw, 1120px);");
    expect(css).toContain(".sale-hero-image {\n    height: min(600.08px, 153.87vw);");
  });

  it("defines measured homepage image slots from desktop and mobile exports", () => {
    expect(homeHighlights).toHaveLength(4);
    expect(homeHighlights.map((item) => item.desktopRendered)).toContain("1350 x 907.88");
    expect(homeHighlights[0].desktopRendered).toBe("1350 x 907.88");
    expect(homeHighlights.at(-1).mobileRendered).toBe("390 x 280.31");
  });

  it("extends the homepage structure with modules observed in the full mobile screenshot", () => {
    expect(homeFullPageModules).toHaveLength(18);
    expect(homeFullPageModules.slice(4).every((item) => item.sourceLevel)).toBe(true);
    expect(homeFullPageModules.map((item) => item.key)).toEqual([
      "home2",
      "home3",
      "home4",
      "sourcebook",
      "milan",
      "sourcebooksCover",
      "interiorsEntry",
      "outdoorScene",
      "interiorSeries",
      "sourcebooksGrid",
      "membersProgram",
      "founder",
      "architecture",
      "restaurant",
      "guesthouse",
      "aviation",
      "yachting",
      "serviceLinks",
    ]);
  });
  it("links completed homepage modules to local pages", () => {
    const source = readFileSync(new URL("../src/pages/HomePage.vue", import.meta.url), "utf8");

    expect(source).toContain("const editorialModules = [");
    expect(source).toContain('href: "/products?room=bedroom"');
    expect(source).toContain('href: "/products?category=storage"');
    expect(source).toContain('href: "/products?category=desk-table"');
    expect(source).toContain('href: "/products?category=seating"');
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
