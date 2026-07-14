# Product Catalog Data and Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every storefront product an appropriate image, normalize the 26 products owned by tenant 121, and leave a repeatable audit path for future testing.

**Architecture:** Keep the repository lightweight by using curated HTTPS image URLs. Treat the tenant-121 seed script as the database source of truth, keep local Demo products as the offline source of truth, and add shared frontend image-failure state plus database audit output.

**Tech Stack:** Vue 3, Vite, Vitest, PowerShell, Java/JDBC, MySQL 8, Docker Compose.

## Global Constraints

- Modify tenant `121` only; never update or delete tenant `1` product data.
- Preserve existing tenant-121 SPU and SKU IDs so carts and orders keep valid references.
- Do not commit downloaded bitmap assets.
- Every active product must have a distinct HTTPS cover and a non-empty gallery.
- Product money remains integer fen and satisfies `cost_price < price < market_price`.
- Preserve unrelated working-tree changes in the three start scripts.

---

### Task 1: Offline Catalog Completeness

**Files:**
- Modify: `furniture web/src/data/demoProducts.js`
- Create: `furniture web/tests/demoProducts.test.js`

**Interfaces:**
- Consumes: existing `demoProducts` array.
- Produces: five offline products where `cover` is HTTPS, `gallery` contains the cover plus at least one additional HTTPS image, and all covers are unique.

- [ ] **Step 1: Write the failing catalog test**

```js
import { describe, expect, it } from "vitest";
import { demoProducts } from "../src/data/demoProducts.js";

describe("offline demo product catalog", () => {
  it("gives every product a distinct HTTPS cover and gallery", () => {
    expect(new Set(demoProducts.map((product) => product.cover)).size).toBe(demoProducts.length);
    demoProducts.forEach((product) => {
      expect(product.cover).toMatch(/^https:\/\//);
      expect(product.gallery.length).toBeGreaterThanOrEqual(2);
      expect(product.gallery).toContain(product.cover);
      expect(product.gallery.every((url) => /^https:\/\//.test(url))).toBe(true);
    });
  });

  it("keeps commercially coherent prices and inventory", () => {
    demoProducts.forEach((product) => {
      expect(product.price).toBeGreaterThan(0);
      expect(product.marketPrice).toBeGreaterThan(product.price);
      expect(product.stock).toBeGreaterThanOrEqual(0);
    });
  });
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm test -- --run tests/demoProducts.test.js` from `furniture web`.
Expected: FAIL because every current `cover` is empty and every `gallery` is empty.

- [ ] **Step 3: Populate the five offline products**

Assign distinct curated image URLs to Cloud Modular Sofa, Oak Shelter Bed, Marble Dining Table, Outdoor Lounge Chair, and Lacquered Brass Pendant. Set each gallery to `[cover, secondAngleUrl]` and retain the existing IDs, SKU IDs, names, prices, and inventory.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `npm test -- --run tests/demoProducts.test.js`.
Expected: 2 tests pass.

- [ ] **Step 5: Commit the offline catalog**

```powershell
git add -- "furniture web/src/data/demoProducts.js" "furniture web/tests/demoProducts.test.js"
git commit -m "fix: add images to offline product catalog"
```

### Task 2: Broken External Image Fallback

**Files:**
- Modify: `furniture web/src/components/ProductImage.vue`
- Create: `furniture web/tests/productImage.test.js`

**Interfaces:**
- Consumes: `src: string`, `label: string` props.
- Produces: an `<img>` only while `src` is non-empty and has not emitted `error`; otherwise produces `.product-image-fallback` with the product label.

- [ ] **Step 1: Write the failing component-structure test**

```js
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/components/ProductImage.vue", import.meta.url), "utf8");

describe("ProductImage external image fallback", () => {
  it("tracks load failures and hides a broken image", () => {
    expect(source).toContain("const failed = ref(false)");
    expect(source).toContain("watch(() => props.src");
    expect(source).toContain('@error="failed = true"');
    expect(source).toContain('v-if="src && !failed"');
  });
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm test -- --run tests/productImage.test.js` from `furniture web`.
Expected: FAIL because the component has no failure state or error listener.

