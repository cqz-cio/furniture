import { describe, expect, it } from "vitest";
import {
  mapAddressResponse,
  mapCartResponseToItems,
  mapFavoritePageToItems,
  mapOrderDetail,
  mapSpuToProduct,
} from "../src/services/yudaoMappers.js";

describe("yudao mapper module", () => {
  it("maps SPU records from the dedicated mapper module", () => {
    const spu = {
      id: 12,
      name: "Cloud Sofa",
      introduction: "Deep modular sofa",
      picUrl: "https://cdn.example/cover.jpg",
      price: 259900,
      marketPrice: 329900,
      skus: [{ id: 99, price: 259900, stock: 8 }],
    };

    expect(mapSpuToProduct(spu)).toMatchObject({
      id: 12,
      spuId: 12,
      skuId: 99,
      name: "Cloud Sofa",
      price: 2599,
      marketPrice: 3299,
      source: "yudao",
    });
  });

  it("preserves backend category metadata for storefront navigation matching", () => {
    expect(
      mapSpuToProduct({
        id: 42,
        name: "Natural Oak Dining Table",
        categoryId: 3,
        categoryName: "Dining Tables",
        recommendNew: true,
        recommendBest: true,
        skus: [{ id: 4201, price: 459900, stock: 12 }],
      }),
    ).toMatchObject({
      categoryId: 3,
      categoryName: "Dining Tables",
      productType: "Dining Tables",
      material: "wood",
      color: "natural",
      isNew: true,
      isBestSeller: true,
    });
  });

  it("maps favorite page records into wishlist item rows", () => {
    expect(
      mapFavoritePageToItems({
        total: 1,
        list: [
          {
            id: 7,
            spuId: 12,
            skuId: 99,
            count: 3,
            spuName: "Cloud Sofa",
            picUrl: "https://cdn.example/sofa.jpg",
            price: 259900,
            marketPrice: 329900,
            color: "Wheat",
            fabric: "Performance Linen",
            dimensions: "92W",
            delivery: "Ships in 3-7 days",
          },
        ],
      }),
    ).toMatchObject({
      total: 1,
      list: [
        {
          favoriteId: 7,
          id: 12,
          spuId: 12,
          skuId: 99,
          name: "Cloud Sofa",
          cover: "https://cdn.example/sofa.jpg",
          price: 2599,
          marketPrice: 3299,
          color: "Wheat",
          fabric: "Performance Linen",
          dimensions: "92W",
          delivery: "Ships in 3-7 days",
          quantity: 3,
          source: "yudao",
        },
      ],
    });
  });

  it("preserves Gift Registry context from remote cart rows", () => {
    expect(
      mapCartResponseToItems({
        validList: [
          {
            id: 77,
            count: 2,
            selected: true,
            registryId: 1001,
            registryItemId: 2002,
            spu: { id: 12, name: "Cloud Sofa", picUrl: "https://cdn.example/sofa.jpg" },
            sku: { id: 99, price: 259900, stock: 8 },
          },
        ],
      }),
    ).toMatchObject([
      {
        cartId: 77,
        registryContext: {
          registryId: 1001,
          registryItemId: 2002,
        },
      },
    ]);
  });

  it("maps order detail from the dedicated mapper module", () => {
    const order = {
      id: 1,
      no: "O1",
      status: 10,
      payPrice: 120000,
      addressVerification: {
        source: "google-address-validation",
        status: "suggested",
        choice: "suggested",
        providerResponseId: "google-response-1",
        selectedAddress: {
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      },
      items: [{ spuName: "Sofa", price: 90000, originalPrice: 120000, productType: "merchandise" }],
    };

    expect(mapOrderDetail(order)).toMatchObject({
      id: 1,
      no: "O1",
      payPrice: 1200,
      addressVerification: {
        source: "google-address-validation",
        status: "suggested",
        choice: "suggested",
        providerResponseId: "google-response-1",
        selectedAddress: {
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      },
      items: [
        {
          name: "Sofa",
          price: 900,
          regularPrice: 1200,
          memberPrice: 900,
          category: "merchandise",
        },
      ],
    });
  });

  it("keeps saved address verification metadata for address book review", () => {
    expect(
      mapAddressResponse({
        id: 9,
        name: "Ada Lovelace",
        mobile: "555-0100",
        areaName: "Boston, MA",
        detailAddress: "12 Main St, Boston, MA 02116",
        addressVerification: {
          status: "unverified",
          choice: "original",
          reason: "google-unverified",
          confirmedAt: "2026-06-16T10:00:00.000Z",
        },
      }),
    ).toMatchObject({
      id: 9,
      addressVerification: {
        status: "unverified",
        choice: "original",
      },
      addressVerificationSummary: {
        statusLabelKey: "membership.account.addressBook.verification.statuses.unverified",
        warningKey: "membership.account.addressBook.verification.warning",
      },
    });
  });

  it("marks saved addresses without verification metadata for review", () => {
    expect(
      mapAddressResponse({
        id: 10,
        name: "Grace Hopper",
        mobile: "555-0101",
        areaName: "Arlington, VA",
        detailAddress: "1 Navy Way, Arlington, VA 22201",
      }),
    ).toMatchObject({
      id: 10,
      addressVerification: null,
      addressVerificationSummary: {
        status: "missing",
        statusLabelKey: "membership.account.addressBook.verification.statuses.missing",
        warningKey: "membership.account.addressBook.verification.missingWarning",
      },
    });
  });
});
