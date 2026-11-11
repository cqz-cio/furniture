# 餐具官网 ERP 运营模块

本模块是 ERP 的真实 controller/service/MyBatis 实现，使用 ERP 登录会话、权限检查与当前租户上下文；不读取旧网站的 `operations.json`。本次只做本地联调，未部署。

## 数据边界

- V056 只建立 `website_operation_record`、`website_document`、`website_private_file`，不创建账号、不改其他租户。菜单与账号由独立配置完成。
- 每张表都有 `tenant_id`、`site_id`，操作同时校验当前租户与站点模板 `TABLEWARE_B2B`。写入锁定该站点配置行，再校验记录版本。
- 审核、任务、巡检与审计分别保存为独立数据库记录。`version` 用于更新冲突检查，不用整个 JSON 文件覆盖全部运营数据。
- 产品、文章仍使用已有 CMS 数据表；发布服务直接检查被批准的内容版本，不能靠跳过网站网关来绕过审核。其他站点模板继续使用原发布流程。
- 图片复用 ERP 租户素材库。目录与文章的封面、首图、正文图在保存/发布时检查管理素材的当前租户归属；历史迁入 `/assets/` 图片继续有效。餐具站点公开素材上传拒绝 PDF，防止绕过资料审核；其他站点模板维持原有 PDF 行为。
- PDF 放入 ERP 私有文件表，限制 5 MB，校验 PDF 文件头、结束标记与 SHA-256，按租户/站点去重。不会生成可绕过审批的公开 infra 文件 URL；格式签名检查不等于恶意文件扫描。
- 公开资料和下载只使用发布快照。修改草稿不改变公开资料；过期、下线或跨租户/站点访问无法下载。响应禁止缓存。

## 接口

管理端前缀为 `/admin-api/seo/operations`。返回沿用 ERP `CommonResult`；调用方须检查业务 `code`，不能只判断 HTTP 200。

| 接口 | 权限 | 用途 |
|---|---|---|
| `GET /state?siteId=1` | `seo:operations:query` | 资料、私有文件元数据、审核、任务、巡检与审计 |
| `POST /file/upload` | `seo:operations:update` | multipart `siteId` 和 `file`，返回文件 ID/名称/大小/哈希 |
| `GET /file/download?siteId=1&id=<文件ID>` | `seo:operations:query` | 审批前读取同租户站点的原 PDF，必须携带 ERP 登录授权，不是公开文件地址 |
| `POST /file/delete` | `seo:operations:publish` | `siteId,id`；所有草稿及发布快照都不再引用时，事务内删除私有文件元数据和字节 |
| `PUT /document` | `seo:operations:update` | 保存资料草稿，已有记录传 `id,version` |
| `POST /document/publish`、`/offline`、`/delete` | `seo:operations:publish` | 传 `siteId,id,version`；发布需精确版本批准，删除需先下线 |
| `POST /review/submit` | `seo:operations:update` | `siteId,kind,targetId,version`；kind 为 catalog/article/document |
| `POST /review/decide` | `seo:operations:approve` | `siteId,id,version,status,note`；退回必须填写原因 |
| `PUT /task` | `seo:operations:update` | `siteId,title,owner,due,status`；更新传 `id,version` |
| `POST /task/delete` | `seo:operations:publish` | `siteId,id,version` |
| `POST /check` | `seo:operations:update` | `siteId`；检查规格缺口、来源日期、空图片字段、资料到期和复核任务 |
| `GET /backup?siteId=1`、`POST /backup/check`、`POST /backup/restore` | `seo:operations:publish` | 内容备份、校验、逐项恢复草稿 |

产品/文章审批通过后仍调用既有 `/seo/page/publish` 或 `/seo/blog/publish`。审核记录同时绑定租户、站点、内容类型、记录 ID 和内容版本；再次编辑、历史恢复或备份恢复后必须重新审核。

公开端：

- `GET /app-api/seo/operations/documents?siteId=1` 返回已发布且未过期资料。
- `GET /app-api/seo/operations/document/download?id=<资料ID>&siteId=1` 返回 PDF 附件；无权限、过期、未发布或不存在时返回 404。
- 网站连接器负责固定自身 tenant header，不接受访客覆盖租户，不使用管理员 token 查询公开内容。

## 备份与运行限制

内容备份包含目录/文章草稿、资料元数据和私有文件清单，不含文件字节、数据库完整快照、账号密码、询价联系人或运营历史。校验使用键顺序无关的规范 JSON SHA-256，同时检查对应文件在当前租户/站点存在且字节哈希匹配。

恢复只作用于仍存在的记录并保留公开快照，不能自动重建已删除文章或资料。它不是完整灾难恢复方案；完整备份仍需包括一致的数据库及 ERP 文件存储备份。

每个站点最多 300 个私有 PDF、资料和任务；列表仅展示最近 300 条审核/巡检/审计记录。内容巡检不等于远程网站全量断链检查；没有新增外部通知或自动定时服务。

旧本地测试的账号、令牌和 `operations.json` 不作为 ERP 权限或业务数据源。实际询价由现有 ERP CRM 接口处理，不在此模块建立第二套询价存储。
