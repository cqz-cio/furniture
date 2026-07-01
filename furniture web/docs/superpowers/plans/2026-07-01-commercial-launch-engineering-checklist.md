# Commercial Launch Engineering Checklist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前家具商城从“可联调 demo”推进到“可试运营、可收款、可由工作人员后台管理”的商业上线状态。

**Architecture:** 保持三端分离：客户访问 `furniture web`，工作人员访问 `yudao-ui-admin-vue3`，两者都接入同一个 `yudao-server` 和同一套 MySQL/Redis/文件存储。前台只调用 `/app-api`，后台只调用 `/admin-api`，商品、价格、库存、订单、支付结果都以后端为事实来源。

**Tech Stack:** Vue 3, Vite, Vitest, Playwright, TypeScript, Element Plus, Java 8, Spring Boot, Maven, MySQL, Redis, Docker/Nginx, Yudao mall/member/trade/pay modules.

---

## 总体目的

这份清单的目的不是继续做页面效果，而是把商业系统的关键风险一层一层收掉：

1. 让商品、用户、订单、支付都进入同一套真实后端数据。
2. 让工作人员可以在后台完成日常运营，而不是依赖工程师改代码。
3. 让真实支付、退款、发货、售后都能被记录、追踪、复核。
4. 让生产环境不再使用本地账号、明文密钥、mock 配置和宽松监控暴露。
5. 形成可重复的上线验收流程，避免“页面看起来好了，但业务链路没跑通”。

---

## Phase 0: 冻结上线范围

**目的:** 先定边界，避免上线前不断加功能，导致支付、订单、后台权限这些核心风险被拖后。

**Files:**
- Read: `D:/code/furniture web/docs/yudao-integration/shopping-checkout-closure-checklist.md`
- Read: `D:/code/furniture web/docs/yudao-integration/payment-security-implementation-notes.md`
- Read: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/ADMIN-DESIGN.md`

- [ ] 明确第一版上线只包含：商品展示、注册登录、购物车、地址、结算、下单、支付、订单查询、后台商品管理、后台会员管理、后台订单处理、后台支付/退款查询。
- [ ] 明确第一版不上线：复杂营销、AI、MES、CRM、IoT、生产工单、非必要 CMS、复杂会员成长体系。
- [ ] 确认虚构图片不作为阻塞项，但商品主图、详情图必须有可替换字段和后台上传入口。
- [ ] 产出一份“上线范围确认表”，每个功能标记为 `Must launch`、`Can wait`、`Do not expose`。

**Done when:** 所有人认可第一版上线范围，后续开发只围绕这个范围验收。

---

## Phase 1: 生产安全整改

**目的:** 现在最大的上线风险不是功能缺失，而是本地配置、明文 key、mock、安全边界没有生产化。

**Files:**
- Modify: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application.yaml`
- Create: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-prod.yaml`
- Review: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml`
- Modify: deployment env files or server runtime variables

- [ ] 新增 `application-prod.yaml`，生产数据库、Redis、支付回调、文件存储、短信、地图、地址校验全部从环境变量读取。
- [ ] 禁止生产默认启用 `local` profile，生产启动必须显式使用 `--spring.profiles.active=prod`。
- [ ] 清理代码仓库里的真实或疑似真实密钥，全部轮换，包括 AI key、地图 key、微信/钉钉 secret、支付相关配置。
- [ ] 生产关闭 `yudao.security.mock-enable`。
- [ ] 生产关闭或收窄 Actuator，只保留 health/info，并用内网或鉴权保护。
- [ ] 数据库不使用 `root/123456`，创建最小权限业务账号。
- [ ] 配置 HTTPS、CORS 白名单，只允许正式前台域名和后台域名。
- [ ] 登录、支付、下单、后台管理接口开启访问日志和错误日志。

**Verification:**
- [ ] 启动生产 profile，确认服务不依赖 `application-local.yaml`。
- [ ] 用错误域名请求接口，确认 CORS 拦截。
- [ ] 用客户 token 请求 `/admin-api`，确认失败。
- [ ] 用未登录请求下单/支付接口，确认失败。

**Done when:** 生产配置可以独立启动，且仓库里不再保存正式密钥。

---

## Phase 2: 基础数据准备

**目的:** 前后台互通的前提是后台维护的数据能被前台正确消费。没有真实商品、SKU、库存、配送和支付配置，系统只能演示，不能运营。

