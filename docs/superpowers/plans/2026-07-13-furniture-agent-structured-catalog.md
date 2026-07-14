# Furniture Agent Structured Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-compatible, ERP-stock-backed furniture SKU catalog and make the Agent return truthful exact, partial, or no-match recommendations for the comprehensive acceptance suite.

**Architecture:** Mall SPUs, SKUs, native properties, images, and prices remain authoritative commerce data; ERP remains authoritative for SKU mapping and sellable stock. A tenant-scoped `product_furniture_sku_search` projection adds normalized furniture facts for typed filtering, while a pure matcher separates exact/partial ranking from database and RPC orchestration. The conversation requirement model produces a typed request, and the existing Agent response receives backward-compatible match and SKU-variant fields.

**Tech Stack:** Java 8, Spring Boot, MyBatis Plus, MySQL 8 JSON columns, JUnit 5, Mockito, PowerShell, JDBC, Node.js, Vitest.

## Global Constraints

- Mall is authoritative for SPUs, SKUs, native properties, images, and prices.
- ERP is authoritative for Mall-to-ERP SKU mapping and sellable stock.
- The search projection must never store an independent price or stock value.
- Recommendations are tenant-scoped and operate on concrete Mall SKU IDs.
- Category, explicit exclusions, must/only constraints, tenant isolation, ERP mapping, and positive ERP stock are never relaxed.
- `PARTIAL` results must list every unmet hard constraint and must not claim unsupported facts.
- Tenant `121` local/test expiry is exactly `2099-12-31 23:59:59`.
- The completed tenant `121` seed contains 35 to 40 active SKUs, each with non-empty native properties, at least two slider images on its SPU, an ERP mapping, and positive ERP stock.
- Seed and audit operations are idempotent and tenant `121` scoped.
- Preserve the existing fail-closed storefront behavior and unrelated worktree changes.
- Conversation ownership authorization, RAG, policy documents, image recognition, payment, membership, and Agent panel UI are outside this plan.

---

### Task 1: Furniture SKU search projection persistence

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/product-furniture-sku-search.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/dataobject/furniture/FurnitureSkuSearchDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/furniture/FurnitureSkuSearchMapper.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/catalog/FurnitureProjectionValidator.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/catalog/FurnitureSkuSearchService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/catalog/FurnitureSkuSearchServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/catalog/FurnitureSkuSearchServiceImplTest.java`

**Interfaces:**
- Produces: `FurnitureSkuSearchService.upsert(FurnitureSkuSearchDO projection)`.
- Produces: `FurnitureSkuSearchService.getByCategory(String categoryCode)` and `getBySkuIds(Collection<Long> skuIds)`.
- Produces: dependency-free `FurnitureProjectionValidator.validate(String, List<String>, String, List<String>, Integer, Integer, Integer, Integer, List<String>, List<String>)`, used by both the Spring service and the standalone Java seed compiled by PowerShell.
- Contract: reject a missing SKU, a mismatched `spuId`, non-positive dimensions/seat count, and unknown controlled codes.

- [ ] **Step 1: Write failing projection-service tests**

Add focused tests that prove a valid SKU projection is saved and invalid SKU/SPU and dimension combinations are rejected:

```java
@Test
void upsert_shouldValidateSkuRelationshipAndDimensions() {
    ProductSkuDO sku = ProductSkuDO.builder().id(2001L).spuId(1001L).build();
    when(productSkuService.getSku(2001L)).thenReturn(sku);
    FurnitureSkuSearchDO value = FurnitureSkuSearchDO.builder()
            .skuId(2001L).spuId(1001L).categoryCode("sofa")
            .materialCodes(Arrays.asList("fabric"))
            .styleCodes(Arrays.asList("modern"))
            .roomTypeCodes(Arrays.asList("living-room"))
            .featureCodes(Arrays.asList("shallow-depth"))
            .seatCount(3).widthMm(2180).depthMm(880).heightMm(820)
            .petFriendly(true).easyClean(true).build();

    service.upsert(value);

    verify(mapper).insert(value);
}

@Test
void upsert_shouldRejectMismatchedSpu() {
    when(productSkuService.getSku(2001L))
            .thenReturn(ProductSkuDO.builder().id(2001L).spuId(9999L).build());
    FurnitureSkuSearchDO value = FurnitureSkuSearchDO.builder()
            .skuId(2001L).spuId(1001L).categoryCode("sofa")
            .widthMm(2180).build();

    assertThrows(IllegalArgumentException.class, () -> service.upsert(value));
    verifyNoInteractions(mapper);
}
```

- [ ] **Step 2: Run the focused test and verify the missing types fail compilation**

Run from `yudao电商管理平台前后端/yudao-cloud`:

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureSkuSearchServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because `FurnitureSkuSearchDO` and `FurnitureSkuSearchServiceImpl` do not exist.

- [ ] **Step 3: Add the projection table and typed data object**

Create the table with no price or stock columns:

```sql
CREATE TABLE IF NOT EXISTS `product_furniture_sku_search` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `category_code` varchar(32) NOT NULL,
  `style_codes` json NOT NULL,
  `color_code` varchar(32) DEFAULT NULL,
  `material_codes` json NOT NULL,
  `seat_count` int DEFAULT NULL,
  `width_mm` int DEFAULT NULL,
  `depth_mm` int DEFAULT NULL,
  `height_mm` int DEFAULT NULL,
  `room_type_codes` json NOT NULL,
  `feature_codes` json NOT NULL,
  `pet_friendly` bit(1) NOT NULL DEFAULT b'0',
  `child_friendly` bit(1) NOT NULL DEFAULT b'0',
  `easy_clean` bit(1) NOT NULL DEFAULT b'0',
  `scratch_resistant` bit(1) NOT NULL DEFAULT b'0',
  `movable` bit(1) NOT NULL DEFAULT b'0',
  `rental_friendly` bit(1) NOT NULL DEFAULT b'0',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sku` (`tenant_id`, `sku_id`),
  KEY `idx_tenant_category` (`tenant_id`, `category_code`),
  KEY `idx_dimensions` (`tenant_id`, `category_code`, `width_mm`, `depth_mm`, `height_mm`),
  KEY `idx_seat_count` (`tenant_id`, `category_code`, `seat_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Furniture SKU typed search projection';
