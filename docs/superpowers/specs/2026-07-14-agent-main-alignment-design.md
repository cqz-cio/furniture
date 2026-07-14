# Agent/Main Alignment Design

## Goal

Align the Agent-facing behavior on `codex/agent-rag` and `main` with the latest verified Agent implementation at commit `b8fc449e`, while retaining non-Agent changes from the latest `origin/main`.

## Source of truth

- Agent behavior, Agent APIs, conversation memory, ERP product lookup, SKU matching, prompt construction, and Agent tests come from `codex/agent-rag` at `b8fc449e`.
- Non-Agent storefront, dashboard, checkout, localization, infrastructure, and commerce changes come from the latest `origin/main`.
- The integration is performed on `codex/agent-rag` first. `main` is updated only after the integrated Agent branch passes verification.
- The untracked `.superpowers/` directory is preserved and excluded from commits.

## Conflict policy

### Agent-owned files

For modify/delete conflicts where `origin/main` deleted an Agent file and `codex/agent-rag` modified it, retain the Agent version. This includes the Assistant panel and client, backend controller and service contracts, prompt builder, product search contracts, and their tests.

### Shared storefront files

Manually combine both sides for shared files such as `ProductImage.vue`, `i18n.js`, product pages, `productDetailModel.js`, `styles.css`, package manifests, and tests. The resulting files must retain current `main` storefront behavior while continuing to call the Agent and ERP-backed catalog contracts.

### Product catalog transition

`codex/agent-rag` removed `demoProducts.js` as part of the ERP-backed catalog transition, while `origin/main` continued to change that file. The integration must not silently restore demo data as the Agent source of truth. Any retained fixture or fallback must be isolated from production Agent recommendations and verified by the ERP-aligned catalog tests.

### Backend and infrastructure

Combine `application.yaml`, local infrastructure startup, and cart-service changes so that current `main` configuration remains valid and Agent/ERP settings remain available. Secrets and machine-specific values must not be committed.

## Verification

The integration is acceptable only when:

1. `origin/main` is an ancestor of `codex/agent-rag`.
2. The frontend full test suite and production build pass.
3. Agent acceptance, ERP catalog, product, trade, and configuration tests pass.
4. No conflict markers remain.
5. Agent-owned files retain the behavior covered by the tests introduced on `codex/agent-rag` through `b8fc449e`.
6. `main` is updated from the verified integration result without a second conflict-resolution pass.

## Repository normalization

The valid inner repository is used for integration. The broken outer worktree and its nested-repository layout are preserved during the merge. After the integration is committed and safely available through Git refs, a new linked worktree is created from `D:\code`; only then may the old outer directory be archived in a separate, explicitly approved cleanup step.
