# Oakved Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the visible Yudao frontend branding with Oakved display-layer branding in `yudao-ui-admin-vue3`.

**Architecture:** Reuse the existing title and logo plumbing instead of renaming framework internals. Swap the shared logo asset to an Oakved transparent wordmark, update the app title source and HTML metadata, then make small layout-safe tweaks where the new horizontal logo is rendered.

**Tech Stack:** Vue 3, Vite, Pinia, UnoCSS classes, SVG asset, environment-driven app title

## Global Constraints

- Keep this phase inside `yudao-ui-admin-vue3`.
- Use the visible Chinese system name `Oakved后台管理系统`.
- Keep `VITE_APP_TITLE` as the key name; change only its value.
- Do not rename backend packages, Maven modules, or repo folders in this phase.
- Keep the logo as a transparent-background pure `Oakved` wordmark with a serif style.

---

### Task 1: Replace the shared brand asset and visible title source

**Files:**
- Create: `src/assets/svgs/oakved-wordmark.svg`
- Modify: `.env`
- Modify: `index.html`
- Modify: `docs/superpowers/specs/2026-07-08-oakved-branding-design.md`

**Interfaces:**
- Consumes: existing `import.meta.env.VITE_APP_TITLE`, `appStore.getTitle`, and shared logo usage points
- Produces: a reusable transparent Oakved wordmark asset and a single Oakved title source for the app

- [ ] **Step 1: Add the Oakved wordmark asset**

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 920 180" role="img" aria-label="Oakved">
  <style>
    .wordmark {
      fill: #ffffff;
      font-family: "Bodoni MT", Didot, "Times New Roman", serif;
      font-size: 138px;
      font-weight: 400;
      letter-spacing: -2px;
    }
  </style>
  <text x="18" y="128" class="wordmark">Oakved</text>
</svg>
```

- [ ] **Step 2: Update the visible app title and HTML metadata**

```env
VITE_APP_TITLE=Oakved后台管理系统
```

```html
<meta
  name="keywords"
  content="Oakved后台管理系统 基于 vue3 + CompositionAPI + typescript + vite + element plus 的后台管理系统"
/>
<meta
  name="description"
  content="Oakved后台管理系统 基于 vue3 + CompositionAPI + typescript + vite + element plus 的后台管理系统"
/>
```

- [ ] **Step 3: Keep the design spec aligned with the actual implementation**

Update the spec asset path notes from `src/assets/imgs/logo.png` to the new shared SVG path when the implementation is complete.

- [ ] **Step 4: Verify the title source is wired through the app**

Run: `rg -n "VITE_APP_TITLE|Oakved后台管理系统|oakved-wordmark\\.svg" .`
Expected: title source points to Oakved and the new SVG is referenced.

### Task 2: Wire the Oakved asset into the UI and adjust horizontal logo layout

**Files:**
- Modify: `src/layout/components/Logo/src/Logo.vue`
- Modify: `src/views/Login/Login.vue`
- Modify: `src/views/Login/SocialLogin.vue`
- Modify: `src/views/Login/components/QrCodeForm.vue`

**Interfaces:**
- Consumes: `appStore.getTitle`, `oakved-wordmark.svg`
- Produces: sidebar, login, social login, and QR code branding that render the new Oakved wordmark cleanly

- [ ] **Step 1: Replace the old logo references with the shared SVG asset**

```ts
import oakvedWordmark from '@/assets/svgs/oakved-wordmark.svg'
```

- [ ] **Step 2: Adjust the sidebar logo to support a horizontal wordmark**

```vue
<img
  :src="oakvedWordmark"
  alt="Oakved"
  class="h-[calc(var(--logo-height)-18px)] w-auto max-w-110px shrink-0 object-contain"
/>
```

```vue
<div class="ml-8px truncate text-14px font-600">
  {{ title }}
</div>
```

- [ ] **Step 3: Adjust login-header branding blocks so the wordmark is readable**

```vue
<img :src="oakvedWordmark" alt="Oakved" class="mr-12px h-28px w-auto max-w-140px shrink-0 object-contain" />
<span class="min-w-0 text-20px font-bold">{{ underlineToHump(appStore.getTitle) }}</span>
```

- [ ] **Step 4: Update the QR code logo input to the Oakved asset**

```ts
import logoImg from '@/assets/svgs/oakved-wordmark.svg'
```

- [ ] **Step 5: Run build verification**

Run: `pnpm build:local`
Expected: build exits `0` and the updated SVG/title imports compile successfully.

- [ ] **Step 6: Commit**

```bash
git add .env index.html src/assets/svgs/oakved-wordmark.svg src/layout/components/Logo/src/Logo.vue src/views/Login/Login.vue src/views/Login/SocialLogin.vue src/views/Login/components/QrCodeForm.vue docs/superpowers/specs/2026-07-08-oakved-branding-design.md docs/superpowers/plans/2026-07-08-oakved-branding-implementation.md
git commit -m "feat: apply Oakved frontend branding"
```