- [ ] **Step 3: Implement minimal reactive fallback**

Use `const props = defineProps(...)`, `const failed = ref(false)`, and a watcher that resets `failed` when `props.src` changes. Render the image with `v-if="src && !failed"` and `@error="failed = true"`; keep the existing fallback markup for the other branch.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `npm test -- --run tests/productImage.test.js tests/demoProducts.test.js`.
Expected: all 3 tests pass.

- [ ] **Step 5: Commit the fallback behavior**

```powershell
git add -- "furniture web/src/components/ProductImage.vue" "furniture web/tests/productImage.test.js"
git commit -m "fix: handle unavailable product images"
```

### Task 3: Tenant-121 Catalog Normalization and Audit

**Files:**
- Modify: `seed-furniture-agent-products.ps1`
- Create: `audit-furniture-agent-products.ps1`
- Create: `furniture web/tests/productSeedScript.test.js`

**Interfaces:**
- Consumes: local MySQL at `127.0.0.1:3306`, database `ruoyi-vue-pro`, tenant `121`.
- Produces: 26 active, readable tenant-121 products and a read-only audit command that exits nonzero for any invariant violation.

- [ ] **Step 1: Write the failing seed-source test**

```js
import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";

const seed = readFileSync(new URL("../../seed-furniture-agent-products.ps1", import.meta.url), "utf8");

describe("tenant 121 product seed", () => {
  it("contains 26 readable unique products and an audit phase", () => {
    expect((seed.match(/ensureProduct\(connection/g) || []).length).toBe(26);
    expect(seed).not.toMatch(/ensureProduct\([\s\S]{0,1000}"\\u[0-9a-fA-F]{4}/);
    expect(seed).toContain("auditCatalog(connection)");
    expect(seed).toContain("update system_tenant set expire_time");
  });
});
```

- [ ] **Step 2: Run the test and verify RED**

Run: `npm test -- --run tests/productSeedScript.test.js` from `furniture web`.
Expected: FAIL because the current source contains escaped mixed-language names, no audit method, and no tenant-expiry update.

- [ ] **Step 3: Normalize the 26-product source data**

Use these 26 distinct readable product names in the existing `ensureProduct` order so migration lookup is deterministic: Cream Fabric Sofa, Cloud Modular Sofa, Leather Lounge Sofa, Ivory Performance Sofa, Compact Linen Sofa, Brown Leather Club Sofa, Natural Oak Dining Table, Upholstered Shelter Bed, Brass Drum Pendant, Fluted Oak Media Console, Black Round Dining Table, Reclaimed Wood Dining Table, Grey Upholstered Dining Chair, Black Spindle Dining Chair, Smoked Glass Coffee Table, Natural Oak Coffee Table, Walnut Drum Side Table, Walnut Writing Desk, Handwoven Beige Wool Rug, Textured Grey Area Rug, Oak Two-Drawer Nightstand, Walnut Six-Drawer Dresser, Natural Oak Wardrobe, Opal Glass Table Lamp, Black Arc Floor Lamp, and Walnut Four-Door Sideboard. No two records may share a cover URL.

For each product provide a concise English keyword string, accurate introduction, distinct HTTPS cover, two-or-more-entry JSON gallery, coherent prices, and nonnegative stock. Extend `ensureProduct`, `updateProduct`, `bindProduct`, and `ensureSku` to pass gallery separately while keeping SKU cover equal to SPU cover.

- [ ] **Step 4: Add safe tenant expiry and invariant audit**

Before product updates, set tenant `121` expiry to `2099-12-31 23:59:59` only when the tenant exists and is not deleted. Add `auditCatalog(Connection)` queries that require exactly 26 active seed-owned SPUs and zero rows for: blank or non-HTTPS cover, empty gallery, names containing `?`, invalid price ordering, negative stock, category/brand tenant mismatch, missing SKU, or SKU cover/price/stock mismatch. Throw `IllegalStateException` when any count is nonzero.

