# Product data lifecycle Phase 0 audit

- Generated at: 2026-08-14T06:31:58.681Z
- Tenant: `121`
- Database: `ruoyi-vue-pro`
- Repository migration: `V047__clean_orphan_mall_erp_mappings.sql`
- Database migration: `V046__customer_specification_fields.sql`
- Mode: read-only; no business data was modified.

## Summary

| Risk key | Findings | Returned records |
|---|---:|---:|
| `missing_p2_product_type` | 0 | 0 |
| `unknown_product_type` | 0 | 0 |
| `legacy_packing_shapes` | 0 | 0 |
| `unverified_default_finish` | 0 | 0 |
| `erp_mapping_integrity` | 26 | 26 |
| `furniture_projection_integrity` | 26 | 26 |
| `orphan_seo_records` | 0 | 0 |
| `erp_soft_delete_unique_key_risks` | 5 | 5 |

## SPUs missing a P2 Product type

- Key: `missing_p2_product_type`
- Requirement: PRD 9.1: list SPUs whose category is missing or is not an active child category.
- Findings: 0

### Finding breakdown

| Issue type | Count |
|---|---:|
| `missing_category` | 0 |
| `category_is_p1` | 0 |
| `missing_p1_parent` | 0 |

### Records

```json
[]
```

## Legacy, ambiguous, or unknown detailConfig.productType values

- Key: `unknown_product_type`
- Requirement: PRD 9.1/A-03: classify every non-canonical legacy Product type without guessing from names.
- Findings: 0

### Finding breakdown

| Issue type | Count |
|---|---:|
| `deterministic_legacy` | 0 |
| `ambiguous_manual_review` | 0 |
| `unknown_manual_review` | 0 |

### Records

```json
[]
```

## Legacy Packing JSON shapes

- Key: `legacy_packing_shapes`
- Requirement: PRD 9.1/B-01: list packingDisplay, object-shaped packing, and records containing both.
- Findings: 0

### Finding breakdown

| Issue type | Count |
|---|---:|
| `packing_display` | 0 |
| `packing_object` | 0 |
| `both_legacy_shapes` | 0 |

### Records

```json
[]
```

## Unverified Natural Oak Finish values

- Key: `unverified_default_finish`
- Requirement: PRD 9.1/B-02: list Finish values matching the former automatic default when provenance is unavailable.
- Findings: 0

### Finding breakdown

| Issue type | Count |
|---|---:|
| `unverified_default_finish` | 0 |

### Records

```json
[]
```

## ERP mapping integrity risks

- Key: `erp_mapping_integrity`
- Requirement: PRD 9.1/C: list unmapped SKUs, duplicate mappings, orphan mappings, and mappings to deleted ERP products.
- Findings: 26

### Finding breakdown

| Issue type | Count |
|---|---:|
| `unmapped_sku` | 26 |
| `duplicate_mall_sku_mapping` | 0 |
| `duplicate_erp_product_mapping` | 0 |
| `orphan_mapping_missing_sku` | 0 |
| `orphan_mapping_deleted_sku` | 0 |
| `orphan_mapping_missing_spu` | 0 |
| `orphan_mapping_deleted_spu` | 0 |
| `orphan_mapping_spu_mismatch` | 0 |
| `orphan_mapping_unknown` | 0 |
| `mapping_missing_erp_product` | 0 |
| `mapping_deleted_erp_product` | 0 |

### Records

