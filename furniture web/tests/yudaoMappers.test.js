import { describe, expect, it } from "vitest";
import { mapOrderDetail, mapSpuToProduct } from "../src/services/yudaoMappers.js";

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
      skuId: 99,
      name: "Cloud Sofa",
      price: 2599,
      marketPrice: 3299,
      source: "yudao",
    });
  });

  it("maps order detail from the dedicated mapper module", () => {
    const order = {
      id: 1,
      no: "O1",
      status: 10,
      payPrice: 120000,
      items: [{ spuName: "Sofa", price: 90000, originalPrice: 120000, productType: "merchandise" }],
    };

    expect(mapOrderDetail(order)).toMatchObject({
      id: 1,
      no: "O1",
      payPrice: 1200,
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
});
