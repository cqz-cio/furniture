# 网站管理（CMS）与统计广告代码

本次在同一套 ERP 中整理网站管理模块，并提供独立的代码草稿、发布和历史恢复。ERP 商品、订单及 B2B CRM 继续使用原有业务链路和账号隔离。公司介绍官网留待页面范围确认后接入。

## 当前范围

| 网站 | 现有租户 | 租户内站点 | 本次接入 |
| --- | --- | --- | --- |
| VANZ B2B | 162 | 1 | 代码管理、公开配置读取、访客同意联动 |
| Oakved B2C | 121 | 1 | 代码管理、公开配置读取、访客同意联动 |
| 公司介绍官网 | 尚未接入 | 待确认 | 本次不创建租户、站点或内容页面 |

siteId 是租户内的编号；两个网站都为 1，不表示属于同一站点。后台页面沿用现有站点设置的 siteId=1。后端按 tenantId + siteId 保存；本次不新增同一租户内的站点切换器。

菜单统一为“网站管理（CMS）”，保留文章、导航、SEO、关键词分析和站点设置的现有功能与地址，新增“统计与广告代码”。没有把 ERP、CRM 拆成多套独立系统，也没有迁移现有商品和客户数据。

## 使用方式

1. 用有权限的本站账号进入“网站管理（CMS）→统计与广告代码”，核对网站名称与域名。
2. 在 Header、Body 正文开始或 Footer 中粘贴平台提供的完整 HTML 标签；纯 JavaScript 要包在 script 中，纯 CSS 要包在 style 中。每处最多 100,000 字符。
3. 打开该位置的启用开关，保存草稿。此时网站继续使用上次发布版本。
4. 点击“发布到网站”。访客下次打开或刷新页面时读取新版本。
5. 若需要恢复，在修改记录中选历史版本“恢复为草稿”，核对后再发布。最近 50 条历史可查看；记录保留在数据库中。

初始配置为空、三处开关均关闭。编辑器里的示例仅用于本地验收，不写入正式初始化数据。代码不会在管理后台执行。

未保存内容离开或切换租户时会提示；保存、加载和切换过程中禁止继续写入。并发保存、恢复、发布采用版本校验，旧版本请求会被拒绝，避免覆盖其他人的改动。

## 访客同意与代码兼容性

VANZ 使用已有 Cookie 同意界面。未标注类别的可执行脚本与外部资源默认同时等待 analytics 和 marketing。一个工具若只需要某一类别，在每个对应顶层标签上写 data-cms-consent：

```html
<script data-cms-consent="analytics" src="https://example.com/analytics.js"></script>
<script data-cms-consent="analytics">
  // 该统计工具的初始化代码
</script>
```

支持 analytics、marketing、preferences，以及必要工具的 necessary。多个类别可用空格或逗号分隔，必须全部满足。未识别的类别按默认较严格规则处理。包装元素还会合并内部资源的类别要求；外部脚本和它依赖的初始化脚本应标相同类别。meta、style、stylesheet 和 JSON-LD 等不执行追踪的元数据默认不等待统计同意。necessary 只用于确实必要的工具，例如同意管理器自身。

Oakved 目前没有新的原生同意弹窗。本次兼容已有统计同意记录、Cookiebot，以及同页 cms:consent 事件；analytics 同意不会自动视为 marketing 同意。投放广告前需配置对应同意管理器。自定义管理器应持久保存访客选择，并在页面初始化及变更时发出：

```js
window.dispatchEvent(new CustomEvent("cms:consent", {
  detail: { analytics: true, marketing: false, preferences: false }
}));
```

加载器每页只初始化一次，非 async 外部脚本保持插入顺序；接口失败不会阻塞网站主页面。撤回已经加载的可选脚本或嵌入资源所需的同意后，页面重载以停止其运行。

代码由浏览器在页面加载后插入对应 DOM 位置，不存在于最初返回的原始 HTML 中。因此：
- Google 等浏览器脚本可通过本功能加载；实际广告账号和平台标签需要运营方提供并验证。
- 要求读取原始 HTML 的网站所有权验证，应使用 DNS 或验证文件；不能把动态 meta 插入等同于这种验证。
- noscript 标签不提供禁用 JavaScript 时的服务器端兜底。
- 本次不新增询盘、购买、加购等广告转化事件。是否收到转化仍需接入具体事件并用平台工具验收。
- 单页应用切换路由不会重新插入整段代码；虚拟页面浏览应由具体统计工具配置。

## 权限和接口

新增独立权限 seo:website-code:query、seo:website-code:update、seo:website-code:publish。V049 只给 121、162 的现有 tenant_admin 角色补齐这些权限；其他运营角色按需由管理员授权。原有商品、文章和 CRM 权限不扩大。

