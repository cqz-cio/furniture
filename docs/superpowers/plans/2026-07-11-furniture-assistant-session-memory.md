# Furniture Assistant Session Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Redis-backed short-term memory so collapsing, reopening, or refreshing the storefront does not lose the active furniture-shopping conversation.

**Architecture:** The product service owns a bounded conversation aggregate stored through Yudao's JSON Redis template. Each chat turn loads or creates the conversation, merges structured requirements, queries live ERP product data, and saves messages plus recommendation references. The browser persists only an opaque conversation ID and restores server-side state on mount.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring Data Redis, JUnit/Mockito, Vue 3, Vite, Vitest, `localStorage`.

## Global Constraints

- Redis idle TTL is 24 hours and renews after successful access.
- Keep at most 12 messages per conversation.
- The browser stores only `conversationId`, not messages or household data.
- Collapsing the panel never deletes the Redis conversation.
- Product price and stock are fetched from ERP for every recommendation.
- Redis failure degrades to stateless chat instead of failing the storefront.
- Preserve `/app-api/ai/furniture-assistant/chat` compatibility.
- Stay on JDK 8; do not add Spring AI to the product module.
- Use UTF-8 for source, scripts, requests, and responses.
- Never stage the existing unrelated startup-script changes with this feature.

---

### Task 1: Conversation Domain and Bounded State

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureAssistantConversation.java`
- Create: `.../conversation/FurnitureAssistantRequirements.java`
- Test: `.../src/test/java/cn/iocoder/yudao/module/product/service/furniture/conversation/FurnitureAssistantConversationTest.java`

**Interfaces:**
- Produces: `newConversation(String id)`, `appendMessage(String role, String content)`, and recommendation references containing product ID, SKU ID, and price.
- Produces: structured category, budget, style, color, material, room, household, and preferred-feature fields.

- [ ] **Step 1: Write the failing bounded-message test**

```java
@Test
void shouldKeepOnlyLatestTwelveMessages() {
    FurnitureAssistantConversation value = FurnitureAssistantConversation.newConversation("c-1");
    for (int i = 0; i < 14; i++) value.appendMessage("user", "message-" + i);
    assertEquals(12, value.getMessages().size());
    assertEquals("message-2", value.getMessages().get(0).getContent());
}
```

- [ ] **Step 2: Run the test and verify compilation fails**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -Dtest=FurnitureAssistantConversationTest test
```

- [ ] **Step 3: Implement Jackson-compatible Lombok POJOs**

`appendMessage` rejects blank content, timestamps the entry, and removes oldest entries until size is 12. Lists initialize empty and never expose null values.

- [ ] **Step 4: Run the test and expect PASS**

- [ ] **Step 5: Commit only Task 1 files**

```powershell
git commit -m "feat(ai): add assistant conversation state"
```

### Task 2: Redis Store With Sliding TTL

**Files:**
- Modify: `.../yudao-module-product-server/pom.xml`
- Modify: `.../service/furniture/FurnitureAssistantProperties.java`
- Create: `.../conversation/FurnitureAssistantConversationStore.java`
- Create: `.../conversation/RedisFurnitureAssistantConversationStore.java`
- Test: `.../conversation/RedisFurnitureAssistantConversationStoreTest.java`

**Interfaces:**
- Produces: `Optional<FurnitureAssistantConversation> find(String id)`, `void save(FurnitureAssistantConversation value)`, `void delete(String id)`.

- [ ] **Step 1: Write mocked Redis tests**

```java
@Test
void saveShouldUseNamespacedKeyAndTwentyFourHourTtl() {
    store.save(FurnitureAssistantConversation.newConversation("c-1"));
    verify(valueOperations).set(eq("furniture:assistant:conversation:c-1"), any(), eq(Duration.ofHours(24)));
}
```

Also verify `find` renews TTL and Redis exceptions return `Optional.empty()` without leaking conversation content into logs.

- [ ] **Step 2: Run tests and verify failure for missing store types**

- [ ] **Step 3: Add `yudao-spring-boot-starter-redis` and implement the store**

Add properties `memoryEnabled=true`, `memoryTtlHours=24`, and `memoryMaxMessages=12`. Reuse `RedisTemplate<String,Object>` and prefix keys with `furniture:assistant:conversation:`.

- [ ] **Step 4: Run store plus existing furniture tests and expect PASS without live Redis**

