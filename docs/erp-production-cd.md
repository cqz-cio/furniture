# 美国正式服务器 CD

## 路径与边界

测试和生产共用一次自动本地构建：一个后端镜像，加测试、生产两个后台变体。自动 CI 检查、构建、保存本地 OCI 产物，然后自动上传 GHCR；两个环境均由用户手动部署。

镜像上传已提前到 `ERP local image CI` 的 `publish-images` job，由本机 Windows Runner 执行 `publish_local.py`，验证上游检查和本次构建任务成功后上传原始 OCI 层、登记清单。`ERP CD - production` 只在 GitHub Ubuntu Runner 执行 `deploy-production.sh` → `runner.py` → SSH → `server.py`，正式服务器直接从 GHCR 拉取。CD 不再使用本机 Runner，也不上传或重新构建镜像；测试仍由本机 SCP 上传。

生产清单嵌入 `source_test`，生产后台 digest 在测试部署之前已包含于原始清单。校验要求派生结果完全一致；只改版本号、源码提交或后台 digest 都不能沿用原测试结果。旧测试清单没有生产变体时必须重跑共享 CI 并测试新版本。原独立 `publish_production=true` 构建入口已移除。

美国 IP 是选择直拉的依据，不是网络测试结果。必须从正式主机实际拉取目标 digest，确认 DNS、HTTPS 出站、GHCR 及其镜像层存储可达。当前 Engine API 拉取使用匿名访问，**不读取 `docker login` 的凭据**；如果镜像包是私有的，需要先实现受限的注册表认证传递，不能仅执行 docker login 后宣称已支持。

本 CD 发布 ERP 后端和后台，不发布官网。首次接管使用原数据库的新副本迁移并切换，原库保留；不替换 MySQL/Redis 服务或删除附件。后端复用测试验证的镜像，后台采用同一 release 清单中的 production 变体。生产 API 为 `https://api.vanzhome.com`。

2026-09-14 已核验 `ubuntu@43.153.40.182:22`，本机 SSH 绑定物理 IPv4 `192.168.110.145`。用户授权后已安装现有 `vanz_github_actions` 公钥并配置 GitHub production 环境的 SSH Secrets/Variables。当前线上为 `furniture-erp-production` Docker 项目，另有旧 `oakved-yudao.service`，共同使用 V048 数据库 `codex_release_v47_20260814_162836`。Python 为 3.10，文件摘要计算已适配。

## 首次接入

生产入口现已支持独立的 `production_bootstrap.py` 和 `production_policy.py`，与测试环境共用受限数据库备份、还原、迁移和恢复引擎，但固定生产主机身份、Docker 项目、旧库与 Nginx 结构。不能对生产套用测试机的目标配置。

完整步骤见 [生产 CD 操作说明](../deploy/erp/PRODUCTION-CD.md)：先 `preflight`，再 `prepare` 演练备份还原和 V048 → V049/V050 迁移；维护窗口内手动勾选确认执行 `cutover`。切换成功后自动登记完整当前版本记录，随后才能设置 `ERP_CD_ENABLED=true`。首次中断使用相同版本的 `recover`。不能手填版本号绕过实际接管。

配置参考 `deploy/erp/server.production.example.json`，实际配置由切换流程生成。新端口为 `48082/18081`，原 `48081/18080` 保留给旧容器。示例保持 `initialized=false`，不能直接覆盖现场。`config/backend.env`、`config/mysql.cnf` 保存真实凭据且权限为 0600，不得提交到 Git。

GitHub 的 `production` Environment 设置：

| 类型 | 名称 | 值 |
| --- | --- | --- |
| Variable | ERP_SSH_HOST | 43.153.40.182 |
| Variable | ERP_SSH_USER | ubuntu，已验证 sudo -n |
| Variable | ERP_SSH_PORT | 22 |
| Variable | ERP_DEPLOY_ROOT | /opt/oakved-deploy/production |
| Variable | ERP_CD_ENABLED | 完成接入和预检后才设 true |
| Secret | ERP_SSH_PRIVATE_KEY | 正式环境部署私钥 |
| Secret | ERP_SSH_KNOWN_HOSTS | 独立核验的正式主机公钥记录 |

