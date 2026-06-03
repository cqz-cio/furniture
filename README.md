# Furniture Workspace

这个仓库是一个家具电商相关的组合项目，外层 README 作为总入口，方便在 GitHub 首页快速了解项目结构、启动方式和各子项目文档位置。

## 项目组成

| 目录 | 说明 | 主要技术 |
| --- | --- | --- |
| `furniture web/` | 家具商城前台页面，包含 RH 风格页面、商品列表/详情、购物车、结算、订单和 Yudao App API 集成 | Vue 3, Vite, Vitest |
| `yudao电商管理平台前后端/yudao-cloud/` | Yudao Cloud 后端，提供商城、会员、支付、订单、基础设施等微服务能力 | Java 8, Spring Boot 2.7, Spring Cloud Alibaba, Maven |
| `yudao电商管理平台前后端/yudao-ui-admin-vue3/` | Yudao 管理后台前端，用于维护商品、订单、会员、系统配置等后台数据 | Vue 3, Vite, Element Plus, TypeScript, pnpm |
| `tools/` | 本地开发用 JDK、Maven 等工具依赖 | JDK 8, Maven |

## 快速启动

### 家具 Web 前台

```powershell
cd "D:\code\furniture web"
npm.cmd install
npm.cmd run dev
```

常用验证命令：

```powershell
npm.cmd test
npm.cmd run build
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

默认 Yudao App API 地址：

```text
http://127.0.0.1:48080/app-api
```

如需覆盖：

```powershell
$env:VITE_YUDAO_APP_API_BASE="http://127.0.0.1:48080/app-api"
npm.cmd run dev
```

### Yudao 本地基础设施

```powershell
cd "D:\code"
powershell -ExecutionPolicy Bypass -File .\start-yudao-infra.ps1
```

如需重新导入本地 SQL：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-yudao-infra.ps1 -ReimportSql
```

### Yudao 后端

```powershell
cd "D:\code"
powershell -ExecutionPolicy Bypass -File .\start-yudao-backend.ps1 -VerifyOnly
powershell -ExecutionPolicy Bypass -File .\start-yudao-backend.ps1
```

该脚本会使用仓库内的 `tools/jdk8` 和 `tools/maven`，并打包运行 `yudao-server`。

### Yudao 管理后台

```powershell
cd "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
pnpm install
pnpm dev
```

Yudao 管理后台要求：

- Node.js `>= 20.19.0`
- pnpm `>= 8.6.0`

## 家具 Web 说明

家具 Web 是一个 Vue 3 + Vite 前台项目，目前包含这些主要页面和能力：

- 首页、促销页、户外页、沙发列表页、沙发详情页、青少年页、儿童页
- 购物车抽屉、本地购物车持久化、Yudao 远程购物车同步
- 登录/注册弹窗、短信登录、密码登录、token 面板
- 结算页、订单列表和订单详情
- 与 Yudao App API 对接商品、购物车、地址、结算和订单接口

更多说明：

- [家具 Web Phase B Harness](furniture%20web/harness/phase-b/README.md)
- [家具 Web 页面清单](furniture%20web/docs/page-inventory.md)
- [Yudao 集成开发指南](furniture%20web/docs/yudao-integration/development-guide.md)
- [本地认证后端与数据库安全运行手册](furniture%20web/docs/yudao-integration/local-auth-backend-db-safety-runbook.md)
- [商业集成工程设计](furniture%20web/docs/yudao-integration/commercial-integration-engineering-design.md)

## Yudao 系统说明

Yudao 系统包含后端微服务和管理后台前端。当前仓库主要使用：

- `yudao-cloud/`：Spring Cloud 微服务后端
- `yudao-ui-admin-vue3/`：Vue3 + Element Plus 管理后台
- `yudao-cloud/yudao-ui/`：Yudao 原项目内的多个前端版本
- `yudao-cloud/script/docker/`：本地基础设施和 Docker 启动脚本
- `yudao-cloud/sql/`：数据库脚本和数据库相关说明

主要 README 文档入口：

- [Yudao Cloud 后端 README](yudao电商管理平台前后端/yudao-cloud/README.md)
- [Yudao Vue3 管理后台 README](yudao电商管理平台前后端/yudao-ui-admin-vue3/README.md)
- [Yudao 本地基础设施 README](yudao电商管理平台前后端/yudao-cloud/script/docker/README-local-infra.md)
- [Yudao SQL 工具 README](yudao电商管理平台前后端/yudao-cloud/sql/tools/README.md)
- [Yudao DB2 SQL README](yudao电商管理平台前后端/yudao-cloud/sql/db2/README.md)

Yudao 原项目内的前端 README：

- [yudao-ui-admin-vue3](yudao电商管理平台前后端/yudao-cloud/yudao-ui/yudao-ui-admin-vue3/README.md)
- [yudao-ui-admin-vue2](yudao电商管理平台前后端/yudao-cloud/yudao-ui/yudao-ui-admin-vue2/README.md)
- [yudao-ui-admin-vben](yudao电商管理平台前后端/yudao-cloud/yudao-ui/yudao-ui-admin-vben/README.md)
- [yudao-ui-admin-uniapp](yudao电商管理平台前后端/yudao-cloud/yudao-ui/yudao-ui-admin-uniapp/README.md)
- [yudao-ui-mall-uniapp](yudao电商管理平台前后端/yudao-cloud/yudao-ui/yudao-ui-mall-uniapp/README.md)

## 目录提示

- `tools/` 下也有一些 JDK、Maven 自带 README，它们属于本地工具说明，不是业务项目文档。
- `dist/`、`node_modules/`、`captures/`、`logs/` 通常是构建、依赖、截图或日志产物，不建议作为主要代码改动提交。
- 家具 Web 与 Yudao 的联调优先参考 `furniture web/docs/yudao-integration/` 下的文档。