```

Map JSON lists with `JacksonTypeHandler`, `@TableName(value = "product_furniture_sku_search", autoResultMap = true)`, and fields named exactly as the schema columns.

- [ ] **Step 4: Implement validated upsert and batch reads**

Use a controlled code set through `FurnitureProjectionValidator` and update the existing row by SKU ID:

```java
public final class FurnitureProjectionValidator {
    private static final Set<String> CATEGORIES = set(Arrays.asList(
            "sofa", "single-chair", "dining-table", "coffee-table", "bed", "desk",
            "bedroom-storage", "wardrobe", "side-table", "rug", "lighting", "media-storage"));
    private static final Set<String> STYLES = set(Arrays.asList(
            "modern", "cream-style", "natural", "light-luxury", "marble-look"));
    private static final Set<String> COLORS = set(Arrays.asList(
            "cream", "light-gray", "gray", "deep-brown", "dark", "black", "natural", "white"));
    private static final Set<String> MATERIALS = set(Arrays.asList(
            "fabric", "solid-wood", "engineered-wood", "metal", "glass", "leather", "marble-look", "wool"));
    private static final Set<String> ROOMS = set(Arrays.asList(
            "living-room", "dining-room", "bedroom", "children-room", "home-office", "rental-apartment"));
    private static final Set<String> FEATURES = set(Arrays.asList(
            "rounded-edges", "shallow-depth", "compact", "modular", "storage"));

    public static void validate(String category, List<String> styles, String color,
                                List<String> materials, Integer seats, Integer width,
                                Integer depth, Integer height, List<String> rooms,
                                List<String> features) {
        requireCode(CATEGORIES, category, "category");
        requireCodes(STYLES, styles, "styles");
        if (color != null) requireCode(COLORS, color, "color");
        requireCodes(MATERIALS, materials, "materials");
        requireCodes(ROOMS, rooms, "rooms");
        requireCodes(FEATURES, features, "features");
        requirePositive(seats, "seatCount");
        requirePositive(width, "widthMm");
        requirePositive(depth, "depthMm");
        requirePositive(height, "heightMm");
    }

    private static Set<String> set(Collection<String> values) {
        return Collections.unmodifiableSet(new HashSet<>(values));
    }

    private static void requireCodes(Set<String> allowed, List<String> values, String field) {
        if (values == null) return;
        for (String value : values) requireCode(allowed, value, field);
    }

    private static void requireCode(Set<String> allowed, String value, String field) {
        if (!allowed.contains(value)) throw new IllegalArgumentException("Unknown " + field + ": " + value);
    }

