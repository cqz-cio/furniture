# Service Modules

This directory keeps storefront domain logic separate from backend integration details.

## Yudao API Modules

New application code should import from the domain module that owns the operation:

| Module | Use for |
| --- | --- |
| `yudaoAuthApi.js` | Sign-in, registration, captcha, trade application upload, logout |
| `yudaoCartApi.js` | Remote cart add, update, delete, and list operations |
| `yudaoMemberApi.js` | Member profile, addresses, mobile update, email verification, area tree |
| `yudaoOrderApi.js` | Checkout settlement, order creation, order list, order detail |
| `yudaoPaymentApi.js` | Yudao pay order submission and pay order lookup |
| `yudaoProductApi.js` | Product list and product detail requests |
| `yudaoRequest.js` | Shared request wrapper, tenant headers, token read/write, token refresh |
| `yudaoMappers.js` | Backend response mapping into storefront product, cart, address, profile, order models |

Use yudaoClient.js only for backwards compatibility with older imports. It should stay a thin facade that re-exports the modules above, not a place for new implementation.

Storefront Yudao requests read `VITE_YUDAO_APP_API_BASE` for the app-api base URL and `VITE_YUDAO_APP_TENANT_ID` for the `tenant-id` header. Keep `.env.example`, deployment variables, and `yudaoRequest.js` aligned so checkout, member address, order, and payment calls hit the intended Yudao tenant. Older `VITE_YUDAO_API_BASE_URL` and `VITE_YUDAO_TENANT_ID` names are still accepted as fallback aliases only to avoid breaking deployments that copied the earlier example.

## Local Storefront Modules

These modules are frontend-only helpers and should stay independent from Yudao request code:

| Module | Use for |
| --- | --- |
| `localCart.js` | Anonymous/local cart storage |
| `addressVerificationProvider.js` | Optional remote address verification provider that posts to a configured backend proxy path |
| `checkoutSession.js` | Checkout payload/session helpers |
| `checkoutPayment.js` | Checkout payment response normalization and Yudao payment payload helpers |
| `checkoutAddressConfirmation.js` | Buyer-confirmed shipping address summary shown before payment submission |
| `orderAddressVerification.js` | Order detail address verification audit summary and label-key helpers |
| `checkoutFlow.js` | Checkout step gating and buyer confirmation rules |
| `checkoutErrors.js` | Buyer-facing checkout error key mapping |
| `checkoutRecovery.js` | Checkout error recovery action mapping |
| `usAddress.js` | US checkout state/ZIP helpers, local/default address verification provider boundary, confirmation audit records |
| `../data/usPostalRegions.js` | Generated/importable ZIP to city/state seed data consumed by `usAddress.js` |
| `membershipCart.js` | Membership pricing and cart eligibility |
| `membershipAccount.js` | Membership account display models |
| `membershipNavigation.js` | Account, checkout, and membership route constants |
| `productDetailModel.js` | Product detail view model shaping |
| `giftRegistry.js` | Registry demo workflows |
| `tradeProgram.js` | Trade program form options and routes |
| `authSession.js` | Browser session persistence and redaction helpers |

## US Postal Region Data

Use `scripts/import-us-postal-regions.mjs` to convert a CSV export with ZIP, city, and state columns into `src/data/usPostalRegions.js`. Keep address verification orchestration in `usAddress.js`; replace the generated postal data file when a fuller or fresher source is selected. Future Google, USPS/CASS, or Smarty-style services should plug into `verifyUsCheckoutAddressWithProvider` so checkout keeps the same review and user-confirmation flow.

Local postal data is only a local ZIP, city, and state match. It is not a carrier deliverability confirmation and should not be presented as proof that a street address is real. Backend fallback verification with `backend-address-verification` is also only normalization, not street-level deliverability. Summaries from `checkoutAddressConfirmation.js`, `addressBookVerification.js`, and `orderAddressVerification.js` expose `sourceWarningKey` when the audit source is `local-postal-region` or `backend-address-verification`; checkout, saved-address, and order-detail UIs should render that warning alongside any unverified or provider-fallback warnings.

Manual US checkout addresses still need a Yudao `areaId` when they are saved into the member address book. `usAddress.js` defaults that compatibility value to `1`; set `VITE_YUDAO_US_DEFAULT_AREA_ID` per deployment when the Yudao area tree has a better US fallback node. This value is only the Yudao region mapping fallback, not proof that the buyer's street address is deliverable.

By default, checkout posts address verification requests to `/member/address/verify` on the configured Yudao app-api base and can read provider health from `/member/address/verification-status`. Override `VITE_ADDRESS_VERIFICATION_PATH` or `VITE_ADDRESS_VERIFICATION_STATUS_PATH` only when deploying different backend proxy paths, or pass an empty path in tests to disable the remote provider. Third-party API keys should stay on the backend.

Production address verification must be configured on the Yudao backend, not in the storefront bundle. For Google Address Validation, set `yudao.member.address-verification.google.api-key` in the backend runtime config and keep `yudao.member.address-verification.google.enable-usps-cass` enabled when USPS CASS processing is expected for US addresses. The backend Google client defaults to `yudao.member.address-verification.google.connect-timeout-millis=3000` and `yudao.member.address-verification.google.read-timeout-millis=5000` so checkout can degrade instead of hanging on a slow provider. When the Google key is missing or the remote provider fails, `/member/address/verification-status` reports fallback mode and checkout/admin screens show the buyer or operator confirmation warnings.

Production payment requires a real or sandbox Yudao pay channel. Set `VITE_YUDAO_PAY_CHANNEL_CODE` in the storefront build to the configured Yudao channel code, then verify order creation, payment submission, provider return, continue-payment, and status refresh against that channel before accepting live orders. Checkout payment submissions request displayMode: `url` and require an absolute `http` or `https` return URL before posting to Yudao, matching the backend payment contract and browser redirect flow. Checkout supports Yudao `url` display responses as redirects and Yudao `form` display responses as safe HTML form submissions.

When the buyer confirms the reviewed address, `checkoutSession.js` sends both the compact human-readable order remark and a structured `addressVerification` audit payload to `/trade/order/create`. The Yudao trade server stores that JSON on `trade_order.address_verification` and exposes it on App order detail responses; run `sql/mysql/trade-order-address-verification.sql` before using this payload on an existing database.

Checkout also saves the latest confirmed address verification audit on the member address book entry so saved-address screens can show whether an address has been reviewed before. Existing MySQL deployments must run `sql/mysql/member-address-address-verification.sql` before saving address verification metadata to `member_address.address_verification`.
