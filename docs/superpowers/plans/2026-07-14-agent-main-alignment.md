# Agent/Main Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a verified `codex/agent-rag` integration commit that contains the latest `origin/main` while preserving Agent behavior from `b8fc449e`, then align local `main` to that result.

**Architecture:** Resolve the integration on the valid inner repository and on the Agent branch first. Agent-owned delete/modify conflicts retain the Agent implementation; shared storefront and configuration conflicts are manually composed and verified with existing Agent, ERP, storefront, and backend tests. Repository cleanup is deferred until a clean linked worktree proves the integrated refs are recoverable.

**Tech Stack:** Git, Vue 3, Vite, Vitest, Java, Spring Boot, Maven, Redis-backed conversation storage, ERP product APIs.

## Global Constraints

- Preserve Agent behavior and contracts from `codex/agent-rag` commit `b8fc449e`.
- Preserve non-Agent changes from the latest `origin/main`.
- Do not add `.superpowers/` working records to a commit.
- Do not delete or move the broken outer worktree during code integration.
- Do not update `main` until all required verification commands pass.
- Do not push `main` without explicit user authorization.

---

### Task 1: Establish the integration baseline

**Files:**
- Verify: repository index and refs only

**Interfaces:**
- Consumes: `origin/main`, `codex/agent-rag`, and the clean tracked worktree.
- Produces: a reproducible baseline and safety ref for the merge.

- [ ] **Step 1: Verify worktree scope**

Run:

```powershell
git status --short --branch
git rev-parse origin/main codex/agent-rag origin/codex/agent-rag
```

Expected: no tracked changes; only `.superpowers/` is untracked; local and remote Agent tips equal `b8fc449e` before integration.

- [ ] **Step 2: Run the Agent-branch frontend baseline**

Run:

```powershell
Set-Location "furniture web"
npm test
npm run build
```

Expected: record the exact baseline result before merging. Do not reinterpret a pre-existing failure as a merge regression.

- [ ] **Step 3: Create the merge safety ref**

Run:

```powershell
git branch backup/agent-rag-pre-main-20260714 b8fc449e
```

Expected: the backup ref resolves exactly to `b8fc449e`.

### Task 2: Merge current main into the Agent branch

**Files:**
- Modify: Git index and the 34 conflict paths reported by `git merge-tree`
- Preserve: `.superpowers/`

**Interfaces:**
- Consumes: clean `codex/agent-rag` and latest `origin/main`.
- Produces: a conflict-resolved working tree where `origin/main` is a parent of the pending merge commit.

- [ ] **Step 1: Start the merge without committing**

Run:

```powershell
git merge --no-commit --no-ff origin/main
```

Expected: 34 conflicts: 12 content conflicts and 22 modify/delete conflicts.

- [ ] **Step 2: Retain Agent-owned implementations**

For each Agent-owned modify/delete conflict, restore the stage-3 Agent version and stage it:

```powershell
git checkout --theirs -- "furniture web/src/components/FurnitureAssistantPanel.vue"
git checkout --theirs -- "furniture web/src/services/furnitureAssistant.js"
git checkout --theirs -- "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/furniture"
git checkout --theirs -- "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture"
git checkout --theirs -- "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/furniture"
git add -- "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/src/services/furnitureAssistant.js" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product"
```

Expected: Agent controller, service, conversation, search, prompt, and test files remain present.

- [ ] **Step 3: Reconcile the shared frontend contracts**

Manually combine:

```text
furniture web/package.json
furniture web/package-lock.json
furniture web/src/components/ProductImage.vue
furniture web/src/i18n.js
furniture web/src/pages/SofaPdpPage.vue
furniture web/src/pages/SofasPlpPage.vue
furniture web/src/services/productDetailModel.js
furniture web/src/styles.css
furniture web/tests/productDetailModel.test.js
```

The result must preserve `main` localization and storefront structure while retaining Agent invocation, truthful product matching, ERP-backed product fields, and image fallback behavior.

- [ ] **Step 4: Reconcile catalog ownership**

Resolve `furniture web/src/data/demoProducts.js` so production Agent recommendations continue to use the ERP-backed catalog. If the file is retained for storefront compatibility, tests must prove it is not the Agent recommendation source.

- [ ] **Step 5: Reconcile backend configuration and cart behavior**

Manually combine:

```text
yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1
yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/resources/application.yaml
yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImpl.java
```

