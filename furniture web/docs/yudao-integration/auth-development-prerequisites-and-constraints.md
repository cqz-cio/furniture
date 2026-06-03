# Furniture Web 会员登录注册开发前置文档与工作约束

## 1. 文档目的

本文用于约束家具前台会员登录、注册、退出、会话保持与受保护业务入口的开发工作。

当前前台已经可以打开账户弹窗，但没有真正调用 Yudao 会员认证接口；购物车、结算、订单页面仍依赖手动填写 `YUDAO_APP_TOKEN`。后续开发必须先按本文完成方案拆解、边界确认和测试准备，再进入代码实现。

## 2. 当前基线

### 2.1 前台现状

- 账户入口位于 `src/components/RhHeader.vue`。
- 弹窗内的 `SIGN IN` 按钮没有提交逻辑。
- `Create an Account`、`Forgot Password?`、`Sign In With a Secure Link`、`Trade Program Sign In` 都是 `href="#"` 静态链接。
- `AuthTokenPanel.vue` 只提供开发联调用的手动 token 输入。
- `src/services/yudaoClient.js` 只负责读取 token、加 `Authorization: Bearer ...`、封装商品/购物车/地址/订单接口。
- `CheckoutPage.vue` 和 `OrdersPage.vue` 在没有 token 时提示用户手动添加 Yudao App token。

### 2.2 后端现状

Yudao App API 已存在会员认证接口，路径前缀为：

```text
http://127.0.0.1:48080/app-api
```

可用认证接口：

- `POST /member/auth/login`
- `POST /member/auth/sms-login`
- `POST /member/auth/send-sms-code`
- `POST /member/auth/refresh-token`
- `POST /member/auth/logout`
- `POST /member/auth/validate-sms-code`

后端没有发现独立的 `/register` App API。注册语义主要来自短信登录：`sms-login` 会在手机号不存在时调用 `createUserIfAbsent` 自动创建会员。

## 3. 开发目标

### 3.1 必须完成

- 用户可从前台账户弹窗完成登录。
- 登录成功后保存 Yudao 返回的 `accessToken`、`refreshToken`、`expiresTime`、`userId`。
- 所有受保护 App API 自动携带 `Authorization: Bearer <accessToken>`。
- 登录成功后远程购物车、地址、结算、订单链路可复用同一个会话。
- 用户可退出登录，前端清理本地会话，并调用后端 logout。
- token 过期时有明确处理策略：刷新成功则重试当前请求；刷新失败则清理会话并提示重新登录。
- 原有手动 token 面板保留为开发联调入口，但不能作为正式会员登录系统的主要入口。

### 3.2 建议优先完成

首个可交付版本优先做“手机号 + 短信验证码登录/注册一体化”：

- 发送验证码：`POST /member/auth/send-sms-code`，`scene = 1`。
- 登录/自动注册：`POST /member/auth/sms-login`。
- 如果手机号不存在，由后端自动创建会员。

密码登录可以作为第二优先级：

- 仅适用于后端已经存在手机号和密码的会员。
- 不要在前台自行发明密码注册接口。
- 如果需要“邮箱 + 密码注册”，必须先明确这是后端扩展任务，不能只在前端伪造。

## 4. 非目标范围

本阶段不做以下内容：

- 不修改 Yudao Java 后端，除非用户明确批准。
- 不调用 Yudao Admin API。
- 不实现真实邮箱注册，因为当前 Yudao 会员认证基线是手机号。
- 不接真实支付网关。
- 不实现第三方登录、微信小程序登录、社交绑定。
- 不实现完整会员中心、优惠券、积分、售后、发票。
- 不把 `node_modules/**`、`dist/**`、截图、日志、临时构建产物作为业务交付内容。

## 5. 推荐用户流程

### 5.1 未登录用户打开账户弹窗

弹窗默认展示手机号登录/注册表单：

- 手机号输入框。
- 验证码输入框。
- 发送验证码按钮。
- 登录/注册按钮。
- 可选：切换到密码登录。

展示文案必须让用户知道这是登录和注册一体化流程，例如：

```text
输入手机号并验证后登录。首次使用的手机号会自动创建账户。
```

### 5.2 发送验证码

前端校验手机号格式后调用：

```http
POST /app-api/member/auth/send-sms-code
Content-Type: application/json

{
  "mobile": "15601691300",
  "scene": 1
}
```

约束：

