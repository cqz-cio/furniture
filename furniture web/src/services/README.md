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
| `yudaoProductApi.js` | Product list and product detail requests |
| `yudaoRequest.js` | Shared request wrapper, tenant headers, token read/write, token refresh |
| `yudaoMappers.js` | Backend response mapping into storefront product, cart, address, profile, order models |

Use yudaoClient.js only for backwards compatibility with older imports. It should stay a thin facade that re-exports the modules above, not a place for new implementation.

## Local Storefront Modules

These modules are frontend-only helpers and should stay independent from Yudao request code:

| Module | Use for |
| --- | --- |
| `localCart.js` | Anonymous/local cart storage |
| `checkoutSession.js` | Checkout payload/session helpers |
| `checkoutFlow.js` | Checkout step gating and buyer confirmation rules |
| `checkoutErrors.js` | Buyer-facing checkout error key mapping |
| `checkoutRecovery.js` | Checkout error recovery action mapping |
| `membershipCart.js` | Membership pricing and cart eligibility |
| `membershipAccount.js` | Membership account display models |
| `membershipNavigation.js` | Account, checkout, and membership route constants |
| `productDetailModel.js` | Product detail view model shaping |
| `giftRegistry.js` | Registry demo workflows |
| `tradeProgram.js` | Trade program form options and routes |
| `authSession.js` | Browser session persistence and redaction helpers |