**Files:**
- Use admin UI: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3`
- Use backend SQL: `D:/code/yudao电商管理平台前后端/yudao-cloud/sql/mysql`
- Reference frontend mapping: `D:/code/furniture web/src/services/yudaoMappers.js`

- [ ] 建立商品分类：沙发、桌椅、灯具、床、户外、儿童等。
- [ ] 建立品牌、规格属性、SKU 属性，例如颜色、材质、尺寸、面料。
- [ ] 导入第一批真实商品，每个商品至少包含 SPU、SKU、价格、库存、主图、详情图、描述、上下架状态。
- [ ] 配置配送方式和配送模板，至少支持一套标准配送规则。
- [ ] 配置会员基础数据：用户等级、标签、分组、积分规则是否启用。
- [ ] 配置支付应用和支付渠道，先用沙箱渠道，后切正式渠道。
- [ ] 配置文件上传和图片访问域名，确认后台上传后前台能访问。

**Verification:**
- [ ] 后台新增一个商品并上架。
- [ ] 前台 `/app-api/product/spu/page` 能返回该商品。
- [ ] 前台商品详情能显示同一个商品的价格、库存、图片、规格。
- [ ] 下架商品不出现在前台列表。

**Done when:** 工作人员可以只通过后台维护商品，前台无需改代码即可展示。

---

## Phase 3: 前台真实业务闭环

**目的:** 把前台从“可展示”推进到“可交易”。前台不能信任本地价格，不能用本地购物车完成真实下单。

**Files:**
- Review: `D:/code/furniture web/src/services/yudaoRequest.js`
- Review: `D:/code/furniture web/src/services/yudaoProductApi.js`
- Review: `D:/code/furniture web/src/services/yudaoCartApi.js`
- Review: `D:/code/furniture web/src/services/yudaoOrderApi.js`
- Review: `D:/code/furniture web/src/services/yudaoPaymentApi.js`
- Review: `D:/code/furniture web/src/services/checkoutSession.js`
- Test: `D:/code/furniture web/tests/yudao*.test.js`

- [ ] 商品列表和商品详情优先展示 Yudao 真实商品。
- [ ] 匿名用户可以浏览，但加入远程购物车、结算、下单必须登录。
- [ ] 登录态使用 Yudao App token，刷新 token 失败时清除会话并要求重新登录。
- [ ] 购物车新增、修改数量、删除、列表都走 `/app-api/trade/cart/*`。
- [ ] 结算金额必须来自 `/app-api/trade/order/settlement`。
- [ ] 创建订单只提交 `skuId`、`count`、`cartId`、`addressId`、配送方式、地址校验记录，不提交可信价格。
- [ ] 本地 fallback 只能用于演示和错误恢复，不能进入真实支付入口。
- [ ] 订单列表和订单详情只展示当前登录用户自己的订单。

**Verification:**
- [ ] Run: `cd "D:/code/furniture web"; npm.cmd test`
- [ ] 浏览器手工跑通：登录 -> 商品详情 -> 加购 -> 购物车 -> 地址 -> 结算 -> 下单 -> 订单详情。
- [ ] 修改浏览器本地价格字段，后端订单金额不受影响。
- [ ] 复制别人的订单 id 查询，接口不返回他人订单。

**Done when:** 一个真实用户能完成下单，订单进入 Yudao 后台。

---

## Phase 4: 真实支付与退款

**目的:** 商业上线的核心是钱。支付不能靠前端占位输入框，必须走后端支付单、渠道托管页面、签名回调和状态同步。

**Files:**
- Review: `D:/code/furniture web/src/services/checkoutPayment.js`
- Review: `D:/code/furniture web/src/services/yudaoPaymentApi.js`
- Review: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-pay`
- Configure: `VITE_YUDAO_PAY_CHANNEL_CODE`
- Configure: backend `yudao.pay.order-notify-url`
- Configure: backend `yudao.pay.refund-notify-url`

- [ ] 决定第一版支付渠道：Stripe/Adyen/支付宝/微信/其他，先接沙箱。
- [ ] 后台配置支付应用和渠道，记录渠道 code。
- [ ] 前台构建环境设置 `VITE_YUDAO_PAY_CHANNEL_CODE`，必须和后台渠道 code 一致。
- [ ] 创建订单后，从订单结果读取 `payOrderId`。
- [ ] 调用 `/app-api/pay/order/submit` 创建支付跳转。
- [ ] 支持 `url` 跳转和 `form` 表单提交两类支付结果。
- [ ] 支付成功以后以后端回调为准，不以前端 return URL 为准。
- [ ] 后台能查询支付订单、渠道交易号、支付状态、通知状态。
- [ ] 退款从后台发起或经售后流程发起，退款结果入库并可查询。

**Verification:**
- [ ] 沙箱支付成功：订单从待支付变为已支付。
- [ ] 沙箱支付取消：订单仍待支付，可继续支付。
- [ ] 沙箱支付失败：前台提示失败，后台支付单有失败记录。
- [ ] 支付成功后重复回调，不重复入账。
- [ ] 金额不匹配回调被拒绝。
- [ ] 退款成功后订单和支付退款记录一致。

**Done when:** 沙箱支付和退款完整跑通，切正式渠道只替换配置和密钥。

---

## Phase 5: 后台运营工作台收口

**目的:** 你关心的重点在这里：工作人员要能直接通过后台管理商品、用户、支付、订单，而不是找工程师操作数据库。

**Files:**
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts`
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/product/spu`
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/trade/order`
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/member/user`
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/pay`
- Fix config: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3/.env.local`

- [ ] 修复后台轻量模式配置，补齐 `VITE_FURNITURE_WEB_URL=http://127.0.0.1:5173`。
- [ ] 后台菜单只暴露家具运营需要的模块：商品、分类、品牌、订单、售后、配送、会员、支付、文件、角色、用户、菜单。
- [ ] 后端角色也同步限制菜单和权限，不能只靠前端隐藏。
- [ ] 创建运营角色：商品运营、客服、仓配、财务、管理员。
- [ ] 商品运营可新增/编辑/上下架商品，但不能配置支付渠道。
- [ ] 客服可查看会员、订单、售后，可改备注，不可改支付渠道。
- [ ] 仓配可处理发货、物流、地址复核，不可改商品价格和支付配置。
- [ ] 财务可看支付订单、退款、对账，不可编辑商品内容。
- [ ] 管理员拥有完整配置权限。
- [ ] 商品列表增加“前台预览”动作，跳到前台对应商品详情。
- [ ] 订单详情突出支付状态、地址校验、物流、售后、操作日志。
- [ ] 高风险操作需要二次确认：改价、退款、改地址、删除商品、关闭支付渠道。

**Verification:**
- [ ] Run: `cd "D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3"; pnpm.cmd check:furniture-lite`
- [ ] 用商品运营账号登录，看不到支付渠道配置。
- [ ] 用财务账号登录，看不到商品编辑入口。
- [ ] 用仓配账号登录，可以发货，但不能退款。
- [ ] 商品后台点击前台预览，打开正确前台商品。

**Done when:** 工作人员按角色登录后台后，只能看到并操作自己职责范围内的内容。

---

## Phase 6: 地址校验、配送、发货

**目的:** 家具电商不是普通小件商品。地址、楼层、配送区域、预约和发货复核会直接影响履约成本。

**Files:**
- Review: `D:/code/furniture web/src/services/addressVerificationProvider.js`
- Review: `D:/code/furniture web/src/services/orderAddressVerification.js`
- Review: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-member`
- Review: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server`

- [ ] 配置真实地址校验服务，例如 Google Address Validation、USPS/CASS 或合规第三方。
- [ ] 后端保存地址校验结果，包括原地址、建议地址、用户最终选择、provider response id。
- [ ] 地址校验失败或 fallback 时，前台允许继续下单与否必须有明确策略。
- [ ] 后台订单详情显示地址风险状态。
- [ ] 发货前若地址未校验、校验失败、fallback，需要仓配人员人工确认。
- [ ] 发货接口后端也校验人工确认标记，防止绕过前端。
- [ ] 物流公司、物流单号、发货时间写入订单操作日志。

**Verification:**
- [ ] 地址校验通过的订单可直接发货。
- [ ] fallback 地址订单发货时要求人工确认。
- [ ] 人工确认后操作日志保留记录。
- [ ] 修改订单地址后，旧地址校验记录失效。

**Done when:** 地址风险不会被前台或后台误吞掉，发货前有人负责复核。

---

## Phase 7: 测试与自动化验收

**目的:** 上线不是看页面，而是看主链路能不能稳定重复跑通。

**Files:**
- Test: `D:/code/furniture web/tests`
- Add or review E2E: `D:/code/furniture web/scripts/checkout-e2e-smoke.mjs`
- Backend tests: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-module-*`

- [ ] 前台单元测试覆盖 Yudao API wrapper、mapper、checkout gating、payment payload。
- [ ] 后端测试覆盖订单创建、库存校验、支付回调、退款、订单归属、地址校验。
- [ ] 浏览器 E2E 覆盖完整链路：登录、加购、下单、支付、订单详情。
- [ ] 后台 E2E 覆盖：商品新增、商品上架、前台预览、订单查询、发货。
- [ ] 支付沙箱 E2E 覆盖：成功、失败、取消、继续支付、重复回调。
- [ ] 每次上线前运行固定命令并保存结果。

**Verification commands:**
- [ ] `cd "D:/code/furniture web"; npm.cmd test`
- [ ] `cd "D:/code/furniture web"; npm.cmd run build`
- [ ] `cd "D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3"; pnpm.cmd check:furniture-lite`
- [ ] `cd "D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3"; pnpm.cmd build:prod`
- [ ] `cd "D:/code/yudao电商管理平台前后端/yudao-cloud"; mvn.cmd -pl yudao-server -am -DskipTests package`

**Done when:** 测试、构建、核心手工验收都能稳定通过。

---

## Phase 8: 部署、监控、备份

**目的:** 系统能跑起来只是第一步，商业上线还要能恢复、能排查、能扩展。

**Files:**
- Review: `D:/code/docker-compose.full.yml`
- Review: `D:/code/furniture web/Dockerfile`
- Review: `D:/code/yudao电商管理平台前后端/yudao-cloud/yudao-server/Dockerfile`
- Review: `D:/code/yudao电商管理平台前后端/yudao-ui-admin-vue3`

- [ ] 确定生产域名：前台、后台、API 分开。
- [ ] Nginx 配置 HTTPS、gzip/brotli、静态缓存、反向代理。
- [ ] 数据库定时备份并做恢复演练。
- [ ] Redis 开启持久化或使用托管服务。
- [ ] 文件存储使用对象存储或可靠磁盘，并有备份策略。
- [ ] 日志按应用、日期、级别收集。
- [ ] 监控 API 错误率、支付失败率、下单成功率、慢接口、数据库连接池。
- [ ] 设置报警：服务不可用、支付回调失败、订单创建异常、数据库备份失败。

**Done when:** 生产环境有明确的发布、回滚、备份、监控和报警机制。

---

## Phase 9: 试运营和正式上线

**目的:** 用小流量验证真实业务，而不是直接全量暴露。

- [ ] 先内部试单：员工账号完整跑 10 单。
- [ ] 再小范围真实用户试单：真实地址、真实支付、真实退款至少各跑一次。
- [ ] 记录每个异常：用户体验问题、后台操作问题、支付问题、数据问题。
- [ ] 修复 P0/P1 问题后再进入正式上线。
- [ ] 正式上线当天安排值守：前端、后端、运维、客服、财务。
- [ ] 上线后 24 小时重点看订单创建、支付成功率、退款、后台错误日志。

**Done when:** 小范围试运营没有阻塞级问题，正式域名可公开访问并收款。

---

## 推荐排期

- 第 1 周：冻结范围、生产安全整改、基础数据结构准备。
- 第 2 周：商品到前台展示、购物车、登录、结算、下单闭环。
- 第 3 周：支付沙箱、退款、后台订单处理、角色权限。
- 第 4 周：地址校验、发货、E2E 自动化、部署演练。
- 第 5-6 周：试运营、修复问题、正式支付、上线准备。

如果支付渠道、地址校验、商品数据、服务器资源都已经准备好，可以压缩到 3-4 周试运营。若这些外部资源还没确定，正式商业上线按 6-10 周更稳。

---

## 当前已知优先缺口

1. 后台轻量模式检查缺少 `VITE_FURNITURE_WEB_URL=http://127.0.0.1:5173`。
2. 前台支付渠道 `VITE_YUDAO_PAY_CHANNEL_CODE` 还需要真实配置。
3. 后端当前存在本地 profile、明文 key、mock 配置、宽松监控暴露，需要生产化。
4. 后台角色权限需要按工作人员职责配置，不能只依赖前端隐藏菜单。
5. 必须完成真实 E2E 验收：后台新增商品到前台下单支付，再回后台发货/退款。
