import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("website inquiry ERP mail relay", () => {
  it("stores tenant configuration and idempotent delivery state", () => {
    const migration = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V037__website_inquiry_mail_relay.sql",
    );

    expect(migration).toContain("system_website_inquiry_mail_config");
    expect(migration).toContain("recipient_email");
    expect(migration).toContain("subject_template");
    expect(migration).toContain("content_template");
    expect(migration).toContain("system_website_inquiry_mail_delivery");
    expect(migration).toContain("next_retry_time");
    expect(migration).toContain("uk_website_inquiry_mail_delivery_inquiry");
  });

  it("routes Reply-To through the existing asynchronous mail sender", () => {
    const message = read(
      "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/mq/message/mail/MailSendMessage.java",
    );
    const sender = read(
      "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/service/mail/MailSendServiceImpl.java",
    );
    const inquiryService = read(
      "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/service/inquiry/WebsiteInquiryServiceImpl.java",
    );

    expect(message).toContain("replyToMails");
    expect(message).toContain("websiteInquiryDeliveryId");
    expect(sender).toContain("mail.setReply");
    expect(sender).toContain("sendPreparedMail");
    expect(inquiryService).toContain("ensureDeliveryAndSend");
  });

  it("uses an unambiguous mapper resource name so the full application context can start", () => {
    const relayService = read(
      "../../yudao电商管理平台前后端/yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/service/inquiry/mail/WebsiteInquiryMailServiceImpl.java",
    );

    expect(relayService).toContain(
      "private WebsiteInquiryMailConfigMapper websiteInquiryMailConfigMapper;",
    );
    expect(relayService).not.toContain("private WebsiteInquiryMailConfigMapper configMapper;");
  });

  it("exposes editable ERP settings and delivery status in the inquiry center", () => {
    const settings = read(
      "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/crm/clue/WebsiteInquiryMailSettings.vue",
    );
    const detail = read(
      "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/crm/clue/detail/InquiryMailDeliveryPanel.vue",
    );
    const inquiryList = read(
      "../../yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/crm/clue/index.vue",
    );

    expect(settings).toContain("绑定接收邮箱");
    expect(settings).toContain("邮件正文格式");
    expect(settings).toContain("sendWebsiteInquiryTestMail");
    expect(detail).toContain("ERP 绑定邮箱");
    expect(detail).toContain("客户回复邮箱");
    expect(inquiryList).toContain("邮件通知设置");
  });
});
