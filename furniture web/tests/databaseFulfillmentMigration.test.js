import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V015__trade_fulfillment_core.sql");
const trackingMappingMigrationPath = join(root, "migrations/V016__trade_tracking_status_mapping.sql");
const trackingWatermarkMigrationPath = join(root, "migrations/V017__trade_tracking_event_watermarks.sql");
const activeRecordUniquenessMigrationPath = join(root, "migrations/V018__trade_fulfillment_active_record_uniqueness.sql");

const activeRecordIndexes = {
  trade_carrier: { uk_carrier_code: ["tenant_id", "code"] },
  trade_logistics_provider: { uk_provider_code: ["tenant_id", "code"] },
  trade_shipment: { uk_shipment_no: ["tenant_id", "shipment_no"] },
  trade_shipment_item: { uk_shipment_item: ["tenant_id", "shipment_id", "order_item_id"] },
  trade_shipment_package: {
    uk_shipment_package_no: ["tenant_id", "shipment_id", "package_no"],
    uk_package_tracking: ["tenant_id", "carrier_id", "tracking_number"],
  },
  trade_shipment_leg: { uk_shipment_leg_sequence: ["tenant_id", "shipment_id", "sequence_no"] },
  trade_tracking_event: {
    uk_tracking_event_external: ["tenant_id", "provider_id", "external_event_id"],
    uk_tracking_event_hash: ["tenant_id", "provider_id", "event_hash"],
  },
  trade_order_fulfillment_summary: { uk_order_fulfillment_summary: ["tenant_id", "order_id"] },
  trade_fulfillment_idempotency: {
    uk_fulfillment_idempotency: ["tenant_id", "operation", "idempotency_key_hash"],
  },
  trade_fulfillment_outbox_event: { uk_fulfillment_outbox_event_id: ["tenant_id", "event_id"] },
  trade_tracking_status_mapping: {
    uk_tracking_status_mapping: [
      "tenant_id", "provider_code", "carrier_code", "provider_status_normalized", "mapping_version",
    ],
  },
};
const manualTrackingAuditMigrationPath = join(root, "migrations/V018__trade_manual_tracking_audit.sql");

describe("V015 trade fulfillment core migration", () => {
  it("creates the complete Phase 1 persistence contract", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const table of [
      "trade_carrier",
      "trade_logistics_provider",
      "trade_shipment",
      "trade_shipment_item",
      "trade_shipment_package",
      "trade_shipment_leg",
      "trade_tracking_event",
      "trade_order_fulfillment_summary",
      "trade_fulfillment_idempotency",
      "trade_fulfillment_outbox_event",
    ]) {
      expect(sql).toContain(`CREATE TABLE IF NOT EXISTS \`${table}\``);
    }
    expect(sql).toContain("CHECK (`origin_country` IN ('US','CA'))");
    expect(sql).toContain("CHECK (`destination_country` = `origin_country`)");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_external`");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_hash`");
    expect(sql).toContain("UNIQUE KEY `uk_fulfillment_idempotency`");
    expect(sql).not.toMatch(/api[_-]?key|secret\s+varchar|receiver_mobile/i);
  });

  it("keeps published migrations immutable and appends version 015", () => {
    const build = readFileSync(join(root, "build-oakved-baseline.mjs"), "utf8");
    expect(build).toContain("discoverMigrations");
    expect(migrationPath).toMatch(/V015__trade_fulfillment_core\.sql$/);
    const normalized = readFileSync(migrationPath, "utf8")
      .replace(/\r\n/g, "\n")
      .replace(/\s+$/, "") + "\n";
    expect(createHash("sha256").update(normalized).digest("hex"))
      .toBe("683687685b5b4943949d965f3b3df86eaa2e4dfcdbf50641fb4fc05db8d80ec4");
  });
});

