# Oakved Logo Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every storefront use of the old Oakved wordmark with one optimized 2026 white transparent asset that remains white on imagery and renders black on light surfaces.

**Architecture:** A small `src/config/brand.js` module owns the only public Logo URL. Vue components bind their `<img>` elements to that constant, while contextual CSS controls white-versus-black presentation with `filter`. The supplied white Alpha PNG is cropped and downscaled deterministically without redrawing the brand.

**Tech Stack:** Vue 3, Vite, Vitest, CSS, Python Pillow for one-time lossless PNG preparation, Playwright/local browser for visual QA.

## Global Constraints

- Use the user-provided white wordmark as the source; do not redraw or alter the Logo letterforms.
- Preserve transparency and original aspect ratio; never introduce a white or black rectangular background.
- Deep/image backgrounds show white; white/light backgrounds show black by filtering the same white asset.
- Update only Oakved branding surfaces; do not change other series, certification, or partner Logos.
- Preserve unrelated working-tree changes and untracked files.
- Commit verified changes to `codex/agent-rag`; do not modify `main`.

---

### Task 1: Centralize Logo references and contextual color rules

**Files:**
- Create: `furniture web/src/config/brand.js`
- Create: `furniture web/tests/brandLogoReferences.test.js`
- Modify: `furniture web/src/components/RhHeader.vue`
- Modify: `furniture web/src/pages/HomePage.vue`
- Modify: `furniture web/src/components/BrandEyebrow.vue`
- Modify: `furniture web/src/components/CartDrawer.vue`
- Modify: `furniture web/src/pages/CheckoutPage.vue`
- Modify: `furniture web/src/styles.css`

**Interfaces:**
- Produces: `OAKVED_LOGO_SRC: string`, exported from `src/config/brand.js` with value `/assets/brand/oakved-logo-2026-white.png`.
- Consumes: Existing Vue `<script setup>` component bindings and existing CSS classes: `.brand-logo`, `.brand-eyebrow-logo`, `.mobile-drawer-brand-logo`, `.cart-brand-logo`, and `.rh-checkout-top img`.

- [ ] **Step 1: Write the failing reference and color-context test**

Create `tests/brandLogoReferences.test.js`:

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

const logoConsumers = [
  "../src/components/RhHeader.vue",
  "../src/pages/HomePage.vue",
  "../src/components/BrandEyebrow.vue",
  "../src/components/CartDrawer.vue",
  "../src/pages/CheckoutPage.vue",
];

describe("Oakved brand Logo", () => {
  it("centralizes every storefront Logo on the 2026 white transparent asset", async () => {
    const { OAKVED_LOGO_SRC } = await import("../src/config/brand.js");
    expect(OAKVED_LOGO_SRC).toBe("/assets/brand/oakved-logo-2026-white.png");

    for (const path of logoConsumers) {
      const source = readSource(path);
      expect(source, path).toContain("OAKVED_LOGO_SRC");
      expect(source, path).not.toMatch(/oakved-logo-(?:black|white)\.png/);
    }
  });

  it("keeps white-on-image and black-on-light presentation rules", () => {
    const css = readSource("../src/styles.css");
    expect(css).toContain(".brand-logo {\n  display: block;");
    expect(css).toContain("filter: invert(1);");
    expect(css).toContain(".rh-header.is-overlay:not(:hover):not(:focus-within):not(.menu-is-open) .brand-logo {\n  filter: none;");
    expect(css).toContain(".brand-eyebrow-dark .brand-eyebrow-logo {\n  filter: invert(1);");
    expect(css).toContain(".mobile-drawer-brand-logo {\n    display: block;");
    expect(css).toContain(".cart-brand-logo {\n  width:");
    expect(css).toContain(".rh-checkout-top img {\n  width:");
  });
});
```

- [ ] **Step 2: Run the focused test and verify the expected failure**

Run: `npm test -- --run tests/brandLogoReferences.test.js`

Expected: FAIL because `src/config/brand.js` does not exist and old black/white paths remain.

- [ ] **Step 3: Add the shared Logo constant and bind all five consumers**

Create `src/config/brand.js`:

```js
export const OAKVED_LOGO_SRC = "/assets/brand/oakved-logo-2026-white.png";
```

In every consumer, import the constant using the correct relative path and replace literal `src` values with `:src="OAKVED_LOGO_SRC"`. In `BrandEyebrow.vue`, remove the `computed` import and `logoSrc` computation; both tones use the same constant:

```vue
<script setup>
import { OAKVED_LOGO_SRC } from "../config/brand.js";
</script>

