import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);

describe("trade program pages", () => {
  it("defines RH trade program routes and source-site aliases", () => {
    const app = readSource("../src/App.vue");

    expect(app).toContain('"trade-sign-in": "/trade/sign-in"');
    expect(app).toContain('"trade-application": "/trade/apply"');
    expect(app).toContain('"trade-faq": "/trade/faq"');
    expect(app).toContain('"/us/en/trade/membership-application": "trade-application"');
    expect(app).toContain('"/us/en/trade/faq": "trade-faq"');
  });

  it("adds the three RH trade pages and shared navigation", () => {
    ["TradeSignInPage.vue", "TradeApplicationPage.vue", "TradeFaqPage.vue"].forEach((fileName) => {
      expect(existsSync(pagePath(fileName)), `${fileName} should exist`).toBe(true);
      const source = readFileSync(pagePath(fileName), "utf8");
      expect(source).toContain("useI18n");
      expect(source).toContain('t("tradeProgram.');
      expect(source).toContain("TradeProgramNav");
    });
    expect(existsSync(new URL("../src/components/TradeProgramNav.vue", import.meta.url))).toBe(true);
  });

  it("connects the trade application page to the backend submit API", () => {
    const application = readSource("../src/pages/TradeApplicationPage.vue");

    expect(application).toContain("submitTradeApplication");
    expect(application).toContain("uploadTradeApplicationAttachment");
    expect(application).toContain("authorizedUsers");
    expect(application).toContain("businessDocuments");
    expect(application).toContain("taxDocuments");
    expect(application).toContain("uploadDocumentFiles");
    expect(application).toContain("openDocumentPicker");
    expect(application).toContain("handleDocumentFileChange");
    expect(application).toContain('event.target.value = ""');
    expect(application).toContain("openBusinessDocumentPicker");
    expect(application).toContain("handleBusinessDocumentFileChange");
    expect(application).toContain('@click="openBusinessDocumentPicker"');
    expect(application).toContain("target.value = selectedFiles.map((file) => ({");
    expect(application).toContain("await uploadAllDocuments()");
    expect(application).toContain("const result = await submitTradeApplication(buildPayload())");
    expect(application).toContain('t("tradeProgram.application.successNotice", { id: result?.id ?? "-" })');
    expect(application).toContain('t("tradeProgram.application.submitError")');
    expect(application).toContain('t("tradeProgram.application.uploadError")');
  });

  it("offers recovery actions after trade application upload or submit errors", () => {
    const application = readSource("../src/pages/TradeApplicationPage.vue");
    const styles = readSource("../src/styles.css");
    const i18n = readSource("../src/i18n.js");

    expect(application).toContain('errorAction = ref("")');
    expect(application).toContain('errorAction.value = "attachments"');
    expect(application).toContain('errorAction.value = "retry"');
    expect(application).toContain("trade-application-recovery");
    expect(application).toContain('href="#trade-application-documents"');
    expect(application).toContain('@click="submit"');
    expect(application).toContain('t("tradeProgram.application.fixAttachments")');
    expect(application).toContain('t("tradeProgram.application.retrySubmit")');
    expect(styles).toContain(".trade-application-recovery");
    expect(i18n).toContain("fixAttachments");
    expect(i18n).toContain("retrySubmit");
  });

  it("aligns the public trade application with admin review wiring", () => {
    const yudaoRoot = "../../yudao电商管理平台前后端";
    const authApi = readSource("../src/services/yudaoAuthApi.js");
    const adminApi = readSource(`${yudaoRoot}/yudao-ui-admin-vue3/src/api/member/trade/application/index.ts`);
    const adminView = readSource(`${yudaoRoot}/yudao-ui-admin-vue3/src/views/member/trade/application/index.vue`);
    const menuSql = readSource(`${yudaoRoot}/yudao-cloud/sql/mysql/member-trade-application.sql`);

    expect(authApi).toContain('/member/auth/trade-application');
    expect(adminApi).toContain('/member/trade-application/page');
    expect(adminApi).toContain('/member/trade-application/approve');
    expect(adminApi).toContain('/member/trade-application/reject');
    expect(adminView).toContain("MemberTradeApplication");
    expect(adminView).toContain("member:trade-application:review");
    expect(menuSql).toContain("member/trade/application/index");
    expect(menuSql).toContain("MemberTradeApplication");
    expect(menuSql).toContain("member:trade-application:query");
    expect(menuSql).toContain("member:trade-application:review");
  });
});
