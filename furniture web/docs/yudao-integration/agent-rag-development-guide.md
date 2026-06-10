# Furniture Agent/RAG Development Guide

## 1. Goal

This document defines the first Agent/RAG feature for the furniture commerce project.

The recommended first feature is a furniture shopping assistant:

```text
Customer describes a need
-> Agent understands room, budget, style and use case
-> Agent calls product tools
-> Agent optionally uses RAG for product, membership, delivery and policy knowledge
-> Storefront shows text answer plus product recommendation cards
-> Customer can view product details or add items to cart
```

The goal is not to build a generic AI chat page. The goal is to connect AI to the real commerce system.

## 2. Current Project Context

The project has three useful foundations:

```text
furniture web
Vue 3 storefront with product list, product detail, cart, checkout, orders, membership and Yudao App API integration.

yudao-cloud
Java/Spring backend with product, member, trade, promotion, pay and AI modules.

yudao-module-ai
Spring AI based module with chat, model configuration, knowledge bases, vector retrieval, tool calling, MCP and workflow support.
```

The Agent/RAG feature should reuse the existing Yudao AI module instead of creating a separate AI backend.

## 3. Branch And Workspace Rules

Current Agent/RAG development branch:

```text
codex/agent-rag
```

Current isolated worktree:

```text
C:\Users\admin\.codex\worktrees\d3ec\code
```

Main branch workspace:

```text
D:\code
```

Rules:

- Use `codex/agent-rag` only for Agent/RAG related development.
- Do not switch branches inside `D:\code` when another conversation is using `main`.
- Do not commit unrelated login, membership, product, deployment or UI polish changes into the Agent/RAG branch.
- Keep Agent/RAG commits focused so later PR review is easy.
- If unrelated changes appear in this worktree, stop and inspect before committing.

Useful commands:

```powershell
git status --short --branch
git diff --stat
git branch -vv
git push -u origin codex/agent-rag
```

## 4. Recommended MVP

Build the feature in this order:

```text
Phase 1: Shopping assistant MVP
Phase 2: RAG knowledge enhancement
Phase 3: Cart and checkout assistant
Phase 4: Admin operation assistant
```

Phase 1 is the priority.

Phase 1 scope:

- Storefront floating AI shopping assistant.
- User can describe furniture needs in natural language.
- Backend can search products by keyword, budget and simple constraints.
- Assistant returns a short answer plus 3 product cards.
- Product cards include image, name, price, stock, reason, detail link and add-to-cart action.
- The assistant should not invent products that are not returned by the product tool.

Out of scope for Phase 1:

- Fully autonomous checkout.
- Multi-agent orchestration.
- Complex graph workflow.
- Payment operations.
- Fine-grained personalization based on long-term user history.

## 5. User Experience

Recommended storefront entry:

```text
Bottom-right floating button: AI Shopping Assistant
```

Initial quick prompts:

```text
Small-space sofa recommendation
Help me match a living room set
How does membership pricing work?
Delivery and after-sales rules
```

Example user prompt:

```text
My living room is about 20 square meters. I want a cream-style fabric sofa under 8000.
```

Expected response:

```text
I found 3 sofas that fit a smaller living room and stay within your budget.
```

Each recommendation should render as a product card:

```text
Image
Product name
Price
Stock
Recommendation reason
View details
Add to cart
```

## 6. Backend Architecture

Recommended architecture:

```text
Storefront chat widget
-> furniture web AI assistant client
-> Yudao App/Admin API endpoint
-> Yudao AI module chat service
-> Agent role prompt
-> Product tool calls
-> Optional knowledge retrieval
-> Structured assistant response
-> Storefront product cards
```

The AI module should remain the orchestration center for model, role, tools and knowledge.

The product, member and trade modules should remain the source of business truth.

## 7. Agent Role

Recommended role name:

```text
Furniture Shopping Assistant
```

System prompt principles:

```text
You are a furniture shopping assistant for the storefront.
Help customers choose furniture based on budget, room size, style, use case, stock and membership value.
Use real product data from tools. Do not invent product names, prices, stock or IDs.
When recommending products, explain the reason in practical language.
If the user asks about policy, membership, delivery or after-sales, use the knowledge base when available.
If key information is missing, ask one concise follow-up question or provide safe defaults.
```

