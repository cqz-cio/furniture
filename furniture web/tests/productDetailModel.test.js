import { describe, expect, it } from "vitest";
import { buildProductDetailModel } from "../src/services/productDetailModel.js";

describe("product detail model", () => {
  it("adds RH-style fixed furniture sections around a yudao product", () => {
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
      savingsLabel: "SAVE 30% ON SELECT ITEMS",
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
    expect(model.relatedLinks).toContainEqual({ label: "EXPLORE COORDINATING FURNITURE", href: "#" });
  });

  it("uses category-specific detail templates for other furniture types", () => {
    const cases = [
      {
        productType: "sofa",
        collection: "CLOUD MODULAR COLLECTION",
        optionKeys: ["configuration", "fabric", "depth", "fill"],
        selectorLabel: "SELECT FROM 26 STOCKED AND 191 SPECIAL ORDER FABRICS",
        dimensionLabel: "Overall width",
      },
      {
        productType: "dining-table",
        collection: "MARBLE DINING COLLECTION",
        optionKeys: ["shape", "size", "top", "base"],
        selectorLabel: "SELECT FROM 8 STONE TOPS AND 6 WOOD FINISHES",
        dimensionLabel: "Seating capacity",
      },
      {
        productType: "chair",
        collection: "OUTDOOR LOUNGE COLLECTION",
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