```json
[
  {
    "skuId": 1,
    "spuId": 1,
    "spuName": "Cream Fabric Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 2,
    "spuId": 2,
    "spuName": "Cloud Modular Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 3,
    "spuId": 3,
    "spuName": "Leather Lounge Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 4,
    "spuId": 4,
    "spuName": "米白布艺沙发 Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 5,
    "spuId": 5,
    "spuName": "小户型沙发 Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 6,
    "spuId": 6,
    "spuName": "皮沙发 Sofa",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 7,
    "spuId": 7,
    "spuName": "餐桌 Dining Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 8,
    "spuId": 8,
    "spuName": "床 Bed",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 9,
    "spuId": 9,
    "spuName": "吊灯 Lighting",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 10,
    "spuId": 10,
    "spuName": "电视柜 Cabinet",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 11,
    "spuId": 11,
    "spuName": "黑色圆形餐桌 Dining Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 12,
    "spuId": 12,
    "spuName": "原木长餐桌 Dining Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 13,
    "spuId": 13,
    "spuName": "灰色软包餐椅 Chair",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 14,
    "spuId": 14,
    "spuName": "黑色现代餐椅 Chair",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 15,
    "spuId": 15,
    "spuName": "黑色玻璃茶几 Coffee Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 16,
    "spuId": 16,
    "spuName": "原木茶几 Coffee Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 17,
    "spuId": 17,
    "spuName": "棕色木质边几 Side Table",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 18,
    "spuId": 18,
    "spuName": "胡桃木书桌 Desk",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 19,
    "spuId": 19,
    "spuName": "米色羊毛地毯 Rug",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 20,
    "spuId": 20,
    "spuName": "灰色纹理地毯 Rug",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 21,
    "spuId": 21,
    "spuName": "木质床头柜 Nightstand",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 22,
    "spuId": 22,
    "spuName": "胡桃木斗柜 Dresser",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 23,
    "spuId": 23,
    "spuName": "原木衣柜 Wardrobe",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 24,
    "spuId": 24,
    "spuName": "白色球形台灯 Table Lamp",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 25,
    "spuId": 25,
    "spuName": "黑色落地灯 Floor Lamp",
    "issueType": "unmapped_sku"
  },
  {
    "skuId": 26,
    "spuId": 26,
    "spuName": "胡桃木餐边柜 Cabinet",
    "issueType": "unmapped_sku"
  }
]
```

## Furniture search projection integrity risks

- Key: `furniture_projection_integrity`
- Requirement: PRD 9.1/E: list missing SKU projections and orphan active projections.
- Findings: 26

### Finding breakdown

| Issue type | Count |
|---|---:|
| `missing_projection` | 26 |
| `orphan_projection_missing_sku` | 0 |
| `orphan_projection_deleted_sku` | 0 |
| `orphan_projection_missing_spu` | 0 |
| `orphan_projection_deleted_spu` | 0 |
| `orphan_projection_spu_mismatch` | 0 |
| `orphan_projection_unknown` | 0 |

### Records

