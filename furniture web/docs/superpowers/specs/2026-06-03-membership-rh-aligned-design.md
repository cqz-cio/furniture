# Membership RH-Aligned Development Spec

> 本文档是家具网站会员模块的开发依据。会员主业务逻辑以用户提供的流程图为准；页面入口、信息架构、规则查看方式、登录注册承接方式对齐 RH 当前站点的组织方式。

**日期:** 2026-06-03  
**项目:** furniture web  
**技术栈:** Vue 3 + Vite + Vitest + Playwright  
**参考站点:**  
- RH Membership: https://rh.com/us/en/membership
- RH Members Program Terms: https://rh.com/us/en/our-company/members-program-terms-and-conditions/
- RH Checkout Sign In/Register: https://rh.com/us/en/checkout/checkout-sign-in-register.jsp
- RH My Account Membership: https://rh-mobile-frontend-v1-blue-production.rhapsody.rh.com/us/en/my-account/membership.jsp
- RH Gift Registry: https://rh.com/us/en/gift-registry

---

## 1. 目标

会员模块要支持流程图中的三条主线：

1. 会员体系：年费会员、整屋套餐会员、会员权益、邮箱匹配、权益绑定、自动续费、会员规则查看。
2. 购物车与结账：会员价展示、未登录/未会员提示、会员加入购物车、会员价在结账摘要中生效。
3. Gift Registry：礼品清单创建、活动信息、收货地址、隐私订阅、礼品卡和邮件偏好。

开发时不要把会员体系做成普通促销弹窗。它应像 RH 一样成为站点基础能力：公开介绍页、账户管理页、结账承接页、条款规则页共同组成完整系统。

---

## 2. 对齐 RH 的核心原则

### 2.1 信息架构

RH 的会员体验不是顶部大导航中的强营销频道，而是通过以下位置露出：

- Footer / Resources 中有 Members Program 入口。
- 独立 Membership 落地页说明权益。
- My Account 中有 Membership 管理入口。
- Checkout 前有 Sign in / Register / Guest checkout 分流。
- Terms & Conditions 独立承载完整规则。

本项目采用同样结构。

### 2.2 业务逻辑边界

以下逻辑以流程图为准，不按 RH 改写：

- 年费会员。
- Whole-Room Membership / 整屋套餐会员。
- 新客入会优惠、入会礼、会员折扣、自动续费。
- 会员邮箱匹配与绑定。
- 礼品注册相关流程。
- 自定义商品在结账中不可退订/不可更改的提示和确认。

RH 只作为页面规划、入口层级、文案风格和规则展示方式的参考。

### 2.3 视觉风格

页面应保持 RH 式克制、高端、低噪音：

- 少用卡片堆叠，优先使用留白、细分割线、窄版内容列。
- 会员落地页使用大图或产品生活方式图作为第一视觉，不使用夸张营销组件。
- 条款页使用长文档布局，正文清晰，目录或锚点辅助阅读。
- Account 页面保持工具型，不做营销页。
- Checkout 页面保持步骤清晰，重点展示价格、地址、支付和条款确认。

---

## 3. 路由与页面清单

### 3.1 公开会员页面

| 路由 | 页面名称 | 作用 | 主要入口 |
| --- | --- | --- | --- |
| `/membership` | Membership Landing | 公开会员介绍页，说明权益、年费、成长体系概览和加入入口 | Footer Resources、购物车会员提示、PDP 会员价提示 |
| `/membership/enrollment` | Membership Enrollment | 会员购买/续费页，将会员作为可购买服务进入结账 | `/membership` 的 Join Now、Checkout 会员提示 |
| `/membership/terms` | Members Program Terms | 完整会员规则、续费、退款、电子协议、隐私和营销通信说明 | `/membership`、`/account/membership`、Checkout 条款勾选 |
| `/membership/faqs` | Membership FAQs | 非法律化解释：如何加入、如何绑定、如何续费、会员价如何生效 | `/membership`、Footer Customer Experience |

如果需要更贴近 RH 的 URL，可以把 `/membership/terms` 映射或跳转到 `/our-company/members-program-terms-and-conditions`。

