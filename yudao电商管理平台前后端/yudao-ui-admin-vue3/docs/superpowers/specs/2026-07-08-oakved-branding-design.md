# Oakved Branding Design

## Purpose

Replace the visible Yudao admin branding in the Vue 3 admin frontend with an Oakved brand presentation that matches the user's selected direction:

- transparent background logo
- `Oakved` as a pure English wordmark
- visual style based on the provided serif reference
- Chinese product name shown as `Oakved后台管理系统`

The goal of this phase is to make the running admin UI feel like an Oakved product without renaming backend package names, Maven modules, route internals, or repository structure yet.

## Scope

This phase changes display-layer branding only inside `yudao-ui-admin-vue3`.

Included:

- left sidebar logo area
- login page logo and system name
- browser title and loading title
- footer product name
- static HTML meta text that currently mentions Yudao
- logo asset used by the admin UI

Deferred to phase 2:

- repository folder names
- frontend project package identity beyond user-visible text
- backend `artifactId`, module names, and Java package paths
- environment variable key renaming such as `VITE_APP_TITLE`
- broad source comment and documentation cleanup across the full codebase

## Brand Decisions

### Product Name

Use the visible Chinese system name:

```text
Oakved后台管理系统
```

### English Product / Project Name

Use the English-facing product name:

```text
Oakved Console
```

This name is intended for confirmation as the future engineering-facing rename candidate, but phase 1 uses it only where it helps display or documentation clarity.

### Logo Direction

Use a transparent-background wordmark logo made from the full `Oakved` text only.

Visual rules:

- white text on transparent background
- serif style close to the supplied reference image
- slightly optimized for small-size readability in the admin sidebar
- no circular avatar
- no extra icon or badge in phase 1

## Frontend Change Plan

### Asset Strategy

Replace the current raster logo asset referenced by the app with a new transparent Oakved wordmark asset.

Preferred implementation:

- use a shared SVG asset at `src/assets/svgs/oakved-wordmark.svg`
- repoint existing frontend logo consumers to the new Oakved transparent wordmark

This keeps existing login, QR code, social login, and layout references working without broader component churn.

### UI Text Targets

Update the visible system title source so the app store title resolves to `Oakved后台管理系统`.

Primary targets:

- `index.html`
- `.env`, `.env.local`, `.env.dev`, `.env.test`, `.env.stage`, `.env.prod`
- `src/store/modules/app.ts` consumers via `appStore.getTitle`

Visible surfaces expected to update automatically once the title source changes:

- sidebar logo title
- login page title
- browser tab title
- footer copyright title
- app loading screen title

### Layout Considerations

The current `Logo.vue` layout uses a square image slot sized from logo height. A horizontal wordmark can look cramped if forced into the old square geometry.

Phase 1 should therefore make a minimal layout-safe adjustment:

- allow the logo image to render as a horizontal wordmark
- preserve alignment in sidebar and top area
- avoid changing the surrounding navigation structure

If the existing width is too constrained, adjust only the local logo image class or container spacing in:

- `src/layout/components/Logo/src/Logo.vue`
- login header blocks in `src/views/Login/Login.vue`
- matching social-login branding block in `src/views/Login/SocialLogin.vue`

Additional logo consumer to verify:

- `src/views/Login/components/QrCodeForm.vue`

## Data Flow

```text
brand asset
  -> src/assets/svgs/oakved-wordmark.svg
  -> Logo / Login / QR code views

display title source
  -> VITE_APP_TITLE
  -> app store title
  -> sidebar / login / browser title / footer / loading screen
```

## Implementation Constraints

- Do not rename `VITE_APP_TITLE` itself in phase 1; only change its value.
- Do not rename source folders containing `yudao`.
- Do not modify backend services for this branding pass.
- Do not delete the existing brand plumbing; reuse it with Oakved values.

## Testing

Manual verification:

- open the login page and confirm the avatar-style logo is gone
- confirm the displayed system name is `Oakved后台管理系统`
- confirm the logo background is transparent on dark UI surfaces
- confirm the sidebar top-left logo reads clearly after scaling
- confirm the browser tab title uses the Oakved title
- confirm the footer shows the Oakved name

Build verification:

- run the frontend build or the project's local verification command after the asset and title changes

## Rollback

Rollback is straightforward because phase 1 only changes display assets and visible text:

- restore the previous shared logo asset reference and branding copy
- restore the previous app title value and HTML meta text

## Phase 2 Preview

After phase 1 is accepted, phase 2 can cover the engineering rename path centered on the approved English project identity `Oakved Console`, including selective repo and module naming changes with a separate migration plan.
