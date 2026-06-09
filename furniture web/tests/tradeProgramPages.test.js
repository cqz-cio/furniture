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
    expect(application).toContain("authorizedUsers");
    expect(application).toContain("businessDocuments");
    expect(application).toContain("taxDocuments");
    expect(application).toContain('t("tradeProgram.application.successNotice")');
    expect(application).toContain('t("tradeProgram.application.submitError")');
  });
});