### 3.2 Account 页面

| 路由 | 页面名称 | 作用 |
| --- | --- | --- |
| `/account/sign-in` | Sign In | 邮箱、密码、忘记密码、记住我、注册链接 |
| `/account/register` | Create Account | 创建账户，收集姓名、邮箱、密码、营销订阅同意 |
| `/account/forgot-password` | Forgot Password | 邮箱找回密码 |
| `/account` | Account Dashboard | 登录后的账户总览 |
| `/account/membership` | Membership Profile | 会员状态、会员类型、到期时间、续费、规则、成长体系、邮箱绑定 |
| `/account/orders` | Order History | 订单历史，与现有 `OrdersPage.vue` 对齐 |
| `/account/address-book` | Address Book | 地址管理 |
| `/account/payment-methods` | Payment Methods | 支付方式管理 |
| `/account/wishlist` | Wish List | 收藏 |
| `/account/profile` | Account Profile | 基本资料 |
| `/account/gift-registry` | Gift Registry | 当前账户的礼品清单入口 |

My Account 侧边菜单顺序建议：

1. Membership
2. Payment Methods
3. Order History
4. Wish List
5. Address Book
6. Account Profile
7. Gift Registry

该顺序对齐 RH 当前 My Account 公开索引中可见的栏目。

### 3.3 Checkout 页面

| 路由 | 页面名称 | 作用 |
| --- | --- | --- |
| `/checkout/auth` | Checkout Sign In / Register / Guest | 结账前身份分流 |
| `/checkout/shipping` | Shipping | 收货地址、配送方式、家具配送注意事项 |
| `/checkout/payment` | Payment | 订单摘要、会员价、支付方式、会员/自定义商品条款确认 |
| `/checkout/confirmation` | Confirmation | 订单完成、邮件提醒、订单追踪入口 |

现有项目只有 `/checkout`，后续可以先在 `CheckoutPage.vue` 内做步骤状态，再逐步拆成独立页面。

### 3.4 Gift Registry 页面

| 路由 | 页面名称 | 作用 |
| --- | --- | --- |
| `/gift-registry` | Gift Registry Landing | 公开入口：Find、Create、Manage |
| `/gift-registry/find` | Find Registry | 按姓名、邮箱、活动信息查找 |
| `/gift-registry/create` | Create Registry | 创建礼品清单 |
| `/gift-registry/manage` | Manage Registry | 登录后管理清单 |
| `/gift-registry/:id` | Public Gift List | 公开礼品清单 |
| `/gift-registry/faqs` | Gift Registry FAQs | 规则解释 |

---

## 4. 页面内容规划

### 4.1 `/membership`

目的：公开解释会员价值，并把用户引导到加入会员或登录账户。

内容模块：

1. Hero
   - 标题：Members Program
   - 副文案：强调会员价、整屋服务、设计服务或项目权益。
   - 主按钮：Join Now
   - 次按钮：Sign In

2. Benefits
   - 年费会员权益。
   - Whole-Room Membership 权益。
   - 会员价适用范围。
   - 不适用范围简述。

3. Member Growth
   - 只展示成长体系概览。
   - 详细等级、任务、成长值明细放到 `/account/membership`。

4. How It Works
   - 创建账户或登录。
   - 选择会员方案。
   - 同意会员条款。
   - 完成支付。
   - 会员价立即生效。

5. Terms Links
   - See Members Program Terms
   - View Membership FAQs

交互规则：

- 未登录点击 Join Now：进入 `/checkout/auth?intent=membership`.
- 已登录非会员点击 Join Now：进入 `/membership/enrollment`.
- 已登录会员点击 Join Now：进入 `/account/membership`.

### 4.2 `/membership/enrollment`

目的：会员购买和续费页。该页应像 RH 的 Membership Enrollment 商品页一样，把会员作为一个可购买服务承接到购物车/结账。

内容模块：

1. Plan Selection
   - Annual Membership
   - Whole-Room Membership

2. Price Summary
   - 年费。
   - 适用税费提示。
   - 自动续费提示。
   - 会员费不可退或退款规则提示，按项目规则填写。

