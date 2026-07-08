# Order Comment Flow Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在当前 `yudao-cloud` 仓库内落地“整单集中评价”后端能力，提供整单批量评价接口，并保证整单评价事务原子性，供后续商城前端仓库按 spec 对接。

**Architecture:** 保留现有单订单项评价接口不动，新增一个整单批量评价接口，由 `AppTradeOrderController` 暴露、`TradeOrderUpdateService` 承接、`TradeOrderUpdateServiceImpl` 统一完成订单级校验、评论批量创建和状态汇总更新。接口层新增整单请求/响应 VO，转换层新增从整单评价项到商品评论 DTO 的映射，测试集中补到现有 `TradeOrderUpdateServiceImplTest` 中。

**Tech Stack:** Spring Boot, MyBatis-Plus, MapStruct, JUnit 5, Mockito, Maven

## Global Constraints

- 当前工作区不包含商城前端完整源码，本计划只覆盖当前仓库内可执行的后端改动；前端对接在实际商城前端仓库中按已批准 spec 落地。
- 保留现有 `POST /trade/order/item/create-comment` 接口，不做删除或语义变更。
- 新增接口固定为 `POST /trade/order/create-comments`，语义固定为“整单集中评价一次提交”。
- 整单批量评价必须使用单事务，任一订单项评论创建失败时整单回滚，不允许半成功状态。
- 整单评价页对应的后端请求继续沿用双评分字段：`descriptionScores` 与 `benefitScores`。
- 前端统一失败提示虽然是 `网络异常，请稍后重试`，但后端必须保留可定位的业务异常与日志上下文。

---

### Task 1: 建立整单评价接口契约

**Files:**
- Create: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/vo/AppTradeOrderCommentCreateReqVO.java`
- Create: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/vo/AppTradeOrderCommentCreateRespVO.java`
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/AppTradeOrderController.java`
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateService.java`
- Modify: `yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java`

**Interfaces:**
- Consumes: `TradeOrderUpdateService.createOrderItemCommentByMember(Long userId, AppTradeOrderItemCommentCreateReqVO createReqVO)`
- Produces: `TradeOrderUpdateService.createOrderCommentsByMember(Long userId, AppTradeOrderCommentCreateReqVO createReqVO)` and `POST /trade/order/create-comments`

- [ ] **Step 1: 写失败测试草稿并先定义接口契约**

```java
@Schema(description = "用户 App - 整单评价创建 Request VO")
@Data
public class AppTradeOrderCommentCreateReqVO {

    @NotNull(message = "交易订单编号不能为空")
    private Long orderId;

    @NotNull(message = "是否匿名不能为空")
    private Boolean anonymous;

    @NotEmpty(message = "评价商品列表不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "交易订单项编号不能为空")
        private Long orderItemId;
        @NotNull(message = "描述评分不能为空")
        private Integer descriptionScores;
        @NotNull(message = "服务评分不能为空")
        private Integer benefitScores;
        private String content;
        @Size(max = 9, message = "评论图片地址数组长度不能超过 9 张")
        private List<String> picUrls;
    }
}
```

- [ ] **Step 2: 运行编译前检查，确认当前仓库还没有同名类型**

Run: `rg -n "AppTradeOrderCommentCreateReqVO|createOrderCommentsByMember|/trade/order/create-comments" yudao-module-mall/yudao-module-trade-server/src/main/java`

Expected: no matches

- [ ] **Step 3: 增加整单接口请求/响应 VO、Controller 入口与 Service 签名**

```java
@PostMapping("/create-comments")
@Operation(summary = "创建整单商品评价")
public CommonResult<AppTradeOrderCommentCreateRespVO> createOrderComments(
        @Valid @RequestBody AppTradeOrderCommentCreateReqVO createReqVO) {
    return success(tradeOrderUpdateService.createOrderCommentsByMember(getLoginUserId(), createReqVO));
}
```

```java
AppTradeOrderCommentCreateRespVO createOrderCommentsByMember(
        Long userId, AppTradeOrderCommentCreateReqVO createReqVO);
```

```java
ErrorCode ORDER_COMMENT_ITEM_LIST_MISMATCH =
        new ErrorCode(1_011_000_042, "创建交易订单评价失败，订单项集合不完整或不匹配");
```

- [ ] **Step 4: 运行模块编译，确认接口层和枚举层通过**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -DskipTests compile`

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/vo/AppTradeOrderCommentCreateReqVO.java yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/vo/AppTradeOrderCommentCreateRespVO.java yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/order/AppTradeOrderController.java yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateService.java yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java
git commit -m "feat: add order comment batch api contract"
```

### Task 2: 实现整单批量评价服务与转换逻辑