    private static void requirePositive(Integer value, String field) {
        if (value != null && value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    private FurnitureProjectionValidator() {
    }
}

@Override
@Transactional(rollbackFor = Exception.class)
public void upsert(FurnitureSkuSearchDO value) {
    ProductSkuDO sku = productSkuService.getSku(value.getSkuId());
    if (sku == null || !Objects.equals(sku.getSpuId(), value.getSpuId())) {
        throw new IllegalArgumentException("Furniture projection SKU/SPU mismatch");
    }
    FurnitureProjectionValidator.validate(value.getCategoryCode(), value.getStyleCodes(),
            value.getColorCode(), value.getMaterialCodes(), value.getSeatCount(),
            value.getWidthMm(), value.getDepthMm(), value.getHeightMm(),
            value.getRoomTypeCodes(), value.getFeatureCodes());
    FurnitureSkuSearchDO current = mapper.selectBySkuId(value.getSkuId());
    if (current == null) {
        mapper.insert(value);
    } else {
        value.setId(current.getId());
        mapper.updateById(value);
    }
}
```

- [ ] **Step 5: Run focused tests and commit**

Run the Task 1 Maven command again. Expected: PASS. Then commit:

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/product-furniture-sku-search.sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/dataobject/furniture' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/furniture' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/catalog' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/catalog'
git commit -m "feat: add furniture SKU search projection"
```

### Task 2: Structured requirement normalization and conversation merging

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureRequirementPatch.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureRequirementNormalizer.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureAssistantRequirements.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureAssistantRequirementMerger.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureRequirementNormalizerTest.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureAssistantRequirementMergerTest.java`

**Interfaces:**
- Produces: `FurnitureRequirementPatch FurnitureRequirementNormalizer.normalize(String message)`.
- `FurnitureRequirementPatch` produces `boolean mentions(String field)` and `void applyTo(FurnitureAssistantRequirements target)`.
- `FurnitureAssistantRequirements` adds `List<String> roomTypes`, `Integer roomWidthMm`, `Integer roomDepthMm`, `Integer maxWidthMm`, `Integer maxDepthMm`, `Integer maxHeightMm`, `List<String> excludedMaterials`, `Boolean easyClean`, `Boolean scratchResistant`, `Boolean movable`, `Boolean rentalFriendly`, `Set<String> hardConstraints`, and `Set<String> nonRelaxableConstraints`.
- Contract: dimensions are millimeters; prices remain yuan in conversation state; aliases normalize to controlled English codes.

- [ ] **Step 1: Write failing bilingual normalization tests**

```java
@Test
void normalize_shouldExtractChineseSofaConstraints() {
    FurnitureRequirementPatch value = normalizer.normalize(
            "我想买一张8000元以内、适合小客厅、宽度不超过220厘米的浅灰色三人布艺沙发。");
    assertEquals("sofa", value.getCategory());
    assertEquals(new BigDecimal("8000"), value.getBudgetMax());
    assertEquals(Integer.valueOf(2200), value.getMaxWidthMm());
    assertEquals(Integer.valueOf(3), value.getSeatCount());
    assertEquals(Collections.singletonList("light-gray"), value.getColors());
    assertEquals(Collections.singletonList("fabric"), value.getMaterials());
    assertTrue(value.getHardConstraints().containsAll(
            Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));
}

@Test
void normalize_shouldExtractEnglishRentalStorageConstraints() {
    FurnitureRequirementPatch value = normalizer.normalize(
            "I need movable bedroom storage under 150 cm wide for a rental apartment.");
    assertEquals("bedroom-storage", value.getCategory());
    assertEquals(Integer.valueOf(1500), value.getMaxWidthMm());
    assertEquals(Boolean.TRUE, value.getMovable());
    assertEquals(Boolean.TRUE, value.getRentalFriendly());
}
```

- [ ] **Step 2: Run tests and verify the normalizer is missing**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureRequirementNormalizerTest,FurnitureAssistantRequirementMergerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because `FurnitureRequirementPatch` and `FurnitureRequirementNormalizer` do not exist.

- [ ] **Step 3: Implement normalized patches with explicit field presence**

`FurnitureRequirementPatch` must distinguish “not mentioned” from “clear the old value” using a `Set<String> mentionedFields`. Use exact aliases for the acceptance data, including:

```java
private static final Map<String, String> MATERIAL_ALIASES = aliasMap(new String[][]{
        {"布艺", "fabric"}, {"布料", "fabric"}, {"fabric", "fabric"}, {"linen", "fabric"},
        {"实木", "solid-wood"}, {"solid wood", "solid-wood"},
        {"真皮", "leather"}, {"leather", "leather"},
        {"大理石纹", "marble-look"}, {"岩板", "marble-look"}, {"marble look", "marble-look"}
});
private static final Map<String, String> COLOR_ALIASES = aliasMap(new String[][]{
        {"米白", "cream"}, {"奶油色", "cream"}, {"cream", "cream"}, {"ivory", "cream"},
        {"浅灰", "light-gray"}, {"light gray", "light-gray"}, {"light grey", "light-gray"},
        {"深棕", "deep-brown"}, {"deep brown", "deep-brown"},
        {"深色", "dark"}, {"dark", "dark"}
});

private static Map<String, String> aliasMap(String[][] entries) {
    Map<String, String> value = new LinkedHashMap<>();
    for (String[] entry : entries) value.put(entry[0], entry[1]);
    return Collections.unmodifiableMap(value);
}
```

Convert `2.2米`, `220厘米`, `220 cm`, and `2200 mm` to `2200`. Mark budget, maximum dimensions, seat count, product-defining material, and explicit capability requirements hard. Add must/only/exclusion constraints to `nonRelaxableConstraints`; ordinary color/style remains preferred unless mandatory wording is present.

- [ ] **Step 4: Merge only mentioned fields and clear category-specific state**

Update the merger to consume the patch:

```java
public MergeResult merge(FurnitureAssistantConversation conversation, String message) {
    FurnitureRequirementPatch patch = normalizer.normalize(message);
    FurnitureAssistantRequirements target = conversation.getRequirements();
    if (patch.mentions("category") && !Objects.equals(target.getCategory(), patch.getCategory())) {
        target.setCategory(patch.getCategory());
        target.setSeatCount(null);
        target.setMaxWidthMm(null);
        target.setMaxDepthMm(null);
        target.setMaxHeightMm(null);
        target.getPreferredFeatures().clear();
    }
    patch.applyTo(target);
    applyOrdinalExclusion(conversation, message.toLowerCase(Locale.ROOT));
    return new MergeResult(false);
}
```

Add tests for preservation across turns, explicit replacement, material exclusion, category switching, rounded-edge child use, pet requirements, and retraction language.

- [ ] **Step 5: Run focused tests and commit**

Expected: both focused test classes PASS.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/conversation'
git commit -m "feat: normalize furniture Agent requirements"
```

### Task 3: Pure exact and partial SKU matcher

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/search/FurnitureMatchType.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/search/FurnitureProductCandidate.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/search/FurnitureCandidateMatch.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/search/FurnitureProductMatcher.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/search/FurnitureProductMatcherTest.java`

**Interfaces:**
- Consumes: `FurnitureAssistantRequirements`, a Mall price in fen, projection facts, and ERP sellable stock.
- Produces: ordered `FurnitureCandidateMatch` values with `matchType`, `matchedConstraints`, and `unmetConstraints`.
- `FurnitureProductCandidate` constructor is `(FurnitureSkuSearchDO projection, ProductSkuDO sku, ProductSpuDO spu, BigDecimal sellableStock, boolean erpMapped)`.
- `FurnitureCandidateMatch` exposes the candidate, match type, matched constraint names, unmet constraint names, and integer coverage score.
- Contract: the matcher is pure Java and performs no database or RPC access.

- [ ] **Step 1: Write failing exact, partial, and no-match tests**

```java
@Test
void match_shouldReturnExactBeforePartial() {
    FurnitureAssistantRequirements request = sofaRequest(8000, 2200, 3, "fabric");
    FurnitureProductCandidate exact = candidate(1001L, 2001L, 699900, 12,
            "sofa", "light-gray", "fabric", 3, 2180, true);
    FurnitureProductCandidate tooWide = candidate(1002L, 2002L, 659900, 20,
            "sofa", "light-gray", "fabric", 3, 2400, true);

    List<FurnitureCandidateMatch> result = matcher.match(request, Arrays.asList(tooWide, exact), 3);

    assertEquals(FurnitureMatchType.EXACT, result.get(0).getMatchType());
    assertEquals(Long.valueOf(2001L), result.get(0).getCandidate().getSku().getId());
    assertTrue(result.get(0).getUnmetConstraints().isEmpty());
}

@Test
void match_shouldDiscloseRelaxedWidth() {
    FurnitureAssistantRequirements request = sofaRequest(8000, 2200, 3, "fabric");
    FurnitureProductCandidate tooWide = candidate(1002L, 2002L, 659900, 20,
            "sofa", "light-gray", "fabric", 3, 2400, true);

    FurnitureCandidateMatch result = matcher.match(request, Collections.singletonList(tooWide), 3).get(0);

    assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
    assertEquals(Collections.singletonList("maxWidthMm"), result.getUnmetConstraints());
}

private FurnitureAssistantRequirements sofaRequest(int budget, int width, int seats, String material) {
    FurnitureAssistantRequirements value = new FurnitureAssistantRequirements();
    value.setCategory("sofa");
    value.setBudgetMax(BigDecimal.valueOf(budget));
    value.setMaxWidthMm(width);
    value.setSeatCount(seats);
    value.setMaterials(Collections.singletonList(material));
    value.setHardConstraints(new HashSet<>(
            Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));
    return value;
}

private FurnitureProductCandidate candidate(long spuId, long skuId, int priceFen, int stock,
                                             String category, String color, String material,
                                             int seats, int widthMm, boolean mapped) {
    FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
            .spuId(spuId).skuId(skuId).categoryCode(category).colorCode(color)
            .materialCodes(Collections.singletonList(material)).seatCount(seats)
            .widthMm(widthMm).build();
    ProductSkuDO sku = ProductSkuDO.builder().id(skuId).spuId(spuId).price(priceFen).build();
    ProductSpuDO spu = ProductSpuDO.builder().id(spuId).name("Candidate " + spuId).build();
    return new FurnitureProductCandidate(projection, sku, spu, BigDecimal.valueOf(stock), mapped);
}
```

Also test that category mismatch, explicit `leather` exclusion, non-relaxable requirements, zero stock, and unmapped candidates produce no result.

- [ ] **Step 2: Run the focused matcher test and verify missing classes**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureProductMatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the search package does not exist.

- [ ] **Step 3: Implement constraint evaluation and deterministic ranking**

Evaluate each named constraint once and retain its name:

```java
private ConstraintCheck maximum(String name, Integer requested, Integer actual) {
    if (requested == null) return ConstraintCheck.notRequested(name);
    return actual != null && actual <= requested
            ? ConstraintCheck.matched(name)
            : ConstraintCheck.unmet(name);
}

private boolean eligibleForPartial(FurnitureAssistantRequirements request,
                                   List<String> unmet) {
    return unmet.stream().noneMatch(request.getNonRelaxableConstraints()::contains);
}
```

Reject non-sellable candidates before scoring. Run an exact pass first; when empty, return eligible partial matches. Rank by descending total coverage, descending ERP stock, ascending Mall price, and ascending SKU ID. Preferred color, style, room type, and feature matches add coverage but never appear as unmet hard constraints.

- [ ] **Step 4: Run matcher tests and commit**

Expected: matcher tests PASS.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/search' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/search'
git commit -m "feat: rank exact and partial furniture SKU matches"
```

### Task 4: Batch Mall and ERP search orchestration

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchRequest.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchResult.java`
- Rewrite: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchTool.java`
- Rewrite: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchToolTest.java`

**Interfaces:**
- `FurnitureProductSearchRequest` contains `message`, `requirements`, `limit`, and `includeAllVariants`; `from(String message, FurnitureAssistantRequirements requirements, int limit)` creates it.
- `FurnitureProductSearchResult` exposes `matchType`, `matchedConstraints`, `unmetConstraints`, concrete product items, `of(FurnitureMatchType, List<String>, List<String>, List<Product>)`, `fromMatches(List<FurnitureCandidateMatch>)`, and `none()`.
- `FurnitureProductSearchTool.shouldSearchProducts(String message, FurnitureAssistantRequirements requirements, List<FurnitureAssistantKnowledgeMatch> knowledgeMatches)` recognizes product intent from normalized category or an explicit product/budget phrase.
- Consumes: Task 1 projection reads, Task 3 matcher, `ProductSkuService.getSkuList(Collection)`, `ProductSpuService.getSpuList(Collection)`, and `MallErpProductApi.validateSellableStock(List<MallErpStockRequestDTO>)`.

- [ ] **Step 1: Replace SPU-page mocks with SKU, projection, and ERP batch tests**

```java
@Test
void searchProducts_shouldReturnRealSkuPriceAndErpStock() {
    FurnitureSkuSearchDO projection = FurnitureSkuSearchDO.builder()
            .spuId(1001L).skuId(2001L).categoryCode("sofa")
            .colorCode("light-gray").materialCodes(Collections.singletonList("fabric"))
            .seatCount(3).widthMm(2180).build();
    when(projectionService.getByCategory("sofa")).thenReturn(Collections.singletonList(projection));
    when(productSkuService.getSkuList(Collections.singletonList(2001L)))
            .thenReturn(Collections.singletonList(ProductSkuDO.builder()
                    .id(2001L).spuId(1001L).price(699900).build()));
    when(productSpuService.getSpuList(Collections.singletonList(1001L)))
            .thenReturn(Collections.singletonList(ProductSpuDO.builder()
                    .id(1001L).name("Shallow Gray Fabric Sofa").status(1).build()));
    when(mallErpProductApi.validateSellableStock(anyList())).thenReturn(CommonResult.success(
            Collections.singletonList(new MallErpStockDTO().setMallSkuId(2001L)
                    .setSellableStock(new BigDecimal("12")).setAvailable(true))));

    FurnitureAssistantRequirements requirements = new FurnitureAssistantRequirements();
    requirements.setCategory("sofa");
    requirements.setBudgetMax(new BigDecimal("8000"));
    requirements.setMaxWidthMm(2200);
    requirements.setSeatCount(3);
    requirements.setMaterials(Collections.singletonList("fabric"));
    requirements.setColors(Collections.singletonList("light-gray"));
    requirements.setHardConstraints(new HashSet<>(
            Arrays.asList("budgetMax", "maxWidthMm", "seatCount", "materials")));
    FurnitureProductSearchResult result = tool.searchProducts(
            FurnitureProductSearchRequest.from("浅灰色三人布艺沙发", requirements, 3));

    assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
    assertEquals(Long.valueOf(1001L), result.getProducts().get(0).getId());
    assertEquals(Long.valueOf(2001L), result.getProducts().get(0).getSkuId());
    assertEquals(new BigDecimal("6999"), result.getProducts().get(0).getPrice());
    assertEquals(Integer.valueOf(12), result.getProducts().get(0).getStock());
}
```

Add tests for batch call count, disabled/deleted SKU exclusion, ERP failure fail-closed behavior, zero stock, partial match disclosure, deterministic order, and all variants belonging to one SPU.

- [ ] **Step 2: Run the focused search test and verify current SPU-only behavior fails**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureProductSearchToolTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the current tool queries `ProductSpuService.getSpuPage`, assigns `skuId = spuId`, and does not call ERP.

- [ ] **Step 3: Implement one batch pipeline**

Use this ordering in `searchProducts`:

```java
List<FurnitureSkuSearchDO> projections = projectionService.getByCategory(request.getRequirements().getCategory());
List<Long> skuIds = projections.stream().map(FurnitureSkuSearchDO::getSkuId).collect(toList());
Map<Long, ProductSkuDO> skus = convertMap(productSkuService.getSkuList(skuIds), ProductSkuDO::getId);
List<Long> spuIds = skus.values().stream().map(ProductSkuDO::getSpuId).distinct().collect(toList());
Map<Long, ProductSpuDO> spus = convertMap(productSpuService.getSpuList(spuIds), ProductSpuDO::getId);
List<MallErpStockRequestDTO> stockRequests = skuIds.stream()
        .map(id -> new MallErpStockRequestDTO().setMallSkuId(id).setCount(BigDecimal.ONE))
        .collect(toList());
CommonResult<List<MallErpStockDTO>> stockResponse = mallErpProductApi.validateSellableStock(stockRequests);
List<FurnitureProductCandidate> candidates = buildCandidates(projections, skus, spus, stockResponse);
return FurnitureProductSearchResult.fromMatches(
        matcher.match(request.getRequirements(), candidates, request.getLimit()));
```

Treat a non-success ERP response, null data, missing SKU stock row, `available != true`, or `sellableStock <= 0` as not sellable. Build prices only from `ProductSkuDO.price`; never use projection or stale SPU stock as the returned stock. Log each excluded candidate with one of the stable reason codes `SKU_MISSING`, `SPU_DISABLED`, `ERP_UNMAPPED`, `ERP_UNAVAILABLE`, or `ERP_ZERO_STOCK`.

The pure matcher evaluates budget, width, depth, height, seat count, materials, excluded materials, colors, styles, room types, feature codes, pet/child suitability, easy cleaning, scratch resistance, movability, and rental suitability. Room dimensions remain structured context unless the user supplied an explicit product-dimension maximum.

- [ ] **Step 4: Return real native SKU properties and grouped variants**

Map `ProductSkuDO.properties` to response labels. When `request.isIncludeAllVariants()` is true, restrict all returned variants to the selected SPU and expose each variant's `skuId`, properties, Mall price, and ERP stock. Never merge SKUs from different SPUs.

- [ ] **Step 5: Run focused tests and commit**

Expected: search tests PASS and Mockito verifies one projection query, one SKU batch read, one SPU batch read, and one ERP batch stock call.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchRequest.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchResult.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchTool.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureProductSearchToolTest.java'
git commit -m "feat: search ERP-backed furniture SKUs"
```

### Task 5: Agent response contract and typed search integration

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/furniture/vo/FurnitureAssistantChatRespVO.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantPromptBuilder.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantServiceImplTest.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantPromptBuilderTest.java`

**Interfaces:**
- Adds top-level `matchType`, `matchedConstraints`, and `unmetConstraints` to the existing response.
- Adds `List<String> skuProperties` and `List<SkuVariant> variants` to `FurnitureAssistantChatRespVO.Product` without removing existing fields. `SkuVariant` contains `skuId`, `skuProperties`, `price`, and `stock`.
- Consumes `FurnitureProductSearchRequest.from(String, FurnitureAssistantRequirements, int)` and the normalized-requirements product-intent method; removes the concatenated `buildSearchMessage` path.

- [ ] **Step 1: Write failing response and truthfulness tests**

```java
@Test
void chat_shouldExposePartialMatchWithoutClaimingMissingConstraint() {
    FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
    product.setId(1001L);
    product.setSkuId(2001L);
    product.setName("Solid-Wood Bed");
    FurnitureProductSearchResult search = FurnitureProductSearchResult.of(
            FurnitureMatchType.PARTIAL,
            Arrays.asList("category", "materials"),
            Collections.singletonList("maxWidthMm"),
            Collections.singletonList(product));
    when(productSearchTool.shouldSearchProducts(anyString(), anyList())).thenReturn(true);
    when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class))).thenReturn(search);

    FurnitureAssistantChatReqVO request = new FurnitureAssistantChatReqVO();
    request.setMessage("找宽度不超过180厘米的实木床");
    FurnitureAssistantChatRespVO result = service.chat(request);

    assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
    assertEquals(Collections.singletonList("maxWidthMm"), result.getUnmetConstraints());
    assertFalse(result.getAnswer().contains("宽度符合"));
    assertEquals(Long.valueOf(2001L), result.getProducts().get(0).getSkuId());
}
```

Add an exact case, a no-match case with no product cards, a model prompt assertion that includes matched/unmet facts, and a multi-SKU bed response whose variants all share one SPU.

- [ ] **Step 2: Run service and prompt tests and verify missing fields**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureAssistantServiceImplTest,FurnitureAssistantPromptBuilderTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the response contract and typed search integration do not exist.

- [ ] **Step 3: Integrate typed search and additive response fields**

Replace search-message concatenation with:

```java
FurnitureProductSearchRequest searchRequest = FurnitureProductSearchRequest.from(
        message, conversation.getRequirements(), 3);
