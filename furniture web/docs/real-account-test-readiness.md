# Real Account Test Readiness

This checklist separates preview/demo behavior from flows that can be tested with a real Yudao member account.

## Readiness Levels

- `ready`: Uses a Yudao token, calls real App APIs, and can reload persisted data.
- `partial`: The API surface exists, but the current page state or test data is missing part of the live loop.
- `blocked`: The module is still demo/local-preview only, or it cannot prove persistence with a logged-in account.

## Core Gate

The live commerce path is ready only when all of these are true:

- A Yudao member token is present.
- Product data comes from the Yudao product API and is marked `source: "yudao"`.
- Each cart item has `source: "yudao"`, `spuId` or `id`, `skuId`, and `cartId`.
- `getCheckoutMode(items, token)` returns `yudao`.
- Demo, local, local-preview, and membership preview items cannot create a live order.

## Smoke Command

Run from `furniture web`:

```bash
npm run test:smoke:real-account -- --env-file .env.launch-smoke --check-order
```

Or include it in the unified launch gate:

```bash
npm run verify:launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --include-db-migrations --include-real-account-smoke --real-account-check-order
```

Required live smoke inputs:

- `VITE_YUDAO_APP_API_BASE` or `YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL`
- `VITE_YUDAO_APP_TENANT_ID` or `YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_TOKEN` or `YUDAO_SMOKE_TOKEN`
- `YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS`
- `YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE`
- `YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_USER_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE`
- `YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID`
- `YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL`
- `YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL`
- `YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID`
- `YUDAO_REAL_ACCOUNT_ADMIN_TOKEN`

The standalone `test:smoke:real-account` command enforces these seeded inputs before calling live APIs. Use `.env.launch-smoke` for the final gate instead of `.env.production`, unless the same `YUDAO_REAL_ACCOUNT_SMOKE_*` and admin variables are provided in the process environment.

Optional inputs:

- `YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID` can be provided to make the product detail SKU check explicit instead of falling back to `YUDAO_ORDER_SMOKE_SKU_ID`.
- `YUDAO_REAL_ACCOUNT_SMOKE_CART_ID` can be provided to make the cart row check explicit instead of falling back to `YUDAO_ORDER_SMOKE_CART_ID`.

