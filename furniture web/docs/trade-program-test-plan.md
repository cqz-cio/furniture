# Trade Program test and integration notes

## Frontend entry points

- Trade sign in: `/trade/sign-in`
- Trade application: `/trade/apply`
- Trade FAQ: `/trade/faq`
- RH-compatible aliases:
  - `/us/en/trade/login`
  - `/us/en/trade/membership-application`
  - `/us/en/trade/faq`

The pages use the existing locale switcher and include English, Chinese, and French copy.

## App API flow

1. Submit application
   - `POST /app-api/member/auth/trade-application`
   - Public endpoint.
   - Stores business information, social media, authorized users, business documents, and tax documents.
   - Duplicate pending applications are blocked by matching business name and primary authorized user email.

2. Upload attachments
   - `POST /app-api/infra/file/upload`
   - Public multipart upload.
   - The Trade application page uploads each selected file first, then submits the returned file URLs in the application payload.

3. Send Trade login email code
   - `POST /app-api/member/auth/trade-login-code`
   - Payload: `tradeId`, `email`
   - Only sends a code when the Trade ID exists, belongs to a reviewed/approved Trade application, and the email matches an authorized user.

4. Trade login
   - `POST /app-api/member/auth/trade-login`
   - Payload: `tradeId`, `email`, `code`
   - Consumes email-code scene `6` and returns the normal member auth token response.

## Admin API flow

1. Page pending and reviewed applications
   - `GET /admin-api/member/trade-application/page`
   - Permission: `member:trade-application:query`

2. Get application detail
   - `GET /admin-api/member/trade-application/get?id={id}`
   - Permission: `member:trade-application:query`

3. Approve application
   - `PUT /admin-api/member/trade-application/approve`
   - Permission: `member:trade-application:review`
   - Payload: `id`, `tradeId`, optional `reviewRemark`

4. Reject application
   - `PUT /admin-api/member/trade-application/reject`
   - Permission: `member:trade-application:review`
   - Payload: `id`, `reviewRemark`

## Database setup

Run:

```sql
source sql/mysql/member-trade-application.sql;
```

The SQL creates `member_trade_application` and inserts the admin menu and permissions under the Member management menu.

## Ready-to-test setup

1. Start the Yudao backend and make sure `/app-api` and `/admin-api` are both reachable.
2. Import `yudao-cloud/sql/mysql/member-trade-application.sql` if it has not been imported yet.
3. In the admin system, assign these permissions to the test admin role:
   - `member:trade-application:query`
   - `member:trade-application:review`
4. Start the storefront with `npm run dev -- --port 5174`.
5. Start the admin frontend with `pnpm dev`.
6. Submit a Trade application from `http://127.0.0.1:5174/trade/apply` and note the application ID shown in the success message.
7. Open the admin menu `Member management > Trade Applications`, search the same application ID, then approve it with a Trade ID.
8. Return to `http://127.0.0.1:5174/trade/sign-in` and log in with the approved Trade ID and authorized email.

## Manual test checklist

1. Open `/trade/apply`, switch English, Chinese, and French, and confirm all major copy changes language.
2. Submit an empty application and confirm required-field validation appears.
3. Fill a valid application with one business document and one tax document. Confirm the page uploads files before submit.
4. Submit again with the same business name and first authorized email. Confirm duplicate pending submission is rejected.
5. In admin API, approve the application and assign a Trade ID.
6. Open `/trade/sign-in`, request an email code using the approved Trade ID and authorized email.
7. Confirm a wrong code is rejected.
8. Confirm the correct email code returns a member token.
9. Confirm a normal personal account login still uses the existing member login endpoints.