**Files:**
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImpl.java`
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvert.java`

**Interfaces:**
- Consumes: `AppTradeOrderCommentCreateReqVO`, `ProductCommentApi.createComment(ProductCommentCreateReqDTO)`
- Produces: `AppTradeOrderCommentCreateRespVO createOrderCommentsByMember(Long userId, AppTradeOrderCommentCreateReqVO createReqVO)`

- [ ] **Step 1: 先写会失败的服务测试目标，明确成功路径与集合校验路径**

```java
@Test
public void testCreateOrderCommentsByMember_success() { }

@Test
public void testCreateOrderCommentsByMember_rejectsMismatchedItems() { }

@Test
public void testCreateOrderCommentsByMember_rollsBackWhenSecondCommentFails() { }
```

- [ ] **Step 2: 运行定向测试，确认评价批量方法尚不存在且测试会失败**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderUpdateServiceImplTest test`

Expected: FAIL with missing method or assertion failures for `createOrderCommentsByMember`

- [ ] **Step 3: 在 ServiceImpl 和 Convert 中实现整单评价主流程**

```java
@Override
@Transactional(rollbackFor = Exception.class)
@TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_COMMENT)
public AppTradeOrderCommentCreateRespVO createOrderCommentsByMember(
        Long userId, AppTradeOrderCommentCreateReqVO createReqVO) {
    TradeOrderDO order = tradeOrderMapper.selectOrderByIdAndUserId(createReqVO.getOrderId(), userId);
    if (order == null) {
        throw exception(ORDER_NOT_FOUND);
    }
    if (ObjectUtil.notEqual(order.getStatus(), TradeOrderStatusEnum.COMPLETED.getStatus())) {
        throw exception(ORDER_COMMENT_FAIL_STATUS_NOT_COMPLETED);
    }
    if (ObjectUtil.notEqual(order.getCommentStatus(), Boolean.FALSE)) {
        throw exception(ORDER_COMMENT_STATUS_NOT_FALSE);
    }

    List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderIdAndCommentStatus(order.getId(), Boolean.FALSE);
    validateBatchCommentItems(orderItems, createReqVO.getItems());

    List<Long> commentIds = new ArrayList<>(createReqVO.getItems().size());
    for (AppTradeOrderCommentCreateReqVO.Item item : createReqVO.getItems()) {
        TradeOrderItemDO orderItem = findOrderItem(orderItems, item.getOrderItemId());
        ProductCommentCreateReqDTO commentDTO = TradeOrderConvert.INSTANCE.convert05(item, createReqVO.getAnonymous(), orderItem);
        Long commentId = productCommentApi.createComment(commentDTO).getCheckedData();
        tradeOrderItemMapper.updateById(new TradeOrderItemDO().setId(orderItem.getId()).setCommentStatus(Boolean.TRUE));
        commentIds.add(commentId);
    }

    tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId()).setCommentStatus(Boolean.TRUE).setFinishTime(LocalDateTime.now()));
    TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus());
    return new AppTradeOrderCommentCreateRespVO().setOrderId(order.getId()).setCommentedItemCount(commentIds.size()).setCommentIds(commentIds);
}
```

```java
@Mappings({
    @Mapping(target = "skuId", source = "tradeOrderItemDO.skuId"),
    @Mapping(target = "orderId", source = "tradeOrderItemDO.orderId"),
    @Mapping(target = "orderItemId", source = "tradeOrderItemDO.id"),
    @Mapping(target = "descriptionScores", source = "item.descriptionScores"),
    @Mapping(target = "benefitScores", source = "item.benefitScores"),
    @Mapping(target = "content", source = "item.content"),
    @Mapping(target = "picUrls", source = "item.picUrls"),
    @Mapping(target = "anonymous", source = "anonymous"),
    @Mapping(target = "userId", source = "tradeOrderItemDO.userId")
})
ProductCommentCreateReqDTO convert05(AppTradeOrderCommentCreateReqVO.Item item, Boolean anonymous, TradeOrderItemDO tradeOrderItemDO);
```

- [ ] **Step 4: 运行定向测试和模块编译，确认服务实现与映射通过**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderUpdateServiceImplTest test`

Expected: PASS for the new batch-comment service tests

- [ ] **Step 5: Commit**

```bash
git add yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImpl.java yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvert.java
git commit -m "feat: implement order comment batch submission"
```

### Task 3: 补齐事务回滚与回归测试

**Files:**
- Modify: `yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImplTest.java`

**Interfaces:**
- Consumes: `TradeOrderUpdateServiceImpl.createOrderCommentsByMember(Long userId, AppTradeOrderCommentCreateReqVO createReqVO)`
- Produces: regression coverage for success, collection mismatch, completed-status guard, and rollback-on-failure behavior

