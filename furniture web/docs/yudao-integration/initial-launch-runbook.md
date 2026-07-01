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
npm.cmd run create:launch-evidence -- --launch-env-file .env.production --launch-smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com --image-tag oakved-storefront:launch
```

This creates `launch-manifest.json`, a README, and placeholder files for each required command output under `launch-evidence/<timestamp>/`.

Do not proceed if the launch commit is not frozen or if database backup evidence is missing.

## 2. Prepare Production Configuration

Create `D:\code\furniture web\.env.production` from `.env.production.example`, then set real values:

```text
VITE_YUDAO_APP_API_BASE=https://api.example.com/app-api
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

Expected result: `Production env check passed: .env.production`.

Do not proceed if the production env file points to localhost, exposes the auth token panel, or lacks the payment channel code.

Create `D:\code\furniture web\.env.backend-production` from `.env.backend-production.example`, then set the real backend runtime values:

```text
SPRING_PROFILES_ACTIVE=prod
YUDAO_DB_URL=jdbc:mysql://mysql.example.com:3306/oakved?useSSL=true&serverTimezone=Asia/Shanghai
YUDAO_DB_USERNAME=<real-backend-db-user>
YUDAO_DB_PASSWORD=<real-backend-db-password>
YUDAO_REDIS_HOST=redis.example.com
YUDAO_REDIS_PORT=6379
YUDAO_ADMIN_UI_URL=https://admin.example.com
YUDAO_APP_UI_URL=https://shop.example.com
YUDAO_PAY_ORDER_NOTIFY_URL=https://api.example.com/admin-api/pay/notify/order
YUDAO_PAY_REFUND_NOTIFY_URL=https://api.example.com/admin-api/pay/notify/refund
YUDAO_PAY_TRANSFER_NOTIFY_URL=https://api.example.com/admin-api/pay/notify/transfer
YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY=<real-google-address-validation-key>
YUDAO_SECURITY_MOCK_ENABLE=false
```

Validate it:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:backend-production-env -- --env-file .env.backend-production
```

Expected result: `Backend production env check passed: .env.backend-production`.

Do not proceed if `SPRING_PROFILES_ACTIVE` is not `prod`, if database or Redis points to localhost, if the database user is `root`, if the password is the default development password, if payment callbacks are not HTTPS, or if mock login is enabled.

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
npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com
```

Expected result: `Launch env alignment check passed.`.

Do not proceed if the smoke runner points to a different API base than the storefront build, if tenant/payment channel values differ, if the backend app URL differs from the public storefront URL, or if payment notify URLs point to another API origin.

## 3. Apply Database Migrations

Apply these backend SQL migrations to the target database before deploying the frontend:

```text
D:\code\yudao...yudao-cloud\sql\mysql\product-favorite-sku-wishlist.sql
D:\code\yudao...yudao-cloud\sql\mysql\trade-order-address-verification.sql
D:\code\yudao...yudao-cloud\sql\mysql\member-address-address-verification.sql
```

Migration filenames that must be present in the launch evidence:

```text
product-favorite-sku-wishlist.sql
trade-order-address-verification.sql
member-address-address-verification.sql
```

After applying them, run the local migration gate:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:db-migrations
```

Expected result: `Database migration check passed: 3 file(s)`.

Do not proceed if any migration was skipped, manually edited in production, or applied to the wrong database.

## 4. Run Automated Readiness Gates

Run the automated launch gate with database, admin, and backend checks:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations --include-backend-prod-config --include-backend-prod-env --backend-env-file .env.backend-production --include-launch-env-alignment --base-url https://shop.example.com --include-admin-check --include-admin-build --include-backend-build
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
YUDAO_SMOKE_BASE_URL=https://api.example.com/app-api
YUDAO_SMOKE_TENANT_ID=121
YUDAO_SMOKE_TOKEN=<real-app-user-token>
YUDAO_ORDER_SMOKE_SKU_ID=<real-in-stock-sku-id>
YUDAO_ORDER_SMOKE_CART_ID=<real-cart-row-id-for-that-user>
YUDAO_ORDER_SMOKE_ADDRESS_ID=<real-saved-address-id-for-that-user>
YUDAO_ORDER_SMOKE_COUNT=1
YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE=<real-yudao-pay-channel-code>
YUDAO_ORDER_SMOKE_RETURN_ORIGIN=https://shop.example.com
YUDAO_ORDER_SMOKE_CREATE_ORDER=false
```

Validate the smoke env file:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-smoke-env -- --env-file .env.launch-smoke
```

Expected result: `Launch smoke env check passed: .env.launch-smoke`.

Run the live business smoke through the unified gate:

```powershell
cd "D:\code\furniture web"
npm.cmd run verify:launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --include-db-migrations --include-live-business-smoke --include-order-live-smoke
```

This performs:

- wishlist create
- wishlist page read
- wishlist count update
- wishlist delete
- order settlement only

Expected result: `Launch readiness check passed.`

To intentionally create a smoke order and submit a payment request, run this as a separate, logged action:

```powershell
cd "D:\code\furniture web"
npm.cmd run test:smoke:order-live -- --env-file .env.launch-smoke --create-order
```

Use `--create-order` only when the team is ready to create a real order record. Record the returned order id and pay order id.

Do not proceed if wishlist smoke leaves test rows behind, if order settlement does not reflect the backend price, or if the smoke user cannot see the created order in Yudao.

## 6. Build And Publish The Storefront Image

Build the production image from the frozen commit:

```powershell
cd "D:\code\furniture web"
docker build -t oakved-storefront:launch .
```

If your deployment tags images by commit SHA, also tag the image:

```powershell
docker tag oakved-storefront:launch registry.example.com/oakved-storefront:<commit-sha>
docker push registry.example.com/oakved-storefront:<commit-sha>
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
npm.cmd run test:deploy:health -- --base-url https://shop.example.com
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
- frozen commit SHA
- database backup record
- output from `npm.cmd run verify:production-env -- --env-file .env.production`
- output from `npm.cmd run verify:backend-production-env -- --env-file .env.backend-production`
- output from `npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com`
- output from `npm.cmd run verify:db-migrations`
- output from `npm.cmd run verify:backend-production-config`
- output from `npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations --include-backend-prod-config --include-backend-prod-env --backend-env-file .env.backend-production --include-launch-env-alignment --base-url https://shop.example.com --include-admin-check --include-admin-build --include-backend-build`
- output from the live business smoke command with `--include-live-business-smoke`
- output from the order smoke command with `--include-order-live-smoke`
- output from `npm.cmd run test:deploy:health -- --base-url https://shop.example.com`
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
npm.cmd run audit:initial-launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com --evidence-dir launch-evidence/<timestamp>
```

Expected result: `Initial launch readiness audit passed`. This is the local evidence gate for declaring the storefront ready for initial launch. If it fails, use the reported blockers as the remaining launch checklist.
