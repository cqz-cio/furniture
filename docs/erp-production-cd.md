# 美国正式服务器 CD

## 路径与边界

测试和生产共用一次自动本地构建：一个后端镜像，加测试、生产两个后台变体。自动 CI 只检查、构建和保存 OCI 产物；两个环境均由用户手动发布。

生产使用 `ERP CD - production` 的两阶段流程：本机 Windows Runner 执行 `promote_local.py`，校验云端 CI、本机构建和测试 CD 的完整清单指纹，将原始 OCI 层和清单上传 GHCR，再登记生产 release；Ubuntu Runner 执行 `deploy-production.sh` → `runner.py` → SSH → `server.py`，正式服务器直接从 GHCR 拉取 `linux/amd64` 镜像。上传保留原始 digest，后端不重新构建；测试仍由本机 SCP 上传。

生产清单嵌入 `source_test`，生产后台 digest 在测试部署之前已包含于原始清单。校验要求派生结果完全一致；只改版本号、源码提交或后台 digest 都不能沿用原测试结果。旧测试清单没有生产变体时必须重跑共享 CI 并测试新版本。原独立 `publish_production=true` 构建入口已移除。

美国 IP 是选择直拉的依据，不是网络测试结果。必须从正式主机实际拉取目标 digest，确认 DNS、HTTPS 出站、GHCR 及其镜像层存储可达。当前 Engine API 拉取使用匿名访问，**不读取 `docker login` 的凭据**；如果镜像包是私有的，需要先实现受限的注册表认证传递，不能仅执行 docker login 后宣称已支持。

本 CD 只发布 ERP 后端和后台；不发布官网，不替换数据库、Redis 或附件。后端复用测试验证的镜像，后台采用同一 release 清单中的 production 构建变体。生产 API 沿用清单中的 `https://api.vanzhome.com`；正式 SSH 地址、后台地址、数据库和目录必须现场核实。

2026-09-11 查验：用户提供的后台为 `https://api.vanzhome.com/admin/index`；Google 公共 DNS 的 A 记录为 **43.153.40.182**，与本机 SSH known_hosts 记录一致。已存在 `vanz_github_actions` 密钥文件，但连接该 IP 的 22 端口在 SSH banner 阶段超时，尚未确认登录用户、系统服务和数据库。GitHub `cqz-cio/furniture` 的 production 环境变量接口返回 404，当前未读取到生产 CD 配置；仍需核对环境是否未创建或当前身份无权读取。

## 首次接入

测试机 `124.220.2.69` 的 `prepare/cutover/recover` 适配器绑定其旧服务、数据库和路由，不能用于生产。当前生产入口是**已完成首次接入后的日常 CD**，不是旧服务器自动迁移脚本。

首次接入需先盘点正式机的服务、端口、MySQL/Flyway、Redis、附件权限、反向代理和备份空间，使用生产数据副本演练迁移及恢复，再安排首次切换。核实成功后登记当前版本及对应 `releases/<release_id>/` 完整记录，最后设置 `initialized=true`。不能仅手填一个版本号或修改开关绕过首次接入；代码会拒绝没有已登记当前版本的生产发布。

配置参考 `deploy/erp/server.production.example.json`。示例保持 `initialized=false`，端口和路径仅为待核实建议值，不能直接覆盖现场配置。`config/backend.env`、`config/mysql.cnf` 保存真实凭据且权限为 0600，发布目录限制为部署用户可读；不得提交到 Git。

GitHub 的 `production` Environment 设置：

| 类型 | 名称 | 值 |
| --- | --- | --- |
| Variable | ERP_SSH_HOST | 域名当前指向 43.153.40.182；完成 SSH 核验后配置 |
| Variable | ERP_SSH_USER | 有 Docker 和部署目录权限的专用账户 |
| Variable | ERP_SSH_PORT | 真实 SSH 端口，默认 22 |
| Variable | ERP_DEPLOY_ROOT | /opt/oakved-deploy/production |
| Variable | ERP_CD_ENABLED | 完成接入和预检后才设 true |
| Secret | ERP_SSH_PRIVATE_KEY | 正式环境部署私钥 |
| Secret | ERP_SSH_KNOWN_HOSTS | 独立核验的正式主机公钥记录 |