- 发送按钮必须有倒计时或禁用状态，避免重复点击。
- 前端倒计时只作为交互保护，不能视为安全限流。
- 后端必须负责同一手机号、同一 IP、同一 scene 的发送频率限制；如果当前后端没有该能力，必须暂停并单独评估。
- 请求失败时展示后端错误信息。
- 不在前端硬编码验证码。

### 5.3 短信登录/注册

调用：

```http
POST /app-api/member/auth/sms-login
Content-Type: application/json

{
  "mobile": "15601691300",
  "code": "1234"
}
```

成功响应字段：

```json
{
  "userId": 1024,
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "expiresTime": "2026-06-03T18:00:00"
}
```

前端收到后必须：

- 保存会话。
- 关闭或切换账户弹窗为已登录状态。
- 触发远程购物车重新加载。
- 刷新结算/订单等依赖登录态的数据。

### 5.4 密码登录

如果实现密码登录，调用：

```http
POST /app-api/member/auth/login
Content-Type: application/json

{
  "mobile": "15601691300",
  "password": "admin123"
}
```

约束：

- 密码长度按后端 VO 约束：4-16 位。
- 不保存密码。
- 不把密码打印到日志、console、测试快照或错误信息里。
- 密码登录失败不能自动注册。
- 密码登录错误文案应保持通用，避免明确暴露“手机号存在但密码错误”或“手机号不存在”。

### 5.5 退出登录

调用：

```http
POST /app-api/member/auth/logout
Authorization: Bearer <accessToken>
```

无论后端 logout 是否成功，前端都必须清理本地会话；如果后端不可用，应给出轻量提示，但不能让旧 token 继续留在本地。

## 6. 前端模块边界

### 6.1 `src/services/yudaoClient.js`

继续作为 Yudao App API 的底层请求层，允许新增：

- `loginByPassword(payload)`
- `sendMemberSmsCode(mobile)`
- `loginBySms(payload)`
- `refreshMemberToken(refreshToken)`
- `logoutMember()`
- `readYudaoSession()`
- `writeYudaoSession(session)`
- `clearYudaoSession()`
- 请求失败时的 401/refresh/retry 处理

禁止：

- 在页面组件中直接拼接 `/app-api` 完整 URL。
- 在页面组件中重复写 `Authorization` header 逻辑。
- 在页面组件中散落 token key 字符串。

### 6.2 新增 `src/services/authSession.js` 或等价模块

建议把会话读写从 `yudaoClient.js` 中拆出，职责包括：

- localStorage key 管理。
- access token / refresh token / expires time / user id 的序列化。
- 判断是否已登录。
- 判断 token 是否临近过期。
- 清理会话。

如果不拆分，也必须保证 `yudaoClient.js` 中的 auth 区域有清晰函数边界和测试覆盖。

### 6.3 `src/components/RhHeader.vue`

账户弹窗可以保留在 `RhHeader.vue` 内，但复杂度超过以下条件时必须拆组件：

- 登录表单超过一种模式。
- 出现验证码倒计时。
- 出现登录态用户摘要。
- 出现退出登录按钮。

推荐拆分：

- `AuthModal.vue`
- `AuthSmsForm.vue`
- `AuthPasswordForm.vue`
- `AuthDeveloperTokenPanel.vue`

### 6.4 `AuthTokenPanel.vue`

保留为开发工具，但必须降级为非主入口：

- 默认不在正式登录表单首屏突出展示。
- 文案明确标记为开发联调用。
- 保存 token 后必须触发统一 auth session 更新事件，而不是形成第二套登录状态。

## 7. 会话存储约束

### 7.1 建议 localStorage key

为了兼容现有代码，继续保留：

```text
YUDAO_APP_TOKEN
```

新增建议：

```text
YUDAO_APP_REFRESH_TOKEN
YUDAO_APP_TOKEN_EXPIRES_TIME
YUDAO_APP_USER_ID
```

或者使用一个 JSON key：

```text
YUDAO_APP_SESSION
```

如果选择 JSON key，必须同时兼容读取旧的 `YUDAO_APP_TOKEN`，避免已有联调入口失效。

### 7.2 安全约束

- 不存储密码。
- 不硬编码 token、手机号、验证码、密码。
- 不把 token 放入 URL。
- 不把 token 打到 console。
- 不把 token 写入测试 fixture、截图命名或日志文件。
- localStorage 中的 token 仅用于本地演示和开发集成；上线前需要重新评估 XSS 风险和存储策略。

## 8. 数据库连接与数据安全边界