3. Account Requirement
   - 未登录时提示登录/创建账户。
   - 邮箱用于会员绑定。

4. Terms Agreement
   - 勾选：I agree to the Members Program Terms.
   - 链接到 `/membership/terms`.

5. CTA
   - Add Membership to Bag
   - Continue to Checkout

### 4.3 `/account/membership`

目的：会员管理中心。所有需要“登录后查看”的会员内容放这里。

内容模块：

1. Membership Status
   - Active / Expired / Canceled / Not a Member。
   - 当前会员类型。
   - 会员 ID。
   - 生效日期。
   - 到期日期。

2. Benefits Snapshot
   - 当前折扣。
   - 整屋权益。
   - 入会礼状态。
   - 可用优惠或权益券。

3. Member Growth
   - 当前等级。
   - 成长值。
   - 升级条件。
   - 已完成任务。
   - 待完成任务。

4. Email Matching
   - 当前账户邮箱。
   - 会员登记邮箱。
   - 匹配成功：自动绑定。
   - 不匹配：输入会员登记邮箱并发送验证邮件。

5. Renewal
   - 自动续费状态。
   - 开关或管理按钮。
   - 续费提醒订阅。
   - 取消会员入口。

6. Rules
   - View Terms & Conditions。
   - View Membership FAQs。
   - Contact Customer Experience。

### 4.4 `/checkout/auth`

目的：对齐 RH 的结账前分流。

三个并列入口：

1. Sign in
   - 已有账户登录。
   - 文案强调可访问会员详情、订单信息和地址簿。

2. Create an account
   - 新用户注册。
   - 文案强调可保存订单、管理会员、使用地址簿。

3. Continue as guest
   - 允许游客结账。
   - 必须提示游客限制：游客订单后续不能自动进入 My Account，会员权益也不会自动绑定。

会员相关规则：

- 如果购物车中包含会员服务，不允许 Guest checkout。
- 如果用户想使用会员价但未登录，引导登录或创建账户。
- 如果用户非会员但购物车有可享会员价商品，显示加入会员提示，但不阻断普通结账。

### 4.5 `/checkout/payment`

目的：完成支付前确认会员权益、商品规则和订单总额。

内容模块：

1. Order Summary
   - 商品原价。
   - 会员折扣。
   - 会员费。
   - 配送费。
   - 税费。
   - 定制商品订金或不可退费用。

2. Payment Method
   - 信用卡。
   - 礼品卡。
   - 会员相关金融方式如果项目后续支持，再加入。

3. Terms & Agreements
   - 会员自动续费条款。
   - 自定义商品不可取消/不可修改/不可退规则。
   - 隐私与营销订阅。

4. Place Order
   - 成功后进入 `/checkout/confirmation`。

### 4.6 Gift Registry

目的：对齐 RH 的礼品登记入口方式，同时保留流程图中的活动信息、地址、隐私和分享流程。

Create Registry 步骤：

1. Event Details
   - 活动类型。
   - 活动日期。

2. Registrant Information
   - 注册人姓名。
   - 共同注册人姓名。
   - 邮箱。
   - 电话。

3. Gift Delivery Addresses
   - 活动前地址。
   - 活动后地址。
   - 可选本地地址/非本地地址/自定义地址。

4. Privacy & Subscription
   - 隐私设置。
   - 邮件订阅。

5. Registry Visibility
   - Public。
   - Searchable by email。
   - Invite-only。

6. Gift Card & Email Preferences
   - 是否接收礼品卡。
   - 邮件通知偏好。

7. Share Registry
   - 分享链接。
   - 未购买商品自动标记 Purchased，避免重复送礼。

---

## 5. 会员状态模型

### 5.1 用户身份状态