boolean shouldSearchProducts = productSearchTool.shouldSearchProducts(
        message, conversation.getRequirements(), knowledgeMatches);
FurnitureProductSearchResult searchResult = shouldSearchProducts
        ? productSearchTool.searchProducts(searchRequest)
        : FurnitureProductSearchResult.none();
respVO.setMatchType(searchResult.getMatchType());
respVO.setMatchedConstraints(searchResult.getMatchedConstraints());
respVO.setUnmetConstraints(searchResult.getUnmetConstraints());
respVO.setProducts(searchResult.getProducts());
```

The deterministic answer and AI prompt must state unmet constraints for `PARTIAL`, return constraint-relaxation guidance for `NONE`, and forbid the model from converting an unmet constraint into a claim. Keep existing `conversationId`, `requirements`, `missingFields`, `answer`, `products`, and `sources` fields intact.

- [ ] **Step 4: Run focused tests and commit**

Expected: service and prompt tests PASS.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/furniture/vo/FurnitureAssistantChatRespVO.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantServiceImpl.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantPromptBuilder.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantServiceImplTest.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture/FurnitureAssistantPromptBuilderTest.java'
git commit -m "feat: expose truthful furniture match results"
```

### Task 6: Idempotent Mall catalog, native SKU properties, and projection seed

