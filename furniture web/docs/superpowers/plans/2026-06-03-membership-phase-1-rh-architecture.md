# Membership Phase 1 RH Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 RH-aligned membership information architecture: public membership pages, account membership shell, checkout auth split, gift registry landing, and footer/account entry points.

**Architecture:** Keep the current hand-written route map in `src/App.vue` and add focused page components under `src/pages`. Add `src/services/membershipNavigation.js` as a tested source of truth for routes, account menu order, checkout auth options, and membership CTA destinations.

**Tech Stack:** Vue 3, Vite, Vitest.

---

### Task 1: Membership Navigation Model

**Files:**
- Create: `src/services/membershipNavigation.js`
- Create: `tests/membershipNavigation.test.js`

- [ ] **Step 1: Write failing tests**

```js
import { describe, expect, it } from "vitest";
import {
  accountMenuItems,
  checkoutAuthOptions,
  getMembershipJoinTarget,
  membershipRoutes,
} from "../src/services/membershipNavigation.js";

describe("membership navigation model", () => {
  it("defines RH-aligned membership and account routes", () => {
    expect(membershipRoutes.membership).toBe("/membership");
    expect(membershipRoutes.membershipEnrollment).toBe("/membership/enrollment");
    expect(membershipRoutes.membershipTerms).toBe("/membership/terms");
    expect(membershipRoutes.accountMembership).toBe("/account/membership");
    expect(membershipRoutes.checkoutAuth).toBe("/checkout/auth");
    expect(membershipRoutes.giftRegistry).toBe("/gift-registry");
  });

  it("keeps the My Account menu order aligned with RH", () => {
    expect(accountMenuItems.map((item) => item.label)).toEqual([
      "Membership",
      "Payment Methods",
      "Order History",
      "Wish List",
      "Address Book",
      "Account Profile",
      "Gift Registry",
    ]);
  });

  it("uses three checkout auth choices and blocks guest membership purchase", () => {
    expect(checkoutAuthOptions.map((option) => option.key)).toEqual(["sign-in", "create-account", "guest"]);
    expect(checkoutAuthOptions.find((option) => option.key === "guest").disabledForMembership).toBe(true);
  });

  it("routes membership join actions by login and member state", () => {
    expect(getMembershipJoinTarget({ signedIn: false, memberStatus: "guest" })).toBe("/checkout/auth?intent=membership");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "not_member" })).toBe("/membership/enrollment");
    expect(getMembershipJoinTarget({ signedIn: true, memberStatus: "active" })).toBe("/account/membership");
  });
});
```

- [ ] **Step 2: Verify tests fail**

Run: `npm test -- tests/membershipNavigation.test.js`

- [ ] **Step 3: Implement minimal model**

Create route constants, account menu items, checkout auth options, and `getMembershipJoinTarget`.

- [ ] **Step 4: Verify tests pass**

Run: `npm test -- tests/membershipNavigation.test.js`

### Task 2: Phase 1 Pages And Routes

**Files:**
- Create: `src/pages/MembershipPage.vue`
- Create: `src/pages/MembershipEnrollmentPage.vue`
- Create: `src/pages/MembershipTermsPage.vue`
- Create: `src/pages/MembershipFaqPage.vue`
- Create: `src/pages/AccountPage.vue`
- Create: `src/pages/AccountMembershipPage.vue`
- Create: `src/pages/CheckoutAuthPage.vue`
- Create: `src/pages/GiftRegistryPage.vue`
- Modify: `src/App.vue`
- Modify: `src/styles.css`

- [ ] **Step 1: Add routes to `App.vue`**

Add route keys for the Phase 1 pages without introducing Vue Router.

- [ ] **Step 2: Add focused static pages**

Each page should expose RH-aligned content from the spec: public benefits, rules, account membership management shell, checkout auth split, and Gift Registry Find/Create/Manage entry points.

- [ ] **Step 3: Add scoped CSS classes**

Use restrained RH-like layout: full-width sections, narrow text columns, thin rules, and compact account panels.

- [ ] **Step 4: Verify build**

Run: `npm run build`

### Task 3: Footer Entry Points

**Files:**
- Modify: `src/data/rhLayout.js`
- Modify: `src/components/RhFooter.vue`
- Modify: `tests/rhLayout.test.js`

- [ ] **Step 1: Write failing footer expectations**

Assert that Resources contains RH Members Program and Customer Experience contains Membership FAQs and Gift Registry.

- [ ] **Step 2: Convert footer links to objects**

Keep existing labels, add `href` values for membership and gift registry links, and update `RhFooter.vue` to render the href.

- [ ] **Step 3: Verify tests pass**

Run: `npm test -- tests/rhLayout.test.js`

### Task 4: Final Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run unit tests**

Run: `npm test`

- [ ] **Step 2: Run production build**

Run: `npm run build`

- [ ] **Step 3: Review status**

Run: `git status --short`