| 状态 | 说明 | 允许动作 |
| --- | --- | --- |
| Guest | 未登录游客 | 浏览、加购、游客结账、查看会员介绍 |
| RegisteredNonMember | 已登录但非会员 | 加入会员、普通结账、绑定已有会员 |
| ActiveAnnualMember | 年费会员生效中 | 使用会员价、查看成长体系、续费管理 |
| ActiveWholeRoomMember | 整屋套餐会员生效中 | 使用整屋权益、查看整屋权益说明 |
| ExpiredMember | 会员过期 | 续费、查看历史权益 |
| CanceledMember | 已取消自动续费或会员终止 | 到期前继续使用或重新加入 |
| OfflineUnlinkedMember | 线下注册但未绑定账户 | 通过邮箱验证绑定 |

### 5.2 推荐数据结构

```ts
type MembershipPlanCode = "annual" | "whole_room";

type MembershipStatus =
  | "not_member"
  | "active"
  | "expired"
  | "canceled"
  | "pending_link";

interface MembershipProfile {
  id: string;
  userId: string;
  planCode: MembershipPlanCode;
  status: MembershipStatus;
  memberEmail: string;
  startedAt: string;
  expiresAt: string;
  autoRenew: boolean;
  renewalReminderEmail: boolean;
  growthLevel: string;
  growthPoints: number;
}

interface MembershipBenefitSnapshot {
  planCode: MembershipPlanCode;
  fullPriceDiscountRate: number;
  saleDiscountRate: number;
  wholeRoomEligible: boolean;
  welcomeGiftEligible: boolean;
  shippingDiscountEligible: boolean;
}

interface MembershipEmailLinkRequest {
  accountEmail: string;
  memberEmail: string;
  verificationStatus: "idle" | "sent" | "verified" | "failed";
}
```

---

## 6. 规则查看位置

会员规则不要只放在一个页面里。按照 RH 的方式，规则应分层展示：

1. `/membership`
   - 放简明权益和限制。
   - 每个权益区域提供 See Terms 链接。

2. `/membership/terms`
   - 放完整法律条款和详细规则。
   - 用长文档承载，不做弹窗。

3. `/membership/faqs`
   - 放用户看得懂的问题解释。

4. `/account/membership`
   - 放当前用户相关规则：续费、取消、成长等级、绑定邮箱。

5. `/checkout/payment`
   - 放支付前必须确认的规则：自动续费、自定义商品、不可退订、隐私订阅。

6. Cart / PDP
   - 在会员价旁边放短提示和 Learn More，不展开长规则。

---

## 7. 与现有代码的落点

当前项目没有 Vue Router，`src/App.vue` 使用 `pageRoutes` 和 `currentPage` 控制页面。第一阶段可以沿用现有方式。

### 7.1 需要新增的页面文件

- `src/pages/MembershipPage.vue`
- `src/pages/MembershipEnrollmentPage.vue`
- `src/pages/MembershipTermsPage.vue`
- `src/pages/MembershipFaqPage.vue`
- `src/pages/AccountPage.vue`
- `src/pages/AccountMembershipPage.vue`
- `src/pages/CheckoutAuthPage.vue`
- `src/pages/GiftRegistryPage.vue`
- `src/pages/GiftRegistryCreatePage.vue`
- `src/pages/GiftRegistryManagePage.vue`

### 7.2 需要新增或调整的组件

- `src/components/AccountLayout.vue`
  - My Account 侧边菜单和内容区域。

- `src/components/MembershipBenefitList.vue`
  - 会员权益展示。

- `src/components/MembershipStatusPanel.vue`
  - 登录后会员状态面板。

- `src/components/MembershipGrowthPanel.vue`
  - 成长体系展示。

- `src/components/MembershipTermsLink.vue`
  - 统一的规则链接。

- `src/components/CheckoutAuthChoice.vue`
  - Sign in / Register / Guest 三分流。

- `src/components/GiftRegistrySteps.vue`
  - Gift Registry 创建步骤。

### 7.3 需要调整的已有文件

- `src/App.vue`
  - 增加会员、账户、礼品登记相关 route key。
  - 支持 `/checkout/auth` 等路径。

- `src/components/RhHeader.vue`
  - Account icon 点击后保留轻量登录入口。
  - 登录弹窗中的 Create an Account 链接指向 `/account/register`。
  - 登录成功后可跳转 `/account` 或用户原始 intent。

