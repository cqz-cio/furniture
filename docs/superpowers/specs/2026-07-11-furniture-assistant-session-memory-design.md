# 家具导购 Agent 短期记忆设计

## 目标

为现有家具导购助手增加可恢复的短期会话记忆，使用户在收起助手、重新打开助手或刷新网页后，仍能继续当前导购对话。记忆不仅保存最近消息，还保存结构化购物需求、最近推荐商品以及用户明确排除的商品。

第一版不建设永久用户画像，不把临时购物需求自动写入长期账户数据。

## 范围

本期包含：

- 由后端生成并返回 `conversationId`。
- Redis 保存会话状态，默认闲置 24 小时后过期，每次有效访问续期。
- 前端在浏览器本地保存 `conversationId`。
- 收起助手只改变界面显示状态，不清空消息、需求或后端会话。
- 页面刷新或重新进入网站时，前端通过 `conversationId` 恢复会话。
- 保存最近 12 条消息、结构化购物需求、最近推荐商品和排除商品。
- 支持“第一款”“便宜一点”“换成真皮”“不要刚才那款”等多轮表达。
- 增加中文编码与中文多轮回归测试。

本期不包含：

- 跨 24 小时的永久记忆。
- 跨设备同步。
- 未经确认写入长期用户画像。
- 历史订单画像、向量记忆或多 Agent 共享记忆。
- 把实时价格、库存当作长期事实缓存。

## 核心架构

```text
FurnitureAssistantPanel
  -> 本地读取 conversationId
  -> POST /app-api/ai/furniture-assistant/chat
  -> FurnitureAssistantService
  -> FurnitureAssistantConversationStore (Redis)
  -> 需求提取与状态合并
  -> ERP 商品搜索
  -> 保存消息、需求和推荐结果
  -> 返回 conversationId、answer、products、requirements
```

Redis Key：

```text
furniture:assistant:conversation:{conversationId}
```

值采用 JSON 序列化，TTL 默认 24 小时。Yudao 已提供 JSON `RedisTemplate<String, Object>`，本功能复用现有 Redis 基础设施。

## 会话数据模型

```json
{
  "conversationId": "uuid",
  "userId": null,
  "messages": [
    { "role": "user", "content": "奶油风，家里有猫", "createdAt": 0 },
    { "role": "assistant", "content": "我为你筛选了三款", "createdAt": 0 }
  ],
  "requirements": {
    "category": "沙发",
    "budgetMin": null,
    "budgetMax": 8000,
    "styles": ["奶油风"],
    "colors": ["米白色"],
    "materials": ["布艺"],
    "roomSize": 20,
    "seatCount": null,
    "hasChildren": null,
    "hasPets": true,
    "preferredFeatures": ["耐磨", "易清洁"]
  },
  "lastRecommendations": [
    { "productId": 1001, "skuId": 1001, "price": 7600 }
  ],
  "likedProductIds": [],
  "excludedProductIds": [],
  "updatedAt": 0
}
```

价格和库存仅用于解释当前推荐上下文。下一次推荐前必须重新从 ERP 查询实时值。

## API 设计

聊天请求扩展为：

```json
{
  "conversationId": "可选；首次请求为空",
  "message": "第一款太贵了，换便宜一点"
}
```

聊天响应扩展为：

```json
{
  "conversationId": "后端生成或沿用的 UUID",
  "answer": "我保留其他条件，重新筛选价格更低的款式。",
  "products": [],
  "sources": [],
  "requirements": {},
  "missingFields": []
}
```

增加恢复接口：

```text
GET /app-api/ai/furniture-assistant/conversations/{conversationId}
```

会话存在时返回可展示消息、需求和最后推荐结果；不存在或已过期时返回明确的“会话不存在”业务结果，前端清除旧 ID 并开始新会话。

增加重置接口：

```text
DELETE /app-api/ai/furniture-assistant/conversations/{conversationId}
```

