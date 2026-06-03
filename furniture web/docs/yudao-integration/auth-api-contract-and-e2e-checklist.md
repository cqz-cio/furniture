# Furniture Web 认证接口合同与端到端联调清单

## 1. 目的

本文用于登录/注册开发和联调时核对 Yudao App API 的请求、响应、错误处理、Network 验收点和数据安全边界。

前台只允许调用 App API，不允许调用 Admin API，不允许直连数据库判断登录状态。

## 2. 基础约定

### 2.1 API Base

默认：

```text
http://127.0.0.1:48080/app-api
```

前台可通过非敏感环境变量覆盖：

```powershell
$env:VITE_YUDAO_APP_API_BASE="http://127.0.0.1:48080/app-api"
```

`VITE_*` 会暴露给浏览器，禁止放入数据库密码、Redis 密码、Nacos 密码、token、验证码、用户密码。

### 2.2 CommonResult

Yudao 接口统一返回：

```json
{
  "code": 0,
  "data": {},
  "msg": ""
}
```

处理规则：

- `code === 0`：使用 `data`。
- `code !== 0`：展示后端 `msg`，但不得展示 SQL、表名、JDBC URL、账号名、token、密码、验证码。
- HTTP 非 2xx：展示网络或认证服务不可用类错误。

### 2.3 Authorization

受保护接口请求头：

```http
Authorization: Bearer <accessToken>
```

验收时只检查 header 存在和前缀正确，不复制完整 token 到文档、日志或截图。

## 3. 发送短信验证码

### 3.1 Request

```http
POST /app-api/member/auth/send-sms-code
Content-Type: application/json

{
  "mobile": "15601691300",
  "scene": 1
}
```

### 3.2 Response

```json
{
  "code": 0,
  "data": true,
  "msg": ""
}
```

### 3.3 前端行为

- 手机号格式不通过时不发请求。
- 发送中按钮 disabled。
- 发送成功后进入倒计时。
- 发送失败显示后端 `msg`。
- 倒计时只作为交互保护，不能替代后端限流。

### 3.4 Network 验收

- Method 是 `POST`。
- URL 是 `/app-api/member/auth/send-sms-code`。
- Payload 只有 `mobile` 和 `scene`。
- Payload 不包含 token、密码、数据库信息。

## 4. 短信登录/自动注册

### 4.1 Request

```http
POST /app-api/member/auth/sms-login
Content-Type: application/json

{
  "mobile": "15601691300",
  "code": "1234"
}
```

### 4.2 Response

```json
{
  "code": 0,
  "data": {
    "userId": 1024,
    "accessToken": "access-token",
    "refreshToken": "refresh-token",
    "expiresTime": "2026-06-03T18:00:00"
  },
  "msg": ""
}
```

### 4.3 前端行为

- 保存 `userId`、`accessToken`、`refreshToken`、`expiresTime`。
- 兼容写入旧 key `YUDAO_APP_TOKEN`。
- 关闭登录表单或切换为已登录状态。
- 触发远程购物车刷新。
- 如果当前在 `/checkout` 或 `/orders`，刷新对应数据。

### 4.4 安全边界

- 不通过数据库查询验证码。
- 不通过数据库查询手机号是否存在。
- 不在错误中暴露验证码正确性细节。
- 不保存验证码。

## 5. 手机号密码登录

### 5.1 Request

```http
POST /app-api/member/auth/login
Content-Type: application/json

{
  "mobile": "15601691300",
  "password": "admin123"
}
```

### 5.2 Response

响应结构与短信登录相同。

### 5.3 前端行为

- 密码长度限制为 4-16 位。
- 登录失败使用通用文案，例如 `Mobile or password is incorrect.`。
- 登录失败不能自动注册。
- 不保存密码。

### 5.4 安全边界

- 不展示“手机号不存在”或“密码错误”这类账号枚举信息。
- 不把密码写入 console、日志、测试快照、URL、localStorage。

## 6. Refresh Token

### 6.1 Request

```http
POST /app-api/member/auth/refresh-token?refreshToken=<refreshToken>
```

### 6.2 Response

响应结构与登录相同。

### 6.3 前端行为

- 受保护接口认证失败时，如果本地有 `refreshToken`，先刷新 token。
- 刷新成功后更新本地 session。
- 自动重试原请求一次。
- 刷新失败后清理 session，提示重新登录。

### 6.4 约束

- 每个原请求最多重试一次。
- 不允许无限刷新循环。
- 不在日志中输出 refresh token。

## 7. Logout

### 7.1 Request

```http
POST /app-api/member/auth/logout
Authorization: Bearer <accessToken>
```

### 7.2 Response

```json
{
  "code": 0,
  "data": true,
  "msg": ""
}
```

### 7.3 前端行为

- 调用后端 logout。
- 无论后端是否成功，都清理本地 session。
- Header 切换为未登录。
- 购物车回到 local 模式或清空远程购物车视图。
- Checkout 和 Orders 回到登录提示。

## 8. 受保护业务接口联动

登录成功后必须验证以下接口携带 `Authorization`：

- `GET /app-api/trade/cart/list`
- `GET /app-api/member/address/list`
- `GET /app-api/member/address/get-default`
- `GET /app-api/trade/order/page`
- `GET /app-api/trade/order/get-detail?id=<id>`
- `POST /app-api/trade/order/create`

验收时不复制完整 token，只记录：

```text
Authorization: Bearer ****
```

## 9. 端到端验收清单

### 9.1 后端可用

- [ ] 打开首页。
- [ ] 点击账户图标。
- [ ] 输入手机号。
- [ ] 点击发送验证码。
- [ ] Network 出现 `/member/auth/send-sms-code`。
- [ ] 输入验证码。
- [ ] 点击登录/注册。
- [ ] Network 出现 `/member/auth/sms-login`。
- [ ] localStorage 出现 session。
- [ ] Header 显示已登录状态。
- [ ] `/trade/cart/list` 携带 `Authorization`。
- [ ] 打开 `/checkout` 后地址接口携带 `Authorization`。
- [ ] 打开 `/orders` 后订单接口携带 `Authorization`。
- [ ] 点击退出。
- [ ] localStorage 中 session 和旧 token 都被清理。

### 9.2 后端不可用

- [ ] 停止 48080。
- [ ] 发送验证码显示认证服务不可用。
- [ ] 登录不会假成功。
- [ ] 本地 demo 浏览不受影响。
- [ ] 本地购物车仍可使用。
- [ ] 远程结算和订单页显示登录或服务不可用提示。

### 9.3 数据安全

- [ ] Network 截图不包含完整 token。
- [ ] console 不包含密码、验证码、完整 token。
- [ ] 测试日志不包含数据库连接串。
- [ ] 前端源码不包含 `jdbc:`、`mysql://`、`redis://`。
- [ ] 前端源码不包含数据库用户名、密码、Nacos 密码。

## 10. 失败排查顺序

按顺序排查：

1. 48080 是否启动。
2. `VITE_YUDAO_APP_API_BASE` 是否以 `/app-api` 结尾。
3. 浏览器 Network 是否请求了正确 App API。
4. 请求 payload 是否符合本文合同。
5. 后端是否返回 `CommonResult`。
6. localStorage 是否写入 session。
7. 受保护接口是否携带 `Authorization`。
8. 后端 MySQL/Redis/Nacos 是否由 Yudao 后端正常连接。

禁止跳过 App API 直接查库来判断前台逻辑是否正确。
