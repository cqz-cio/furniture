import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");
const cloud = "yudao电商管理平台前后端/yudao-cloud/";
const admin = "yudao电商管理平台前后端/yudao-ui-admin-vue3/";
const migrationPath = `${cloud}sql/mysql/migrations/V032__crm_inquiry_center.sql`;

describe("V032 CRM inquiry center", () => {
  it("stores every VANZ inquiry field and protects idempotency", () => {
    const migration = read(migrationPath);

    for (const column of [
      "external_inquiry_id",
      "contact_name",
      "company_name",
      "country_code",
      "inquiry_subject",
      "inquiry_message",
      "source_page",
      "locale",
      "utm_source",
      "utm_medium",
      "utm_campaign",
      "submitted_at",
      "process_status",
      "processed_at",
      "contact_id",
    ]) {
      expect(migration).toContain(`\`${column}\``);
    }
    expect(migration).toContain("uk_crm_clue_tenant_external");
    expect(migration).toContain("idx_crm_clue_tenant_process");
  });

  it("exposes only inquiry summary, customer records, and contacts", () => {
    const migration = read(migrationPath);
    const navigation = JSON.parse(
      read(
        `${cloud}yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json`,
      ),
    );

    expect(migration).toContain("WHEN 2397 THEN '询盘中心'");
    expect(migration).toContain("WHEN 2404 THEN '询盘汇总'");
    expect(migration).toContain("WHEN 2391 THEN '客户档案'");
    expect(migration).toContain("WHERE `id` IN (2391, 2404, 2416)");
    expect(migration).toContain("DELETE role_menu");
    expect(navigation.filter((path) => path.startsWith("/crm"))).toEqual([
      "/crm",
      "/crm/clue",
      "/crm/customer",
      "/crm/contact",
    ]);
  });

  it("persists before notifying and does not fail accepted inquiries on notification errors", () => {
    const service = read(
      `${cloud}yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/service/inquiry/WebsiteInquiryServiceImpl.java`,
    );
    const persistCall = service.indexOf("crmWebsiteInquiryApi.createWebsiteInquiry");
    const notifyCall = service.indexOf("notifySendService.sendSingleNotifyToAdmin");

    expect(persistCall).toBeGreaterThan(0);
    expect(notifyCall).toBeGreaterThan(persistCall);
    expect(service).toContain("catch (RuntimeException ex)");
    expect(service).toContain("return createResult.getInquiryId()");
  });

  it("converts a valid inquiry into a deduplicated customer and contact", () => {
    const service = read(
      `${cloud}yudao-module-crm/yudao-module-crm-server/src/main/java/cn/iocoder/yudao/module/crm/service/clue/CrmClueServiceImpl.java`,
    );

    expect(service).toContain("customerMapper.selectByCustomerName(companyName)");
    expect(service).toContain("selectFirstByCustomerIdAndEmail");
    expect(service).toContain("setCustomerId(customerId)");
    expect(service).toContain("setContactId(contactId)");
    expect(service).toContain("INQUIRY_COMPANY_NAME_REQUIRED");
  });

  it("renders inquiry statuses and customer history in the admin UI", () => {
    const inquiryList = read(`${admin}src/views/crm/clue/index.vue`);
    const customerDetail = read(`${admin}src/views/crm/customer/detail/index.vue`);
    const contactList = read(`${admin}src/views/crm/contact/index.vue`);

    expect(inquiryList).toContain("全部询盘");
    expect(inquiryList).toContain("生成客户档案");
    expect(inquiryList).toContain("无效询盘");
    expect(customerDetail).toContain('label="历史询盘"');
    expect(contactList).toContain("一个客户公司可以有多位联系人");
  });

  it("keeps the generated baseline section byte-equivalent to V032", () => {
    const migration =
      read(migrationPath).replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = read(`${cloud}sql/mysql/oakved-baseline.sql`).replace(
      /\r\n/g,
      "\n",
    );
    const marker = "-- BEGIN V032__crm_inquiry_center.sql\n";
    const start = baseline.indexOf(marker);
    const sectionStart = start + marker.length;
    const nextMarkerOffset = baseline
      .slice(sectionStart)
      .search(/\n-- BEGIN (?:V\d{3}__|Oakved demo catalog)/);
    const end =
      nextMarkerOffset < 0 ? -1 : sectionStart + nextMarkerOffset;

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(
      baseline.slice(sectionStart, end).replace(/\s+$/, "") + "\n",
    ).toBe(migration);
  });
});