**Files:**
- Modify: `seed-furniture-agent-products.ps1`
- Modify: `audit-furniture-agent-products.ps1`
- Modify: `furniture web/tests/productSeedScript.test.js`

**Interfaces:**
- Produces: 26 useful furniture SPUs and 35 to 40 active SKUs for tenant `121`.
- Produces: native `product_property`/`product_property_value` data, non-empty `product_sku.properties`, and one projection per active SKU.
- Produces: deterministic stable-key upserts and two or more slider images per SPU.

- [ ] **Step 1: Strengthen the seed source tests before changing data**

```javascript
it("seeds production-compatible SKU facts and acceptance variants", () => {
  expect(seed).toContain("product_furniture_sku_search");
  expect(seed).toContain("product_property_value");
  expect(seed).toContain("ensureSkuVariant");
  expect(seed).toContain("agent-seed:");
  expect(seed).toContain("2099-12-31 23:59:59");
  ["Shallow Gray Three-Seat Sofa", "Pet-Friendly Easy-Clean Chair",
    "Six-Seat Marble-Look Dining Table", "Rounded Children's Desk",
    "Rounded Family Coffee Table", "Movable Rental Bedroom Storage"]
    .forEach((name) => expect(seed).toContain(name));
});
```

Update audit-source assertions to require `active_skus`, `empty_sku_properties`, `missing_projections`, and `acceptance_variant_gaps`. ERP mapping and stock counters belong to the cross-system audit in Task 7.