- [ ] **Step 1: 在现有测试文件中补齐批量评价所需 mock、构造器与断言工具**

```java
@Mock
private ProductCommentApi productCommentApi;

private static AppTradeOrderCommentCreateReqVO buildBatchCommentReqVO(Long orderId, Long... itemIds) {
    AppTradeOrderCommentCreateReqVO reqVO = new AppTradeOrderCommentCreateReqVO();
    reqVO.setOrderId(orderId);
    reqVO.setAnonymous(Boolean.FALSE);
    reqVO.setItems(Arrays.stream(itemIds)
            .map(id -> new AppTradeOrderCommentCreateReqVO.Item()
                    .setOrderItemId(id)
                    .setDescriptionScores(5)
                    .setBenefitScores(5)
                    .setContent("")
                    .setPicUrls(Collections.emptyList()))
            .toList());
    return reqVO;
}
```

- [ ] **Step 2: 运行单测，确认新增 mock 和测试骨架能编译但仍有行为失败**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderUpdateServiceImplTest test`

Expected: FAIL only on behavior assertions, no compile errors

- [ ] **Step 3: 补齐三组关键测试**

```java
@Test
public void testCreateOrderCommentsByMember_success() {
    Long userId = 10L;
    Long orderId = 100L;
    TradeOrderDO order = new TradeOrderDO().setId(orderId)
            .setUserId(userId)
            .setStatus(TradeOrderStatusEnum.COMPLETED.getStatus())
            .setCommentStatus(Boolean.FALSE);
    List<TradeOrderItemDO> orderItems = Arrays.asList(
            new TradeOrderItemDO().setId(201L).setOrderId(orderId).setUserId(userId).setSkuId(301L).setCommentStatus(Boolean.FALSE),
            new TradeOrderItemDO().setId(202L).setOrderId(orderId).setUserId(userId).setSkuId(302L).setCommentStatus(Boolean.FALSE));

    when(tradeOrderMapper.selectOrderByIdAndUserId(orderId, userId)).thenReturn(order);
    when(tradeOrderItemMapper.selectListByOrderIdAndCommentStatus(orderId, Boolean.FALSE)).thenReturn(orderItems);
    when(productCommentApi.createComment(any())).thenReturn(CommonResult.success(9001L), CommonResult.success(9002L));

    AppTradeOrderCommentCreateRespVO respVO =
            tradeOrderUpdateService.createOrderCommentsByMember(userId, buildBatchCommentReqVO(orderId, 201L, 202L));

    assertEquals(2, respVO.getCommentedItemCount());
    verify(tradeOrderMapper).updateById(argThat(update -> Boolean.TRUE.equals(update.getCommentStatus())));
}
```

```java
@Test
public void testCreateOrderCommentsByMember_rejectsMismatchedItems() {
    // arrange order with two pending items but submit only one
    // expect ServiceException with ORDER_COMMENT_ITEM_LIST_MISMATCH
}
```

```java
@Test
public void testCreateOrderCommentsByMember_rollsBackWhenSecondCommentFails() {
    // first comment returns success, second throws ServiceException
    // verify transaction-facing behavior by asserting method throws
    // and verify orderMapper.updateById for order commentStatus was never called
}
```

- [ ] **Step 4: 运行定向测试与模块全量测试，确认新旧订单逻辑均未回归**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderUpdateServiceImplTest test`
Expected: PASS

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am test`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImplTest.java
git commit -m "test: cover order comment batch submission"
```

## Spec Coverage Check

- Spec 中“整单集中评价页 + 新增批量评价接口”由 Task 1 和 Task 2 覆盖。
- Spec 中“整单原子提交，任一失败整单回滚”由 Task 2 的事务实现和 Task 3 的回滚测试覆盖。
- Spec 中“保留旧接口 `/item/create-comment`”通过 Global Constraints 固定，不在任务中修改旧接口。
- Spec 中“前端统一失败提示、内容保留、入口展示”属于前端仓库任务，当前仓库无法给出精确文件路径，已明确标注为本计划外部前置条件。

## Placeholder Scan

- 本计划未使用 TBD、TODO、implement later 等占位描述。
- 所有执行命令、提交信息、文件路径均为当前仓库内可执行或可定位内容。

## Type Consistency

- Controller、Service、ServiceImpl 使用的整单请求类型统一为 `AppTradeOrderCommentCreateReqVO`。
- 整单响应类型统一为 `AppTradeOrderCommentCreateRespVO`。
- 转换层新增映射方法统一命名为 `convert05`，避免与现有 `convert04` 冲突。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-07-order-comment-backend-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