### 8.1 数据库连接边界

家具前台不得以任何形式直连数据库。所有会员、token、购物车、地址、订单数据只能通过 Yudao App API 读取或写入。

禁止：

- 在前台代码、Vite 配置、浏览器脚本、测试脚本中创建 MySQL、Redis、JDBC、ODBC、Prisma、Knex、Sequelize 等数据库连接。
- 在 `VITE_*` 环境变量中放数据库地址、用户名、密码、Redis 密码、Nacos 密码或任何服务端密钥。
- 通过前台开发脚本直接查询或修改会员表、OAuth2 token 表、购物车表、订单表。
- 为了登录调试手工插入 token、手工更新会员密码、手工改订单归属。
- 把数据库连接串或密码写入 Markdown、测试 fixture、截图、日志、浏览器 localStorage。

允许：

- 前台读取 `VITE_YUDAO_APP_API_BASE` 这类非敏感 API base URL。
- Yudao 后端按现有配置连接 MySQL/Redis/Nacos。
- 开发人员在明确授权的后端联调场景中查看本地数据库，但不得把该操作固化进前台代码或测试。

如果登录链路因为数据库连接、数据缺失或 token 表异常无法推进，必须暂停并汇报，不得绕过 App API 直接改库。

### 8.2 后端配置与凭据边界

- 数据库、Redis、Nacos、Druid 账号密码只属于后端运行配置，不属于家具前台配置。
- 如果需要调整后端数据库连接，必须单独获得用户批准，并在 Yudao 后端目录内按现有配置体系处理。
- 前台 `.env` 只能保存浏览器可公开的信息；凡是带 `VITE_` 前缀的值都视为会暴露给用户。
- 不新增 `.env`、`.env.local`、PowerShell 脚本或 Docker 文件来保存明文数据库密码，除非用户明确要求并确认该文件不会提交。
- 审查提交前必须检查是否新增了 `jdbc:`、`mysql://`、`redis://`、`password=`、`MYSQL_ROOT_PASSWORD`、`NACOS_PASSWORD` 等敏感连接信息。

### 8.3 会员数据最小化

前台只保存业务必要的登录会话字段：

- `userId`
- `accessToken`
- `refreshToken`
- `expiresTime`

前台默认不保存：

- 手机验证码。
- 密码。
- 完整手机号以外的额外身份信息。
- 数据库主键以外的内部表结构信息。
- 后端 token 表、用户表、登录日志表的任何原始行数据。

如需展示手机号，优先脱敏，例如：

```text
156****1300
```

### 8.4 日志、截图与测试数据脱敏

- console、错误提示、测试输出不得出现完整 token、refresh token、密码、验证码。
- 测试手机号必须使用固定假数据，不使用真实客户手机号。
- 手工验收截图不得包含完整 token、数据库连接串、后台配置页密码。
- 如果需要记录请求 payload，必须对 `Authorization`、`accessToken`、`refreshToken`、`password`、`code` 做脱敏。
- 登录失败错误不得暴露数据库连接异常细节，例如表名、SQL、JDBC URL、账号名。

### 8.5 XSS 与本地 token 风险

因为当前方案使用 localStorage 保存 access token，后续开发必须降低 XSS 风险：

- 不使用 `v-html` 渲染用户输入或后端可变文案。
- 不把后端错误当 HTML 插入页面。
- 不在 URL query/hash 中传播 token。
- 不通过 `postMessage`、剪贴板、下载文件导出 token。
- 第三方脚本或外部 CDN 脚本需要单独审批。

如果未来改成 Cookie 会话，必须重新补充 SameSite、HttpOnly、Secure、CSRF 约束；不能直接套用本文 localStorage 方案。

### 8.6 数据库变更停止条件

登录注册开发过程中，只要出现以下任一需求，必须停止并重新评审：

- 需要新增或修改数据库表结构。
- 需要改 Yudao OAuth2 token 存储逻辑。
- 需要手工导入生产会员数据。
- 需要重置本地 MySQL/Redis 数据卷。
- 需要用数据库脚本创建会员或订单测试数据。
- 需要读取或修改后端明文密码、验证码、token 存储。

这些工作都不属于家具前台登录 UI 的默认开发范围。

### 8.7 认证风控与数据一致性