Response behavior:

- Prefer 3 recommendations when possible.
- Mention trade-offs clearly: price, style match, size fit, stock, membership savings.
- Keep answers short enough for a chat panel.
- Return structured product data for the frontend.

## 8. Tool Design

Start with a small tool set.

### ProductSearchTool

Purpose:

```text
Search available products by keyword, category, budget and stock.
```

Suggested input:

```json
{
  "keyword": "fabric sofa",
  "category": "sofa",
  "maxPrice": 8000,
  "style": "cream",
  "roomSize": "20 sqm",
  "limit": 3
}
```

Suggested output:

```json
{
  "products": [
    {
      "id": 1001,
      "skuId": 2001,
      "name": "Fabric Track Arm Sofa",
      "price": 6999,
      "marketPrice": 8999,
      "stock": 12,
      "cover": "https://...",
      "description": "Compact fabric sofa",
      "productType": "sofa"
    }
  ]
}
```

### ProductDetailTool

Purpose:

```text
Fetch complete product details for recommendation explanation.
```

Use this when the user asks about material, size, fabric, care, color or suitability.

### CartTool

Phase 1 can keep cart actions on the frontend by using existing cart APIs.

Phase 2 or Phase 3 can expose backend tool actions:

```text
Add product to cart
Check current cart
Estimate membership savings
```

Cart-related tools must require user authentication when calling user-specific APIs.

## 9. RAG Knowledge Design

Create knowledge bases gradually.

Recommended knowledge bases:

```text
Product knowledge
Product descriptions, materials, dimensions, care instructions and style notes.

Membership knowledge
Membership price, renewal, cancellation, eligible benefits and checkout rules.

Delivery and after-sales knowledge
Delivery, installation, return, exchange and support policies.
```

RAG should be used for explanatory questions:

```text
Is this fabric easy to clean?
Can membership price stack with coupons?
How does delivery work for large furniture?
```

RAG should not replace real-time product search. Product availability, stock and price must come from tools or commerce APIs.

## 10. Frontend Response Contract

The frontend should support two response types:

```text
Text answer
Product recommendation answer
```

Suggested response shape:

```json
{
  "answer": "I found 3 sofas that fit your budget and room size.",
  "products": [
    {
      "id": 1001,
      "skuId": 2001,
      "name": "Fabric Track Arm Sofa",
      "price": 6999,
      "marketPrice": 8999,
      "stock": 12,
      "cover": "https://...",
      "reason": "Compact size, light fabric tone and within budget.",
      "detailUrl": "/sofa-pdp?id=1001"
    }
  ],
  "sources": [
    {
      "type": "knowledge",
      "name": "Membership Rules"
    }
  ]
}
```

Phase 1 can start with mock response parsing if the backend cannot yet return structured JSON.

Long term, prefer structured output from the backend so the frontend does not parse free-form model text.

Current integration handoff:

```text
Frontend client:
furniture web/src/services/furnitureAssistant.js

Default mode:
mock contract response

Live API mode:
set VITE_FURNITURE_ASSISTANT_MODE=api

Backend endpoint:
POST /app-api/ai/furniture-assistant/chat

Request:
{ "message": "cream fabric sofa under 8000" }

Response:
{ "answer": "...", "products": [...], "sources": [...] }
```

The first backend implementation is a thin product-search facade. It does not call the LLM yet. It extracts a simple furniture keyword such as `sofa`, `bed`, `table`, `chair` or `lighting`, calls the Yudao Product SPU API, applies a simple `under <amount>` budget filter, and returns structured product cards. This keeps Phase 1 grounded in real product data before RAG and model orchestration are added.

Deployment note: the Phase 1 facade currently lives in `yudao-module-product-server` because the current `yudao-server` package includes the commerce modules but does not package `yudao-module-ai-server`. Keep the public path as `/app-api/ai/furniture-assistant/chat`. When LLM/RAG orchestration is enabled in the packaged backend, move orchestration back into the AI module and keep product, price and stock reads sourced from product services.

