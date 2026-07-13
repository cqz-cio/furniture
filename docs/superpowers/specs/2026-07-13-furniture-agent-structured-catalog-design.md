# Furniture Agent Structured Catalog Design

## Goal

Create a production-compatible furniture catalog that can satisfy the existing comprehensive Agent acceptance suite with real, purchasable Mall SKUs. The Agent must filter and rank products using explicit structured facts, return the exact Mall `spuId` and `skuId`, and validate live sellable stock through ERP. It must not invent unavailable specifications or silently present a partial match as an exact match.

This work improves product data, SKU modeling, ERP alignment, requirement parsing, filtering, ranking, and test fixtures. It does not introduce RAG, payment or membership rules, image recognition, or conversation-authorization changes.

## Current-state findings

The current tenant catalog has 26 active furniture SPUs and 26 SKUs. Every SPU has exactly one SKU, the SKU property lists are empty, and every product has only one slider image. Although the catalog covers major furniture categories, it does not contain enough structured information to prove requirements such as dimensions, seat count, pet suitability, child safety, easy cleaning, scratch resistance, movability, or rental suitability.

`FurnitureProductSearchTool` currently performs meaningful filtering only for a category keyword and a maximum price. Parsed fields such as material, style, color, room size, seat count, and preferred features are not consistently carried into the database query. As a result, several acceptance scenarios can only produce guesses or superficially plausible results.

Tenant `121`, which is used by the local Agent environment, also has an expired date. A valid catalog cannot be exercised through the Agent API until the tenant fixture is made active.

## Design principles

1. Mall remains the commerce authority for SPUs, SKUs, specifications, images, and prices.
2. ERP remains the authority for sellable stock and the Mall-to-ERP SKU mapping.
3. A furniture search projection stores normalized filter facts only. It is not a second price, stock, or product catalog.
4. Recommendations operate at SKU granularity. SPUs group related choices, but the Agent recommends a concrete purchasable variant.
5. Hard constraints are never weakened silently. Partial matches disclose exactly which requested constraints were not met.
6. Seeds and migrations are idempotent, tenant-scoped, and safe to rerun in local and test environments.

## Catalog authority and model

### Mall SPU and native SKU properties

Keep the existing 26 SPUs where their product identity remains useful. Add real variants only where the acceptance scenarios require distinct purchasable choices. The completed seed contains between 35 and 40 SKUs, rather than creating a separate test-only catalog.

Use the existing `ProductPropertyDO`, `ProductPropertyValueDO`, and `ProductSkuDO.properties` model for customer-facing variant properties. At minimum, seeded SKUs use applicable values from:

- color;
- material;
- size or dimensions;
- seat count;
- bed width;
- finish.

Properties that select a price-bearing or stock-bearing variant belong on the Mall SKU. Each seeded SKU has a non-empty property list, an actual Mall price, an ERP mapping, and ERP stock.

### Furniture search projection

Add one tenant-scoped projection row per searchable SKU. The table is keyed by `(tenant_id, sku_id)`, includes `spu_id`, and stores fields that need typed filtering or ranking:

| Field | Meaning |
| --- | --- |
| `category_code` | Normalized furniture category |
| `style_codes` | Normalized applicable styles |
| `color_code` | Normalized primary color |
| `material_codes` | Normalized materials |
| `seat_count` | Supported number of seats |
| `width_mm` | Product width |
| `depth_mm` | Product depth |
| `height_mm` | Product height |
| `room_type_codes` | Suitable room types |
| `feature_codes` | Controlled features such as rounded edges and shallow depth |
| `pet_friendly` | Explicit pet-suitability claim |
| `child_friendly` | Explicit child-suitability claim |
| `easy_clean` | Explicit easy-clean claim |
| `scratch_resistant` | Explicit scratch-resistance claim |
| `movable` | Explicitly movable/lightweight design |
| `rental_friendly` | Suitable for rental use |

Codes are normalized enums or controlled strings, not prose extracted at query time. Multi-valued code fields use JSON arrays and are queried with exact element membership, not substring matching. Scalar fields used for category, dimension, and seat filters receive ordinary database indexes. Boolean fields are true only when the seeded product facts explicitly support the claim. Missing facts remain null or false and cannot satisfy a hard constraint.

The projection never stores an independent price or stock value. Search joins its `sku_id` back to Mall for the current price and asks `MallErpProductApi` for current sellable stock. Deleting or disabling a Mall SKU makes the projection non-searchable even if a stale row remains.

## Minimum acceptance-covering inventory

The production-compatible seed must provide at least one ERP-aligned, in-stock SKU for each row below. One SKU may cover multiple rows when all attributes genuinely apply.