<img class="brand-eyebrow-logo" :src="OAKVED_LOGO_SRC" alt="Oakved" />
```

- [ ] **Step 4: Reverse the header filter and add light-surface filters**

Update `src/styles.css` so the white asset is black by default, white only on the untouched overlay, and black on all explicitly light surfaces:

```css
.brand-logo {
  /* existing size/layout declarations remain */
  filter: invert(1);
  transition: filter var(--rh-header-reveal-duration) ease;
}

.rh-header.is-overlay:not(:hover):not(:focus-within):not(.menu-is-open) .brand-logo {
  filter: none;
}

.brand-eyebrow-dark .brand-eyebrow-logo,
.mobile-drawer-brand-logo,
.cart-brand-logo,
.rh-checkout-top img {
  filter: invert(1);
}
```

Keep default/light-tone eyebrow and home hero declarations unfiltered so their Logo remains white.

- [ ] **Step 5: Run the focused test and confirm it passes**

Run: `npm test -- --run tests/brandLogoReferences.test.js`

Expected: PASS, 2 tests passed.

- [ ] **Step 6: Search for legacy references**

Run: `rg -n "oakved-logo-(black|white)\.png" src public index.html`

Expected: no output from `src` or `index.html`; only the two unused legacy files may still exist under `public` as filenames.

- [ ] **Step 7: Commit the reference migration**

```bash
git add "furniture web/src/config/brand.js" "furniture web/tests/brandLogoReferences.test.js" "furniture web/src/components/RhHeader.vue" "furniture web/src/pages/HomePage.vue" "furniture web/src/components/BrandEyebrow.vue" "furniture web/src/components/CartDrawer.vue" "furniture web/src/pages/CheckoutPage.vue" "furniture web/src/styles.css"
git commit -m "feat: migrate storefront to unified Oakved logo"
```

### Task 2: Prepare and verify the optimized transparent asset

**Files:**
- Create: `furniture web/public/assets/brand/oakved-logo-2026-white.png`
- Create: `furniture web/tests/brandLogoAsset.test.js`

**Interfaces:**
- Consumes: Source file `D:/Documents/WXWork/1688858083816459/Cache/File/2026-07/白底白logo-2026.7.16-01.png`.
- Produces: RGBA PNG at `public/assets/brand/oakved-logo-2026-white.png`, with a 2048px visible Logo width plus 32px transparent padding on each side.

- [ ] **Step 1: Write the failing PNG contract test**

Create `tests/brandLogoAsset.test.js`:

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("Oakved 2026 Logo asset", () => {
  it("ships an optimized RGBA PNG for high-density web displays", () => {
    const png = readFileSync(new URL("../public/assets/brand/oakved-logo-2026-white.png", import.meta.url));
    expect(png.subarray(1, 4).toString("ascii")).toBe("PNG");
    expect(png.readUInt32BE(16)).toBe(2112);
    expect(png.readUInt32BE(20)).toBeGreaterThan(550);
    expect(png.readUInt32BE(20)).toBeLessThan(700);
    expect(png[25]).toBe(6);
    expect(png.byteLength).toBeLessThan(250_000);
  });
});
```

- [ ] **Step 2: Run the asset test and verify it fails**

Run: `npm test -- --run tests/brandLogoAsset.test.js`