工作流固定 `ERP_IMAGE_TRANSPORT=ghcr`。CI 上传使用本次任务临时 `GITHUB_TOKEN`，不需要每次网页授权。现有两个公开 GHCR 包及上传流程已验证；生产 CD 仅读取清单并记录部署。未初始化时 `preflight` 做首次环境检查，已初始化时检查当前服务与待发布版本。

## 日常操作

1. 等待共享 CI 的本机构建和 GHCR 上传全部成功，取得 `cd-<commit>-<local-build-run>-<attempt>`。手动运行测试 CD，部署这个版本并完成自动与人工业务验收。
2. 手动运行 **ERP CD - production**，输入同一 release ID，默认 operation 为 **preflight**。校验完整 CI 和原始测试记录后，检查正式机当前服务、数据库兼容审核、磁盘空间并拉取已发布镜像。只写镜像缓存、锁和诊断日志，不停止容器、不迁移数据库、不变更当前/历史版本、不登记部署成功。已接入环境可在 ERP_CD_ENABLED 未开启时预检。
3. 确认预检结果后，运行同一入口选择 **deploy**。脚本重新校验实时条件；预检报告不是永久放行凭证。
4. 有新增迁移时先生成并校验备份，且必须已有按该 release 登记的向后兼容审核和恢复演练；拉取或备份失败时保留当前服务。
5. 停止当前配套服务，启动固定 digest 的后端和后台。检查容器、本机和公网代理后的版本标识、后台匿名访问限制、租户 121/162 的公开 CMS 接口。
6. 全部通过才更新当前版本、成功历史和 GitHub Deployment 状态。当前版本重复部署只做健康检查。

这是单机原位替换，**切换期间有短暂不可用窗口**，不承诺零停机。破坏性数据库迁移不在日常自动发布支持范围。

## 失败、回滚与保留

- CI 上传失败：整个 CI 失败，CD 拒绝该版本。上传使用分块请求、已有层检查和 30 分钟总限时，短期注册表令牌失效时自动换取一次新令牌，不要求人工登录。取消上传可能被 GHCR 以 405 拒绝，日志不宣称已清除临时数据。重试用 Re-run all jobs 产生新版本，不能只重跑上传 job 并沿用旧 attempt。已完成上传的相同镜像层可以复用；不完整 GitHub release 草稿需核对后处理。下载或备份失败仍不切换服务。
- 启动/验收失败：仅当数据库未发生变化，或迁移完整完成且已验证旧程序兼容时，自动启动旧配套版本并验收。
- DDL 部分执行、状态不明或恢复失败：保留未完成记录，停止自动重试，先核对现场。不会用旧备份覆盖当前业务数据。
- 手工 **rollback**：只允许该生产机历史成功且仍受保护的最近两次旧版本，或显式 pin 的旧版本；仍检查数据库兼容性。无需重新构建，也不要求该旧版本当前仍是测试机运行版本。
- SSH/进程超时：远端日常操作有 1400 秒总期限及 15 秒强制终止宽限；首次接管留有 200 秒恢复宽限。单次拉取最多 300 秒，60 秒无实际进度会停止。先检查持久阶段记录，不能把超时当成恢复成功。首次生产 `recover` 在流量开放前恢复原服务，可能开放后只向前恢复新版本，禁止回退到过时的原库。
- 结果：CI 下载 `erp-ci-image-publication-result`，生产 CD 下载 `erp-production-deployment-result`。CI 上传与生产发布、注册表清理共用发布锁；本机构建、上传和测试 CD 共用串行锁；服务器另有进程锁和清理租约。
- 本地归档和 GHCR 各只保留最新五个完整版本，详见 [清理策略](../deploy/erp/RETENTION.md)。超过五版的旧版本清理后不能再通过 CD 拉取回滚。服务器现有镜像回收及日志轮转沿用原逻辑，不删除数据卷、附件或数据库备份。

## 当前验证状态

2026-09-14 已验证 SSH 密钥、生产预检查、GHCR 原始镜像下载、原库备份与独立库还原、V048 → V050 迁移、重复迁移无操作以及容器和代理保持原样。原生产库仍是 V048，线上未切换；`ERP_CD_ENABLED=false`。演练版本为 `cd-2c4acf698a5fa73e0caa6b3f94563c4fc5b7939e-34802780451-1`，已通过 CI 和测试 CD。备份约 216 MB，首次完整准备约 157 秒。准备有效期 24 小时，代码、审计或配置变化时需刷新。