| Required purchasable variant | Required facts |
| --- | --- |
| Shallow gray three-seat fabric sofa | sofa, gray, fabric, 3 seats, width at most 2200 mm, shallow depth, price at most CNY 8,000 |
| Dark pet-friendly single chair | single chair, dark color, non-leather, pet friendly, easy clean; scratch resistance may be claimed only when explicitly supported |
| Four-seat solid-wood dining table | dining table, solid wood, 4 seats, explicit width/depth/height |
| Six-seat solid-wood dining table | dining table, solid wood, 6 seats, explicit dimensions |
| Six-seat marble-look dining table | dining table, marble-look material or finish, 6 seats, explicit dimensions |
| Deep-brown 1.8 m solid-wood bed | bed, deep brown, solid wood, 1.8 m bed width |
| Multi-SKU bed | at least three purchasable SKUs under one bed SPU with meaningful size/color choices |
| Rounded children’s desk | desk, child friendly, rounded-edge feature, price at most CNY 3,000 |
| Rounded child-friendly coffee table | coffee table, child friendly, rounded-edge feature, price at most CNY 4,000 |
| Movable bedroom storage | bedroom storage, width at most 1500 mm, movable, rental friendly |
| Compact modern two-seat sofa | sofa, modern, 2 seats, compact dimensions |
| Cream fabric sofa | sofa, cream family color, fabric, explicit dimensions |

The existing catalog may retain additional products, but every active storefront SKU must remain ERP-aligned under the existing fail-closed catalog rule.

## Seed and media quality

The catalog seed performs stable-key upserts instead of relying on generated database IDs. Re-running it updates the intended tenant’s records without creating duplicate properties, SPUs, SKUs, projection rows, ERP products, or mapping rows.

The seed must:

- set tenant `121` to the deterministic local/test expiry `2099-12-31 23:59:59`;
- keep all inserted rows tenant `121` scoped;
- give each active product at least two valid slider images;
- avoid shared placeholder covers when distinct product images are available;
- create native SKU property/value records before assigning SKU properties;
- synchronize each seeded SKU to ERP and give it positive initial stock;
- create or update the corresponding furniture search projection;
- preserve existing primary keys and mappings when rerun.

Production deployments must manage tenant expiry through normal tenant administration. The deterministic expiry adjustment is part of the repository’s local/test seed only.

## Requirement model and normalization

Extend the Agent’s structured furniture requirement model so the following facts survive extraction, follow-up merging, persistence, and search:

- category and room type;
- minimum or exact seat count;
- maximum width, depth, and height;
- room dimensions when the user describes available space;
- colors, materials, and styles;
- maximum budget;
- pet-friendly, child-friendly, easy-clean, scratch-resistant, movable, and rental-friendly requirements;
- preferred features such as rounded edges or shallow depth.

Each constraint also carries whether it is hard or preferred. Explicit limits and exclusion language make a constraint hard: examples include `预算不超过`, `宽度不超过`, `必须`, `只要`, and `不要真皮`. Category is hard once identified. Seat count and material are hard when they define the requested product, while ordinary color and style wording is a preference unless the user marks it mandatory. Constraints marked with must/only/exclusion language are also non-relaxable. This permits a deep-brown solid-wood bed request to fall back truthfully to a non-deep-brown solid-wood bed, while an exact deep-brown candidate still ranks first.

Normalize Chinese and English aliases to controlled codes before querying. Examples include `米白/奶油色/cream`, `实木/solid wood`, `三人位/3-seat`, and dimensions expressed in meters, centimeters, or millimeters. Store dimensions internally in millimeters and prices in the project’s existing minor currency unit.

Follow-up turns merge only fields mentioned by the user. A new explicit value replaces the prior value for the same single-valued constraint; multi-valued preferences are combined unless the user retracts them. A category change clears category-specific fields that no longer apply, such as bed width when switching from a bed to a sofa.

## Search and recommendation flow

1. Extract normalized constraints from the current user message.
2. Merge them with the conversation’s prior furniture requirements.
3. Build a typed search request rather than relying on a concatenated natural-language search string.
4. Query enabled Mall SKUs joined to their furniture projection within the current tenant.
5. Run the exact pass using category and every hard constraint, including applicable maximum price, dimensions, seat count, material, exclusions, or required capabilities.
6. Verify that each candidate is ERP mapped and read its live sellable stock through `MallErpProductApi`.
7. Exclude disabled, deleted, unmapped, or zero-stock SKUs.
8. If the exact pass is empty, run a partial pass that never relaxes category, explicit exclusions, must/only constraints, tenant isolation, or sellability. Other hard constraints may be missed only when the miss is returned explicitly.
9. Rank candidates using constraint coverage and preferences such as ordinary color/style wording and preferred-but-not-required features, then deterministic tie breakers: available stock, lower price, and SKU ID.
10. Return real `spuId`, `skuId`, native specification labels, Mall price, ERP stock, images, and a reason grounded only in stored facts.

