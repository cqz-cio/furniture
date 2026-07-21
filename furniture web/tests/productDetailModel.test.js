import { describe, expect, it } from "vitest";
import { demoProducts } from "../src/data/demoProducts.js";
import { buildProductDetailModel } from "../src/services/productDetailModel.js";

describe("product detail model", () => {
  it("adds Oakved fixed furniture sections around a yudao product", () => {
    const model = buildProductDetailModel({
      id: 88,
      skuId: 188,
      name: "Oak Shelter Bed",
      subtitle: "Hotel-inspired upholstered bed",
      description: "<p>Soft sheltering headboard for primary bedrooms.</p>",
      cover: "https://cdn.example/bed-main.jpg",
      gallery: ["https://cdn.example/bed-side.jpg"],
      price: 888,
      marketPrice: 999,
      stock: 20,
      source: "yudao",
      productType: "bed",
    });

    expect(model).toMatchObject({
      name: "Oak Shelter Bed",
      collection: "LUXE BED COLLECTION",
      description: "Soft sheltering headboard for primary bedrooms.",
      productType: "bed",
      price: { member: 888, regular: 999, prefix: "Starting at" },
      stock: { label: "Inventory", value: 20, status: "In stock" },
    });
    expect(model.gallery).toHaveLength(5);
    expect(model.gallery[0]).toMatchObject({ src: "https://cdn.example/bed-main.jpg", label: "Hero" });
    expect(model.optionGroups.map((group) => group.key)).toEqual(["size", "fabric", "finish", "configuration"]);
    expect(model.heroNote).toContain("Shown in");
    expect(model.price).toMatchObject({
      sale: 932,
      savingsLabel: "ANNUAL 5% FIRST ORDER / WHOLE-ROOM 15%",
      context: "Starting at price reflects the displayed size and stocked finish.",
    });
    expect(model.fabricSelector).toMatchObject({
      stockedCount: 26,
      specialOrderCount: 191,
      label: "SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS",
    });
    expect(model.availability).toMatchObject({
      title: "VIEW IN STOCK ITEMS",
      readyToShip: "Ready to ship in 3-7 days",
      specialOrder: "Special order options ship by confirmed production window",
    });
    expect(model.purchaseAssurance.map((item) => item.title)).toEqual(["Delivery", "Installation", "Returns"]);
    expect(model.companionProducts[0]).toMatchObject({
      title: "Oak Nightstand",
      href: "/product?id=1002",
    });
    expect(model.highlights).toContain("Hand upholstered in premium performance fabric");
    expect(model.accordions.map((item) => item.title)).toEqual([
      "DETAILS",
      "DIMENSIONS",
      "MATERIALS",
      "CARE",
      "DELIVERY",
    ]);
  });

  it("uses polished defaults when product data is sparse", () => {
    const model = buildProductDetailModel({ name: "Sample Furniture" });

    expect(model.name).toBe("Sample Furniture");
    expect(model.description).toContain("fixed product information fields");
    expect(model.gallery.every((item) => item.kind)).toBe(true);
    expect(model.relatedLinks).toContainEqual({ label: "ALSO AVAILABLE IN LEATHER", href: "/products?material=leather" });
    expect(model.relatedLinks.every((link) => link.href.startsWith("/"))).toBe(true);
  });

  it("uses safe defaults while the backend product detail is still loading", () => {
    const model = buildProductDetailModel(null);

    expect(model).toMatchObject({
      name: "Luxury Furniture",
      productType: "furniture",
      source: "demo",
    });
    expect(model.gallery).toHaveLength(5);
  });

  it("uses category-specific detail templates for other furniture types", () => {
    const cases = [
      {
        productType: "single-sofa",
        collection: "BEDROOM LOUNGE COLLECTION",
        optionKeys: ["configuration", "fabric", "depth", "fill"],
        selectorLabel: "SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS",
        dimensionLabel: "Overall width",
      },
      {
        productType: "round-table",
        collection: "ROUND WOOD TABLE COLLECTION",
        optionKeys: ["shape", "size", "top", "base"],
        selectorLabel: "SELECT FROM 8 STONE TOPS AND 6 WOOD FINISHES",
        dimensionLabel: "Seating capacity",
      },
      {
        productType: "chair",
        collection: "BEDROOM CHAIR COLLECTION",
        optionKeys: ["frame", "fabric", "cushion", "orientation"],
        selectorLabel: "SELECT FROM 12 STOCKED AND 48 SPECIAL ORDER OUTDOOR FABRICS",
        dimensionLabel: "Seat height",
      },
      {
        productType: "lighting",
        collection: "ARCHITECTURAL LIGHTING COLLECTION",
        optionKeys: ["size", "finish", "shade", "bulb"],
        selectorLabel: "SELECT FROM 6 METAL FINISHES AND 4 SHADE OPTIONS",
        dimensionLabel: "Canopy",
      },
    ];

    cases.forEach((item) => {
      const model = buildProductDetailModel({ name: item.productType, productType: item.productType });

      expect(model.productType).toBe(item.productType);
      expect(model.collection).toBe(item.collection);
      expect(model.optionGroups.map((group) => group.key)).toEqual(item.optionKeys);
      expect(model.fabricSelector.label).toBe(item.selectorLabel);
      expect(model.accordions.find((section) => section.title === "DIMENSIONS").rows).toContainEqual(
        expect.arrayContaining([item.dimensionLabel]),
      );
    });
  });

  it("prefers admin detailConfig over the default product type template", () => {
    const model = buildProductDetailModel({
      name: "Admin Configured Sofa",
      productType: "sofa",
      detailConfig: {
        productType: "sofa",
        collection: "ADMIN CLOUD COLLECTION",
        heroNote: "Shown with admin-managed performance linen.",
        highlights: ["Configured highlight from admin"],
        fabricSelector: {
          stockedCount: 3,
          specialOrderCount: 9,
          label: "ADMIN FABRIC OPTIONS",
          swatches: [{ label: "Admin Ivory", swatch: "#f4efe5" }],
        },
        optionGroups: [
          {
            key: "admin-size",
            label: "Admin Size",
            helper: "Managed by backend",
            values: ["Small", "Large"],
          },
        ],
        accordions: [
          {
            title: "DIMENSIONS",
            rows: [["Admin width", "260 cm"]],
          },
        ],
        relatedLinks: [{ label: "ADMIN RELATED LINK", href: "#" }],
      },
    });

    expect(model.collection).toBe("ADMIN CLOUD COLLECTION");
    expect(model.heroNote).toBe("Shown with admin-managed performance linen.");
    expect(model.highlights).toEqual(["Configured highlight from admin"]);
    expect(model.fabricSelector.label).toBe("ADMIN FABRIC OPTIONS");
    expect(model.optionGroups.map((group) => group.key)).toEqual(["admin-size"]);
    expect(model.accordions[0].rows).toEqual([["Admin width", "260 cm"]]);
    expect(model.relatedLinks).toEqual([{ label: "ADMIN RELATED LINK", href: "#" }]);
  });

  it("keeps demo catalog image-led for showroom browsing", () => {
    expect(demoProducts).toHaveLength(8);
    expect(demoProducts.map((product) => product.productType)).toEqual([
      "single-sofa",
      "nightstand",
      "round-table",
      "bed-bench",
      "dresser",
      "vanity",
      "desk",
      "chair",
    ]);
    demoProducts.forEach((product) => {
      expect(product.cover).toMatch(/^\/assets\/generated-furniture\/.+\.webp$/);
      expect(product.gallery).toEqual(
        expect.arrayContaining([expect.stringMatching(/^\/assets\/generated-furniture\/.+\.webp$/)]),
      );
    });
  });

  it("adds quiet PDP merchandising guidance for membership and room inspiration", () => {
    const model = buildProductDetailModel({
      name: "Walnut Single Sofa",
      productType: "single-sofa",
      price: 3299,
      marketPrice: 4299,
    });

    expect(model.membershipPrompt).toMatchObject({
      title: "Member pricing available",
      href: "/membership",
    });
    expect(model.roomInspiration).toHaveLength(2);
    expect(model.roomInspiration[0]).toMatchObject({
      title: "Style the room",
      image: "/assets/generated-furniture/home-module-002-bedroom-desktop.webp",
    });
  });

  it("keeps company product types intact while using the nearest merchandising template", () => {
    const cases = [
      ["Oak Nightstand", "nightstand", "BEDSIDE STORAGE COLLECTION"],
      ["Carved Walnut Dresser", "dresser", "CARVED STORAGE COLLECTION"],
      ["Oak Vanity Desk", "vanity", "VANITY & DRESSING COLLECTION"],
      ["Walnut Writing Desk", "desk", "BEDROOM STUDY COLLECTION"],
      ["End-of-Bed Bench", "bed-bench", "END-OF-BED BENCH COLLECTION"],
      ["Round Oak Table", "round-table", "ROUND WOOD TABLE COLLECTION"],
      ["Walnut Single Sofa", "single-sofa", "BEDROOM LOUNGE COLLECTION"],
      ["Bedroom Side Chair", "chair", "BEDROOM CHAIR COLLECTION"],
    ];

    cases.forEach(([name, productType, collection]) => {
      const model = buildProductDetailModel({ name, productType });

      expect(model.productType).toBe(productType);
      expect(model.collection).toBe(collection);
      expect(model.relatedLinks.every((link) => link.href.startsWith("/"))).toBe(true);
      expect(model.roomInspiration).toHaveLength(2);
    });
  });

  it("does not classify storage furniture as a dining table from the word tableware", () => {
    const model = buildProductDetailModel({
      name: "Walnut Four-Door Sideboard",
      subtitle: "Walnut sideboard for tableware storage and living-room display.",
    });

    expect(model.productType).toBe("furniture");
    expect(model.collection).toBe("FURNITURE COLLECTION");
    expect(model.highlights.join(" ")).not.toMatch(/dining table|stone top/i);
  });
});
