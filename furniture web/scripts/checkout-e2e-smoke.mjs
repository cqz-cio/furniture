import { chromium } from "playwright";

const baseUrl = (process.env.CHECKOUT_E2E_BASE_URL || "http://127.0.0.1:5173").replace(/\/$/, "");
const checkoutUrl = `${baseUrl}/checkout?guest=true`;
const timeout = Number(process.env.CHECKOUT_E2E_TIMEOUT_MS || 30000);

const ok = (data) => ({
  code: 0,
  data,
  msg: "",
});

const json = (route, data) =>
  route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(ok(data)),
  });

const seedBrowserStorage = async (page) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      "YUDAO_APP_SESSION",
      JSON.stringify({
        userId: 1001,
        accessToken: "checkout-e2e-token",
        refreshToken: "checkout-e2e-refresh",
        expiresTime: "2099-01-01T00:00:00Z",
      }),
    );
    window.localStorage.setItem("YUDAO_APP_TOKEN", "checkout-e2e-token");
    window.localStorage.setItem(
      "furniture-web-cart",
      JSON.stringify([
        {
          id: 501,
          skuId: 5001,
          cartId: 7001,
          name: "E2E Cloud Sofa",
          subtitle: "Checkout smoke item",
          price: 2199,
          cover: "",
          quantity: 1,
          source: "yudao",
        },
      ]),
    );
  });
};

const installYudaoApiMocks = async (page, diagnostics = []) => {
  const recordRoute = (name, route, data) => {
    diagnostics.push(`[api] ${name}`);
    return json(route, data);
  };

  await page.route("**/app-api/member/address/get-default", (route) => recordRoute("member/address/get-default", route, null));
  await page.route("**/app-api/member/address/list", (route) => recordRoute("member/address/list", route, []));
  await page.route("**/app-api/member/address/verification-status", (route) =>
    recordRoute("member/address/verification-status", route, {
      provider: "remote-address-verification",
      fallbackActive: false,
    }),
  );
  await page.route("**/app-api/trade/cart/list", (route) =>
    recordRoute("trade/cart/list", route, {
      validList: [
        {
          id: 7001,
          count: 1,
          selected: true,
          spu: {
            id: 501,
            name: "E2E Cloud Sofa",
            introduction: "Checkout smoke item",
          },
          sku: {
            id: 5001,
            price: 219900,
            stock: 10,
          },
        },
      ],
      invalidList: [],
    }),
  );
  await page.route("**/app-api/trade/order/settlement?**", (route) =>
    recordRoute("trade/order/settlement", route, {
      price: {
        payPrice: 219900,
        totalPrice: 219900,
        deliveryPrice: 0,
      },
      items: [
        {
          skuId: 5001,
          count: 1,
          spuName: "E2E Cloud Sofa",
          payPrice: 219900,
        },
      ],
    }),
  );
  await page.route("**/app-api/member/address/verify", (route) =>
    recordRoute("member/address/verify", route, {
      source: "remote-address-verification",
      status: "verified",
      reason: "google-address-complete",
      requiresConfirmation: true,
      deliverable: true,
      providerStatus: "live",
      providerResponseId: "checkout-e2e-address-1",
      originalAddress: {
        street: "1 Market St",
        city: "San Francisco",
        state: "CA",
        postalCode: "94105",
      },
    }),
  );
  await page.route("**/app-api/member/address/create", (route) => recordRoute("member/address/create", route, { id: 8101 }));
  await page.route("**/app-api/trade/order/create", (route) =>
    recordRoute("trade/order/create", route, {
      id: 9101,
      orderId: 9101,
      payOrderId: 9201,
    }),
  );
  await page.route("**/app-api/pay/order/submit", (route) =>
    recordRoute("pay/order/submit", route, {
      displayMode: "url",
      displayContent: "/account/orders?id=9101&payOrderId=9201&status=waiting",
    }),
  );
  await page.route("**/app-api/trade/order/get-detail?**", (route) =>
    recordRoute("trade/order/get-detail", route, {
      id: 9101,
      no: "E2E-9101",
      status: 10,
      payStatus: false,
      payPrice: 219900,
      payOrderId: 9201,
      createTime: "2026-06-17T00:00:00Z",
      addressVerification: {
        source: "remote-address-verification",
        status: "verified",
        choice: "original",
        addressSource: "new",
        providerStatus: "live",
      },
      items: [
        {
          id: 1,
          skuId: 5001,
          spuName: "E2E Cloud Sofa",
          count: 1,
          price: 219900,
        },
      ],
    }),
  );
  await page.route("**/app-api/trade/order/page?**", (route) =>
    recordRoute("trade/order/page", route, {
      list: [],
      total: 0,
    }),
  );
};

