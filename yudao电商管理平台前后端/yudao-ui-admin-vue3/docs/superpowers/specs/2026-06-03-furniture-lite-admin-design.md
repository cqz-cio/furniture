# Furniture Lite Admin Design

## Purpose

Build a lightweight admin mode for the furniture commerce platform. The first version controls what the admin UI exposes without deleting existing Yudao source code or backend modules.

The goal is to make the admin feel like a focused furniture commerce console instead of a full generic Yudao platform.

## Non Goals

- Do not delete frontend feature folders in the first version.
- Do not remove backend Maven modules in the first version.
- Do not delete database tables or menu seed data in the first version.
- Do not change product, order, member, payment, shipping, file, or permission APIs.
- Do not replace the Yudao permission model.

## Mode Configuration

Add a Vite environment switch:

```env
VITE_ADMIN_MODE=furniture-lite
```

Supported values:

- `furniture-lite`: show only the furniture commerce admin surface.
- `full`: keep the current full Yudao admin behavior.

Additional display switches:

```env
VITE_SHOW_DOC_ALERT=false
VITE_SHOW_DEV_LINKS=false
```

- `VITE_SHOW_DOC_ALERT=false` hides page-level Yudao documentation alert banners.
- `VITE_SHOW_DEV_LINKS=false` hides developer-oriented links such as Boot docs, Cloud docs, code generation, and API logs.

## Feature Surface

Furniture lite mode keeps these areas visible:

- Home dashboard
- Mall home
- Product center: product list, category, brand, property
- Trade center: order list, after-sale refund, delivery, freight template
- Member center: member users, addresses when available, level, tag, group
- Payment: payment app, payment order, refund order
- File management: file list and file config
- System settings: admin users, roles, menus

Furniture lite mode hides these areas:

- AI
- BPM workflow
- CRM
- ERP
- IoT
- MES
- MP official account
- Reports
- WMS
- Complex promotion tooling unless explicitly re-enabled
- Developer documentation links
- Code generation
- API logs and other development-only operations

## Frontend Architecture

Create a small config module:

```text
src/config/furnitureLite.ts
```

It owns the allowlist and helper functions:

- `isFurnitureLiteMode()`
- `isDocAlertVisible()`
- `isDevLinksVisible()`
- `filterFurnitureLiteMenus(routes)`
- `filterFurnitureLiteFixedRoutes(routes)`

`src/store/modules/permission.ts` remains the central place where backend menus become frontend routes. It should:

1. Read the cached backend menus from `ROLE_ROUTERS`.
2. If `VITE_ADMIN_MODE` is `furniture-lite`, filter the backend menu tree before `generateRoute`.
3. Generate routes from the filtered tree.
4. Filter fixed routes from `remainingRouter` before assigning `this.routers`.
5. Keep the 404 route appended to dynamic routes.

This keeps menu rendering, route registration, breadcrumbs, and tab behavior aligned.

## Route Access Rules

Hidden modules must not appear in the sidebar.

Direct visits to hidden dynamic routes should not register in furniture lite mode. They should fall through to the existing 404 or permission flow.

Detail pages that support kept workflows should remain accessible, even if they are hidden sidebar routes:

- Product add, edit, detail
- Product property value pages
- Order detail
- After-sale detail
- Member detail
- Payment cashier if still needed by payment flows

Detail pages for hidden modules such as CRM, BPM, AI, IoT, and MES should not be registered in furniture lite mode.

## UI Display Rules

In furniture lite mode:

- Hide Boot development documentation.
- Hide Cloud development documentation.
- Hide page documentation alert banners.
- Hide developer-only tools and shortcuts.
- Keep user profile, logout, locale, theme, and fullscreen controls unless later design removes them.
- Rename or reorganize visible menu labels only after the first filtering version is stable.

## Data Flow

```text
.env.local
  -> import.meta.env
  -> src/config/furnitureLite.ts
  -> permission store route filtering
  -> sidebar / tabs / breadcrumbs / route registration
```

The backend still returns the full authorized menu tree. The frontend chooses the visible subset when furniture lite mode is active.

## Testing

Automated checks:

- `pnpm.cmd build:local`
- Existing frontend tests if available
- Add focused tests for the route filtering helpers if the project test setup supports them

Manual checks:

- Login as `admin / admin123`.
- Confirm only furniture commerce menus are visible.
- Confirm hidden modules such as AI, BPM, CRM, ERP, IoT, MES, Reports, and WMS are absent.
- Confirm product category, product list, order list, member user, payment app, file management, and system role pages still open.
- Confirm direct access to a hidden module route does not show the hidden page.
- Confirm documentation alert banners are hidden when `VITE_SHOW_DOC_ALERT=false`.

## Rollback

Set:

```env
VITE_ADMIN_MODE=full
VITE_SHOW_DOC_ALERT=true
VITE_SHOW_DEV_LINKS=true
```

Then rebuild or restart the dev server. The full Yudao admin surface should return without restoring deleted source files.
