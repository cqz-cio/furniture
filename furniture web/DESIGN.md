# DESIGN.md - Furniture Web

## 1. Purpose

This document defines the customer-facing visual language for the furniture web storefront.

Use it for:

- Home page
- Sale landing page
- Outdoor, Baby & Child, Teen, and category landing pages
- Product listing pages
- Product detail pages
- Cart, checkout, account entry, membership, trade program, and gift registry pages when they are customer-facing
- Lookbook, sourcebook, room inspiration, and editorial furniture content

Do not use it for:

- Admin dashboards
- Product, order, inventory, member, payment, permission, or delivery management consoles
- Dense tables, audit logs, batch operations, or system configuration

The admin surface has a separate design document: `ADMIN-DESIGN.md` in the admin project.

## 2. Design Brief

The storefront should feel like a premium furniture showroom and an interior design magazine, not a generic ecommerce template.

The visual direction combines:

- RH-like luxury home retail: quiet navigation, warm neutrals, editorial scale, large room photography
- Apple-like restraint: product and photography come first, UI chrome recedes
- Airbnb-like spatial browsing: rooms, categories, and lifestyle imagery help customers imagine usage
- Bugatti/Lamborghini-like premium sparseness: dark moments are allowed, but used for special collections only
- Pinterest/WIRED-like editorial discovery: inspiration pages may use image-led grids and article pacing

The result should be calm, architectural, spacious, and commercially clear.

## 3. Core Principles

1. Photography is the primary interface.
2. Warm neutral surfaces beat bright color.
3. Typography should feel editorial, mature, and quiet.
4. Commerce controls must be easy to find but never visually louder than the furniture.
5. Layouts should breathe. Do not pack products like a discount marketplace.
6. Use lines, spacing, and contrast before shadows or decorative effects.
7. The brand should feel high-end even on utility pages like cart and checkout.

## 4. Color System

Use the existing project tokens as the base:

```css
:root {
  --bg: #f6f5f2;
  --surface: #ffffff;
  --ink: #171717;
  --soft-ink: #57524b;
  --muted: #89827a;
  --line: #d8d4cc;
  --deep: #2f312e;
  --moss: #596257;
  --paper: #fbfaf8;
}
```

Recommended roles:

| Role | Color | Use |
| --- | --- | --- |
| Canvas | `#f6f5f2` | Default page background |
| Paper | `#fbfaf8` | Header, overlays, quiet panels |
| Surface | `#ffffff` | Product info, forms, drawers, checkout panels |
| Ink | `#171717` | Main text, primary actions |
| Soft Ink | `#57524b` | Body text and secondary copy |
| Muted | `#89827a` | Metadata, helper text, inactive states |
| Line | `#d8d4cc` | Hairlines, dividers, input borders |
| Deep | `#2f312e` | Dark premium panels, checkout emphasis, footer accents |
| Moss | `#596257` | Rare editorial accent or success-adjacent tone |

Accent guidance:

- Use black or deep charcoal for primary actions.
- Use moss or warm taupe only as quiet editorial accents.
- Use red only for sale, error, or destructive moments.
- Do not introduce blue, purple, neon green, large gradients, or SaaS-style accent palettes.

## 5. Typography

The current storefront uses:

- Display: `Georgia, "Times New Roman", serif`
- UI/body: `Arial, "Microsoft YaHei", sans-serif`

Keep this split.

Display rules:

- Use serif for hero headlines, collection names, membership/editorial headings, and premium story moments.
- Keep serif weight at `400`.
- Avoid bold serif.
- Do not overuse italic; reserve it for small brand or editorial details.

UI/body rules:

- Use sans-serif for navigation, buttons, forms, product metadata, price, filters, checkout, and account flows.
- Use uppercase carefully for nav labels, buttons, and micro-labels.
- Letter spacing should be `0` for most text. Small uppercase labels may use `0.06em` to `0.1em`.

Suggested scale:

| Token | Desktop | Mobile | Use |
| --- | --- | --- | --- |
| `display-xl` | 54-72px | 34-44px | Home hero, campaign hero |
| `display-lg` | 38-48px | 28-34px | Collection and room-story headings |
| `display-md` | 28-34px | 24-28px | PDP, account, membership headings |
| `title` | 18-22px | 17-20px | Product names, panel headings |
| `body` | 14-16px | 14-16px | Main copy |
| `caption` | 11-13px | 11-13px | Metadata, labels, helper text |
| `button` | 11-13px | 11-13px | Buttons and utility actions |