Expected: FAIL with `ENOENT` because the optimized PNG does not exist.

- [ ] **Step 3: Crop and downscale the supplied transparent white source**

Use bundled Python/Pillow to load the RGBA source, crop the Alpha bounding box, resize the visible content to exactly 2048px wide with Lanczos resampling, add 32px transparent padding, and save optimized PNG output. Do not recolor or redraw the Logo.

```python
from pathlib import Path
from PIL import Image

source = Path(r"D:/Documents/WXWork/1688858083816459/Cache/File/2026-07/白底白logo-2026.7.16-01.png")
output = Path(r"D:/code/furniture web/public/assets/brand/oakved-logo-2026-white.png")
image = Image.open(source).convert("RGBA")
bounds = image.getchannel("A").getbbox()
cropped = image.crop(bounds)
visible_width = 2048
visible_height = round(cropped.height * visible_width / cropped.width)
resized = cropped.resize((visible_width, visible_height), Image.Resampling.LANCZOS)
canvas = Image.new("RGBA", (visible_width + 64, visible_height + 64), (255, 255, 255, 0))
canvas.alpha_composite(resized, (32, 32))
canvas.save(output, optimize=True)
```

- [ ] **Step 4: Validate transparency and visible bounds**

Run a Pillow inspection that asserts mode `RGBA`, all four corner Alpha values equal `0`, visible Alpha bounds equal `(32, 32, 2080, height - 32)`, and all non-transparent RGB pixels are white.

Expected: all assertions succeed with no output.

- [ ] **Step 5: Run both focused Logo test files**

Run: `npm test -- --run tests/brandLogoReferences.test.js tests/brandLogoAsset.test.js`

Expected: PASS, 3 tests passed.

- [ ] **Step 6: Commit the optimized asset and its contract test**

```bash
git add "furniture web/public/assets/brand/oakved-logo-2026-white.png" "furniture web/tests/brandLogoAsset.test.js"
git commit -m "feat: add optimized transparent Oakved logo"
```

### Task 3: Full regression and visual verification

**Files:**
- Verify only; modify earlier task files only if a measured visual defect is found.

**Interfaces:**
- Consumes: `OAKVED_LOGO_SRC`, the optimized RGBA asset, and contextual CSS filters.
- Produces: Verified production build and screenshots/inspection evidence for dark and light surfaces.

- [ ] **Step 1: Run Logo tests, complete Vitest suite, and production build**

Run:

```bash
npm test -- --run tests/brandLogoReferences.test.js tests/brandLogoAsset.test.js
npm test
npm run build
```

Expected: all Logo tests pass, the full Vitest suite passes, and Vite reports a successful production build.

- [ ] **Step 2: Start the local Vite server**

Run: `npm run dev -- --port 5173`

Expected: Vite serves the storefront at `http://127.0.0.1:5173`.

- [ ] **Step 3: Inspect desktop dark and light surfaces**

Open the home page at desktop width and verify the overlay header and hero Logo are white, transparent, sharp, uncropped, and proportionate. Hover the header or open the navigation and verify the same asset becomes black on the light header. Open the cart or checkout page and verify the Logo is black and visible on white.

- [ ] **Step 4: Inspect mobile presentation**

At 390px viewport width, verify the hero Logo remains within the viewport, the overlay header Logo is white, and the mobile drawer Logo is black on its light panel.

- [ ] **Step 5: Final repository audit**

Run:

```bash
rg -n "oakved-logo-(black|white)\.png" src index.html
git diff --check
git status --short
```

Expected: no legacy path references, no whitespace errors, and only intended Logo implementation files differ from prior commits.

- [ ] **Step 6: Commit any measured visual adjustment**

Only if visual QA required a CSS size correction:

```bash
git add "furniture web/src/styles.css" "furniture web/tests/brandLogoReferences.test.js"
git commit -m "fix: tune Oakved logo presentation"
```

