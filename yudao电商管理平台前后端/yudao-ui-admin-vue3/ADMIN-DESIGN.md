# ADMIN-DESIGN.md - Furniture Admin

## 1. Purpose

This document defines the frontend design language for the furniture commerce admin system.

Use it for:

- Product management
- Category, brand, property, SKU/SPU, and media management
- Order management
- Delivery, freight, address, and after-sale workflows
- Member and trade application management
- Payment, refund, and file management
- System user, role, and menu operations that remain visible in furniture-lite mode

Do not use it for:

- Customer-facing furniture storefront pages
- RH-like editorial/product display pages
- Marketing, lookbook, sourcebook, or home page modules

The customer-facing storefront has a separate design document: `D:\code\furniture web\DESIGN.md`.

## 2. Design Brief

The admin should feel like a focused furniture commerce operations console.

It should be:

- Efficient
- Clear
- Dense but not crowded
- Table-first where work is list-based
- Form-first where data quality matters
- Calm and predictable
- Optimized for repeated daily operations

It should not feel like:

- A luxury marketing page
- A lifestyle showroom
- A generic full Yudao platform with every module exposed
- A colorful analytics demo
- A SaaS landing page

## 3. Reference Direction

The admin design borrows principles from these `awesome-design-md` directions:

- Linear: crisp hierarchy, restrained surfaces, precise labels, low-noise controls
- Airtable: structured data editing, clear grids, calm workflow pages
- Stripe: trustworthy financial and payment operations, precise forms
- Sentry/PostHog: issue/order/event triage, status-driven tables, actionable detail pages

It does not copy their marketing surfaces. It translates their product-console discipline into the existing Yudao Vue 3 + Element Plus admin stack.

## 4. Existing Project Constraints

The admin project is `yudao-ui-admin-vue3`.

Current stack:

- Vue 3
- Vite
- TypeScript
- Element Plus
- Pinia
- Vue Router
- UnoCSS utility classes

Current furniture-lite behavior is controlled by:

```env
VITE_ADMIN_MODE=furniture-lite
VITE_SHOW_DOC_ALERT=false
VITE_SHOW_DEV_LINKS=false
```

The existing `src/config/furnitureLite.ts` filters the visible menu surface. This design document describes how the visible admin surface should look and behave after that filtering.

## 5. Product Surface

Furniture-lite admin should focus on these modules:

- Home dashboard
- Product center
- Product list
- Category
- Brand
- Property
- Product comments if needed
- Mall statistics if useful
- Trade center
- Order list
- After-sale
- Delivery and freight templates
- Member center
- Member users
- Trade applications
- Level, tag, group
- Payment
- Payment app
- Payment order
- Refund order
- File management
- System users, roles, menus
- Mail/message settings when required by commerce flows

Hide or de-emphasize:

- AI
- BPM
- CRM
- ERP
- IoT
- MES
- MP official account
- WMS unless furniture warehouse operations are explicitly reintroduced
- Code generation
- API logs
- Dev documentation
- Generic platform demo features

## 6. Visual Theme

The admin should be a quiet work surface.

Visual qualities:

- Light neutral canvas
- White panels
- Subtle borders
- Low-shadow hierarchy
- Compact controls
- Clear status colors
- Data tables with strong scanability
- No luxury editorial imagery

Avoid:

- Full-bleed hero sections
- Large lifestyle photography
- Serif marketing headings
- Oversized decorative cards
- Gradient backgrounds
- Dark cinematic UI as the default
- Excessive rounded pills

## 7. Color System

Use Element Plus variables as the implementation base, but keep the admin palette restrained.

Recommended roles:

| Role | Color | Use |
| --- | --- | --- |
| App canvas | `#f5f6f8` | Page background |
| Panel | `#ffffff` | Search forms, tables, cards, dialogs |
| Panel soft | `#fafafa` | Secondary regions, nested detail blocks |
| Border | `#e5e7eb` | Table borders, form outlines, dividers |
| Border strong | `#cfd4dc` | Active/focused outlines |
| Ink | `#1f2933` | Primary text |
| Body | `#4b5563` | Secondary text |
| Muted | `#8a94a3` | Helper text, placeholders |
| Primary | `#1f2933` | Main action, active nav, selected row |
| Link | `#2563eb` | Detail links and safe navigation |
| Success | `#16803c` | Paid, shipped, active, verified |
| Warning | `#b76e00` | Pending, partial, attention |
| Error | `#b42318` | Failed, rejected, destructive |
| Info | `#2563eb` | Processing, informational |

