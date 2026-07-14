# Initial Launch Runbook

This runbook is for the first production-like launch of the Oakved storefront connected to the Yudao app-api backend. It is intentionally operational: follow it in order, record evidence, and stop when a gate fails.

## 1. Freeze The Launch Window

1. Pick the exact launch commit and deployment window.
2. Record the commit SHA:

```powershell
git rev-parse HEAD
```

3. Record the backend database snapshot name and backup location.
4. Confirm that no one is editing product, payment, tenant, or order configuration during the launch window.
5. Save the command output for every command in this runbook into the launch evidence folder.

Create the launch evidence bundle:

```powershell
cd "D:\code\furniture web"
npm.cmd run create:launch-evidence -- --launch-env-file .env.production --launch-smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com --image-tag oakved-storefront:launch
```

This creates `launch-manifest.json`, a README, and placeholder files for each required command output under `launch-evidence/<timestamp>/`.

Use the actual reachable production storefront URL for `--base-url`; the evidence generator rejects missing, relative, `.example`, and `example.com` documentation URLs. Do not proceed if the launch commit is not frozen or if database backup evidence is missing.

## 2. Prepare Production Configuration

Create `D:\code\furniture web\.env.production` from `.env.production.example`, then set real values:

```text
VITE_YUDAO_APP_API_BASE=https://api.oakvedhome.com/app-api
VITE_YUDAO_APP_TENANT_ID=121
VITE_YUDAO_US_DEFAULT_AREA_ID=100200
VITE_YUDAO_PAY_CHANNEL_CODE=<real-yudao-pay-channel-code>
VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify
VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status
VITE_SHOW_AUTH_TOKEN_PANEL=false
```

Validate it:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:production-env -- --env-file .env.production
```

Expected result: `Production env check passed: .env.production`. The storefront App API base must be a reachable production URL, not localhost and not a `.example` or `example.com` documentation domain.

Do not proceed if the production env file points to localhost, exposes the auth token panel, or lacks the payment channel code.

Create `D:\code\furniture web\.env.backend-production` from `.env.backend-production.example`, then set the real backend runtime values:

```text
SPRING_PROFILES_ACTIVE=prod
YUDAO_DB_URL=jdbc:mysql://mysql.oakvedhome.com:3306/oakved?useSSL=true&serverTimezone=Asia/Shanghai
YUDAO_DB_USERNAME=<real-backend-db-user>
YUDAO_DB_PASSWORD=<real-backend-db-password>
YUDAO_REDIS_HOST=redis.oakvedhome.com
YUDAO_REDIS_PORT=6379
YUDAO_ADMIN_UI_URL=https://admin.oakvedhome.com
YUDAO_APP_UI_URL=https://shop.oakvedhome.com
YUDAO_PAY_ORDER_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/order
YUDAO_PAY_REFUND_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/refund
YUDAO_PAY_TRANSFER_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/transfer
YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY=<real-google-address-validation-key>
YUDAO_SECURITY_MOCK_ENABLE=false
```

Validate it:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:backend-production-env -- --env-file .env.backend-production
```

Expected result: `Backend production env check passed: .env.backend-production`. Backend DB, Redis, Admin/App UI, and pay notify callback values must be reachable production values, not localhost and not `.example` or `example.com` documentation domains.

Do not proceed if `SPRING_PROFILES_ACTIVE` is not `prod`, if database or Redis points to localhost or a documentation/example domain, if the database user is `root`, if the password is the default development password, if payment callbacks are not HTTPS, or if mock login is enabled.

Validate the checked-in backend production profile before packaging:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:backend-production-config
```

Expected result: `Backend production config check passed.`.

Do not proceed if `application-prod.yaml` uses localhost database defaults, if mock login is enabled, or if Actuator exposes more than `health,info`.

Validate that the storefront env, smoke env, backend storefront URL, and payment callback origins all point to the same launch environment:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com
```

Expected result: `Launch env alignment check passed.`. This gate must fail if the environment is merely internally aligned while still using `.example` or `example.com` documentation domains.

