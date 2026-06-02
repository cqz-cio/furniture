# Phase B 代码边界安全规范

## 必须遵守

本规范是 Phase B 的硬边界。任何实现步骤都必须先满足这里的规则，再继续开发。

## 仓库边界

### 主要可写仓库

- `D:\code\furniture web`

### 只读参考仓库

- `D:\code\yudao电商管理平台前后端`

Phase B 默认不修改 Yudao Java 后端、Yudao 管理后台、数据库 SQL 或 Docker 配置。除非用户明确批准，否则所有 Yudao 目录只用于读取接口定义与字段结构。

## 允许修改的路径

以下路径允许在 Phase B 修改：

- `src/App.vue`
- `src/main.js`
- `src/i18n.js`
- `src/styles.css`
- `src/components/*.vue`
- `src/pages/*.vue`
- `src/services/*.js`
- `src/data/demoProducts.js`
- `tests/*.test.js`
- `docs/yudao-integration/*.md`
- `docs/superpowers/plans/*.md`
- `harness/phase-b/*`
- `package.json`
- `package-lock.json`
- `vite.config.js`

## 禁止修改的路径

以下路径禁止作为 Phase B 业务交付修改：

- `node_modules/**`
- `dist/**`
- `captures/**`
- `logs/**`
- `data/**`
- `fixtures/**`
- `tools/**`
- `scripts/**`
- `D:\code\yudao电商管理平台前后端\**`

如果测试或构建导致 `node_modules/.vite/**`、`dist/**` 发生变化，不得把这些变化作为业务实现的一部分提交或依赖。

## 用户已有改动

当前仓库存在未提交改动。处理规则：

1. 不还原用户已有改动。
2. 不用 `git reset --hard`、`git checkout --`、`git clean`。
3. 如果已有改动影响 Phase B，实现必须先读懂再衔接。
4. 如果已有改动在禁止路径中，只记录为 baseline，不继续扩大。

当前 dirty baseline 记录在：

- `harness/phase-b/baseline-dirty-files.txt`

## API 边界

### 前台可调用的 Yudao App API

只允许调用 App API，不允许家具前台调用 Admin API：

- `/app-api/product/spu/page`
- `/app-api/product/spu/get-detail`
- `/app-api/trade/cart/list`
- `/app-api/trade/cart/add`
- `/app-api/trade/cart/update-count`
- `/app-api/trade/cart/delete`
- `/app-api/trade/order/settlement`
- `/app-api/trade/order/create`
- `/app-api/trade/order/page`
- `/app-api/trade/order/get-detail`
- `/app-api/member/address/list`
- `/app-api/member/address/get-default`

### 认证边界

- 不能在代码里硬编码 token、手机号、密码。
- 只能从 `localStorage` 读取 `YUDAO_APP_TOKEN`。
- token 输入框只能作为开发/联调入口，不作为完整会员登录系统。
- 未登录时，远程购物车和下单必须失败回退或提示登录，不能伪造用户。

## 数据边界

### 商品价格

Yudao 商品价格单位是分，前台展示单位是美元金额数字。转换规则必须集中在 `src/services/yudaoClient.js` 或纯辅助函数中，禁止页面各自重复转换。

### 商品图片

商品图片来源优先级：

1. `spu.picUrl`
2. `sku.picUrl`
3. `spu.sliderPicUrls[0]`
4. 前台 fallback 占位

### 购物车

远程购物车只处理 `source === "yudao"` 的条目。本地 demo 商品只能进入本地购物车，不创建远程订单。

### 订单

创建订单必须满足：

- 有 Yudao token。
- 有远程购物车项 `cartId`。
- 有默认地址或用户选择的地址。
- 配送方式默认为 `1`，即 Yudao `DeliveryTypeEnum.EXPRESS`。

## TDD 边界

每个新行为必须先写失败测试：

1. 新增 mapper/helper/service 行为，先写 `tests/integrationModels.test.js` 或新增测试文件。
2. 运行 `npm.cmd test`，确认测试因缺少行为失败。
3. 写最小实现。
4. 再运行 `npm.cmd test`，确认通过。

页面交互可通过纯函数和服务层测试覆盖，不为 Phase B 引入新的测试依赖，除非用户批准。

## Harness 边界

每次任务完成后运行：

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

Harness 检查内容：

- 当前变更是否在 allowlist。
- `npm.cmd test` 是否通过。
- Vite 是否能构建到临时目录。

## 停止条件

遇到以下情况必须停止并汇报：

- 需要修改 Yudao 后端 Java 代码。
- 需要新增依赖或访问网络安装依赖。
- Yudao App API 字段与本规范冲突。
- 创建订单需要的地址、配送模板或 token 无法从现有接口获得。
- 测试失败但原因不清楚。

## 禁止命令

除非用户明确要求，禁止运行：

```powershell
git reset --hard
git checkout --
git clean
Remove-Item -Recurse .\dist
Remove-Item -Recurse .\node_modules
```