- [ ] **Step 2: Run focused Vitest and verify the stronger contract fails**

Run from `furniture web`:

```powershell
npm.cmd test -- productSeedScript.test.js
```

Expected: FAIL because the current seed creates one empty-property SKU per SPU and no search projections.

- [ ] **Step 3: Replace positional identity with stable seed keys**

Use a keyword token such as `agent-seed:sofa-shallow-gray-3` as the lookup key:

```java
private static Long findSeededSpu(Connection connection, String seedKey) throws Exception {
    return findId(connection,
            "select id from product_spu where tenant_id=? and keyword like ? and deleted=b'0'",
            TENANT_ID, "%agent-seed:" + seedKey + "%");
}
```

Upsert by seed key, not by result position. Preserve existing SPU/SKU IDs on rerun.

- [ ] **Step 4: Seed native properties, 35 to 40 SKUs, images, and projections**

Create controlled properties `Color`, `Material`, `Size`, `Seat Count`, `Bed Width`, and `Finish`. Each `ensureSkuVariant` call supplies native properties, price, image, and stock. Before its JDBC upsert, compile and invoke Task 1's dependency-free `FurnitureProjectionValidator` from the standalone Java seed, so seed and runtime writes share category/code/dimension validation. Upsert the projection with category, dimensions, multi-value JSON facts, and booleans. Include at least these verified variants:

```text
Shallow Gray Three-Seat Sofa: sofa, light-gray, fabric, 3 seats, 2180x880x820 mm, CNY 6,999
Pet-Friendly Easy-Clean Chair: single-chair, dark, fabric, pet-friendly, easy-clean, non-leather
Four-Seat Solid-Wood Dining Table: dining-table, solid-wood, 4 seats, explicit dimensions
Six-Seat Solid-Wood Dining Table: dining-table, solid-wood, 6 seats, explicit dimensions
Six-Seat Marble-Look Dining Table: dining-table, marble-look, 6 seats, at most CNY 7,000
Deep-Brown Solid-Wood Bed: bed, deep-brown, solid-wood, 1800 mm bed width
Multi-SKU Bed: one SPU with at least three real size/color SKUs
Rounded Children's Desk: desk, child-friendly, rounded-edges, at most CNY 3,000
Rounded Family Coffee Table: coffee-table, child-friendly, rounded-edges, at most CNY 4,000
Movable Rental Bedroom Storage: bedroom-storage, at most 1500 mm wide, movable, rental-friendly
Compact Modern Two-Seat Sofa: sofa, modern, 2 seats, compact dimensions
Cream Fabric Sofa: sofa, cream, fabric, explicit dimensions
```

Set every SPU gallery to two valid HTTPS images and set tenant expiry exactly as required.

- [ ] **Step 5: Expand the read-only audit and run local data checks**

The audit must require: 26 active seeded SPUs; 35 to 40 active seeded SKUs; 26 distinct covers; two or more slider images; valid price relationships; non-empty SKU property JSON; one projection per SKU; no tenant mismatch; and all acceptance variants present. It must remain read-only.

Apply the checked-in projection schema once before the first seed run:

```powershell
Get-Content -Raw 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/product-furniture-sku-search.sql' | docker compose -f 'yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml' exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p123456 ruoyi-vue-pro
```

Run:

```powershell
./seed-furniture-agent-products.ps1
./audit-furniture-agent-products.ps1
```

Expected: seed completes without duplicates and every audit counter representing an error is `0`.

- [ ] **Step 6: Prove idempotency and commit**

Run the seed and audit a second time. Expected: SPU/SKU/projection/property counts remain unchanged and all audits still pass. Then:

```powershell
git add -- 'seed-furniture-agent-products.ps1' 'audit-furniture-agent-products.ps1' 'furniture web/tests/productSeedScript.test.js'
git commit -m "feat: seed structured furniture SKU catalog"
```

### Task 7: ERP mappings, positive stock, and cross-system catalog audit

**Files:**
- Modify: `seed-mall-erp-products.ps1`
- Modify: `audit-mall-erp-integration.ps1`
- Modify: `furniture web/tests/mallErpSeed.test.js`

**Interfaces:**
- Consumes: every active tenant `121` Mall SKU from Task 6.
- Produces: exactly one valid Mall-to-ERP mapping and positive ERP warehouse stock for every active seeded SKU.
- Contract: counts are derived from the Mall SKU set and are not fixed at 26.

- [ ] **Step 1: Write failing dynamic-count and stock assertions**

```javascript
it("maps every active seeded SKU without a fixed legacy count", () => {
  expect(seed).toContain("activeMallSkuCount");
  expect(seed).toContain("mappedSkuCount");
  expect(seed).toContain("positiveStockSkuCount");
  expect(seed).not.toContain("$mallSkuCount -ne 26");
  expect(seed).toContain("mappedSkuCount -ne activeMallSkuCount");
  expect(seed).toContain("positiveStockSkuCount -ne activeMallSkuCount");
});
```

- [ ] **Step 2: Run the ERP seed test and verify the legacy fixed-count assertion fails**

```powershell
npm.cmd test -- mallErpSeed.test.js
```

Expected: FAIL because the current script requires exactly 26 Mall SKUs.

- [ ] **Step 3: Make ERP synchronization cover the full seeded SKU set**

Query active tenant `121` SKUs belonging to `creator='furniture-agent-seed'` SPUs, upsert ERP products and mappings by Mall SKU ID, and upsert positive warehouse stock. Derive success conditions from the active Mall SKU count:

```powershell
if ($activeMallSkuCount -lt 35 -or $activeMallSkuCount -gt 40) {
    throw "Expected 35 to 40 active furniture SKUs, got $activeMallSkuCount."
}
if ($mappedSkuCount -ne $activeMallSkuCount) {
    throw "ERP mapping count does not match active Mall SKU count."
}
if ($positiveStockSkuCount -ne $activeMallSkuCount) {
    throw "Positive ERP stock count does not match active Mall SKU count."
}
```

- [ ] **Step 4: Expand the read-only ERP audit and run it twice**

