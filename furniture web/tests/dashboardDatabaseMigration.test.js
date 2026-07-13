import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const migration = readFileSync(
  new URL("../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql", import.meta.url),
  "utf8",
);
const backfill = readFileSync(
  new URL(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard-backfill.sql",
    import.meta.url,
  ),
  "utf8",
);

describe("dashboard database migration", () => {
  it("contains the revised schema, tasks, menu and permissions", () => {
    for (const token of [
      "hash_key_version",
      "traffic_quality",
      "exact_cost_item_count",
      "estimated_cost_item_count",
      "missing_cost_item_count",
      "known_cost_amount",
      "statistics_dashboard_hmac_day",
      "fk_behavior_event_hmac_day",
      "statistics_behavior_ingestion_gap",
      "event_source",
      "chk_statistics_behavior_event_source_type",
      "traffic_data_status",
      "FurnitureDashboard",
      "statistics:dashboard:query",
      "statistics:dashboard:profit-query",
      "statistics:dashboard:export",
      "statistics:dashboard:profit-export",
      "TODAY_AND_YESTERDAY",
      "FINALIZE_YESTERDAY",
      "ROLLING_7_COMPLETE_DAYS",
      "dashboardBehaviorCleanupJob",
      "productStatisticsJob",
    ]) {
      expect(migration).toContain(token);
    }
    expect(migration).toMatch(/`cost_amount` bigint DEFAULT NULL/i);
    expect(migration).toMatch(/`gross_profit` bigint DEFAULT NULL/i);
    expect(migration).toMatch(/`gross_margin_percent` decimal\(12,4\) DEFAULT NULL/i);
  });

  it("keeps the heavy cost backfill resumable and tenant bounded", () => {
    expect(backfill).toContain("@tenant_id");
    expect(backfill).toContain("@after_id");
    expect(backfill).toContain("@batch_size");
    expect(backfill).toContain("item.id > @after_id");
    expect(backfill).toContain("item.tenant_id = @tenant_id");
    expect(backfill).toContain("item.cost_price IS NULL");
  });
});