工作流固定 `ERP_IMAGE_TRANSPORT=ghcr`。本机上传 job 使用仓库 `GITHUB_TOKEN` 的 packages/contents write 和 actions/deployments read 权限；现有 GHCR 镜像包必须允许该仓库的 Actions 写入，且必须公开可读。上传器会实际校验匿名读取，私有包会阻止后续部署。本机 Runner 标签、缓存和运行要求见 [本地 CI 说明](../deploy/erp/LOCAL-CI.md)。首次接入的预备检查通过独立只读盘点完成；下述 preflight 要求已有完整 CD 配置和当前版本记录。

## 日常操作

1. 共享 CI 在本机生成 `cd-<commit>-<local-build-run>-<attempt>`；手动运行测试 CD，部署这个版本并完成自动与人工业务验收。
2. 手动运行 **ERP CD - production**，输入相同 release ID，默认 operation 是 **preflight**。它会校验并上传已有镜像、登记 GHCR 对应 release，然后检查正式机当前服务、数据库兼容审核、磁盘空间并实际拉取生产镜像。预检会写入远端镜像缓存、锁文件和诊断日志，但不停止容器、不迁移数据库、不变更当前/历史版本、不登记部署成功。即使 `ERP_CD_ENABLED` 未开启，也可在已接入的环境执行。
3. 确认预检结果后，运行同一入口选择 **deploy**。脚本重新校验实时条件；预检报告不是永久放行凭证。
4. 有新增迁移时先生成并校验备份，且必须已有按该 release 登记的向后兼容审核和恢复演练；拉取或备份失败时保留当前服务。
5. 停止当前配套服务，启动固定 digest 的后端和后台。检查容器、本机和公网代理后的版本标识、后台匿名访问限制、租户 121/162 的公开 CMS 接口。
6. 全部通过才更新当前版本、成功历史和 GitHub Deployment 状态。当前版本重复部署只做健康检查。

这是单机原位替换，**切换期间有短暂不可用窗口**，不承诺零停机。破坏性数据库迁移不在日常自动发布支持范围。

## 失败、回滚与保留

- 上传/下载/备份失败：不切换。上传使用分块请求、缓存层检查、15 分钟总限时，校验成功才登记 release；重试复用已上传层。若 GitHub release 创建过程中失败留下 draft，需核对后清理或恢复该草稿，脚本不会绕过不完整记录。
- 启动/验收失败：仅当数据库未发生变化，或迁移完整完成且已验证旧程序兼容时，自动启动旧配套版本并验收。
- DDL 部分执行、状态不明或恢复失败：保留未完成记录，停止自动重试，先核对现场。不会用旧备份覆盖当前业务数据。
- 手工 **rollback**：只允许该生产机历史成功且仍受保护的最近两次旧版本，或显式 pin 的旧版本；仍检查数据库兼容性。无需重新构建，也不要求该旧版本当前仍是测试机运行版本。
- SSH/进程超时：远端日常操作有 1400 秒总期限及 15 秒强制终止宽限；单次拉取最多 300 秒，60 秒无实际进度会停止。超时不等于回滚成功，先查 `state.json`、`deployment.log`、`command-logs/`；生产不能调用测试专用 recover。
- 结果：Actions 下载 `erp-production-promotion-result` 和 `erp-production-deployment-result`；前置门禁失败也会生成失败 JSON。生产与注册表清理共用发布锁，本机构建、测试 CD 和生产上传共用串行锁；服务器还有进程锁和清理租约。
- 保护当前版本、最近两次成功旧版本及 pin；远端另外保留五套普通版本。日志轮转沿用测试修复；镜像回收不删除数据卷、附件或数据库备份。

## 当前验证状态

本次校验包括 Python 单元测试和 Actions 工作流语法检查，覆盖配套 OCI 归档、原始 digest 保留、分块上传、失败不登记、精确测试门禁、预检不切换及生产初始状态门禁。上传 HTTP 行为使用模拟响应验证，尚未实际写入 GHCR、触发共享 CI 全量构建或在正式机做端到端部署。测试不代表正式服务器已完成首次接入、已拉取镜像或已上线。
