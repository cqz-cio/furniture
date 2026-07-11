import { demoProducts } from "../data/demoProducts.js";
import { requestYudao } from "./yudaoClient.js";

export const FURNITURE_ASSISTANT_ENDPOINT = "/ai/furniture-assistant/chat";

const DEFAULT_LIMIT = 3;
const KNOWLEDGE_MOCK_RESPONSES = [
  {
    keywords: ["membership", "member", "coupon", "benefit", "price stack", "stack"],
    source: { type: "knowledge", name: "Membership Rules" },
    answer:
      "Membership prices can be used with eligible coupons at checkout unless a campaign marks them as exclusive.",
  },
  {
    keywords: ["delivery", "install", "installation", "shipping", "large furniture"],
    source: { type: "knowledge", name: "Delivery And Installation" },
    answer:
      "Large furniture delivery should confirm address coverage, elevator access and installation availability before checkout.",
  },
  {
    keywords: ["return", "exchange", "after-sales", "refund", "support"],
    source: { type: "knowledge", name: "Returns And After-sales" },
    answer:
      "Return and exchange requests should follow the order after-sales flow, and used or installed items may require support review.",
  },
];

const toFiniteNumber = (value, fallback = 0) => {
  const nextValue = Number(value);
  return Number.isFinite(nextValue) ? nextValue : fallback;
};

const normalizeSources = (sources) =>
  Array.isArray(sources)
    ? sources
        .filter((source) => source && typeof source === "object")
        .map((source) => ({
          type: String(source.type || "knowledge"),
          name: String(source.name || source.title || "Assistant source"),
        }))
    : [];

export const buildAssistantProductDetailUrl = (product) => {
  const id = product?.id ?? product?.spuId;
  return id === undefined || id === null ? "/sofas-plp" : `/sofa-pdp?id=${encodeURIComponent(id)}`;
};

export const normalizeAssistantProduct = (product = {}) => ({
  id: product.id ?? product.spuId ?? product.skuId,
  skuId: product.skuId ?? product.id ?? product.spuId,
  name: product.name || "Recommended product",
  subtitle: product.subtitle || product.description || product.introduction || "",
  price: toFiniteNumber(product.price),
  marketPrice: toFiniteNumber(product.marketPrice, toFiniteNumber(product.price)),
  stock: toFiniteNumber(product.stock),
  cover: product.cover || product.picUrl || product.image || "",
  reason: product.reason || product.assistantReason || "Selected from the current furniture catalog for this request.",
  detailUrl: product.detailUrl || buildAssistantProductDetailUrl(product),
  source: "assistant",
  raw: product,
});

export const normalizeAssistantResponse = (payload = {}) => {
  const data = payload?.data && typeof payload.data === "object" ? payload.data : payload;

  return {
    ...(data?.conversationId ? { conversationId: String(data.conversationId) } : {}),
    answer: String(data?.answer || data?.content || ""),
    products: Array.isArray(data?.products) ? data.products.map(normalizeAssistantProduct) : [],
    sources: normalizeSources(data?.sources),
    ...(Array.isArray(data?.messages) ? { messages: data.messages } : {}),
    ...(data?.requirements && typeof data.requirements === "object" ? { requirements: data.requirements } : {}),
  };
};

const findMockKnowledgeResponse = (message) => {
  const normalized = String(message || "").toLowerCase();
  return KNOWLEDGE_MOCK_RESPONSES.find((entry) => entry.keywords.some((keyword) => normalized.includes(keyword)));
};

export const createMockAssistantResponse = (message, products = demoProducts) => {
  const prompt = String(message || "").trim();
  const knowledgeResponse = findMockKnowledgeResponse(prompt);

  if (knowledgeResponse) {
    return normalizeAssistantResponse({
      answer: knowledgeResponse.answer,
      products: [],
      sources: [knowledgeResponse.source],
    });
  }

  const answer = prompt
    ? `I can use this request as the assistant contract: "${prompt}". Here are three structured product recommendations while the backend endpoint is connected.`
    : "Tell me about a room, style or budget and I will return structured recommendations.";

  return normalizeAssistantResponse({
    answer,
    products: products.slice(0, DEFAULT_LIMIT).map((product, index) => ({
      ...product,
      reason:
        [
          "Closest match for the requested room and budget.",
          "Good alternate if you want a quieter material or silhouette.",
          "Useful comparison piece for scale, stock and styling trade-offs.",
        ][index] || "Useful comparison piece from the current catalog.",
    })),
    sources: [{ type: "mock", name: "Assistant response contract" }],
  });
};

const shouldUseMockResponse = (options) => {
  if (Object.prototype.hasOwnProperty.call(options, "useMock")) {
    return Boolean(options.useMock);
  }
  return import.meta.env?.VITE_FURNITURE_ASSISTANT_MODE !== "api";
};

export const sendFurnitureAssistantMessage = async (message, options = {}) => {
  const content = String(message || "").trim();
  if (!content) return createMockAssistantResponse("");

  if (shouldUseMockResponse(options)) {
    return createMockAssistantResponse(content, options.products || demoProducts);
  }

  const request = options.request || requestYudao;
  const endpoint = options.endpoint || FURNITURE_ASSISTANT_ENDPOINT;
  const response = await request(endpoint, {
    method: "POST",
    body: JSON.stringify({
      ...(options.conversationId ? { conversationId: options.conversationId } : {}),
      message: content,
    }),
  });

  return normalizeAssistantResponse(response);
};

export const getFurnitureAssistantConversation = async (conversationId, options = {}) => {
  const id = String(conversationId || "").trim();
  if (!id) return null;
  const request = options.request || requestYudao;
  const response = await request(`${FURNITURE_ASSISTANT_ENDPOINT.replace(/\/chat$/, "")}/conversations/${encodeURIComponent(id)}`);
  const normalized = normalizeAssistantResponse(response);
  return normalized.conversationId ? normalized : null;
};

export const deleteFurnitureAssistantConversation = async (conversationId, options = {}) => {
  const id = String(conversationId || "").trim();
  if (!id) return;
  const request = options.request || requestYudao;
  await request(`${FURNITURE_ASSISTANT_ENDPOINT.replace(/\/chat$/, "")}/conversations/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
};
