# CMS 整站预览、历史恢复与真实访问统计

## 使用

- 网站管理 → 页面内容 / 导航管理 / 企业日志：先保存草稿，再点击预览。TRIPEER 使用同一预览会话查看首页、导航和选中的文章，可以在官网内切换页面。其他页面仍显示已发布内容。
- 页面内容 / 企业日志 / 导航管理 → 发布历史 → 恢复为草稿：覆盖当前草稿，线上已发布内容保持不变。确认预览后必须再点击发布。恢复和发布均检查当前版本，避免覆盖别人的编辑。
- 中英文内容分别保存和发布。企业日志发布后进入官网“公司动态”，详情地址为 `/#/blog/<slug>`。既有静态文章继续保留。
- 导航使用 TRIPEER 专属页面目标；发布后官网页头加载 CMS 导航。页脚和非 CMS 静态页面继续使用现有代码。
- 数据看板中的官网 PV/UV 来自实际采集。访客允许统计后开始上报；撤回后停止。预览、URL 查询参数和咨询内容不会上报。

## 统计口径

当前展示最近 90 天内的页面浏览事件，按北京时间分日；PV 为接受并去重的浏览事件数量，UV 为所选时间范围内去重的匿名浏览器标识数，不等同真实人数。不同浏览器、清除存储和重新同意可能产生新标识。只统计同意的访客，不能当作全站所有访问量。HMAC 密钥轮换期间跨版本 UV 存在重复计算边界，需在轮换说明中标注。

启用之前的日期显示未采集，而不是伪造为 0。已启用且无事件的日期为 0；已记录的采集缺口显示不完整。当前不统计咨询转化，也未接入第三方分析平台。

## API 与隔离

- `POST /admin-api/seo/site-preview/ticket`：检查页面、导航、文章的预览权限；固定当前租户和站点的快照。
- `POST /app-api/seo/site-preview/exchange`：一次性票据有效 2 分钟；绑定已配置的官网源地址，换取 15 分钟会话。
- `GET /app-api/seo/site-preview/snapshot`：`X-Site-Preview-Session` 传会话；不把 ERP 登录凭证交给官网。预览凭证只放内存，刷新需重新从 ERP 打开。
- `POST /admin-api/seo/page/restore-draft`、`/seo/blog/restore-draft`：校验租户、历史归属和草稿版本，仅恢复草稿。
- `GET /app-api/statistics/website-traffic/config`；同意接口 `/statistics/consent/evidence`、撤回接口 `/statistics/consent/withdraw`；采集接口 `POST /statistics/website-traffic/track`。
- 公共采集只接受页面浏览；使用现有同意证据、服务端 HMAC、去重与限流。运营看板继续使用现有权限及租户过滤。

## 部署

1. ERP 按常规 CI → image CI → test CD → production CD 部署。本改动复用现有表，无新增 Flyway 脚本。
2. 在对应服务器上使用 `deploy/erp/configure_website_traffic.py --root /opt/oakved-deploy/test --tenant 163`（生产用 production），由 root 执行。脚本保护备份、生成每环境独立密钥、保留其他配置；下一次正常 CD 生效。不得提交 backend.env 或备份到 Git。
3. 在官网 Nginx server 内使用 `CMS_TENANT_ID=163 CMS_UPSTREAM=http://127.0.0.1:48081 node deploy/render-cms-proxy.mjs` 生成接口白名单。保留其他 server 配置，备份后执行 nginx -t 和 reload。生产官网若使用独立 ERP，改为其实际可访问的后端地址。
4. 官网仓库变量 `VITE_CMS_ENABLED=true`，按现有 Deploy to Tencent Cloud 手动 CD。站点设置中的官网草稿预览地址必须精确对应官网的协议、主机与端口，不带页面路径。
5. 测试官网目前在 `http://124.220.2.69:18081`，连接测试 ERP。生产 ERP 更新不代表测试官网的数据会同步到生产。

## 验证

后端测试覆盖历史恢复不改线上版本、版本冲突、跨租户/跨记录拒绝、票据一次性消费与来源绑定、TRIPEER 导航目标、采集同意、统计起始日期和 90 天边界。官网 Node 测试覆盖预览快照、路由验证、代理白名单、统计排除与接口约束。官网和 ERP 管理端均进行生产构建；仓库既有的无关 TypeScript 错误单独记录，不将其误报为本次通过。