后台接口位于 /admin-api/seo/website-code：
- GET /draft、GET /history：查询权限；
- PUT /draft、POST /restore-draft：保存与恢复权限；
- POST /publish：发布权限。

公开接口为 GET /app-api/seo/website-code/public?siteId=1，只返回已发布内容；关闭的代码内容被清空，不包含草稿、历史或操作人。响应不缓存，并按 tenant-id 区分。VANZ Worker 只代理这一公开读取地址，固定租户 162，剥离浏览器提交的授权信息。

数据表 website_code_config 保存草稿和发布快照；website_code_history 保存每次保存、发布、恢复的快照。写入先锁定本站配置，再校验请求版本，首次创建也在同一事务内串行处理。

## 上线顺序

本次只提交本地代码，没有修改线上数据库或发布线上网站。

1. 按既有流程备份数据库和当前部署版本。
2. 部署包含 SEO 服务及 V049__website_code_management.sql 的 ERP 后端。单体 yudao-server 已有 Flyway 打包和启动迁移机制；分服务环境按现有数据库迁移流程执行同一版本，避免另行重复手工执行。
3. 核对两个租户的站点配置、菜单与权限，发布管理端；角色菜单缓存更新后重新登录验证入口。
4. 发布 VANZ 前端和对应 Worker，或更新自建 Nginx 的公开 API 白名单示例。发布 Oakved 前端及 nginx.conf。
5. Oakved 的 HTML 响应通过 sub_filter 为入口脚本和 Vite 预加载设置 CSP nonce，并保持 no-store。部署环境必须支持 ngx_http_sub_module。先执行 nginx -t，再验证实际响应的 CSP、HTML nonce 和 JS 分块加载；反向代理不能缓存或重复压缩改写前的 HTML。
6. 先使用无外部请求的临时验证代码，在本站分别检查保存不生效、发布后生效、其他站点不变、历史恢复仍需再发布。随后配置真实平台代码及同意类别，验证同意、拒绝、撤回及具体转化事件。

可通过关闭相应位置、保存并发布来停用新代码。历史恢复只改草稿；如需停用已发布代码，必须再发布。数据库迁移为新增表及菜单授权，回退应用时保留表和历史，避免丢失记录。

CSP 实现参考：[Nginx sub_filter](https://nginx.org/en/docs/http/ngx_http_sub_module.html)、[MDN script-src](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/script-src)。本机 Docker daemon 未运行，因此没有完成真实 Nginx 容器的 nginx -t；已做配置契约检查和浏览器 nonce 执行验证。

## 2026-09-09 本地验证

- 管理端、VANZ、Oakved 三个最终生产构建通过。
- SEO 后端代码管理、文章、导航、站点配置共 49 项通过；包含真实租户拦截器的 H2 测试。
- 系统菜单目录与权限同步 7 项通过。
- VANZ 现有单元测试 149 项通过；Worker 与 CMS 加载器合计 37 项通过。
- Oakved CMS 加载器 4 项通过；整体前端回归 847 项通过、11 项失败。这 11 项在未修改的 main 复现，位于 databaseNavigationPermissionsMigration、furnitureB2BMenuFlattening、headerLanguageMenu、tenantBusinessModeAdmin、vanzB2BNavigationPermissions。
- 管理端家具模块契约检查通过。已有 check:seo-foundation 在改动前后的 main/任务分支均因 handleDelete(scope.row.id) 的旧标签匹配断言失败。
- 管理端完整 vue-tsc 连续 60 秒无输出后按规则终止。缩小到本次新增页面/API/租户切换及其依赖后完成检查；本次目标文件没有类型诊断，依赖进来的已有模块仍有类型错误，所以不宣称全项目类型检查通过。
- 真实浏览器隔离 iframe 检查 13 项通过，覆盖插入位置、顺序、nonce、拒绝、重复加载和撤回。
- 真实管理端组件在隔离测试 API 上完成保存、发布、恢复、切换租户取消、另一租户空配置验收。此界面验收没有调用生产后台。
- 桌面 1488×1058 与移动端 390 像素宽截图已完成；移动端页面和历史弹窗无横向页面溢出。视觉对照见根目录 design-qa.md。

运行日志位于 D:/furniture web2b/work/codex-logs，关键日志：
- 20260909-101113-405204-cms-backend-final.log
- 20260909-100810-998707-cms-menu-tests.log
- 20260909-101735-314130-cms-admin-build-final.log
- 20260909-101759-906257-cms-vanz-build-final.log
- 20260909-101827-609053-cms-oakved-build-final.log
- 20260909-101910-366476-cms-runtime-final.log
- 20260909-100758-018134-oak-tests-final.log
- 20260909-100431-580092-oak-baseline-failures.log
- 20260909-101057-570145-cms-admin-types.log
- 20260909-102032-133591-cms-admin-types-focused-final.log
- 20260909-102032-817272-cms-seo-contract-baseline.log
