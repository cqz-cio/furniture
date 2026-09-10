import { auditSections, buildAuditQueries, assertReadOnlySql } from "../../furniture web/scripts/product-data-lifecycle-audit.mjs";

const result = {};
for (const phase of ["before", "after"]) {
  result[phase] = [121, 162].flatMap(tenantId => auditSections.map(section => {
    const effective = phase === "after" && section.findingsSqlWithCategoryCode
      ? { ...section, findingsSql: section.findingsSqlWithCategoryCode } : section;
    const q = buildAuditQueries(effective, { tenantId, limit: 500 });
    assertReadOnlySql(q.countSql);
    assertReadOnlySql(q.breakdownSql);
    return { tenant_id: tenantId, key: section.key, count_sql: q.countSql.trim().replace(/;$/, ""),
      breakdown_sql: q.breakdownSql.trim().replace(/;$/, "") };
  }));
}
process.stdout.write(JSON.stringify(result));