Do not proceed if the smoke runner or real-account smoke points to a different API base than the storefront build, if tenant/payment channel values differ, if the backend app URL differs from the public storefront URL, or if payment notify URLs point to another API origin.

## 3. Apply Database Migrations

Apply these backend SQL migrations to the target database before deploying the frontend:

```text
D:\code\yudao...yudao-cloud\sql\mysql\product-favorite-sku-wishlist.sql
D:\code\yudao...yudao-cloud\sql\mysql\trade-order-address-verification.sql
D:\code\yudao...yudao-cloud\sql\mysql\member-address-address-verification.sql
D:\code\yudao...yudao-cloud\sql\mysql\member-trade-application.sql
D:\code\yudao...yudao-cloud\sql\mysql\member-membership.sql
D:\code\yudao...yudao-cloud\sql\mysql\member-gift-registry.sql
D:\code\yudao...yudao-cloud\sql\mysql\trade-gift-registry-context.sql
```

Migration filenames that must be present in the launch evidence:

```text
product-favorite-sku-wishlist.sql
trade-order-address-verification.sql
member-address-address-verification.sql
member-trade-application.sql
member-membership.sql
member-gift-registry.sql
trade-gift-registry-context.sql
```

After applying them, run the local migration gate:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:db-migrations
```

Expected result: `Database migration check passed: 7 file(s)`.

Do not proceed if any migration was skipped, manually edited in production, or applied to the wrong database.

## 4. Run Automated Readiness Gates

Run the automated launch gate with database, admin, and backend checks:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations --include-backend-prod-config --include-backend-prod-env --backend-env-file .env.backend-production --include-launch-env-alignment --base-url https://shop.oakvedhome.com --include-admin-check --include-admin-build --include-backend-build
```

This command covers:

- production env validation
- `npm audit --audit-level=low`
- full Vitest suite
- production Vite build
- DB migration file gate
- Yudao backend production configuration gate
- Yudao backend runtime env gate
- cross-file launch env alignment gate
- Yudao admin furniture-lite configuration gate
- Yudao admin production build
- Yudao backend server package build

Expected result: `Launch readiness check passed.`

Do not proceed if the test count changes unexpectedly without a known code change, if audit reports vulnerabilities, if any frontend/admin/backend build fails, if the backend production configuration or runtime env check fails, or if the admin furniture-lite check fails.

## 5. Run Live Business Smoke

Use a real Yudao App user token from the target environment. The user must own the smoke cart and saved address.

Create `D:\code\furniture web\.env.launch-smoke` from `.env.launch-smoke.example`, then set real values:

```text
YUDAO_SMOKE_BASE_URL=https://api.oakvedhome.com/app-api
YUDAO_SMOKE_TENANT_ID=121
YUDAO_SMOKE_TOKEN=<real-app-user-token>
YUDAO_ORDER_SMOKE_SKU_ID=<real-in-stock-sku-id>
YUDAO_ORDER_SMOKE_CART_ID=<real-cart-row-id-for-that-user>
YUDAO_ORDER_SMOKE_ADDRESS_ID=<real-saved-address-id-for-that-user>
YUDAO_ORDER_SMOKE_COUNT=1
YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE=<real-yudao-pay-channel-code>
YUDAO_ORDER_SMOKE_RETURN_ORIGIN=https://shop.oakvedhome.com
YUDAO_ORDER_SMOKE_CREATE_ORDER=false
YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL=https://api.oakvedhome.com/app-api
YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID=121
YUDAO_REAL_ACCOUNT_SMOKE_TOKEN=<real-app-user-token>
YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID=<real-spu-id-with-sku>
YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID=<real-saved-address-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID=<real-wishlist-spu-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID=<real-wishlist-sku-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS=<real-membership-status-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE=<real-membership-plan-code-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID=<real-registry-item-spu-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID=<real-registry-item-sku-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID=<real-order-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_USER_ID=<real-user-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE=<real-gift-registry-public-code-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID=<real-trade-id-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL=<real-trade-application-email-for-token-owner>
YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER=true
YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL=https://api.oakvedhome.com/admin-api
YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID=121
YUDAO_REAL_ACCOUNT_ADMIN_TOKEN=<real-admin-user-token>
```

