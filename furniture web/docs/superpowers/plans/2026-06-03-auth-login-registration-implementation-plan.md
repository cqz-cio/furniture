# Email Auth Login Registration Implementation Plan

> **Direction update:** The Furniture Web auth plan is email-first and email-only for customer login and registration. Do not plan or build mobile-number login, SMS-code login, or mobile-number registration for this project unless the product direction changes again.

## Goal

Build a real Yudao App API backed member session for Furniture Web using:

- Email + password sign in.
- Email + password account creation.
- Optional email secure-link request as a secondary recovery/help flow.
- Trade Program email + Trade ID sign in.
- Logout.
- Refresh token retry for protected commerce flows.
- Compatibility with the existing developer token panel during local integration.

This replaces the old manual-token-only path without introducing phone-number/SMS auth as a product flow.

## Architecture

Keep Furniture Web as a Vue/Vite frontend that talks only to Yudao App API. Centralize session persistence in `src/services/authSession.js`, centralize all Yudao App API calls and token refresh behavior in `src/services/yudaoClient.js`, and keep auth UI state in focused components wired through `RhHeader.vue` and `App.vue`.

The customer auth UI should use RH-style account patterns:

- Default state: email + password sign in.
- Create account: first name, last name, email, password, email opt-in, privacy acceptance.
- Forgot password / secure link: request secure link by email, but do not treat it as a completed login unless the backend later provides a real magic-link callback flow.
- Trade: Trade ID + email.
- Developer token panel: hidden behind `VITE_SHOW_AUTH_TOKEN_PANEL === "true"`.

## Non-Goals

- Do not create `AuthSmsForm.vue`.
- Do not create phone-number login or phone-number registration UI.
- Do not make SMS-code login the primary path.
- Do not use `sendMemberSmsCode` or `loginBySms` from UI.
- Do not introduce a phone-number password form.
- Do not modify Yudao Java backend unless explicitly approved.
- Do not call Yudao Admin API from Furniture Web.
- Do not store passwords, verification codes, real tokens, database credentials, or database connection strings in frontend code, docs, fixtures, logs, or screenshots.

## Current Backend Contract

Yudao App API base:

```text
http://127.0.0.1:48080/app-api
```

Local Yudao App API also requires a tenant header. Furniture Web should send `tenant-id: 1` by default for local development and allow overriding it with:

```powershell
$env:VITE_YUDAO_APP_TENANT_ID="1"
```

Primary endpoints for this plan:

- `POST /member/auth/email-login`
- `POST /member/auth/email-register`
- `POST /member/auth/email-secure-link`
- `POST /member/auth/trade-login`
- `POST /member/auth/refresh-token?refreshToken=<refreshToken>`
- `POST /member/auth/logout`

Protected commerce APIs must continue to send:

```http
Authorization: Bearer <accessToken>
```

## File Map

- `src/services/authSession.js`: session read/write/clear helpers, compatibility with legacy `YUDAO_APP_TOKEN`, redaction helpers.
- `src/services/yudaoClient.js`: email auth wrappers, request auth header, refresh-token retry, logout, commerce APIs.
- `src/components/AuthEmailSignInForm.vue`: email + password sign-in form, plus secure-link mode.
- `src/components/AuthCreateAccountForm.vue`: email account creation form.
- `src/components/AuthTradeSignInForm.vue`: Trade Program sign-in form.
- `src/components/AuthModal.vue`: owns auth mode, success/error/loading state, logged-in account view, logout, auth-change events.
- `src/components/AuthTokenPanel.vue`: developer token fallback routed through unified session helpers.
- `src/components/RhHeader.vue`: account icon opens `AuthModal`, forwards auth-change.
- `src/App.vue`: tracks auth session version and refreshes remote cart after login/logout.
- `src/pages/CheckoutPage.vue`: reloads auth-dependent checkout data when session changes.
- `src/pages/OrdersPage.vue`: reloads auth-dependent order data when session changes.
- `tests/authSession.test.js`: session storage compatibility and redaction tests.
- `tests/yudaoAuthClient.test.js`: email auth API payloads, refresh retry, logout cleanup.
- `tests/authUiStructure.test.js`: verifies email auth UI structure and rejects SMS/phone auth UI.
- `tests/authCommerceRefresh.test.js`: verifies auth-change commerce refresh wiring.

## Task 1: Session Storage

**Files:**

- `src/services/authSession.js`
- `tests/authSession.test.js`

Steps:

- [x] Store `userId`, `accessToken`, `refreshToken`, and `expiresTime` in `YUDAO_APP_SESSION`.
- [x] Keep compatibility with existing `YUDAO_APP_TOKEN`.
- [x] Clear both session keys on logout or invalid session.
- [x] Tolerate blocked or failing `localStorage`.
- [x] Redact token-like values for UI and tests.

Verification:

```powershell
cd "D:\code\furniture web"
npm.cmd test -- tests/authSession.test.js
```

## Task 2: Email Auth API Wrappers

**Files:**

- `src/services/yudaoClient.js`
- `tests/yudaoAuthClient.test.js`

Required wrappers:

- `loginByEmailPassword(payload, options)`
- `registerByEmail(payload, options)`
- `requestEmailSignInLink(email, options)`
- `loginByTradeAccount(payload, options)`
- `refreshMemberToken(refreshToken, options)`
- `logoutMember(options)`

Requirements:

- Email login posts to `/member/auth/email-login`.
- Email registration posts to `/member/auth/email-register`.
- Secure-link request posts to `/member/auth/email-secure-link`.
- Trade login posts to `/member/auth/trade-login`.
- Successful login/register/trade responses persist the unified session.
- Public auth requests must not send stale `Authorization` headers.
- Public auth requests must send the configured `tenant-id` header.
- Protected requests use the stored access token when no explicit token override is provided.
- Protected requests must keep sending the configured `tenant-id` header during refresh and retry.