const fillCheckoutAddress = async (page) => {
  await page.getByLabel(/first name/i).fill("Ada");
  await page.getByLabel(/last name/i).fill("Lovelace");
  await page.getByLabel(/street address/i).fill("1 Market St");
  await page.getByLabel(/city/i).fill("San Francisco");
  await page.getByLabel(/state/i).selectOption("CA");
  await page.getByLabel(/postal code/i).fill("94105");
  await page.getByLabel(/phone/i).fill("4155550134");
};

const fillPayment = async (page) => {
  await page.getByPlaceholder("1234 5678 9012 3456").fill("4111111111111111");
  await page.getByPlaceholder("MM/YY").fill("12/30");
  await page.getByPlaceholder("3 digits").fill("123");
  const agreements = page.locator(".rh-payment-agreements input");
  await agreements.nth(0).check();
  await agreements.nth(1).check();
};

const waitForPaymentStep = async (page, diagnostics = []) => {
  try {
    await page.getByRole("heading", { name: /^payment$/i }).waitFor();
  } catch (error) {
    const visibleText = await page.locator("body").innerText().catch(() => "");
    throw new Error(
      [
        "Checkout did not enter the payment step after address confirmation.",
        `Current URL: ${page.url()}`,
        diagnostics.length ? `Browser diagnostics:\n${diagnostics.join("\n")}` : "",
        visibleText.slice(0, 2000),
      ].filter(Boolean).join("\n\n"),
      { cause: error },
    );
  }
};

const waitForAddressReviewAction = async (addressReviewPanel) => {
  const useVerifiedButton = addressReviewPanel.getByRole("button", { name: /use verified address/i });
  await useVerifiedButton.waitFor({ state: "visible" });
  await useVerifiedButton.evaluate((button) =>
    new Promise((resolve, reject) => {
      const startedAt = Date.now();
      const check = () => {
        const hasVueClickInvoker = Reflect.ownKeys(button).some((key) => String(key).includes("_vei"));
        if (!button.disabled && hasVueClickInvoker) {
          resolve();
          return;
        }
        if (Date.now() - startedAt > 5000) {
          reject(new Error("Address review action did not become clickable"));
          return;
        }
        requestAnimationFrame(check);
      };
      check();
    }),
  );
  return useVerifiedButton;
};

const run = async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1200 } });
  const diagnostics = [];

  try {
    page.on("console", (message) => diagnostics.push(`[console:${message.type()}] ${message.text()}`));
    page.on("pageerror", (error) => diagnostics.push(`[pageerror] ${error.message}`));
    page.on("request", (request) => {
      const url = request.url();
      if (url.includes("/app-api/")) diagnostics.push(`[request] ${request.method()} ${url}`);
    });
    page.on("requestfailed", (request) => diagnostics.push(`[requestfailed] ${request.method()} ${request.url()} ${request.failure()?.errorText || ""}`));
    page.on("response", (response) => {
      const url = response.url();
      if (url.includes("/app-api/") && !response.ok()) diagnostics.push(`[response:${response.status()}] ${url}`);
    });
    page.setDefaultTimeout(timeout);
    await seedBrowserStorage(page);
    await installYudaoApiMocks(page, diagnostics);

    await page.goto(checkoutUrl, { waitUntil: "networkidle" });
    await fillCheckoutAddress(page);

    const continueButton = page.getByRole("button", { name: /continue to payment/i });
    await continueButton.click();

    await page.getByRole("dialog", { name: /address verification/i }).waitFor();
    const addressReviewPanel = page.locator(".rh-address-review-panel");
    const useVerifiedButton = await waitForAddressReviewAction(addressReviewPanel);
    const clickState = await useVerifiedButton.evaluate((button) => ({
      disabled: button.disabled,
      text: button.innerText,
      className: button.className,
    }));
    diagnostics.push(`[address-confirm-button] ${JSON.stringify(clickState)}`);
    await useVerifiedButton.click({ force: true });

    await waitForPaymentStep(page, diagnostics);
    const channelWarning = page.getByText(/payment channel/i);
    if (await channelWarning.isVisible().catch(() => false)) {
      throw new Error(
        "Checkout payment channel is not configured. Start Vite with VITE_YUDAO_PAY_CHANNEL_CODE set to a real test channel code.",
      );
    }

    await fillPayment(page);
    await page.getByRole("button", { name: /submit order/i }).click();
    await page.waitForURL("**/account/orders?id=9101**");
    await page.getByText(/E2E Cloud Sofa/i).first().waitFor();

    console.log("Checkout E2E smoke passed: /checkout -> address review -> payment submit -> /account/orders");
  } finally {
    await browser.close();
  }
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