前端仅在用户点击“开始新对话”时调用。收起和普通关闭绝不调用删除接口。

## 前端行为

- `open=false` 只收起面板，组件状态和 `conversationId` 保持不变。
- `conversationId` 保存到 `localStorage`，Key 使用版本化名称，例如 `furniture-assistant-conversation-id:v1`。
- 组件启动时若存在 ID，调用恢复接口；恢复成功后渲染历史消息和最后推荐。
- 恢复失败或 Redis 已过期时，清除旧 ID，展示默认欢迎语。
- 首次聊天响应返回 ID 后立即写入本地存储。
- “开始新对话”需要用户主动触发，成功删除后清空本地状态。
- 不在浏览器中持久化完整聊天内容和用户家庭信息，浏览器只保存不透明的会话 ID。

## 需求状态合并规则

- 新增条件与已有条件合并。
- 用户明确修改条件时，以最新表达覆盖对应字段，其他字段保持不变。
- “便宜一点”以最近推荐价格为参照收紧预算；无法确定参照时追问具体预算。
- “第一款/第二款”根据 `lastRecommendations` 解析。
- “不要这款”将对应商品加入 `excludedProductIds`。
- 新查询必须排除 `excludedProductIds`，并尽量避免原样重复上一批结果。
- 用户说“重新开始”时必须二次确认或由明确的新对话操作完成，不根据模糊表达静默删除会话。
- 后端校验预算范围、集合大小、消息长度和商品 ID，不信任模型直接生成的状态。

第一版条件提取以确定性中文规则为主，模型可作为补充提取器，但模型输出必须经过白名单字段反序列化与校验。

## 容量与安全

- 每个会话最多保留 12 条消息，每条消息限制长度。
- 最近推荐和排除商品分别限制数量，防止 Redis 值无限增长。
- `conversationId` 使用不可预测 UUID，不采用自增 ID。
- 登录用户的会话可记录 `userId`；读取时校验用户归属。匿名会话使用会话 ID 访问，但不得包含账户敏感信息。
- Redis 故障时聊天接口应降级为无记忆单轮导购，不应导致整个商品服务不可用。
- 日志不记录完整家庭信息和原始敏感对话。

## 中文编码处理

现有 Java 和文档源文件实际为正确 UTF-8，先前乱码来自 PowerShell 的错误显示解码。因此不修改正常中文文案，只进行以下加固：

- 启动与测试脚本统一 UTF-8 控制台输出。
- Java 编译与资源过滤保持 UTF-8。
- HTTP 请求和响应明确使用 UTF-8 JSON。
- 增加中文源码与接口回归用例，检测常见乱码片段。

## 测试与验收

必须覆盖：

1. 首次请求生成 `conversationId` 并写入 Redis。
2. 第二轮沿用会话并保留第一轮预算和品类。
3. 收起再打开面板，当前消息和推荐不丢失。
4. 刷新页面后通过恢复接口还原会话。
5. Redis 会话过期后前端安全开始新会话。
6. “第一款太贵”能够识别商品、排除商品并调整预算。
7. “换成真皮，其他不变”只覆盖材质。
8. 商品价格和库存每次推荐都来自 ERP 当前数据。
9. Redis 不可用时降级为单轮回答。
10. 中文输入、中文知识和中文推荐理由没有乱码。

端到端验收对话：

```text
用户：想要 8000 元以内的布艺沙发
助手：你偏好什么风格？
用户：奶油风，家里有猫
助手：返回 A、B、C 三款
用户收起助手并重新打开
助手：仍展示原对话和 A、B、C
用户刷新页面
助手：通过 conversationId 恢复原对话
用户：第一款太贵，换个便宜一点的
助手：保留沙发、布艺、奶油风、有猫等条件，排除 A 后重新查询
```

## 后续演进

短期记忆稳定后，再增加 MySQL 用户画像。短期会话中发现的偏好只作为候选项，必须经用户确认后才能写入长期画像，并提供查看、修改、删除和关闭个性化功能。
