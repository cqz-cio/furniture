import { describe, expect, it, vi } from "vitest";
import {
  createMockAssistantResponse,
  normalizeAssistantResponse,
  sendFurnitureAssistantMessage,
} from "../src/services/furnitureAssistant.js";

describe("furniture assistant client", () => {
  it("normalizes structured assistant responses for the chat panel", () => {
    const response = normalizeAssistantResponse({
      answer: "I found three stocked sofas that fit your budget.",
      products: [
        {
          id: 1001,
          skuId: 2001,
          name: "Fabric Track Arm Sofa",
          price: "6999",
          marketPrice: "8999",
          stock: "12",
          cover: "https://cdn.example/sofa.jpg",
          reason: "Compact scale, light fabric tone and within budget.",
        },
      ],
      sources: [{ type: "knowledge", name: "Membership Rules" }],
    });

    expect(response).toEqual({
      answer: "I found three stocked sofas that fit your budget.",
      products: [
        {
          id: 1001,
          skuId: 2001,
          name: "Fabric Track Arm Sofa",
          subtitle: "",
          price: 6999,
          marketPrice: 8999,
          stock: 12,
          cover: "https://cdn.example/sofa.jpg",
          reason: "Compact scale, light fabric tone and within budget.",
          detailUrl: "/product?id=1001",
          source: "assistant",
          raw: expect.any(Object),
        },
      ],
      sources: [{ type: "knowledge", name: "Membership Rules" }],
    });
  });

  it("builds a mock structured response until the backend endpoint is ready", () => {
    const response = createMockAssistantResponse("small cream sofa under 8000", [
      { id: 1, skuId: 11, name: "Cloud Sofa", price: 7999, stock: 4, cover: "/cloud.jpg" },
      { id: 2, skuId: 22, name: "Track Arm Sofa", price: 6999, stock: 8, cover: "/track.jpg" },
      { id: 3, skuId: 33, name: "Slope Sofa", price: 5999, stock: 2, cover: "/slope.jpg" },
      { id: 4, skuId: 44, name: "Extra Sofa", price: 4999, stock: 9, cover: "/extra.jpg" },
    ]);

    expect(response.answer).toContain("small cream sofa under 8000");
    expect(response.products).toHaveLength(3);
    expect(response.products[0]).toMatchObject({
      id: 1,
      skuId: 11,
      detailUrl: "/product?id=1",
      reason: expect.any(String),
    });
  });

  it("builds a mock knowledge response for policy style prompts", () => {
    const response = createMockAssistantResponse("Can membership price stack with coupons?");

    expect(response.answer).toContain("Membership");
    expect(response.products).toHaveLength(0);
    expect(response.sources).toEqual([{ type: "knowledge", name: "Membership Rules" }]);
  });

  it("can call the configured backend endpoint and normalize its payload", async () => {
    const request = vi.fn().mockResolvedValue({
      answer: "Here are live Yudao products.",
      products: [{ id: 8, skuId: 80, name: "Live Sofa", price: 8000, stock: 5 }],
      sources: [{ type: "model", name: "DeepSeek deepseek-v4-flash" }],
    });

    const response = await sendFurnitureAssistantMessage("live sofa", {
      request,
      useMock: false,
    });

    expect(request).toHaveBeenCalledWith("/ai/furniture-assistant/chat", {
      method: "POST",
      body: JSON.stringify({ message: "live sofa" }),
    });
    expect(response.answer).toBe("Here are live Yudao products.");
    expect(response.products[0]).toMatchObject({ id: 8, skuId: 80, detailUrl: "/product?id=8" });
    expect(response.sources).toEqual([{ type: "model", name: "DeepSeek deepseek-v4-flash" }]);
  });

  it("normalizes Yudao CommonResult data for model-backed assistant responses", () => {
    const response = normalizeAssistantResponse({
      code: 0,
      data: {
        answer: "Model-backed answer",
        products: [],
        sources: [{ type: "model", name: "DeepSeek deepseek-v4-flash" }],
      },
    });

    expect(response).toEqual({
      answer: "Model-backed answer",
      products: [],
      sources: [{ type: "model", name: "DeepSeek deepseek-v4-flash" }],
    });
  });
});