- 前台不得通过数据库查询判断手机号是否存在。
- 前台不得通过数据库查询判断验证码是否正确。
- 前台不得通过数据库查询判断 token 是否有效。
- 账号存在性、验证码校验、密码校验、token 有效性都必须以 Yudao App API 返回为准。
- 验证码发送限流、登录失败次数限制、IP 风控、账号禁用判断都属于后端职责；前台只能展示后端结果。
- 如果后端返回数据库连接失败、Redis 连接失败、Nacos 配置失败等底层异常，前台统一归类为认证服务不可用，不展示底层连接细节。
- 不允许为了“保证登录成功”在前台维护一份影子用户表、假 token 表或本地账号数据库。

## 9. 请求与错误处理约束

### 9.1 统一解包

所有 Yudao `CommonResult` 继续由统一函数解包：

- `code === 0`：返回 `data`。
- `code !== 0`：抛出包含后端 `msg` 的错误。

页面不得重复实现 `CommonResult` 解析。

### 9.2 401 与刷新 token

请求层遇到认证失败时按顺序处理：

1. 如果有 `refreshToken`，调用 `/member/auth/refresh-token`。
2. 刷新成功后更新本地 session。
3. 自动重试原请求一次。
4. 如果刷新失败，清理 session，提示用户重新登录。

约束：

- 每个请求最多自动重试一次。
- 不允许无限刷新循环。
- 不允许多个并发请求同时无序刷新 token；至少要避免重复清理和重复弹多条错误。

### 9.3 后端不可用

当 `127.0.0.1:48080` 不可用时：

- 商品列表可继续 fallback 到 demo 数据。
- 未登录态购物车可继续使用本地购物车。
- 远程购物车、结算、订单必须给出明确错误或登录/服务不可用提示。
- 登录表单必须展示“认证服务不可用”类错误，不得假装登录成功。

## 10. 业务链路联动约束

### 10.1 登录后

登录成功后必须触发：

- Header 登录态刷新。
- 远程购物车加载。
- 如果当前在 `/checkout`，重新加载地址和结算数据。
- 如果当前在 `/orders`，重新加载订单列表。

### 10.2 退出后

退出后必须触发：

- 清理本地 auth session。
- 购物车回到 local 模式，或清空远程购物车视图。
- Checkout 禁用远程下单。
- Orders 显示登录提示。

### 10.3 本地购物车与远程购物车

本阶段不强制实现本地购物车登录后自动合并到 Yudao 购物车。

如果要实现合并，必须单独设计：

- 哪些本地商品可合并。
- demo 商品如何处理。
- 合并失败如何回滚。
- 数量冲突如何处理。

## 11. UI 与文案约束

- 保持当前 RH 风格，不做营销落地页。
- 登录弹窗必须紧凑、可扫描，不能变成大面积说明页。
- 表单错误必须贴近输入区域或按钮区域。
- 按钮需要有 loading/disabled 状态。
- 手机号、验证码、密码输入需要合理 autocomplete。
- 不能继续用邮箱输入框冒充 Yudao 手机号登录。
- “注册”文案必须准确：短信登录首次使用手机号会自动创建账户。
- 失败信息优先展示后端返回 `msg`，但不能暴露 token、密码、堆栈。

## 12. 测试准入

开发前必须新增或扩展测试，覆盖以下行为。

### 12.1 服务层测试

建议新增：

```text
tests/authSession.test.js
tests/yudaoAuthClient.test.js
```

覆盖：

- 写入/读取/清理 session。
- 兼容旧 `YUDAO_APP_TOKEN`。
- `sendMemberSmsCode` 请求 payload 正确。
- `loginBySms` 请求 payload 正确。
- `loginByPassword` 请求 payload 正确。
- 登录成功后保存 access token、refresh token、expires time、user id。
- logout 后清理本地 session。
- 401 后 refresh token 并重试一次。
- refresh token 失败后清理 session。

### 12.2 组件行为测试

如果引入组件测试工具，需要用户批准新增依赖。

在不新增依赖的前提下，至少通过纯函数和服务层测试覆盖：

- 表单状态机。
- 验证码倒计时 helper。
- 登录成功/失败状态映射。
- Header 登录态显示所需的 derived state。

### 12.3 数据安全测试与检查

开发完成前必须检查：

- 前台代码没有新增数据库客户端依赖。
- 前台代码没有出现 `jdbc:`、`mysql://`、`redis://`、`MYSQL_ROOT_PASSWORD`、`NACOS_PASSWORD`。
- `VITE_*` 环境变量没有保存任何密钥或数据库凭据。
- 测试输出和错误快照不包含完整 token、密码、验证码。
- 登录失败场景不会展示 SQL、表名、数据库连接串。
- 验证码发送和登录提交不能只依赖前端倒计时作为安全限制。
- 密码登录失败不会暴露账号存在性。
- `Authorization` header 在测试断言和日志中只检查是否存在或使用脱敏值。

