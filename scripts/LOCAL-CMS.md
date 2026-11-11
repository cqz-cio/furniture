# 本地 CMS 隔离运行

此入口用于 Trendz Green 本地验收，不是完整 ERP，也不是生产部署入口。使用现有 `WebsitePageService`、`WebsiteBlogServiceImpl`、MyBatis mapper、`@PreAuthorize` controller；以 test-classpath Spring 配置与 MockMvc HTTP bridge 提供真实业务接口。数据库为工作区 H2 文件；预览凭证使用仅限本地的带 TTL 内存实现，媒体上传不在本轮范围。不会启动或连接真实 MySQL、Redis、Nacos、CRM。

在 `yudao电商管理平台前后端/yudao-cloud` 执行一次性测试（外层进程请设 120 秒超时并记录日志）：

```text
mvn -o -B -ntp -pl yudao-module-seo/yudao-module-seo-server -am -Dspring.config.location=classpath:/application-unit-test.yaml -Dspring.cloud.nacos.config.enabled=false -Dspring.cloud.nacos.discovery.enabled=false -Dtest=TablewareCmsServiceTest,WebsitePageServiceTest,WebsiteBlogServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

测试生成模块 `target/local-cms-classpath.txt`。Java 17、Maven 3.9、本地已有 Maven 依赖与 Python 3 即可；没有新增包安装。

在工作区根目录执行：

```text
python erp-tableware/scripts/local-cms-runtime.py start
python erp-tableware/scripts/local-cms-runtime.py status
python erp-tableware/scripts/local-cms-runtime.py stop
```

启动器后台运行 Java，最多等待 50 秒，记录 PID、日志路径；stop 核对进程命令行包含 `LocalCmsMain`。仅监听 `127.0.0.1:48081`，`GET /health` 明确返回 `isolated-local-cms`。H2 文件、进程信息和角色凭据都在工作区 `work/phase2-cms-migration/`，不可进入前端 bundle 或版本控制。

`local-runtime-private.json` 含 `baseUrl`、`tenantId:9001`、`siteId:1` 和 `roles.viewer/editor/publisher.{token,password}`。管理接口接受 `Authorization: Bearer <token>`。这些凭据仅供本地同源网关使用；浏览器通过 4174 网关的 HttpOnly 会话与 CSRF 访问。后端每个请求固定本地租户，忽略传入 tenant header，权限仍通过 controller 方法拦截。viewer 只能查询；editor 可以编辑和预览；publisher 额外可发布及下线。

首次缺失时，从网站 `src/catalog-data.json` 与 `src/legacy-articles.json` 导入。目录通过 initialize/save/publish，文章通过 create/publish 建立明确的本地公开快照；不直接写公开快照 SQL。两篇原站无摘要的文章取第一段原文前 600 字生成摘要，SEO 默认描述最多 320 字；正文块不截断。后续启动只补缺失记录，不覆盖编辑。建表只运行 `create_tables.sql`，不运行 `clean.sql`。

主要接口：

- `GET /app-api/seo/page/public?siteId=1&pageKey=catalog&locale=en`：公开目录，数据在 `data.content.products`。
- `GET /app-api/seo/blog/public?siteId=1&locale=en&page=1&pageSize=100`：公开文章，`data.items[].sections[].blocks` 包含全文。
- `GET /app-api/seo/blog/public/{slug}?siteId=1&locale=en`：公开单篇。
- `/admin-api/seo/page` 下的 `GET draft/history/schema`、`POST initialize/publish/restore-draft/preview-ticket`、`PUT draft`。
- `/admin-api/seo/blog` 下的 `GET page/summary/get/history`、`POST create/publish/offline/restore-draft/preview-ticket`、`PUT update`、`DELETE delete`。

目录版本字段 `expectedVersion`，文章版本字段 `version`；过期版本 HTTP 409，权限不足 403，未认证 401，非法输入 400。发布历史恢复只改草稿。博客公开日期是 ISO 字符串，后端本地管理员时间也序列化为 ISO。

生产 ERP 后续采用这些代码前，必须在另行授权部署时执行 `sql/mysql/migrations/V055__website_blog_published_visibility_snapshot.sql`：它把文章公开可见性、排期、排序与草稿编辑隔离。此次仅在独立 H2 验证，未执行任何生产迁移。