Rules:

- Use color for state, not decoration.
- Tables should mostly be black/gray/white.
- Do not bring the storefront's warm showroom palette into operational tables unless a subtle brand touch is needed.
- Do not use bright neon status tags.

## 8. Typography

Use the existing admin sans-serif stack.

Recommended scale:

| Token | Size | Weight | Use |
| --- | --- | --- | --- |
| Page title | 20-24px | 600 | Page heading |
| Section title | 16-18px | 600 | Card/panel headings |
| Table body | 13-14px | 400 | Table cells |
| Form label | 13-14px | 500 | Field labels |
| Helper text | 12-13px | 400 | Hints, validation, metadata |
| Button | 13-14px | 500 | Action labels |
| Badge | 12px | 500 | Status tags |

Rules:

- Use sans-serif everywhere.
- Do not use storefront serif typography in admin.
- Keep labels short.
- Prefer sentence case or concise Chinese labels.
- Avoid decorative uppercase except for short IDs or codes.

## 9. Layout System

Default admin layout:

- Left sidebar navigation
- Top utility bar
- Content area with page title, filters, table, and detail panels
- Optional right-side drawer for create/edit/detail

Page structure:

1. Page header
2. Search/filter panel
3. Toolbar
4. Data table or main work area
5. Pagination
6. Drawer/dialog for create/edit/detail

Spacing:

| Role | Value |
| --- | --- |
| Page padding | 16-24px |
| Panel padding | 16-20px |
| Filter row gap | 12-16px |
| Table cell padding | 8-12px |
| Toolbar gap | 8-12px |
| Drawer padding | 20-24px |

Rules:

- Keep most list pages above-the-fold useful.
- Do not use large hero cards.
- Do not hide key actions below scroll.
- Do not create card stacks inside card stacks.

## 10. Navigation

Furniture-lite sidebar groups should be understandable to commerce operators.

Recommended top groups:

- Dashboard
- Products
- Orders
- Members
- Payments
- Files
- System

Rules:

- Keep menu depth shallow where possible.
- Use clear nouns: Products, Orders, Members, Payments.
- Hide generic platform modules from daily operators.
- Active state should be visible but quiet.
- Do not use storefront category language like Living/Dining/Bedroom for admin navigation unless managing storefront menus.

## 11. Tables

Tables are the core admin component.

Default table rules:

- Use readable 13-14px text.
- Keep rows compact but not cramped.
- Use sticky operation columns for wide tables.
- Use fixed widths for IDs, status, price, inventory, date, and operation columns.
- Use ellipsis with tooltip for long product names, addresses, SKUs, and notes.
- Use row selection only when batch actions exist.
- Keep row actions short and predictable.

Common columns:

Product table:

- Image
- Product name
- SPU/SKU
- Category
- Brand
- Price
- Inventory
- Sale status
- Updated time
- Operations

Order table:

- Order number
- Member
- Amount
- Payment status
- Delivery status
- After-sale status
- Address verification status
- Created time
- Operations

Member table:

- Member ID
- Name/mobile/email
- Level/tag/group
- Trade status
- Membership status
- Last order
- Created time
- Operations

## 12. Filters And Search

Filters should be fast and predictable.

Use:

- Text input for order number, product name, SKU, member name, mobile, email
- Select for category, status, payment, delivery, after-sale
- Date range for created/paid/shipped/refunded time
- Cascader for category when needed
- Region/address filters only when operationally useful

Rules:

- Put common filters in the first row.
- Collapse advanced filters.
- Search, Reset, Export, and Create should have consistent placement.
- Do not make every possible backend field a visible filter.

## 13. Forms

Forms should prioritize accuracy and recovery.

Rules:

- Split long forms into sections.
- Use clear required markers.
- Validate inline.
- Show field-level errors near the field.
- Save actions should remain visible at the bottom or in a sticky footer.
- Destructive changes need confirmation.

Product form sections:

1. Basic information
2. Category and brand
3. Images and media
4. Properties and variants
5. Price and inventory
6. Delivery and sale status
7. SEO/storefront metadata if supported

Order update sections:

1. Order summary
2. Customer and address
3. Payment
4. Delivery
5. After-sale
6. Internal notes and operation log

## 14. Status System

Status tags must be consistent.

Recommended mapping:

| State | Color role | Examples |
| --- | --- | --- |
| Neutral | gray | Draft, disabled, unlisted |
| Info | blue | Processing, verifying, syncing |
| Success | green | Paid, shipped, active, verified |
| Warning | amber | Pending, partial, address attention |
| Error | red | Failed, rejected, refunded, blocked |

Rules:

- Same business state uses the same color everywhere.
- Avoid using red for ordinary sale discounts in admin tables.
- Use text plus color for accessibility.
- Prefer small tags over large badges.

## 15. Detail Pages

Detail pages should support fast investigation.

Recommended layout:

- Header with core identity and status
- Summary strip with key metrics
- Main detail area
- Side panel for operation history or internal notes
- Action bar with allowed operations

Order detail should show:

- Order number and status
- Customer
- Address and verification result
- Items
- Payment
- Delivery
- After-sale/refund
- Operation logs
- Internal notes

Product detail should show:

- Product identity
- Images
- Categories and properties
- Variant table
- Price and inventory
- Storefront visibility
- Recent changes

## 16. Drawers And Dialogs

Use drawers for contextual create/edit/detail tasks.

Use dialogs for:

- Confirmation
- Small forms
- Destructive actions
- Status changes

Rules:

- Large create/edit forms should use drawers or full pages, not cramped modals.
- Confirmation dialogs must state consequence clearly.
- Keep primary action on the right.
- Keep cancel secondary and quiet.

## 17. Dashboard

The dashboard should summarize operations, not decorate the product.

Recommended modules:

- Today's orders
- Pending shipment
- Pending after-sale
- Payment exceptions
- Address verification attention
- Low inventory
- New trade applications
- Top products

Rules:

- Use compact metric cards.
- Each metric should link to the filtered table.
- Avoid vanity charts unless they drive action.
- Do not use customer-facing lifestyle photography.

## 18. Batch Operations

Batch operations should be explicit.

Rules:

- Show batch toolbar only after selection.
- Display selected count.
- Disable actions that do not apply to selected rows.
- Confirm destructive or irreversible batch actions.
- Export should respect current filters unless explicitly exporting all.

## 19. Empty, Loading, Error

Empty state:

- Tell the operator what is missing.
- Offer a relevant action: create, reset filters, import, or retry.
- Avoid marketing copy.

Loading:

- Use table skeleton or inline loading.
- Keep layout stable.

Error:

- Show what failed.
- Provide retry or recovery action.
- Technical detail may be collapsible.

## 20. Responsive Behavior

Admin is desktop-first.

Rules:

- Primary target width is `1280px+`.
- At tablet widths, sidebar may collapse.
- Tables may use horizontal scroll.
- Do not redesign dense operations into storefront-style cards.
- Mobile support can be limited to emergency review, not full operations.

## 21. Accessibility

- All table actions must be keyboard reachable.
- Focus state must be visible.
- Status tags must include text.
- Dialogs and drawers must trap focus.
- Required fields and errors must be announced by text.
- Keep touch/click targets at least `32px` on desktop and `44px` on touch surfaces.

## 22. Do

- Keep the admin clear, quiet, and operational.
- Use Element Plus patterns consistently.
- Prefer tables, filters, forms, drawers, and detail panels.
- Make statuses and next actions obvious.
- Keep module scope focused through furniture-lite filtering.
- Use color only for state and priority.
- Keep actions near the object they affect.

## 23. Do Not

- Do not apply RH-like showroom design to admin.
- Do not use large hero images.
- Do not use serif headings.
- Do not use decorative gradients.
- Do not create oversized marketing cards.
- Do not make tables sparse for aesthetic reasons.
- Do not hide operational actions behind visual flourish.
- Do not expose full Yudao generic modules in furniture-lite mode.

## 24. Agent Prompt Guide

When generating admin UI for this project:

Build a focused furniture commerce operations console using Vue 3, Element Plus, and the existing Yudao admin patterns. Prioritize searchable tables, compact filters, clear status tags, reliable forms, contextual drawers, and action-oriented detail pages. Keep the visual language calm, light, neutral, and efficient. Do not use the customer-facing RH-like furniture showroom style inside admin workflows.