Audit active Mall SKUs, ERP products, unique mappings, orphan mappings, cross-tenant mappings, missing mappings, stock without warehouse, and non-positive stock. Run:

```powershell
./seed-mall-erp-products.ps1
./audit-mall-erp-integration.ps1
./seed-mall-erp-products.ps1
./audit-mall-erp-integration.ps1
```

Expected: counts remain stable; missing/orphan/cross-tenant/non-positive counters are `0`.

- [ ] **Step 5: Run focused Vitest and commit**

Expected: `mallErpSeed.test.js` PASS.

```powershell
git add -- 'seed-mall-erp-products.ps1' 'audit-mall-erp-integration.ps1' 'furniture web/tests/mallErpSeed.test.js'
git commit -m "feat: align structured furniture SKUs with ERP"
```

### Task 8: Comprehensive acceptance assertions and end-to-end verification

**Files:**
- Modify: `furniture web/tests/furnitureAgentAcceptanceDataset.test.js`
- Create: `furniture web/tests/furnitureAgentCatalogCoverage.test.js`
- Modify: `docs/testing/furniture-agent-comprehensive-manual.md`

**Interfaces:**
- Verifies: 40 scenarios and 110 turns remain intact.
- Verifies: product-search expectations map to seeded SKU facts rather than response-shape checks alone.
- Produces: exact commands and evidence for local API smoke testing.

- [ ] **Step 1: Add catalog-coverage contract tests**

Read the seed and acceptance fixture and require every structured product scenario to have a named catalog capability:

```javascript
const requiredCapabilities = [
  "sofa-light-gray-fabric-3-seat-max-2200-max-8000",
  "chair-dark-non-leather-pet-easy-clean",
  "dining-table-solid-wood-4-seat",
  "dining-table-solid-wood-6-seat",
  "dining-table-marble-look-6-seat-max-7000",
  "bed-deep-brown-solid-wood-1800",
  "bed-multi-sku",
  "desk-child-rounded-max-3000",
  "coffee-table-child-rounded-max-4000",
  "bedroom-storage-movable-rental-max-1500"
];
requiredCapabilities.forEach((capability) => expect(seed).toContain(capability));
expect(dataset.meta.scenarioCount).toBe(40);
expect(dataset.meta.userTurnCount).toBe(110);
```

- [ ] **Step 2: Run Web acceptance tests and fix only contract mismatches**

```powershell
npm.cmd test -- furnitureAgentAcceptanceDataset.test.js furnitureAgentCatalogCoverage.test.js productSeedScript.test.js mallErpSeed.test.js
```

Expected: PASS with 40 scenarios and 110 turns unchanged.

- [ ] **Step 3: Run backend regression tests**

From `yudao电商管理平台前后端/yudao-cloud`:

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -am "-Dtest=FurnitureSkuSearchServiceImplTest,FurnitureRequirementNormalizerTest,FurnitureAssistantRequirementMergerTest,FurnitureProductMatcherTest,FurnitureProductSearchToolTest,FurnitureAssistantServiceImplTest,FurnitureAssistantPromptBuilderTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all focused furniture tests PASS.

- [ ] **Step 4: Run full Web regression and build**

From `furniture web`:

```powershell
npm.cmd test
npm.cmd run build
```

Expected: all Vitest tests PASS and Vite production build exits `0`.

- [ ] **Step 5: Run database audits and Agent API smoke checks**

From repository root:

```powershell
./audit-furniture-agent-products.ps1
./audit-mall-erp-integration.ps1
```

Then exercise the running Agent with tenant `121` using the existing local authentication/header setup. Send the shallow-gray sofa, multi-SKU bed, marble-look table, and impossible-constraint prompts. Verify returned `spuId`/`skuId` resolve through the storefront detail endpoint, exact products satisfy stored facts, partial results enumerate unmet constraints, no-match responses contain no invented product card, and every returned SKU has positive ERP sellable stock. Record the exact curl or browser-network requests used in the manual document without committing credentials.

- [ ] **Step 6: Inspect final state and commit acceptance evidence**

```powershell
git diff --check
git status --short
git add -- 'furniture web/tests/furnitureAgentAcceptanceDataset.test.js' 'furniture web/tests/furnitureAgentCatalogCoverage.test.js' 'docs/testing/furniture-agent-comprehensive-manual.md'
git commit -m "test: verify structured furniture Agent catalog"
```

Expected: `git diff --check` is clean, only intended files are staged, and the commit succeeds.

### Task 9: Final branch verification and review handoff

**Files:**
- No production files expected.

**Interfaces:**
- Verifies the complete requirement → projection → Mall SKU → ERP stock → Agent response chain.

- [ ] **Step 1: Re-run the required verification set from a clean process state**

Run Task 8 backend tests, full Web tests/build, both read-only audits, and the four API smoke scenarios again. Expected: every command passes without depending on stale in-memory or browser state.

- [ ] **Step 2: Inspect commits and branch scope**

```powershell
git status --short --branch
git log --oneline --decorate origin/codex/agent-rag..HEAD
git diff --stat origin/codex/agent-rag HEAD
```

Expected: the branch contains only the approved design/plan and structured-catalog implementation commits; no credential, generated `.tmp` output, dependency directory, or unrelated file is included.

- [ ] **Step 3: Request code review before publishing**

Invoke `superpowers:requesting-code-review` against the complete branch diff. Resolve findings through `superpowers:receiving-code-review`, then repeat verification. Use `superpowers:verification-before-completion` before claiming completion and `superpowers:finishing-a-development-branch` for the final push/integration choice.