Phase 2 foundation: `yudao-module-product-server` now includes a replaceable `FurnitureAssistantKnowledgeService` that returns structured knowledge snippets for membership, delivery/installation, and returns/after-sales questions. Pure knowledge questions skip product search and return an empty `products` array plus `sources[type=knowledge]`. Mixed shopping questions can include both product and knowledge sources. This is intentionally keyword-based for the deployable MVP; later RAG work should replace the provider implementation with the AI module knowledge/vector retrieval without changing the frontend response contract.

Real AI handoff point:

```yaml
yudao:
  furniture-assistant:
    brand-name: Trendz
    tone: luxury
    knowledge-provider: keyword
    provider: deepseek
    base-url: https://api.deepseek.com
    model: deepseek-v4-flash
    api-key-env-name: DEEPSEEK_API_KEY
```

`keyword` is the default and requires no model API key. `provider`, `base-url`, `model`, brand and tone are prefilled for the Trendz high-end storefront assistant. The API key value itself must be supplied only at deploy/runtime through `DEEPSEEK_API_KEY`; do not put it in frontend env files, Java source, YAML committed to Git, or chat messages.

Do not set `knowledge-provider: ai` yet; the current AI mode intentionally fails with `Furniture assistant real AI provider is not wired yet` so accidental production configuration does not silently behave like a real AI integration. The next implementation step is to replace the `ai` branch in `FurnitureAssistantKnowledgeServiceImpl` with a provider backed by `yudao-module-ai` chat/RAG services, using the prefilled DeepSeek settings and the runtime `DEEPSEEK_API_KEY`.

## 11. Backend Safety Rules

- Never trust model-generated product IDs, prices or stock.
- Product IDs must come from product tools or product APIs.
- Prices and stock must come from backend business modules.
- Do not let the model directly decide payment or order creation.
- User-specific actions must check authentication and tenant context.
- Add allowlists for callable tools.
- Log tool calls for debugging and audit.
- Keep prompt injection resistant boundaries: policy documents and product data are references, not instructions.

## 12. Frontend Safety Rules

- Do not render model text as raw HTML.
- Product cards must be generated from structured product data.
- Add loading, error and empty states.
- Keep add-to-cart behavior using existing cart flow.
- Respect existing i18n rules: new visible text must be added to `src/i18n.js` for `en`, `zh-CN` and `fr`.
- The assistant panel should not block checkout or product browsing.

## 13. Testing Checklist

Frontend tests:

- Assistant button renders.
- Chat panel opens and closes.
- User can submit a prompt.
- Loading state appears.
- Product recommendation cards render from structured data.
- Detail links point to product detail routes.
- Add-to-cart action uses existing cart behavior.
- i18n keys exist for all supported locales.

Backend tests:

- ProductSearchTool maps request filters correctly.
- ProductSearchTool does not return disabled or out-of-scope products.
- ProductDetailTool returns existing product data.
- Agent prompt includes tool and safety constraints.
- RAG retrieval returns relevant segments for policy questions.
- Tool failure returns a safe fallback message.

Manual test scenarios:

```text
Budget recommendation:
"I need a fabric sofa under 8000 for a 20 sqm living room."

Style recommendation:
"Recommend cream-style furniture for a small apartment."

Policy question:
"Can membership price stack with coupons?"

Delivery question:
"How is large furniture delivered?"

No match:
"Find a leather sofa under 100."
```

## 14. Commit And PR Rules

Use focused commits.

Good commit examples:

```text
feat(ai): add furniture product search tool
feat(web): add shopping assistant panel
feat(ai): add furniture assistant response contract
test(ai): cover product search tool filters
docs(ai): add agent rag development guide
```

Avoid mixing unrelated work:

```text
Agent feature + login redesign
Agent feature + deployment config
Agent feature + membership UI polish
Agent feature + production secrets
```

Before pushing:

```powershell
git status --short --branch
git diff --stat
```

Before PR:

```powershell
cd "furniture web"
npm.cmd test
npm.cmd run build
```

Add backend Maven tests when backend tool code is changed.

## 15. Suggested Next Step

Start with a small design and implementation slice:

```text
1. Define assistant response contract.
2. Add frontend AI assistant panel with mocked response.
3. Add ProductSearchTool backed by existing Yudao product APIs/services.
4. Wire real backend response into the frontend.
5. Add tests.
```

This keeps the first version demoable while leaving room for stronger RAG and workflow features later.