describe("V017 deterministic tracking watermarks", () => {
  it("persists the complete ordering tuple on every tracked aggregate", () => {
    const sql = readFileSync(trackingWatermarkMigrationPath, "utf8");

    expect(sql.match(/ADD COLUMN `last_event_status_priority` int DEFAULT NULL/g)).toHaveLength(3);
    expect(sql.match(/ADD COLUMN `last_event_id` bigint DEFAULT NULL/g)).toHaveLength(3);
    expect(sql).toContain("ADD COLUMN `status_priority` int NOT NULL DEFAULT 0");
    expect(sql).toContain("ALTER TABLE `trade_shipment`");
    expect(sql).toContain("ALTER TABLE `trade_shipment_package`");
    expect(sql).toContain("ALTER TABLE `trade_shipment_leg`");
    expect(sql).toContain("MODIFY COLUMN `external_event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL");
    expect(sql).not.toContain("V016");
  });

  it("keeps the generated baseline V017 section byte-equivalent to the migration", () => {
    const migration = readFileSync(trackingWatermarkMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V017__trade_tracking_event_watermarks.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN V018__trade_fulfillment_active_record_uniqueness.sql", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});

describe("V018 manual tracking audit", () => {
  it("appends only the structured manual audit columns", () => {
    const sql = readFileSync(manualTrackingAuditMigrationPath, "utf8");

    expect(sql).toContain("ALTER TABLE `trade_tracking_event`");
    expect(sql).toContain("ADD COLUMN `manual_operator_id` bigint DEFAULT NULL");
    expect(sql).toContain("ADD COLUMN `manual_reason` varchar(500) DEFAULT NULL");
    expect(sql).toContain("ADD COLUMN `request_trace_id` varchar(64) DEFAULT NULL");
    expect(sql.match(/ADD COLUMN/g)).toHaveLength(3);
    expect(sql).not.toMatch(/api[_-]?key|secret|credential|tracking_number|raw_payload/i);
  });

  it("keeps V015 through V017 immutable and baseline V018 byte-equivalent", () => {
    const hashes = new Map([
      ["V015__trade_fulfillment_core.sql", "683687685b5b4943949d965f3b3df86eaa2e4dfcdbf50641fb4fc05db8d80ec4"],
      ["V016__trade_tracking_status_mapping.sql", "21dbb820f0e1099b73154bcc2d6011cdc1ea98556f580aaa0e5ccdd7ed7951da"],
      ["V017__trade_tracking_event_watermarks.sql", "4bddf6d0d0833138a45a6c4b52a6634e67a2798d66b7bf43cee8908a02bed46b"],
    ]);
    for (const [name, expected] of hashes) {
      const normalized = readFileSync(join(root, "migrations", name), "utf8")
        .replace(/\r\n/g, "\n")
        .replace(/\s+$/, "") + "\n";
      expect(createHash("sha256").update(normalized).digest("hex")).toBe(expected);
    }

    const migration = readFileSync(manualTrackingAuditMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V018__trade_manual_tracking_audit.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});

describe("V016 tracking status mapping migration", () => {
  it("adds versioned exact-carrier mappings and replayable event decisions", () => {
    const sql = readFileSync(trackingMappingMigrationPath, "utf8");

    expect(sql).toContain("CREATE TABLE IF NOT EXISTS `trade_tracking_status_mapping`");
    for (const column of [
      "`provider_code` varchar(32) NOT NULL",
      "`carrier_code` varchar(32) NOT NULL",
      "`provider_status_normalized` varchar(128) NOT NULL",
      "`standard_status` varchar(32) NOT NULL",
      "`mapping_version` varchar(32) NOT NULL",
      "`effective_at` datetime(6) NOT NULL",
    ]) {
      expect(sql).toContain(column);
    }
    expect(sql).toContain("UNIQUE KEY `uk_tracking_status_mapping`");
    expect(sql).toContain("KEY `idx_tracking_status_mapping_effective`");
    expect(sql).toContain("ADD COLUMN `provider_status_normalized`");
    expect(sql).toContain("ADD COLUMN `mapping_known`");
    expect(sql).toContain("ADD COLUMN `transition_decision`");
    expect(sql).toContain("MODIFY COLUMN `occurred_at` datetime(6) NOT NULL");
    expect(sql).toContain("MODIFY COLUMN `received_at` datetime(6) NOT NULL");
    expect(sql).toContain("MODIFY COLUMN `last_event_occurred_at` datetime(6) DEFAULT NULL");
    expect(sql.match(/ADD COLUMN `last_event_occurred_at` datetime\(6\) DEFAULT NULL/g)).toHaveLength(2);
    expect(sql).not.toMatch(/INSERT\s+INTO\s+`?trade_tracking_status_mapping`?/i);
  });

  it("keeps the generated baseline V016 section byte-equivalent to the migration", () => {
    const migration = readFileSync(trackingMappingMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V016__trade_tracking_status_mapping.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN V017__trade_tracking_event_watermarks.sql", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});

describe("V018 active-record uniqueness correction", () => {
  it("replaces every V015/V016 deleted-bit unique key with one nullable active marker per table", () => {
    const sql = readFileSync(activeRecordUniquenessMigrationPath, "utf8");

    for (const [table, indexes] of Object.entries(activeRecordIndexes)) {
      const alters = [...sql.matchAll(new RegExp("ALTER TABLE `" + table + "`([\\s\\S]*?);", "g"))];
      expect(alters, `${table} must have one coherent ALTER TABLE`).toHaveLength(1);
      expect(alters[0][1]).toMatch(
        /ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS \(CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END\) STORED/i,
      );
      for (const [index, businessColumns] of Object.entries(indexes)) {
        expect(alters[0][1]).toContain(`DROP INDEX \`${index}\``);
        const replacement = [...businessColumns, "active_record"].map((column) => `\`${column}\``).join(",");
        expect(alters[0][1]).toContain(`ADD UNIQUE KEY \`${index}\` (${replacement})`);
      }
    }
    expect(sql.match(/ALTER TABLE/g)).toHaveLength(Object.keys(activeRecordIndexes).length);
    expect(sql).not.toMatch(/ADD UNIQUE KEY[^;]+`deleted`/i);
  });

  it("models active-delete-recreate-delete-recreate semantics for every corrected business-key shape", () => {
    const sql = readFileSync(activeRecordUniquenessMigrationPath, "utf8");

    for (const [table, indexes] of Object.entries(activeRecordIndexes)) {
      for (const [index, expectedColumns] of Object.entries(indexes)) {
        const match = sql.match(new RegExp("ADD UNIQUE KEY `" + index + "` \\(([^)]+)\\)"));
        expect(match, `${table}.${index} replacement index`).not.toBeNull();
        const columns = match[1].match(/`([^`]+)`/g).map((column) => column.slice(1, -1));
        expect(columns).toEqual([...expectedColumns, "active_record"]);

        const activeKeys = new Set();
        const businessKey = JSON.stringify(expectedColumns.map((column, position) => `${column}-${position}`));
        const insertActive = () => {
          if (activeKeys.has(businessKey)) throw new Error("duplicate active business key");
          activeKeys.add(businessKey);
        };
        const softDelete = () => activeKeys.delete(businessKey); // generated active_record becomes NULL

        insertActive();
        expect(insertActive).toThrow("duplicate active business key");
        expect(softDelete()).toBe(true);
        insertActive();
        expect(softDelete()).toBe(true);
        insertActive();
        expect(activeKeys).toEqual(new Set([businessKey]));
      }
    }
  });

  it("keeps the generated baseline V018 section byte-equivalent to the migration", () => {
    const migration = readFileSync(activeRecordUniquenessMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V018__trade_fulfillment_active_record_uniqueness.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN V019__seo_foundation.sql", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});