- `src/components/RhFooter.vue`
  - Resources 增加 Members Program。
  - Customer Experience 增加 Gift Registry / Membership FAQs。

- `src/components/CartDrawer.vue`
  - 展示会员价、普通价差异。
  - 非会员展示 Join Members Program 提示。
  - 购物车含会员服务时，Checkout 进入 `/checkout/auth?intent=membership`.

- `src/pages/CheckoutPage.vue`
  - 第一阶段保留现有结账能力。
  - 增加会员价、会员条款、自定义商品确认。
  - 后续拆分为 `/checkout/auth`、`/checkout/shipping`、`/checkout/payment`、`/checkout/confirmation`。

---

## 8. 关键用户流程

### 8.1 未登录用户加入会员

1. 用户进入 `/membership`。
2. 点击 Join Now。
3. 进入 `/checkout/auth?intent=membership`。
4. 用户选择 Create an account 或 Sign in。
5. 登录后进入 `/membership/enrollment`。
6. 选择会员方案，勾选条款。
7. 加入购物车或直接结账。
8. 支付成功后进入 `/account/membership`，显示 Active 状态。

### 8.2 已登录非会员在购物车看到会员价

1. 用户加购商品。
2. Cart Drawer 显示 Regular Price / Member Price。
3. 购物车摘要提示加入会员可节省金额。
4. 用户点击 Join Members Program。
5. 进入 `/membership/enrollment`。
6. 购买会员后返回购物车或结账页。
7. 会员价在订单摘要中生效。

### 8.3 线下会员绑定线上账户

1. 用户登录账户。
2. 进入 `/account/membership`。
3. 页面显示 Not a Member，同时提供 Link Existing Membership。
4. 用户输入会员登记邮箱。
5. 如果与账户邮箱一致，自动绑定。
6. 如果不一致，发送验证邮件。
7. 验证成功后状态变为 Active。

### 8.4 会员查看成长体系

1. 登录用户进入 `/account/membership`。
2. 查看当前等级、成长值和权益。
3. 点击 View Rules 进入 `/membership/terms` 或 `/membership/faqs`。
4. 成长任务只展示与当前用户相关的任务，不在公开页面展示完整管理逻辑。

### 8.5 游客结账

1. 用户进入 checkout。
2. `/checkout/auth` 展示 Sign in、Create an account、Continue as guest。
3. 用户选择 guest。
4. 系统提示游客限制。
5. 如果购物车中有会员服务，禁止 guest 继续。
6. 如果只是普通商品，允许继续到 Shipping。

---

## 9. 文案规则

### 9.1 会员落地页

英文站点保持 RH 风格，使用短句：

- Members Program
- Enjoy member savings on full-priced and sale items.
- Join Now
- Learn More
- See Members Program Terms & Conditions

中文站点可对应：

- 会员计划
- 享受正价与促销商品会员优惠
- 立即加入
- 了解更多
- 查看会员条款与规则

### 9.2 结账分流

Sign in：

> If you already have an account, sign in to access your membership details, order information and saved addresses.

Create an account：

> Create an account to manage membership benefits, order history and delivery details.

Guest checkout：

> If you continue as a guest, this order and its delivery details may not be available in My Account later.

### 9.3 规则链接

统一使用：

- See Terms
- View Membership Rules
- Members Program Terms & Conditions
- Membership FAQs

不要在按钮上使用过长解释。

---

## 10. 验收标准

### 10.1 信息架构

- Footer Resources 中可以进入 Membership。
- Account 菜单中有 Membership。
- Membership Landing 可以进入 Enrollment、Terms、FAQs。
- Checkout 前有 Sign in / Register / Guest 三分流。
- 规则可以从公开页、账户页和结账页进入。

### 10.2 会员状态

- Guest 不显示个人会员详情。
- RegisteredNonMember 可以加入会员和绑定已有会员。
- ActiveAnnualMember 可以看到当前权益、到期时间、续费状态。
- ActiveWholeRoomMember 可以看到整屋权益。
- ExpiredMember 可以续费。
- OfflineUnlinkedMember 可以通过邮箱绑定。