```json
[
  {
    "skuId": 1,
    "spuId": 1,
    "spuName": "Cream Fabric Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 2,
    "spuId": 2,
    "spuName": "Cloud Modular Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 3,
    "spuId": 3,
    "spuName": "Leather Lounge Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 4,
    "spuId": 4,
    "spuName": "米白布艺沙发 Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 5,
    "spuId": 5,
    "spuName": "小户型沙发 Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 6,
    "spuId": 6,
    "spuName": "皮沙发 Sofa",
    "issueType": "missing_projection"
  },
  {
    "skuId": 7,
    "spuId": 7,
    "spuName": "餐桌 Dining Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 8,
    "spuId": 8,
    "spuName": "床 Bed",
    "issueType": "missing_projection"
  },
  {
    "skuId": 9,
    "spuId": 9,
    "spuName": "吊灯 Lighting",
    "issueType": "missing_projection"
  },
  {
    "skuId": 10,
    "spuId": 10,
    "spuName": "电视柜 Cabinet",
    "issueType": "missing_projection"
  },
  {
    "skuId": 11,
    "spuId": 11,
    "spuName": "黑色圆形餐桌 Dining Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 12,
    "spuId": 12,
    "spuName": "原木长餐桌 Dining Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 13,
    "spuId": 13,
    "spuName": "灰色软包餐椅 Chair",
    "issueType": "missing_projection"
  },
  {
    "skuId": 14,
    "spuId": 14,
    "spuName": "黑色现代餐椅 Chair",
    "issueType": "missing_projection"
  },
  {
    "skuId": 15,
    "spuId": 15,
    "spuName": "黑色玻璃茶几 Coffee Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 16,
    "spuId": 16,
    "spuName": "原木茶几 Coffee Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 17,
    "spuId": 17,
    "spuName": "棕色木质边几 Side Table",
    "issueType": "missing_projection"
  },
  {
    "skuId": 18,
    "spuId": 18,
    "spuName": "胡桃木书桌 Desk",
    "issueType": "missing_projection"
  },
  {
    "skuId": 19,
    "spuId": 19,
    "spuName": "米色羊毛地毯 Rug",
    "issueType": "missing_projection"
  },
  {
    "skuId": 20,
    "spuId": 20,
    "spuName": "灰色纹理地毯 Rug",
    "issueType": "missing_projection"
  },
  {
    "skuId": 21,
    "spuId": 21,
    "spuName": "木质床头柜 Nightstand",
    "issueType": "missing_projection"
  },
  {
    "skuId": 22,
    "spuId": 22,
    "spuName": "胡桃木斗柜 Dresser",
    "issueType": "missing_projection"
  },
  {
    "skuId": 23,
    "spuId": 23,
    "spuName": "原木衣柜 Wardrobe",
    "issueType": "missing_projection"
  },
  {
    "skuId": 24,
    "spuId": 24,
    "spuName": "白色球形台灯 Table Lamp",
    "issueType": "missing_projection"
  },
  {
    "skuId": 25,
    "spuId": 25,
    "spuName": "黑色落地灯 Floor Lamp",
    "issueType": "missing_projection"
  },
  {
    "skuId": 26,
    "spuId": 26,
    "spuName": "胡桃木餐边柜 Cabinet",
    "issueType": "missing_projection"
  }
]
```

## SEO records referencing missing products or categories

- Key: `orphan_seo_records`
- Requirement: PRD 9.1/F: list active SEO metadata whose PRODUCT or CATEGORY source entity does not exist.
- Findings: 0

### Finding breakdown

| Issue type | Count |
|---|---:|
| `missing_product` | 0 |
| `missing_category` | 0 |

### Records

```json
[]
```

## ERP soft-delete unique-key lifecycle risks

- Key: `erp_soft_delete_unique_key_risks`
- Requirement: PRD 9.1/G-03: expose legacy (business key, deleted) indexes and data that can collide on another delete/recreate cycle.
- Findings: 5

### Finding breakdown

| Issue type | Count |
|---|---:|
| `legacy_unique_index` | 5 |
| `active_and_deleted_history` | 0 |
| `deleted_history` | 0 |

### Records

```json
[
  {
    "issueType": "legacy_unique_index",
    "tableName": "erp_product",
    "activeCount": null,
    "businessKey": "uk_erp_product_tenant_bar_code_deleted",
    "deletedCount": null
  },
  {
    "issueType": "legacy_unique_index",
    "tableName": "erp_product_category",
    "activeCount": null,
    "businessKey": "uk_erp_product_category_tenant_code_deleted",
    "deletedCount": null
  },
  {
    "issueType": "legacy_unique_index",
    "tableName": "erp_product_unit",
    "activeCount": null,
    "businessKey": "uk_erp_product_unit_tenant_name_deleted",
    "deletedCount": null
  },
  {
    "issueType": "legacy_unique_index",
    "tableName": "erp_stock",
    "activeCount": null,
    "businessKey": "uk_erp_stock_tenant_product_warehouse_deleted",
    "deletedCount": null
  },
  {
    "issueType": "legacy_unique_index",
    "tableName": "erp_warehouse",
    "activeCount": null,
    "businessKey": "uk_erp_warehouse_tenant_name_deleted",
    "deletedCount": null
  }
]
```