Validate the smoke env file:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-smoke-env -- --env-file .env.launch-smoke
```

Expected result: `Launch smoke env check passed: .env.launch-smoke`. Real smoke URLs must not use `.example` or `example.com` documentation domains, and the Trade email must not use `example.com`.
`YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER` must be explicitly set to `true` or `false`; keep it `true` for final launch evidence and run the standalone smoke with `--check-order`.

Run the live business smoke through the unified gate:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --include-db-migrations --include-live-business-smoke --include-order-live-smoke --include-real-account-smoke --real-account-check-order
```

This performs:

- wishlist create
- wishlist page read
- wishlist count update
- wishlist delete
- order settlement only
- real account module readiness across product, cart, checkout, orders, billing, profile, address book, wishlist, membership, Gift Registry, and Trade Program
- admin-api visibility for the same seeded Membership, Gift Registry list/detail, and Trade Application records by user id, registry public code, registry item SPU/SKU, trade id, and Trade application email

Expected result: `Launch readiness check passed.`

To intentionally create a smoke order and submit a payment request, run this as a separate, logged action:

```powershell
cd "D:\code\furniture web"
npm.cmd run test:smoke:order-live -- --env-file .env.launch-smoke --create-order
```

Use `--create-order` only when the team is ready to create a real order record. Record the returned order id and pay order id.

Do not proceed if wishlist smoke leaves test rows behind, if order settlement does not reflect the backend price, if the smoke user cannot see the created order in Yudao, or if the real-account smoke reports any module as `partial` or `blocked`.

## 6. Build And Publish The Storefront Image

Build the production image from the frozen commit:

```powershell
cd "D:\code\furniture web"
docker build -t oakved-storefront:launch .
```

If your deployment tags images by commit SHA, also tag the image:

```powershell
docker tag oakved-storefront:launch registry.oakvedhome.com/oakved-storefront:<commit-sha>
docker push registry.oakvedhome.com/oakved-storefront:<commit-sha>
```

Record the image tag:

```text
oakved-storefront:<commit-sha-or-launch-tag>
```

Record the image digest from the registry after push. Do not deploy an image that was not built from the frozen commit.

## 7. Post-Deploy Verification

After deploying the image behind Nginx or the target ingress, verify:

Run the automated deployed-site health check:

```powershell
cd "D:\code\furniture web"
npm.cmd run test:deploy:health -- --base-url https://shop.oakvedhome.com
```

Expected result: `Post-deploy health check passed`.

This checks the public app shell, critical page routes, SPA fallback, asset cache headers, security headers, and compressed immutable assets.

1. Public storefront opens over HTTPS.
2. `index.html` is not long-cacheable.
3. `/assets/*` files are long-cacheable and gzip-compressed.
4. Language switch works.
5. Product listing reads live Yudao product data.
6. PDP can add a live SKU to cart for a logged-in user.
7. Wishlist add, page read, count update, and delete work for the smoke user.
8. Checkout settlement returns backend price and delivery totals.
9. Order create and payment submit work in the agreed sandbox or real-payment mode.
10. Yudao admin can find the smoke order and pay order.

Save the command output and browser screenshots for the smoke account. Do not proceed to public traffic if any item fails.

## 8. Rollback Criteria

Rollback immediately if any of these happen:

- storefront cannot load for more than 5 minutes
- login or token refresh fails for real users
- product prices do not match Yudao backend settlement
- order creation fails for valid carts
- payment submit creates duplicate or invalid pay orders
- payment callback success cannot be reconciled in Yudao
- admin cannot find or process new orders
- database migration causes unexpected errors in product favorite, trade order, or member address flows

Rollback action:

1. Repoint the frontend deployment to the previous image tag.
2. Keep the migrated database in place unless the backend team confirms a database rollback is required.
3. Disable traffic to the new storefront route if order or payment correctness is uncertain.
4. Record the incident time, image tag, commit SHA, failed command output, and rollback operator.

Do not proceed with another launch attempt until the failing gate has a reproducible fix and fresh evidence.

## Evidence Checklist