Expected: current infrastructure, Agent settings, Redis conversation storage, ERP stock integration, and current cart semantics are all retained.

- [ ] **Step 6: Verify conflict resolution completeness**

Run:

```powershell
git diff --name-only --diff-filter=U
git grep -n -e "<<<<<<<" -e "=======" -e ">>>>>>>" -- . ":(exclude).superpowers"
```

Expected: both commands produce no conflict paths or markers.

### Task 3: Verify and repair the integrated behavior

**Files:**
- Test: `furniture web/tests/*.test.js`
- Test: Agent, product, trade, and ERP Java test packages
- Modify: only files required by a failing integration test

**Interfaces:**
- Consumes: conflict-resolved integration tree.
- Produces: tested frontend and backend behavior with a zero-failure gate.

- [ ] **Step 1: Run focused Agent frontend tests**

Run:

```powershell
npm test -- --run tests/furnitureAssistantClient.test.js tests/furnitureAssistantPanel.test.js tests/furnitureAgentAcceptanceDataset.test.js tests/erpAlignedCatalogUi.test.js tests/productDetailModel.test.js tests/productImage.test.js
```

Expected: all focused Agent and catalog tests pass.

- [ ] **Step 2: Run the complete frontend verification**

Run:

```powershell
npm test
npm run build
```

Expected: zero failed test files, zero failed tests, and a successful production build.

- [ ] **Step 3: Run focused backend verification**

Run from `yudao电商管理平台前后端/yudao-cloud`:

```powershell
mvn -pl yudao-module-mall/yudao-module-product-server,yudao-module-mall/yudao-module-trade-server,yudao-module-erp/yudao-module-erp-server -am test
```

Expected: the selected reactor builds successfully and all Agent, product, trade, and ERP tests pass.

- [ ] **Step 4: Fix each integration failure test-first**

For every newly discovered behavior gap, add or narrow a failing regression test, run it to confirm the expected failure, make the smallest production change, and rerun the focused test before rerunning the full suite.

### Task 4: Commit the integration and align local main

**Files:**
- Commit: all resolved integration files and the two alignment documents
- Exclude: `.superpowers/`

**Interfaces:**
- Consumes: fully verified integration tree.
- Produces: a merge commit on `codex/agent-rag` and a local `main` pointing to the same verified history.

- [ ] **Step 1: Verify staged scope**

Run:

```powershell
git status --short
git diff --check
git diff --cached --stat
```

Expected: no `.superpowers/` paths are staged and no whitespace errors are reported.

- [ ] **Step 2: Commit the Agent integration**

Run:

```powershell
git commit -m "merge: align agent implementation with current main"
```

Expected: a merge commit with parents from the previous Agent tip and latest `origin/main`.

- [ ] **Step 3: Prove ancestry and rerun final verification**

Run:

```powershell
git merge-base --is-ancestor origin/main codex/agent-rag
git status --short --branch
```

Expected: ancestry command exits 0; tracked worktree is clean.

- [ ] **Step 4: Align local main without a second merge resolution**

Run only after verification:

```powershell
git switch main
git merge --ff-only origin/main
git merge --ff-only codex/agent-rag
```

Expected: local `main` and `codex/agent-rag` resolve to the same verified commit. Do not push `main` without explicit authorization.

### Task 5: Normalize the repository layout

**Files:**
- Create later: `D:\code\.worktrees\agent-rag-clean`
- Preserve: `D:\code\.worktrees\codex-permanent-agent`

**Interfaces:**
- Consumes: verified and safely referenced integration commit.
- Produces: a clean linked worktree managed by `D:\code\.git` and an explicit archive candidate for the broken outer directory.

- [ ] **Step 1: Synchronize the canonical repository refs**

Run after the verified Agent ref is available to `D:\code`:

```powershell
git -C D:\code fetch origin
git -C D:\code branch -f codex/agent-rag origin/codex/agent-rag
```

Expected: `D:\code` resolves `codex/agent-rag` to the verified integration commit.

- [ ] **Step 2: Create a clean linked worktree at a new path**

Run:

```powershell
git -C D:\code worktree add D:\code\.worktrees\agent-rag-clean codex/agent-rag
```

Expected: the new worktree has a valid `.git` file and a clean tracked status.

- [ ] **Step 3: Verify before cleanup**

Run the ancestry, status, frontend test, frontend build, and focused backend commands again from the clean worktree. Archive or remove the old outer directory only in a later explicitly approved cleanup operation.