### 10.3 结账

- 未登录用户购买会员时必须先登录或注册。
- Guest checkout 不能购买会员服务。
- 会员价在 Cart 和 Checkout Summary 中一致。
- 自定义商品在支付前必须展示不可取消/不可修改/不可退说明。
- Place Order 前必须完成必要条款确认。

### 10.4 Gift Registry

- Gift Registry 首页包含 Find、Create、Manage 三个入口。
- Create Registry 包含活动信息、注册人信息、两个收货地址、隐私订阅、可见性、邮件偏好。
- Public Gift List 可以显示礼品清单和已购买状态。

### 10.5 视觉与响应式

- Desktop 和 mobile 都不能出现文字重叠。
- Header、Footer、Account、Checkout 的风格保持 RH 式克制。
- 会员页第一屏应有明确品牌和会员主题。
- Account 页面不做营销式大 Hero。

---

## 11. 测试建议

### 11.1 Vitest

覆盖以下纯逻辑：

- 根据用户状态计算会员 CTA。
- 根据会员状态计算可见权益。
- 根据购物车和会员状态计算会员价。
- 购物车含会员服务时禁止 Guest checkout。
- 邮箱匹配绑定逻辑。

### 11.2 Playwright

覆盖以下页面路径：

- `/membership`
- `/membership/enrollment`
- `/membership/terms`
- `/account/membership`
- `/checkout/auth`
- `/checkout/payment`
- `/gift-registry`
- `/gift-registry/create`

截图视口：

- Desktop: 1365px 或 1440px 宽。
- Mobile: 390px 宽。

### 11.3 手工验收

- 从 Footer 进入会员页。
- 从 Header account 进入登录/注册。
- 从会员页 Join Now 到登录分流。
- 从 Cart 会员提示进入 Enrollment。
- 从 Account Membership 查看规则。
- 从 Checkout Payment 查看条款并完成下单。

---

## 12. 分阶段开发建议

### Phase 1: 信息架构与静态页面

目标：先把入口和页面框架搭起来。

- 新增 Membership Landing。
- 新增 Membership Terms。
- 新增 Account Membership。
- Footer 增加 Members Program。
- Header Account 链接到登录/注册页面。

### Phase 2: 会员状态和购物车展示

目标：让会员状态影响 Cart 和 Checkout 展示。

- 增加本地 membership mock state。
- Cart Drawer 展示会员价。
- Checkout Summary 展示会员折扣。
- Enrollment 可以把会员服务加入购物车。

### Phase 3: 账户绑定与成长体系

目标：完成 My Account 中会员管理。

- Account Membership 展示状态、成长值、权益。
- 增加邮箱匹配和绑定流程。
- 增加自动续费和提醒设置 UI。

### Phase 4: Checkout 拆分与规则确认

目标：完成 RH 风格 checkout 分流。

- 新增 `/checkout/auth`。
- 拆分 Shipping / Payment / Confirmation 状态。
- 增加会员条款和自定义商品确认。

### Phase 5: Gift Registry

目标：补齐礼品登记业务。

- Gift Registry Landing。
- Find / Create / Manage。
- 创建步骤和公开清单页。

---

## 13. 开发约束

- 不复制 RH 的图片、商标素材或法律文本。
- 可以参考 RH 的页面组织方式和交互层级。
- 会员规则文案必须使用本项目业务规则。
- 页面路径可以先在 `App.vue` 的手写 route map 中实现，后续再考虑 Vue Router。
- 每个阶段完成后都需要运行 `npm run build` 和相关测试。
- 涉及 UI 的阶段需要用 Playwright 检查 desktop 和 mobile 截图。

---

## 14. 自查结果

- 没有未决事项。
- 页面清单覆盖会员、账号、结账、Gift Registry 四个相关区域。
- 规则查看位置已覆盖公开页、账户页、购物车/PDP、结账页。
- 会员成长体系已明确放在 `/account/membership`，公开页只做概览。
- 与当前代码结构的落点已列出。
- 后续可以基于本文档继续生成实施计划。