- [ ] **Step 5: Commit**

```powershell
git commit -m "feat(ai): persist assistant sessions in redis"
```

### Task 3: Deterministic Multi-Turn Requirement Merger

**Files:**
- Create: `.../conversation/FurnitureAssistantRequirementMerger.java`
- Create: `.../conversation/FurnitureAssistantRequirementUpdate.java`
- Test: `.../conversation/FurnitureAssistantRequirementMergerTest.java`

**Interfaces:**
- Produces: `MergeResult merge(FurnitureAssistantConversation conversation, String message)` with updated requirements, exclusions, and clarification state.

- [ ] **Step 1: Write failing transition tests**

Required cases:

```java
assertState("想要8000以内的布艺沙发", "沙发", 8000, "布艺");
assertPreserves("奶油风，家里有猫", "沙发", 8000, "奶油风", true);
assertExcludesFirst("第一款太贵，换便宜一点", 1001L);
assertOnlyReplacesMaterial("换成真皮，其他不变", "真皮");
```

- [ ] **Step 2: Run tests and verify missing-class failure**

- [ ] **Step 3: Implement normalized Chinese/English extraction and merge**

Recognize category, numeric budget, style, color, material, pets/children, ordinal references, exclusions, and “cheaper.” Explicit new values overwrite the same field; unmentioned fields remain. Invalid or ambiguous relative budgets request one clarification.

- [ ] **Step 4: Run merger tests and expect all transitions PASS**

- [ ] **Step 5: Commit**

```powershell
git commit -m "feat(ai): merge multi-turn shopping requirements"
```

### Task 4: Search ERP From Remembered Requirements

**Files:**
- Modify: `.../FurnitureProductSearchRequest.java`
- Modify: `.../FurnitureProductSearchTool.java`
- Test: `.../FurnitureProductSearchToolTest.java`

**Interfaces:**
- Consumes structured requirements and excluded IDs.
- Produces live products with current price/stock and exclusions applied.

- [ ] **Step 1: Add failing tests for remembered category/budget, exclusions, and fresh ERP values**

- [ ] **Step 2: Run tests and verify the structured overload is absent**

- [ ] **Step 3: Add the exact overload**

```java
FurnitureProductSearchResult searchForAssistant(
        String message,
        FurnitureAssistantRequirements requirements,
        List<Long> excludedProductIds)
```

Use structured values before message fallbacks. Filter exclusions and out-of-stock entries before limiting results.

- [ ] **Step 4: Run old and new search tests and expect PASS**

- [ ] **Step 5: Commit**

```powershell
git commit -m "feat(ai): search products from remembered requirements"
```

### Task 5: Memory-Aware Chat, Recovery, and Reset APIs

**Files:**
- Modify: `.../vo/FurnitureAssistantChatReqVO.java`
- Modify: `.../vo/FurnitureAssistantChatRespVO.java`
- Create: `.../vo/FurnitureAssistantConversationRespVO.java`
- Modify: `.../FurnitureAssistantService.java`
- Modify: `.../FurnitureAssistantServiceImpl.java`
- Modify: `.../AppFurnitureAssistantController.java`
- Test: `.../FurnitureAssistantServiceImplTest.java`
- Test: `.../AppFurnitureAssistantControllerTest.java`

**Interfaces:**
- Chat accepts optional UUID `conversationId` and returns the active ID, requirements, and missing fields.
- Recovery: `getConversation(String id)`.
- Reset: `deleteConversation(String id)`.

- [ ] **Step 1: Write failing service tests**

Cover create-on-first-turn, load/merge on follow-up, two messages appended per turn, recommendation references, and stateless fallback when Redis misses or fails.

- [ ] **Step 2: Write failing controller tests for POST, GET, and DELETE**

```text
POST /ai/furniture-assistant/chat
GET /ai/furniture-assistant/conversations/{conversationId}
DELETE /ai/furniture-assistant/conversations/{conversationId}
```

- [ ] **Step 3: Extend request/response while preserving existing fields**

- [ ] **Step 4: Implement the orchestration order**

Load/create → append user message → merge → retrieve knowledge → query live products → generate answer → append assistant message → update references → save → respond. Recovery returns bounded messages and requirements. Delete removes only the exact namespaced key.

