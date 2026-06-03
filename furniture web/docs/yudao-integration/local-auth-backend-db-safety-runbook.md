# 本地认证后端与数据库安全运行手册

## 1. 目的

本文用于登录/注册联调时安全启动和排查 Yudao 本地后端、MySQL、Redis、Nacos 等依赖，避免为了打通前台登录链路而泄露凭据、直连数据库或手工破坏数据。

## 2. 角色边界

### 2.1 家具前台

允许：

- 调用 `http://127.0.0.1:48080/app-api`。
- 使用 `VITE_YUDAO_APP_API_BASE` 配置 App API base URL。
- 在浏览器 localStorage 保存 Yudao App session。

禁止：

- 连接 MySQL。
- 连接 Redis。
- 读取 Nacos 密码。
- 读取 Druid 控制台密码。
- 读取或修改会员表、token 表、订单表。

### 2.2 Yudao 后端

允许：

- 按既有配置连接 MySQL、Redis、Nacos。
- 通过 App API 对外提供认证、购物车、地址、订单能力。

未经用户明确批准，禁止：

- 修改 Java 后端代码。
- 修改数据库表结构。
- 修改 Docker 配置。
- 重置 MySQL/Redis 数据卷。
- 手工导入生产会员数据。

## 3. 本地启动前检查

### 3.1 前台目录

```powershell
cd "D:\code\furniture web"
npm.cmd test
```

期望：

- 前台测试通过。
- 没有新增数据库客户端依赖。

### 3.2 后端端口

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -eq 48080 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

期望：

- 如果后端已启动，能看到 48080。
- 如果未启动，前台登录应显示认证服务不可用，而不是假成功。

### 3.3 API 健康探测

不要用真实密码做健康探测。可以只检查服务是否响应：

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:48080/app-api/member/auth/login" -Method Post -ContentType "application/json" -Body "{}"
```

期望：

- 服务可达时返回业务校验错误或 CommonResult。
- 服务不可达时返回连接失败。
- 不把返回内容中的敏感信息写入提交文件。

## 4. 数据库与 Redis 安全规则

### 4.1 绝对禁止

- 在前台代码中写 `jdbc:`。
- 在前台代码中写 `mysql://`。
- 在前台代码中写 `redis://`。
- 在前台 `.env` 或 `VITE_*` 中写数据库用户名密码。
- 用 SQL 手工创建会员来绕过注册流程。
- 用 SQL 手工插入 token 来绕过登录流程。
- 用 SQL 手工修改订单归属来验证订单页。
- 把本地数据库密码复制到 Markdown、日志、截图或测试 fixture。

### 4.2 允许的后端排查

仅在用户批准后，可以查看本地后端配置和本地数据库状态。

允许目的：

- 确认 MySQL 是否启动。
- 确认 Redis 是否启动。
- 确认 Yudao 后端是否能连接依赖。
- 确认 App API 是否返回预期错误。

不允许把这些排查步骤写进前台自动化测试。

## 5. 凭据处理

### 5.1 可进入前台的配置

```text
VITE_YUDAO_APP_API_BASE=http://127.0.0.1:48080/app-api
```

### 5.2 不可进入前台的配置

```text
MYSQL_ROOT_PASSWORD
SPRING_DATASOURCE_PASSWORD
NACOS_PASSWORD
REDIS_PASSWORD
DRUID_LOGIN_PASSWORD
jdbc:mysql://...
redis://...
```

### 5.3 提交前搜索

在提交前运行：

```powershell
rg -n "jdbc:|mysql://|redis://|MYSQL_ROOT_PASSWORD|SPRING_DATASOURCE_PASSWORD|NACOS_PASSWORD|REDIS_PASSWORD|DRUID_LOGIN_PASSWORD|password=" "furniture web"
```

期望：

- `src/`、`tests/`、`docs/` 中没有真实凭据。
- 文档中只出现禁止项或示例项，不出现真实密码。

## 6. 测试账号与测试数据

### 6.1 手机号

使用固定假手机号：

```text
15601691300
15601691301
```

不要使用真实客户手机号。

### 6.2 验证码

- 不在前台硬编码验证码。
- 不把真实短信验证码写入测试。
- 如果后端本地环境需要固定验证码，必须记录为后端联调前置条件，不写进前台源码。

### 6.3 token

测试 token 使用明显假值：

```text
access-token
refresh-token
expired-token
```

不要把真实 token 写入：

- docs
- tests
- fixture
- screenshots
- console logs

## 7. 常见失败与安全处理

### 7.1 48080 连接失败

前台表现：

- 登录表单显示认证服务不可用。
- 本地 demo 页面仍可浏览。
- 本地购物车仍可使用。

禁止：

- 通过数据库手工插入 token 来跳过后端。
- 在前台 mock 一个成功登录并继续远程订单。

### 7.2 MySQL 连接失败

前台表现：

- 后端返回错误时，前台显示认证服务不可用或后端错误摘要。

禁止：

- 展示 JDBC URL。
- 展示数据库用户名。
- 展示表名和 SQL。
- 在前台保存数据库错误堆栈。

### 7.3 Redis 连接失败

可能影响：

- token 创建。
- token 校验。
- 验证码校验。

前台处理：

- 展示认证服务不可用。
- 清理失败登录产生的中间状态。
- 不保存半截 session。

### 7.4 验证码发送失败

前台处理：

- 展示后端 `msg` 的安全摘要。
- 保持手机号输入。
- 不自动提交登录。

安全要求：

- 不泄露账号是否存在。
- 不泄露短信服务供应商密钥。

## 8. 本地联调步骤

1. 启动 Yudao 基础依赖。
2. 启动 Yudao 后端，确认 48080 可达。
3. 启动 Furniture Web。
4. 打开首页账户弹窗。
5. 发送验证码。
6. 使用后端允许的本地验证码策略完成短信登录。
7. 检查 localStorage session。
8. 检查 `/trade/cart/list` 携带 `Authorization`。
9. 检查 `/checkout` 地址接口。
10. 检查 `/orders` 订单接口。
11. 退出登录。
12. 检查 localStorage 清理。

## 9. 事故停止条件

遇到以下情况立即停止：

- 需要生产数据库数据。
- 需要真实用户手机号。
- 需要真实短信验证码。
- 需要复制真实 token。
- 需要改数据库表结构。
- 需要改后端认证逻辑。
- 需要把数据库密码写进前台环境变量。
- 需要关闭后端认证校验。

停止后记录：

- 当前操作。
- 失败接口。
- 后端错误摘要。
- 是否涉及敏感信息。
- 下一步需要谁批准。

## 10. 交付前安全检查

```powershell
cd "D:\code"
rg -n "jdbc:|mysql://|redis://|MYSQL_ROOT_PASSWORD|SPRING_DATASOURCE_PASSWORD|NACOS_PASSWORD|REDIS_PASSWORD|DRUID_LOGIN_PASSWORD" "furniture web"
git status --short
```

通过标准：

- 没有真实数据库凭据。
- 没有前台数据库连接。
- 没有真实 token、验证码、密码进入文档或测试。
- 只有明确计划内的文档和前台文件发生变化。