Before declaring the initial launch ready, the launch folder must contain:

- `launch-manifest.json`
- `launch-manifest.json` `baseUrl` must be the reachable production storefront URL, not `.example` or `example.com`
- frozen commit SHA
- database backup record
- output from `npm.cmd run verify:production-env -- --env-file .env.production`
- output from `npm.cmd run verify:backend-production-env -- --env-file .env.backend-production`
- output from `npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com`
- output from `npm.cmd run verify:db-migrations`
- output from `npm.cmd run verify:backend-production-config`
- output from `npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations --include-backend-prod-config --include-backend-prod-env --backend-env-file .env.backend-production --include-launch-env-alignment --base-url https://shop.oakvedhome.com --include-admin-check --include-admin-build --include-backend-build`
- output from the live business smoke command with `--include-live-business-smoke`
- output from the order smoke command with `--include-order-live-smoke`
- output from the standalone real account smoke command: `npm.cmd run test:smoke:real-account -- --env-file .env.launch-smoke --check-order`
- `launch-manifest.json` `requiredEvidenceFiles` must include `real-account-smoke.txt`, and its `real-account-smoke` command must include `--check-order`
- `launch-manifest.json` every command `outputFile` must be listed in `requiredEvidenceFiles`
- `launch-manifest.json` `envFile`, `smokeEnvFile`, `backendEnvFile`, and `baseUrl` must match the final `audit:initial-launch-readiness` arguments
- `launch-manifest.json` commands must not include `--allow-placeholders`; that flag is only for validating checked-in example env files, not final launch evidence
- `real-account-smoke.txt` must include the `==> ...` step logs for product, cart, order page, profile, address, wishlist, membership, Gift Registry, Admin membership, Admin Gift Registry list/detail, and Admin Trade checks
- `real-account-smoke.txt` must include the JSON module snapshot with every module set to `ready`
- `real-account-smoke.txt` must include `==> order-detail` when the standalone command uses `--check-order`
- `real-account-smoke.txt` must include the `seededAccount` JSON block with positive-integer `userId`, `cartId`, `skuId`, `addressId`, `orderId`, `giftRegistryItemSpuId`, `giftRegistryItemSkuId`, plus `giftRegistryPublicCode`, `tradeId`, `membershipStatus`, `membershipPlanCode`, and a valid non-`example.com` `tradeEmail`; these identifiers must be inside `seededAccount`, not only elsewhere in the log
- The `seededAccount` values in `real-account-smoke.txt` must match the corresponding `.env.launch-smoke` `YUDAO_REAL_ACCOUNT_SMOKE_*` values for user, membership, registry, Trade, address, and order data, with cart/SKU falling back to `YUDAO_ORDER_SMOKE_CART_ID` / `YUDAO_ORDER_SMOKE_SKU_ID` when dedicated real-account cart/SKU values are not set
- `.env.launch-smoke` `YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER` must be `true` for the final `audit:initial-launch-readiness` gate
- `real-account-smoke.txt` `seededAccount` values must be real seeded account values, not placeholders such as `<real-...>` or `replace-me`
- `real-account-smoke.txt` must not include `Optional readiness step skipped`, `Real account readiness failed`, `"partial"`, `"blocked"`, or mixed output from a previous failed run
- output from `npm.cmd run test:deploy:health -- --base-url https://shop.oakvedhome.com`
- image tag and image digest
- post-deploy browser screenshots
- smoke order id and pay order id if `--create-order` was used

After all evidence files are filled, run:

```powershell
cd "D:\code\furniture web"
npm.cmd run audit:launch-evidence -- --dir launch-evidence/<timestamp>
```

Expected result: `Launch evidence audit passed`. Do not declare initial launch readiness while this audit reports missing metadata, missing files, or placeholder text.

Finally, run the full initial launch readiness audit:

```powershell
cd "D:\code\furniture web"
npm.cmd run audit:initial-launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com --evidence-dir launch-evidence/<timestamp>
```

Expected result: `Initial launch readiness audit passed`. This is the local evidence gate for declaring the storefront ready for initial launch. If it fails, use the reported blockers as the remaining launch checklist.