- [ ] **Step 5: Add a standalone read-only audit wrapper**

Create `audit-furniture-agent-products.ps1` that compiles a small JDBC helper using the same resolved Java and MySQL driver configuration, runs the invariant queries without updating data, prints named counts, and exits nonzero when the catalog is not valid.

- [ ] **Step 6: Run the focused source test and verify GREEN**

Run: `npm test -- --run tests/productSeedScript.test.js`.
Expected: the seed-source test passes.

- [ ] **Step 7: Commit seed and audit tooling**

```powershell
git add -- "seed-furniture-agent-products.ps1" "audit-furniture-agent-products.ps1" "furniture web/tests/productSeedScript.test.js"
git commit -m "feat: normalize tenant product test data"
```

### Task 4: Apply and Verify the Database Catalog

**Files:**
- Runtime data only: local MySQL tenant `121` rows.

**Interfaces:**
- Consumes: Task 3 seed and audit scripts.
- Produces: passing database audit without changing tenant `1`.

- [ ] **Step 1: Capture pre-migration reference counts**

Run read-only SQL for tenant-121 SPU/SKU counts and references in cart/order item tables. Record the IDs before execution.

- [ ] **Step 2: Run the normalization script**

Run: `powershell -ExecutionPolicy Bypass -File .\seed-furniture-agent-products.ps1`.
Expected: transaction commits and prints all 26 normalized products plus zero audit violations.

- [ ] **Step 3: Run the standalone database audit**

Run: `powershell -ExecutionPolicy Bypass -File .\audit-furniture-agent-products.ps1`.
Expected: active products `26`; every violation count `0`; exit code `0`.

- [ ] **Step 4: Verify tenant isolation and ID preservation**

Query both tenants and confirm tenant `1` rows are unchanged, tenant `121` still uses its previous SPU/SKU IDs, tenant expiry is `2099-12-31 23:59:59`, and existing cart/order references still resolve.

### Task 5: Full Frontend and Storefront Verification

**Files:**
- No planned production file changes; fix only defects demonstrated by failing verification.

**Interfaces:**
- Consumes: normalized database and frontend changes.
- Produces: buildable storefront whose list, detail, cart, and checkout product images render or degrade cleanly.

- [ ] **Step 1: Run the complete frontend test suite**

Run: `npm test` from `furniture web`.
Expected: all Vitest suites pass.

- [ ] **Step 2: Run the production build**

Run: `npm run build` from `furniture web`.
Expected: Vite build succeeds with exit code `0`.

- [ ] **Step 3: Verify live API catalog**

Request `/app-api/product/spu/page?pageNo=1&pageSize=30` with `tenant-id: 121`; confirm 26 products, readable names, distinct non-empty covers, and non-empty galleries.

- [ ] **Step 4: Verify browser flows**

Open the product list and inspect representative sofa, bed, dining table, chair, lighting, rug, and storage items. Open a detail page, add an item to cart, and reach checkout; confirm the appropriate image appears at each stage. Temporarily test one invalid image URL and confirm the labeled fallback appears without layout collapse.

- [ ] **Step 5: Review repository scope**

Run `git status --short` and `git diff --check`. Confirm only catalog-related files and the pre-existing unrelated start-script changes remain.

### Task 6: Publish the Completed Work

**Files:**
- Git history and remote branch only.

**Interfaces:**
- Consumes: verified commits from Tasks 1-5.
- Produces: pushed current `codex/agent-rag` branch.

- [ ] **Step 1: Commit any final verification-only fixes**

Stage only catalog-related files and commit with a scoped message. Do not stage the pre-existing modified start scripts unless their changes are proven necessary for this feature.

- [ ] **Step 2: Inspect commits and branch status**

Run: `git log --oneline -6` and `git status --short`.
Expected: catalog commits are present; no uncommitted catalog files remain.

- [ ] **Step 3: Push the current branch**

Run: `git push -u origin codex/agent-rag`.
Expected: remote branch updates successfully.
