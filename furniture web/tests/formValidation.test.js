import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import { isEmailAddress, isPasswordInRange, isSixDigitCode } from "../src/services/formValidation.js";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("shared form validation helpers", () => {
  it("validates email, password, and verification code boundaries", () => {
    expect(isEmailAddress("member@example.com")).toBe(true);
    expect(isEmailAddress(" member@example.com ")).toBe(true);
    expect(isEmailAddress("bad-email")).toBe(false);
    expect(isEmailAddress(`${"a".repeat(250)}@example.com`)).toBe(false);

    expect(isPasswordInRange("1234")).toBe(true);
    expect(isPasswordInRange("123")).toBe(false);
    expect(isPasswordInRange("1".repeat(17))).toBe(false);

    expect(isSixDigitCode("123456")).toBe(true);
    expect(isSixDigitCode("12345")).toBe(false);
    expect(isSixDigitCode("abc123")).toBe(false);
  });

  it("uses shared helpers from auth and trade forms", () => {
    [
      "../src/components/AuthEmailSignInForm.vue",
      "../src/components/AuthCreateAccountForm.vue",
      "../src/components/AuthTradeSignInForm.vue",
      "../src/pages/TradeApplicationPage.vue",
    ].forEach((path) => {
      const source = readSource(path);
      expect(source).toContain("../services/formValidation.js");
      expect(source).not.toContain("/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test");
    });
  });
});