## 6. Layout System

The storefront layout should feel like a showroom.

Use:

- Full-bleed hero photography
- Generous vertical sections
- Centered editorial text blocks
- Two- and three-column product grids
- Wide gutters
- Large image modules between product grids

Avoid:

- Dense 5-up or 6-up product grids on desktop
- Nested cards
- Floating marketing cards inside section cards
- Overdecorated page backgrounds

Spacing:

| Role | Desktop | Mobile |
| --- | --- | --- |
| Page gutter | 40-80px | 18-24px |
| Major section vertical padding | 80-140px | 48-72px |
| Product grid gap | 24-40px | 18-24px |
| Editorial block gap | 32-56px | 24-36px |
| Form row gap | 12-18px | 12-16px |

## 7. Imagery

Photography is the strongest design token in this project.

Use:

- Large room scenes
- Full-width category images
- Material close-ups
- Product cover and gallery imagery
- Editorial lifestyle photography
- Warm natural light
- Neutral interior palettes

Image ratios:

| Context | Ratio |
| --- | --- |
| Home hero | Full viewport, 16:9, or 21:9 |
| Category hero | 16:9 or 16:10 |
| Product grid card | 4:5, 3:4, or square depending on source |
| Product detail gallery | Mix of vertical and horizontal images |
| Lookbook/editorial | Masonry or alternating 16:10 and 4:5 |

Rules:

- Do not replace product photography with icons or illustrations.
- Do not darken photos so heavily that furniture details are lost.
- Do not use blurry atmospheric images for product inspection areas.
- Above-the-fold imagery should clearly show furniture, room, or collection context.

## 8. Navigation

Primary navigation should be quiet and category-led.

Recommended top-level categories:

- Living
- Dining
- Bedroom
- Outdoor
- Lighting
- Textiles
- Rugs
- Decor
- Baby & Child
- Teen
- Sale
- Interior Design

Rules:

- Header chrome should be minimal.
- Mega menus should use text columns and restrained dividers.
- Active and hover states should use underline or text contrast, not bright fills.
- Icons in the header should remain thin-line and familiar: menu, search, account, bag.
- Mobile navigation may become a full-screen sheet.

## 9. Components

### Buttons

Primary button:

- Background: `#211f1b` or `#171717`
- Text: `#ffffff`
- Border: same as background
- Radius: `0-4px`
- Height: `42-50px`
- Text: uppercase, 11-13px

Secondary button:

- Background: transparent or `#ffffff`
- Text: `#171717`
- Border: `1px solid #211f1b` or `#d8d4cc`
- Radius: `0-4px`

Text button:

- No background
- Underline or bottom border
- Uppercase for utility actions

Avoid pill CTAs on the main storefront. Pill shapes are allowed only for small badges, counts, or rare filter chips.

### Product Cards

Product cards are image-led and lightly framed.

Card structure:

1. Product image
2. Product name
3. Collection, material, finish, or option count
4. Price or price range
5. Optional member price or sale note

Rules:

- Keep product text below the image.
- Avoid heavy shadows and card frames.
- Use hover to reveal secondary image or quick view subtly.
- Do not cover product images with noisy badges.
- Sale labels should be restrained and textual.

### Product Detail

PDP hierarchy:

1. Large gallery
2. Product name and collection
3. Price, member price, sale state
4. Finish/material/size selectors
5. Delivery and availability
6. Add to bag
7. Dimensions, details, care, shipping, returns
8. Related products and room inspiration

Rules:

- Gallery must dominate desktop.
- Purchase panel may be sticky but should not feel like a marketplace ad.
- Material selectors should be visual when assets exist.
- Customization warnings must be clear before checkout.

### Filters

Use calm filters:

- Category
- Size
- Material
- Color
- Finish
- Price
- Collection
- Availability
- Sale

Rules:

- Desktop filters may be a left rail or top drawer.
- Mobile filters should be a drawer.
- Use checkboxes, text rows, and simple range controls.
- Avoid colorful chips as the default filter pattern.

### Cart And Checkout

Cart and checkout should keep the luxury tone while becoming more operational.

Rules:

- Use white/paper panels on warm canvas.
- Keep order summary clear and sticky when useful.
- Use hairline dividers instead of card stacks.
- Show price, member savings, shipping, tax, and custom-item rules plainly.
- Error states may use muted red, not bright alert styling.

### Account, Membership, Trade, Gift Registry

These are customer-facing utility pages, not admin pages.

Rules:

- Keep serif headings.
- Use restrained panels and hairlines.
- Avoid large promotional hero treatment inside account pages.
- Use clear empty states and recovery actions.
- Forms should remain calm, centered, and easy to complete.

## 10. Page Patterns

### Home

Recommended structure:

1. Full-bleed hero image
2. Category nav or collection entrance
3. Featured rooms or collections
4. Seasonal campaign module
5. Product/category grid
6. Interior design service
7. Sourcebook or editorial module
8. Footer

### Category Landing

Recommended structure:

1. Category hero
2. Editorial intro
3. Subcategory tiles
4. Featured collections
5. Product preview
6. Inspiration scene

### Product Listing

Recommended structure:

1. Category title and quiet description
2. Filter/sort row
3. 2- or 3-column grid
4. Occasional full-width room scene
5. Pagination or load-more

### Product Detail

Recommended structure:

1. Gallery and purchase information
2. Details and dimensions
3. Materials and care
4. Delivery and returns
5. Related collection
6. Room inspiration

### Editorial / Lookbook

Recommended structure:

1. Large room image
2. Sparse title and introduction
3. Alternating image/text sections
4. Shop the room modules
5. Related stories

## 11. Motion

Use subtle, functional motion:

- Image fade
- Header reveal
- Drawer slide
- Gallery switch
- Cart add fly animation when already implemented
- Hover image scale up to `1.02-1.03` maximum

Avoid:

- Bouncy transitions
- Decorative parallax
- Fast carousel motion
- Colorful hover effects
- Animation that hides product information

Respect `prefers-reduced-motion`.

## 12. Responsive Rules

Breakpoints:

| Name | Width | Behavior |
| --- | --- | --- |
| Mobile | `< 640px` | Single column, drawer nav, large tap targets |
| Tablet | `640-1024px` | 2-column grids, compact header |
| Desktop | `1024-1440px` | Full nav, 2-3 column product grids |
| Wide | `> 1440px` | Cap content width, increase outer margins |

Rules:

- Never scale text with viewport width.
- Preserve readable product names and prices.
- Keep tap targets at least `44px`.
- Do not let buttons wrap awkwardly; stack them if needed.
- Product images should maintain stable aspect ratios.

## 13. Accessibility

- Use semantic headings in order.
- Maintain strong contrast for body text and controls.
- Do not rely on color alone for sale, error, or selected states.
- Provide alt text for product and room images.
- Drawers and modals must trap focus.
- Header, cart, account, and filter drawers must be keyboard usable.
- Keep focus states visible, preferably with a 1-2px dark outline or underline.

## 14. Do

- Prioritize real furniture and room imagery.
- Use serif headings for premium editorial moments.
- Use warm neutral surfaces.
- Keep product grids spacious.
- Use hairlines and spacing before shadows.
- Make commerce actions clear but quiet.
- Keep RH-like restraint across cart, checkout, account, and membership.

## 15. Do Not

- Do not make the storefront look like a SaaS dashboard.
- Do not use admin table patterns on customer pages.
- Do not use bright gradients or decorative orbs.
- Do not overuse cards inside cards.
- Do not pack too many products per row.
- Do not make filters louder than products.
- Do not use playful illustration as a replacement for photography.
- Do not copy RH assets, logos, or legal copy.

## 16. References

This document is adapted for this project after reviewing:

- Current project CSS in `src/styles.css`
- Current page structure in `src/pages`
- Current generated furniture assets in `public/assets/generated-furniture`
- RH public site structure and category language
- `awesome-design-md` examples most relevant to furniture storefronts: Apple, Airbnb, Nike, Tesla, Bugatti, Lamborghini, Pinterest, WIRED

## 17. Agent Prompt Guide

When generating customer-facing UI for this project:

Build a premium furniture ecommerce storefront with RH-like restraint. Use large room and product photography, warm neutral surfaces, serif editorial headings, clean sans-serif utility text, spacious product grids, thin hairline dividers, subtle drawers, and quiet black primary actions. The interface should feel like a luxury furniture showroom and interior design magazine, not a generic ecommerce marketplace or SaaS dashboard.