### 12.4 手工验收脚本

每次登录功能开发完成后，至少手工验证：

1. 未登录打开首页，账户弹窗可打开。
2. 发送验证码失败时显示错误。
3. 短信登录成功后 token 写入 localStorage。
4. 登录后 `/trade/cart/list` 携带 `Authorization`。
5. 刷新页面后仍识别登录态。
6. 进入 `/checkout` 可读取地址。
7. 进入 `/orders` 可读取订单。
8. 点击退出后 token 清理，受保护页面回到未登录提示。
9. 后端 48080 停止时，登录失败提示明确，不影响本地 demo 浏览。
10. 浏览器控制台、Network 复制结果、测试日志中没有完整 token、密码、验证码或数据库连接信息。

## 13. 验证命令

开发任务完成前必须执行：

```powershell
cd "D:\code\furniture web"
npm.cmd test
npm.cmd run build -- --outDir harness/phase-b/.tmp-dist --emptyOutDir
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

如果只修改文档，可至少执行：

```powershell
git status --short
```

## 14. 开发工作约束

### 14.1 Git 与工作区

- 新开发必须在独立分支进行。
- 不回滚用户已有未提交改动。
- 不运行 `git reset --hard`、`git checkout --`、`git clean`。
- 不把构建产物、依赖缓存、日志、截图作为业务改动提交。

### 14.2 文件边界

默认允许修改：

- `src/components/*.vue`
- `src/pages/*.vue`
- `src/services/*.js`
- `src/App.vue`
- `src/styles.css`
- `tests/*.test.js`
- `docs/yudao-integration/*.md`

默认禁止修改：

- `D:\code\yudao电商管理平台前后端\**`
- `node_modules/**`
- `dist/**`
- `logs/**`
- `captures/**`
- `tools/**`

如需修改 Yudao 后端、数据库脚本或 Docker 配置，必须先停下并取得用户明确批准。

### 14.3 依赖约束

- 不新增前端依赖，除非用户明确批准。
- 不引入完整状态管理库，除非 auth 状态复杂度已经超过简单组合函数/服务模块可维护范围。
- 不引入 Vue Router；继续遵守当前 `src/App.vue` 轻量路由方式，除非单独立项。

## 15. 停止条件

遇到以下情况必须暂停并汇报：

- 后端没有满足目标流程的 App API。
- 需要邮箱注册或密码注册，但后端没有接口。
- 需要修改 Yudao Java 后端。
- 需要新增依赖。
- token refresh 行为与后端实际返回不一致。
- 登录成功后无法读取购物车、地址、订单，且原因不明确。
- 需要前台直连数据库或读取数据库凭据。
- 需要手工修改会员、token、购物车、订单数据库记录。
- 需要使用真实会员手机号、真实 token 或生产数据库数据做测试。
- 后端缺少验证码发送限流、登录失败限制或账号禁用校验，且当前任务要求上线级安全。
- 测试失败且无法判断是否为既有问题。

## 16. 推荐实施顺序

1. 新增 auth session 服务和测试。
2. 在 `yudaoClient.js` 新增认证 API 封装。
3. 实现短信验证码登录/注册表单。
4. 接入 Header 登录态和退出登录。
5. 登录后刷新购物车、结算、订单链路。
6. 实现 refresh token 和 401 重试。
7. 将 `AuthTokenPanel` 降级为开发工具入口。
8. 完成测试、构建和 harness 验证。

## 17. 验收标准

登录注册功能只有同时满足以下条件，才算链路打通：

- UI 能提交真实认证请求。
- 后端返回 token 后前端能保存会话。
- 刷新页面后仍识别登录态。
- 远程购物车请求携带 token。
- 结算页能读取地址并发起 settlement。
- 订单页能读取当前用户订单。
- 退出登录后本地 token 被清理。
- token 过期或无效时不会静默失败。
- 前台没有直连数据库或保存数据库凭据。
- 日志、测试、截图不泄露 token、密码、验证码、数据库连接串。
- 验证码和密码登录的安全限制不依赖前端倒计时或按钮禁用。
- 登录失败不会泄露账号存在性或数据库连接细节。
- 相关服务层测试通过。
- 构建和边界 harness 通过。