- [ ] **Step 5: Run focused backend tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -Dtest='*FurnitureAssistant*,FurnitureProductSearchToolTest' test
```

- [ ] **Step 6: Commit**

```powershell
git commit -m "feat(ai): add recoverable assistant chat sessions"
```

### Task 6: Frontend Conversation Client

**Files:**
- Modify: `furniture web/src/services/furnitureAssistant.js`
- Modify: `furniture web/tests/furnitureAssistantClient.test.js`

**Interfaces:**
- Produces: `sendFurnitureAssistantMessage(message, { conversationId })`.
- Produces: `getFurnitureAssistantConversation(conversationId, options)`.
- Produces: `deleteFurnitureAssistantConversation(conversationId, options)`.

- [ ] **Step 1: Write failing tests for chat ID, recovery GET, reset DELETE, and normalization**

- [ ] **Step 2: Run client tests and verify failure**

```powershell
npm.cmd test -- --run tests/furnitureAssistantClient.test.js
```

- [ ] **Step 3: Implement the contract**

Omit the ID on first request, preserve returned messages/requirements, URL-encode recovery IDs, and keep mock mode working with a stable mock ID.

- [ ] **Step 4: Run client tests and expect PASS**

- [ ] **Step 5: Commit**

```powershell
git commit -m "feat(web): add assistant conversation client"
```

### Task 7: Restore Across Collapse and Refresh

**Files:**
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`
- Modify if visible copy is added: `furniture web/src/i18n.js`

**Interfaces:**
- Persists only `furniture-assistant-conversation-id:v1` in `localStorage`.

- [ ] **Step 1: Add failing panel tests**

Verify close only sets `open=false`; reopen retains state; first response stores ID; mount restores history; expired recovery clears ID; explicit new conversation invokes delete.

- [ ] **Step 2: Run panel tests and verify failure**

```powershell
npm.cmd test -- --run tests/furnitureAssistantPanel.test.js
```

- [ ] **Step 3: Implement ID persistence and mount-time restoration**

Add `conversationId` and `restoringConversation`. Pass ID on every chat call. Replace local history only after successful recovery. The existing close action remains visibility-only.

- [ ] **Step 4: Implement explicit “new conversation” reset**

Clear local state after successful delete or confirmed server absence. Add every visible string to `en`, `zh-CN`, and `fr`.

- [ ] **Step 5: Run panel and i18n tests**

```powershell
npm.cmd test -- --run tests/furnitureAssistantPanel.test.js tests/i18n.test.js
```

- [ ] **Step 6: Commit**

```powershell
git commit -m "feat(web): restore assistant sessions after collapse"
```

### Task 8: UTF-8 and End-to-End Verification

**Files:**
- Modify: `test-furniture-agent.ps1`
- Preserve unrelated edits in all startup scripts.

**Interfaces:**
- Produces a repeatable two-turn smoke test and final verification evidence.

- [ ] **Step 1: Add two-turn smoke coverage**

The first request captures `conversationId`; the second sends `第一款太贵，换个便宜一点的`. Assert the same ID returns, requirements remain, the excluded product does not reappear, and output contains none of `锟斤拷`, `浼氬憳`, or `瀹跺叿`.

- [ ] **Step 2: Run frontend tests and build**

```powershell
cd "furniture web"
npm.cmd test -- --run tests/furnitureAssistantClient.test.js tests/furnitureAssistantPanel.test.js tests/i18n.test.js
npm.cmd run build
```

- [ ] **Step 3: Run focused backend tests**

```powershell
cd "yudao电商管理平台前后端\yudao-cloud"
mvn.cmd -pl yudao-module-mall/yudao-module-product-server -Dtest='*FurnitureAssistant*,FurnitureProductSearchToolTest' test
```

- [ ] **Step 4: Run the live smoke script with Redis/backend running**

```powershell
cd "C:\w\furniture-agent-rag"
.\test-furniture-agent.ps1
```

- [ ] **Step 5: Check scope and commit regression changes**

```powershell
git status --short
git diff --check
git diff --stat
git commit -m "test(ai): cover assistant session memory"
```

## Completion Criteria

- Collapse/reopen leaves the conversation untouched.
- Refresh restores via the locally stored opaque ID.
- Follow-ups preserve and update structured requirements.
- Redis expires after 24 idle hours and renews during activity.
- Redis failure leaves stateless chat functional.
- ERP price and stock are fresh for every recommendation.
- Backend tests, frontend tests/build, and live smoke pass.
- No unrelated workspace changes enter feature commits.
