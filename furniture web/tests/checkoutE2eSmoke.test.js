import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { buildYudaoAddressPayload, buildAddressVerificationAudit } from "../src/services/checkoutSession.js";
import { buildAddressConfirmationRecord } from "../src/services/usAddress.js";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const checkoutE2eScriptPath = new URL("../scripts/checkout-e2e-smoke.mjs", import.meta.url);

describe("checkout browser smoke test entry", () => {
  it("exposes a repeatable checkout E2E smoke command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:e2e:checkout"]).toBe("node scripts/checkout-e2e-smoke.mjs");
    expect(existsSync(checkoutE2eScriptPath)).toBe(true);
  });

  it("mocks the address verification, order create, and payment submit app-api calls", () => {
    const source = readFileSync(checkoutE2eScriptPath, "utf8");

    expect(source).toContain("**/app-api/member/address/verify");
    expect(source).toContain("**/app-api/trade/cart/list");
    expect(source).toContain("**/app-api/trade/order/create");
    expect(source).toContain("**/app-api/pay/order/submit");
    expect(source).toContain('source: "remote-address-verification"');
    expect(source).toContain("CHECKOUT_E2E_BASE_URL");
    expect(source).toContain("/checkout");
    expect(source).toContain("/account/orders");
    expect(source).toContain('page.locator(".rh-address-review-panel")');
    expect(source).toContain("waitForAddressReviewAction");
    expect(source).toContain("useVerifiedButton.click");
    expect(source).toContain("viewport");
    expect(source).toContain(".rh-payment-agreements input");
    expect(source).toContain('getByRole("heading", { name: /^payment$/i })');
    expect(source).toContain("getByText(/E2E Cloud Sofa/i).first()");
    expect(source).toContain("process.exit(1)");
  });

  it("uses address verification mock data that can produce a persisted checkout audit", () => {
    const confirmation = buildAddressConfirmationRecord(
      {
        source: "remote-address-verification",
        status: "verified",
        reason: "google-address-complete",
        deliverable: true,
        providerStatus: "live",
        providerResponseId: "checkout-e2e-address-1",
        originalAddress: {
          street: "1 Market St",
          city: "San Francisco",
          state: "CA",
          postalCode: "94105",
        },
      },
      "original",
      { addressSource: "new" },
    );

    expect(buildAddressVerificationAudit(confirmation)).toMatchObject({
      source: "remote-address-verification",
      status: "verified",
      choice: "original",
      addressSource: "new",
      selectedAddress: {
        street: "1 Market St",
        city: "San Francisco",
        state: "CA",
        postalCode: "94105",
      },
    });
    expect(
      buildYudaoAddressPayload(
        {
          firstName: "Ada",
          lastName: "Lovelace",
          street: "1 Market St",
          city: "San Francisco",
          state: "CA",
          postalCode: "94105",
          phone: "4155550134",
        },
        { addressConfirmation: confirmation },
      ).addressVerification,
    ).toBeTruthy();
  });
});
