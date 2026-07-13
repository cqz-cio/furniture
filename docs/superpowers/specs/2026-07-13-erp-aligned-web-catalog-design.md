# ERP-Aligned Web Catalog Design

## Goal

The furniture Web storefront must display only Mall products whose SKUs have been synchronized to ERP. It must never substitute hard-coded demo products when the backend is unavailable, returns no products, or rejects a product detail request.

## Catalog authority

ERP alignment is enforced by the backend, not inferred by the browser. A storefront SPU is visible only when every sellable SKU returned for that SPU has a valid Mall-to-ERP product mapping. This keeps list, detail, cart, and order behavior consistent and prevents an unmapped SKU from appearing purchasable.

The existing Mall/ERP mapping table and `MallErpProductApi` are the source of alignment truth. The App product list and product detail endpoints apply the same visibility rule. Admin product endpoints remain unchanged so an administrator can still see an unsynchronized Mall product and trigger synchronization.

## Backend behavior

- `GET /app-api/product/spu/page` returns only ERP-aligned SPUs and reports pagination totals for that filtered catalog.
- `GET /app-api/product/spu/get-detail?id=...` rejects an SPU that has no valid ERP mapping using the existing product-not-found behavior.
- An SPU with no sellable SKU is not storefront-visible.
- An SPU with multiple sellable SKUs is storefront-visible only when all of them are mapped. Partial synchronization must not expose an option that later fails in cart or checkout.
- ERP or mapping lookup failures fail closed: the product is not returned as aligned.

## Frontend behavior

- Remove `src/data/demoProducts.js` completely.
- The product-list page starts with an empty product collection and populates it only from `getProductPage`.
- An empty backend page renders an explicit empty-catalog message.
- A product API failure renders an explicit catalog-unavailable message and no cards.
- The product-detail page starts without a product. A missing ID, backend rejection, or request failure renders a product-unavailable state and disables all purchase actions.
- Product labels are derived only from backend data; the `DEMO` label and offline-catalog wording are removed from these commerce pages.
- Assistant mock responses must not import or recommend demo products. When no backend products are supplied, the mock response contains no product recommendations.

## Data flow

1. An administrator creates or updates a Mall SPU and its SKUs.
2. The administrator invokes the existing ERP synchronization endpoint.
3. ERP product records and Mall-SKU mappings are created or updated.
4. App catalog endpoints check those mappings before returning an SPU.
5. The Web storefront renders only the returned products and never supplies local substitutes.

## Error handling

The system fails closed. Backend connectivity errors, invalid responses, missing mappings, and direct links to unavailable products show a non-purchasable state. They do not expose stale or invented price, stock, SKU, or product information.

## Testing

- Backend unit/controller tests cover fully mapped, partially mapped, unmapped, and SKU-less SPUs for both list and detail behavior.
- Frontend tests cover successful catalogs, empty catalogs, request failures, unavailable details, and the absence of imports or references to `demoProducts`.
- Existing cart, order, product client, and assistant tests run as regression coverage.

## Scope

This change does not automatically synchronize newly created Mall products and does not change the existing admin synchronization workflow. It only makes ERP alignment a mandatory storefront visibility condition and removes all fixed Web demo products.