The search service must batch product, projection, and ERP lookups to avoid one database or ERP call per candidate.

## Match contract

Every recommendation response has one of three match types:

- `EXACT`: every hard constraint is satisfied by the recommended SKU.
- `PARTIAL`: no exact candidate exists, but one or more useful candidates satisfy the category and a defined subset of constraints.
- `NONE`: no safe and meaningful candidate exists.

For `PARTIAL`, the response includes matched constraints and unmet constraints as structured fields. User-facing copy must state the gap, for example that a table fits the budget but supports four rather than six seats. It must not claim the missing property in recommendation text.

For `NONE`, return guidance that helps the user relax a constraint or choose another category. Do not manufacture a product card. ERP lookup failures fail closed for the affected candidates; if all candidates fail validation, the result is `NONE` with a temporary availability message rather than an invented stock value.

The existing Agent response remains backward compatible. `matchType`, `matchedConstraints`, and `unmetConstraints` are additive fields. Each product item continues to identify the SPU and now always identifies its concrete SKU. When the user explicitly asks for all variants of one product, the response groups the real SKUs under that SPU and reports each SKU's native properties, Mall price, and ERP stock without combining records from different SPUs.

## Administration and update behavior

The initial delivery may populate projections through an idempotent repository seed, but the projection is a maintained product concern rather than a disposable test fixture. Create a focused application service that validates and upserts the projection for a Mall SKU. Seed code and future admin integration call that service instead of duplicating normalization rules.

Changing price remains a Mall SKU operation. Changing inventory remains an ERP operation. Changing a searchable furniture fact updates the projection and, when customer-facing, the native SKU properties. The search service always re-reads Mall and ERP authority data before returning a recommendation.

## Error handling and consistency

- Reject projection writes for a nonexistent SKU, cross-tenant SKU, or mismatched SPU/SKU pair.
- Reject negative dimensions or seat counts and unknown controlled codes.
- Do not mark a boolean capability true based only on a marketing title.
- Exclude projection rows whose Mall SKU is disabled or deleted.
- Treat a missing ERP mapping, ERP lookup error, or non-positive ERP stock as not sellable.
- Keep seed operations transactional per product where practical and make reruns repair partial prior runs.
- Log excluded candidates with a machine-readable reason without exposing internal errors to the user.

## Testing and verification

### Unit and persistence tests

- normalization of Chinese and English category, material, color, size, and feature aliases;
- dimension and budget unit conversion;
- follow-up requirement merging, replacement, retraction, and category switching;
- projection validation and tenant isolation;
- exact filtering for each typed field;
- deterministic ranking;
- `EXACT`, `PARTIAL`, and `NONE` classification;
- truthful matched/unmet constraint reporting;
- ERP unmapped, unavailable, zero-stock, and positive-stock behavior;
- multi-SKU selection under one SPU;
- idempotent seed reruns without duplicate rows.

### Catalog audit

An automated audit must fail when any active seeded storefront product has fewer than two slider images, an invalid cover, an empty SKU property list, a missing search projection, a tenant mismatch, a missing ERP mapping, or no positive ERP stock. It also verifies that every minimum acceptance-covering variant exists.

### Acceptance and smoke tests

Run the checked-in 40-scenario, 110-turn comprehensive acceptance dataset. Product-search scenarios assert not only response shape but that the selected SKU facts satisfy the expected constraints. Follow-up scenarios verify that earlier constraints persist correctly. Add API smoke tests that exercise the Agent through active tenant `121` and confirm returned product identifiers resolve through the storefront product endpoint.

## Rollout order

1. Add the projection schema and validation service.
2. Add normalized requirement fields and typed search behavior behind existing Agent interfaces.
3. Add the idempotent catalog/property/SKU/projection/ERP seed and tenant-fixture repair.
4. Run the catalog audit and backend tests.
5. Run the comprehensive Agent acceptance suite and API smoke tests.

The change is ready only when the seeded catalog passes the audit, every required variant is ERP aligned and in stock, and exact-match test prompts resolve to SKUs whose stored facts satisfy all asserted hard constraints.

## Non-goals

- Building a RAG knowledge base or defining payment, membership, refund, or promotion policy documents.
- Implementing image-based furniture recognition.
- Reworking the Agent panel UI.
- Changing checkout price authority or ERP stock authority.
- Repairing the separately identified conversation ownership and authorization issue.
- Adding a general-purpose product information management system beyond the focused furniture projection and seed needed here.