The smoke is non-mutating by default. It reads product, cart, order page, profile, address, wishlist, membership, and gift registry App endpoints, then evaluates whether the account can create a live checkout from the current cart.
The App token must resolve to the same seeded `userId`, Gift Registry `publicCode`, and `tradeId`; otherwise the smoke fails instead of mixing data from another test account.
When `YUDAO_ORDER_SMOKE_CART_ID` / `YUDAO_ORDER_SMOKE_SKU_ID` or their `YUDAO_REAL_ACCOUNT_SMOKE_*` equivalents are provided, the cart list must contain that same seeded cart row before checkout readiness is counted.
The address list must contain `YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID` before Address Book readiness is counted.
The wishlist page must contain `YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID` / `YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID` before Wishlist readiness is counted.
Both the App membership profile and Admin membership page must match `YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS` / `YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE`.
The signed-in Gift Registry must include `YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID` / `YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID`.
It also reads Admin membership, Gift Registry list/detail, and Trade Application pages with the seeded `userId`, Gift Registry `publicCode`, and Trade application email, then verifies the returned rows match the smoke account's `userId`, registry `publicCode`, seeded registry item SPU/SKU, `tradeId`, and Trade application email.
If the App Gift Registry payload includes `userId`, it must match the seeded smoke `userId`; a matching `publicCode` alone is not enough to prove the registry belongs to the token owner.
Successful real-account smoke output includes both the module readiness snapshot and a `seededAccount` block with positive-integer `userId`, `cartId`, `skuId`, `addressId`, `orderId`, `giftRegistryItemSpuId`, and `giftRegistryItemSkuId`, plus `giftRegistryPublicCode`, `tradeId`, `membershipStatus`, `membershipPlanCode`, and a valid non-`example.com` `tradeEmail`; launch evidence audit rejects output that omits the `seededAccount` block, puts those identifiers only elsewhere in the log, uses placeholder values, uses invalid numeric ids, uses an invalid Trade email, or uses an `example.com` Trade email.
The initial launch readiness audit also compares those `seededAccount` values back to `.env.launch-smoke`, so final evidence cannot silently use a different seeded user, cart, SKU, address, order, membership status/plan, Gift Registry item, Gift Registry public code, or Trade application than the configured smoke account.
For the final `audit:initial-launch-readiness` gate, `.env.launch-smoke` must set `YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER=true`; this keeps the order-detail part of the real-account smoke evidence explicit instead of depending only on a command-line flag.
The same initial launch readiness audit compares `launch-manifest.json` `envFile`, `smokeEnvFile`, `backendEnvFile`, and `baseUrl` back to the command arguments, so launch evidence captured for one environment cannot be reused to sign off another environment.
Launch evidence audit also rejects real-account smoke output that contains any module snapshot with `"partial"` or `"blocked"`, even if a previous line in the same file contains a `"ready"` value for that module.
Launch evidence audit requires the captured `real-account-smoke.txt` output to include the `==> ...` step logs for the App product/cart/order/profile/address/wishlist/membership/Gift Registry checks and the Admin membership, Gift Registry list/detail, and Trade checks.
When the manifest runs `test:smoke:real-account` with `--check-order`, launch evidence audit requires the captured smoke output to include `==> order-detail`, proving the order-detail API was actually requested.
`verify:launch-smoke-env` also rejects invalid `YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL` values and `example.com` addresses unless `--allow-placeholders` is explicitly used for the checked-in example file.
`YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER` is required in the launch smoke env and must be an explicit `true` or `false` value, so the operator cannot accidentally leave the order-detail part of the real-account gate ambiguous.
Final launch evidence manifest commands must not include `--allow-placeholders`; real-account smoke evidence needs real seeded URLs and identifiers, not example-file placeholder allowances.
`test:smoke:real-account` performs the same direct guard before calling live APIs: App/Admin smoke URLs must not use `.example` or `example.com`, and the seeded Trade email must not use a documentation/example domain.
The seeded Trade email for direct real-account smoke must be a valid email address because it is used to query the Admin Trade Application page.
Direct real-account smoke App/Admin base URLs must be absolute `http(s)` URLs.
Direct real-account smoke values must also be real seeded values, not angle-bracket placeholders or `replace-me` markers.
Seeded numeric identifiers used by direct real-account smoke, such as tenant, user, SPU/SKU, cart, address, wishlist, registry item, and order ids, must be positive integers.
For real launch smoke files, App/Admin/return URLs must also use reachable non-documentation domains; `.example` and `example.com` hosts are accepted only when validating the checked-in example with `--allow-placeholders`.
The production storefront env applies the same principle for `VITE_YUDAO_APP_API_BASE`: real launch files cannot point at localhost or documentation/example domains.
The backend production env gate also rejects documentation/example domains for `YUDAO_DB_URL`, `YUDAO_REDIS_HOST`, Admin/App UI URLs, and pay notify callback URLs; only the checked-in example may use placeholders with `--allow-placeholders`.
The launch env alignment gate also rejects aligned-but-fake documentation domains across storefront, smoke, real-account smoke, admin, and pay callback URLs, so matching `.example` values cannot be used as launch evidence.
Trade Program readiness is evaluated from `member/user/get`: the signed-in smoke account must return a non-empty `tradeId` that was bound by the admin application approval flow.
Optional endpoints are not counted as ready when the request fails; skipped optional checks are marked unavailable instead of being treated as a successful `null` response. The smoke fails when any module snapshot is `partial` or `blocked`, so use a seeded account that has the required membership, registry, wishlist, order, cart, and trade state when running the final real-account gate.
Membership and Gift Registry endpoints must return a persisted record; a successful `null` response proves the API is reachable, but it does not prove the signed-in account can test those modules.
Orders/Billing, Address Book, and Wishlist must also return at least one persisted row for the final gate. Empty pages prove the API can load an empty state, but they do not prove a tester can verify reload persistence or account-owned data.

## Current Module Targets

- Product Catalog: ready when Yudao product page/detail returns real SPU/SKU identity.
- Cart: ready when remote cart rows preserve `spuId`, `skuId`, and `cartId`.
- Checkout: ready when the cart mode is `yudao`; local preview carts must stop before live order creation.
- Orders/Billing: ready when the signed-in user can read order pages and details.
- Account Profile/Address Book/Wishlist: ready when signed-in pages use Yudao APIs and surface auth/error/empty states.
- Membership: ready when status reads from the real membership API; purchase activation still needs the payment-success lifecycle.
- Gift Registry: ready for MVP management when signed-in CRUD works and registry cart/order items carry registry context. Minimal paid-order purchase quantity write-back is implemented; invitations, social sharing, and richer marketing lifecycle remain out of scope.
- Trade Program: ready only after an approved application is bound to `member_user.trade_id` and the signed-in storefront profile returns that `tradeId`.