Verification:

```powershell
npm.cmd test -- tests/yudaoAuthClient.test.js
```

## Task 3: Refresh Token Retry

**Files:**

- `src/services/yudaoClient.js`
- `tests/yudaoAuthClient.test.js`

Requirements:

- If a protected Yudao response indicates auth failure and a refresh token exists, call `/member/auth/refresh-token`.
- Persist the refreshed session.
- Retry the original request once with the new access token.
- Remove stale caller-provided `Authorization` headers during refresh/retry.
- If refresh fails, clear local session and surface the backend error.
- Never retry indefinitely.

Verification:

```powershell
npm.cmd test -- tests/yudaoAuthClient.test.js
```

## Task 4: Email Auth UI

**Files:**

- `src/components/AuthEmailSignInForm.vue`
- `src/components/AuthCreateAccountForm.vue`
- `src/components/AuthTradeSignInForm.vue`
- `src/components/AuthModal.vue`
- `src/components/RhHeader.vue`
- `src/styles.css`
- `tests/authUiStructure.test.js`

Required behavior:

- Default sign-in mode accepts email and password.
- Submit calls `loginByEmailPassword` and emits `authenticated` on success.
- Create Account calls `registerByEmail` and emits `authenticated` on success.
- Secure-link mode remains a secondary request flow and must clearly show that a link was requested; it must not fake a logged-in session.
- Trade mode calls `loginByTradeAccount` and emits `authenticated` on success.
- Logged-in mode shows account navigation and a sign-out action.
- Logout calls `logoutMember`, clears local session even when remote logout cannot be confirmed, and emits `auth-change`.
- Developer token panel remains hidden unless `VITE_SHOW_AUTH_TOKEN_PANEL === "true"`.
- UI tests should assert that `AuthSmsForm` and phone-number password forms are not used.

Important validation alignment:

- Backend email password validation is 4-16 characters.
- Frontend create-account and email sign-in validation should match backend password length constraints.
- Frontend should show general credential errors for login failure and avoid account enumeration copy.

Verification:

```powershell
npm.cmd test -- tests/authUiStructure.test.js
```

## Task 5: Commerce Refresh Wiring

**Files:**

- `src/App.vue`
- `src/pages/CheckoutPage.vue`
- `src/pages/OrdersPage.vue`
- `tests/authCommerceRefresh.test.js`

Requirements:

- `RhHeader` forwards `auth-change` from `AuthModal`.
- `App.vue` reloads remote cart after login/logout.
- `App.vue` increments `authVersion` after auth changes.
- `CheckoutPage.vue` reloads address and settlement data when `authVersion` changes.
- `OrdersPage.vue` reloads order data when `authVersion` changes.
- Remote cart failures fall back to the local cart without breaking local browsing.

Verification:

```powershell
npm.cmd test -- tests/authCommerceRefresh.test.js
```

## Task 6: Security and Boundary Verification

Run before considering the auth work ready:

```powershell
cd "D:\code\furniture web"
npm.cmd test
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

Search for unsafe additions:

```powershell
rg -n "jdbc:|mysql://|redis://|MYSQL_ROOT_PASSWORD|NACOS_PASSWORD|password=|accessToken|refreshToken" src tests docs/yudao-integration
```

Expected:

- No database connection strings in frontend source or tests.
- No database credentials in `VITE_*` variables.
- No real access tokens or refresh tokens.
- Test token values are obvious fake values.
- No passwords or verification codes in logs, fixtures, screenshots, or browser storage beyond user-entered password fields during form submission.

## Manual E2E Checklist

Backend available:

- [ ] Open homepage.
- [ ] Click account icon.
- [ ] Sign in with email + password.
- [ ] Network shows `/member/auth/email-login`.
- [ ] Local storage contains `YUDAO_APP_SESSION`.
- [ ] Header/account modal shows logged-in state.
- [ ] `/trade/cart/list` sends `Authorization`.
- [ ] Checkout reloads address/settlement data.
- [ ] Orders reloads current-user orders.
- [ ] Sign out clears `YUDAO_APP_SESSION` and `YUDAO_APP_TOKEN`.

Registration:

- [ ] Open Create Account.
- [ ] Submit first name, last name, email, password, privacy acceptance.
- [ ] Network shows `/member/auth/email-register`.
- [ ] Successful response creates a local session.
- [ ] Password validation matches backend 4-16 character constraint.

Secure link:

- [ ] Open secure-link flow.
- [ ] Submit email.
- [ ] Network shows `/member/auth/email-secure-link`.
- [ ] UI confirms link request.
- [ ] UI does not mark the user as logged in unless a future backend callback flow is completed.

Backend unavailable:

- [ ] Stop local backend on 48080.
- [ ] Email login shows auth service unavailable or a safe general error.
- [ ] Registration shows a safe general error.
- [ ] Local demo browsing still works.
- [ ] Local cart still works.
- [ ] Checkout and Orders show login/service-required states instead of fake success.

## Final Acceptance

Email auth is ready for review when:

- Email login creates a real Yudao member session.
- Email registration creates a real Yudao member session.
- Refresh-token retry works for protected commerce APIs.
- Logout clears local session even if backend logout fails.
- Remote cart, checkout, and orders react to auth changes.
- `npm.cmd test` passes.
- Vite build passes.
- Phase B harness passes.
- The plan and tests do not require phone-number login, SMS-code login, or phone-number registration.
- No frontend code or tests contain database credentials, real tokens, real passwords, real verification codes, or direct database clients.
