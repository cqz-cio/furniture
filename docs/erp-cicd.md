# ERP 测试与生产 CD

测试环境新增流程：[自动本机 CI 构建 → 本地镜像包 → 手动 CD 上传部署](../deploy/erp/LOCAL-CI.md)。正常 push 运行云端验证，验证成功后由已启用的本机 Runner 构建测试版本并结束，不推送 GHCR、不更新服务器。测试与生产 CD 都只能手动触发。

下文保留旧 GHCR 发布、首次接入及生产手动入口说明。生产镜像构建改为手动运行 `ERP full-stack CI` 并选择 `publish_production=true`；本地测试清单与生产 GHCR 清单分别验收。

## 本次实现与当前状态

测试、生产现在有两个独立入口，共用 `deploy/erp` 中的部署、版本记录、回滚和清理代码：

美国正式服务器的直拉、预检、首次接入边界和操作顺序见 [生产 CD 说明](erp-production-cd.md)。生产工作流默认 `preflight`，需要明确选择 `deploy` 才会切换服务。

| GitHub Actions 入口 | 仓库入口脚本 | 固定环境 |
| --- | --- | --- |
| ERP CD - test | `deploy/erp/deploy-test.sh` | `test` |
| ERP CD - production | `deploy/erp/deploy-production.sh` | `production` |
| ERP image retention | `deploy/erp/retention.py` | 同时读取两环境的保护记录 |

两套 CD 均手动触发。日常 `deploy/rollback` 要求 `ERP_CD_ENABLED=true`。测试入口另有 `prepare/cutover/recover`，用于已核实测试机的首次接入，切换/恢复要求勾选 `confirm_cutover`。生产部署仍要求同一清单的测试 CD 成功；准备/演练成功不会解除生产门禁。生产回滚仍要求本机登记成功、受保护且数据库兼容。

清理默认 dry-run，实际删除还要求仓库变量 `ERP_IMAGE_CLEANUP_ENABLED=true`。定时入口建议在真实 dry-run 和首轮清理核对后开启；变量未开启时定时任务不执行。工作流默认北京时间每天 03:30 检查。

最新服务器核对记录见 [测试接入说明](erp-test-cd-onboarding.md)。第三个官网内容接入不在本次范围。`ERP test environment check` 仍是旧环境的只读诊断；新 CD 自带容器验收，不能把旧检查通过当成新版本发布成功。

## CI 与版本清单

手动选择 `publish_production=true` 时，原 CI 构建并推送生产后台和后端，原 commit SHA、main、latest 标签保留。该 GHCR 路径包含：

1. 部署脚本单元测试和两个 shell 入口的语法检查。
2. 用同一提交构建测试后台，测试 API 与生产 API 分开；测试产物不能包含生产 API 地址。
3. 唯一 `cd-<完整 commit SHA>-<run ID>-<attempt>` 版本号。
4. 两类镜像全部成功后，向对应 GitHub 预发布记录上传 `release.json`。清单记录后端 digest、测试/生产后台 digest、源码、CI run/attempt、数据库迁移目标及指纹、架构与公开地址。
5. 部署时核对源仓库、main、CI 工作流路径、指定 run attempt 的成功状态和清单内容；按 digest 拉取，不能输入任意镜像或 latest。

后台目前使用 Vite 构建时配置。因此测试和生产后台是同一提交的两个镜像变体；后端在两环境使用同一份镜像。五套版本按配套发布计算，不是总共五个镜像条目。

仓库变量：

| 变量 | 配置位置与含义 |
| --- | --- |
| `ERP_TEST_API_BASE_URL` | Repository variable，真实测试 API 根地址，无末尾 `/` |
| `ERP_TEST_STOREFRONT_URL` | Repository variable，真实测试网站根地址，无末尾 `/` |
| `ERP_IMAGE_CLEANUP_ENABLED` | Repository variable，初始不配置；核对后填 `true` 才允许实际删除和定时清理 |
| `ERP_RETAIN_RELEASES_JSON` | Repository variable，可选的额外保护版本数组，如 `["cd-...-123-1"]` |

前两个变量均未配置时，原有生产镜像构建和推送继续工作，但不会登记可供新 CD 使用的完整发布清单。只填一个、格式不合法或指向生产 API 时 CI 会明确失败。生产公开地址沿用现有 `https://api.vanzhome.com`、`https://www.vanzhome.com`。

CI 发布清单需要仓库 contents write 权限；清理还需要对应镜像包的管理权限。若仓库策略禁止写发布记录/清理标记，任务会在删除之前失败。GitHub Release 仅保存小型清单和清理记录；镜像本体继续保存在 GHCR。

## 两个 GitHub Environments

分别在 `test`、`production` 中配置以下同名变量和秘密；值相互独立：

| 类型 | 名称 | 说明 |
| --- | --- | --- |
| Variable | `ERP_CD_ENABLED` | 完成对应环境首次接入演练后才设为 `true` |
| Variable | `ERP_SSH_HOST` | 本环境 SSH 主机；不含用户名或协议 |
| Variable | `ERP_SSH_USER` | 本环境部署用户 |
| Variable | `ERP_SSH_PORT` | 默认 22 |
| Variable | `ERP_DEPLOY_ROOT` | 默认 `/opt/oakved-deploy/test` 或 `/opt/oakved-deploy/production`；最后一级必须匹配环境 |
| Secret | `ERP_SSH_PRIVATE_KEY` | 本环境部署用户的 SSH 私钥 |
| Secret | `ERP_SSH_KNOWN_HOSTS` | 从可信渠道核对过的服务器主机公钥记录 |

测试入口兼容已有 `test` Environment 中的 `TENCENT_SSH_HOST`、`TENCENT_SSH_USER`、`TENCENT_SSH_PORT`、`TENCENT_SSH_PRIVATE_KEY`、`TENCENT_SSH_KNOWN_HOSTS`，仅在对应 `ERP_SSH_*` 未配置时回退。无需读出或复制已有 GitHub Secret。清理流程也仅为 `test` 使用此回退；生产入口和生产清理目标仍只读取 `ERP_SSH_*`。

脚本启用严格主机公钥检查，不在部署时自动信任 `ssh-keyscan` 的结果。数据库、Redis 和业务密钥不传到 GitHub，也不写入镜像或清单。镜像私有时，服务器需预先使用仅可读取镜像的凭据登录 GHCR；公开镜像可匿名拉取。清理权限只给独立清理工作流。

测试、生产部署、CI 镜像发布登记、远端清理共用 `erp-release-control` 并发组。运行中的操作不会因为另一个发布主动取消；GitHub 待执行任务仍可能被后来的待执行任务替换，未执行不能记为成功。服务器另有文件锁与部署日志，SSH 中断后不能绕过未完成状态重复操作。

## 服务器首次接入

已确认测试机 `ubuntu@124.220.2.69` 使用专用 [首次接入适配器](erp-test-cd-onboarding.md)：`prepare` 自动备份/隔离恢复/演练；`cutover` 使用最新备份创建新业务库、切换 Docker 与代理；`recover` 按记录恢复。原 V047 库不迁移，不假定 V048 向后兼容。

生产首次接入仍须根据真实服务、数据库和路由准备，不能套用测试主机适配器。不要伪造迁移历史或向既有业务库导入初始化基线；测试与生产必须使用不同业务库及适当隔离的 Redis/文件存储。

服务器要求：Linux amd64、Python 3.11+、Docker、Compose 2.30+、MySQL 8 客户端及 mysqldump。部署账户必须能操作本机 Docker、读受限配置、写部署目录、读取磁盘占用。容器内 yudao 用户必须能写已存在的附件目录和日志目录；目录权限在首次接入时验证。

建议目录：

```text
/opt/oakved-deploy/test/                    # production 使用独立同名目录
  config/
    server.json                            # 根据 server.example.json 填真实地址/端口/目录
    backend.env                            # 对应环境的真实后端配置，0600
    mysql.cnf                              # 根据 mysql.example.cnf 配置，0600
  releases/<release_id>/                    # 固定清单、Compose、配置快照；含秘密，目录受限
  state.json                               # 脚本维护当前/历史成功/保护/未完成状态
  backups/                                 # 迁移前备份及哈希；独立备份保留策略
  deployment.log                           # 自动轮转，每份 5 MB，另保留 3 份
  operation.lock
  cleanup-lease.json                       # 实际清理时的短期保护锁
```

`server.json` 重点：

- `environment` 和 `project` 必须分别对应 `test`/`oakved-erp-test`、`production`/`oakved-erp-production`。
- `api_base_url`、`storefront_url` 必须匹配发布清单。`admin_url` 应为最终返回 200 的 `/admin/` 地址，不应依赖跨站跳转。
- `backend_port`、`admin_port` 是本机独立监听端口。后端采用 Linux host network 并固定绑定 127.0.0.1，便于沿用既有本机数据库/Redis连接；后台只映射到 127.0.0.1，外部入口由现有反向代理管理。
- `database_name` 与 `mysql.cnf` 主机/端口必须和 `backend.env` 中的 `YUDAO_DB_URL` 指向同一库。备份账号可不同，但不能备份错库。`backend.env` 使用无引号的 `KEY=value` 原始格式，保留真实密码中的 `$` 等字符。
- 附件挂载的 `uploads_target` 必须和现有应用文件存储配置的目录一致；不能按示例猜测。Compose 不新建目录、不启动 MySQL/Redis、不带网站容器。
- `image_peak_bytes`、`backup_estimate_bytes`、`min_free_bytes` 按实测填写。脚本分别检查 Docker、部署备份、日志和附件所在磁盘；占用到 90% 或不足本次所需空间时停止。
- `smoke_checks` 默认覆盖租户 162、121 的 CMS 导航和博客只读接口。真实业务数据、表单提交、广告触发等不在生产自动验收中执行。
- 验证旧服务已退出、迁移账本完整、路径和代理正确后，才把 `initialized` 设为 true。初始模板为 false，不可仅为绕过检查而开启。

反向代理必须把后台指向该环境的 admin 端口，把 API 指向该环境的 backend 端口。应能从服务器经公开地址访问 `/actuator/health` 和只含版本标识的 `/actuator/info`；可用代理访问限制控制这两个诊断入口。CD 同时检查本机与代理后的版本，防止“旧服务仍返回 200”造成误判。

后台 `/admin/release.json` 记录版本和环境且禁用缓存。后端通过已有 Actuator info 返回相同信息，仅在 CD 环境通过开关启用；未受 CD 管理的现有启动方式默认不开启这项信息。

## 日常发布和回滚

1. 代码推送 main，等待 ERP full-stack CI 成功，取得完整的 `cd-...` 发布版本号。
2. 运行 `ERP CD - test`，选 `deploy` 并填写该版本号。
3. 测试 CD 校验原始 CI，按固定 digest 拉镜像，记录未完成状态，处理必要的备份与迁移，切换配套后端和后台，检查实际容器及经代理访问到的版本、依赖健康、匿名后台拒绝访问、CMS 公共接口。
4. 在测试环境完成人员登录、商品操作、账号隔离和 CMS 编辑发布等业务验收。自动只读检查并不能替代这些操作验证。
5. 运行 `ERP CD - production`，选同一版本。生产新部署会核对该清单最近一次测试 CD 成功记录，再进行相同的部署和只读验收。
6. 回滚时在对应入口选 `rollback`，填写最近两次成功旧版本之一，或已登记保护的历史成功版本。脚本恢复其配套镜像和配置快照，不重新构建。

重复发布同一当前版本只验证健康，不重启。失败尝试不计入成功历史。两套入口均不允许改变目标环境参数来混用凭据。

## 数据库变化

无迁移的版本正常更新。有迁移时，`server.json` 需为该 release_id 记录匹配的已审核迁移信息，例如：

```json
{
  "migration_reviews": {
    "cd-<40位提交>-<run>-<attempt>": {
      "from_version": 49,
      "to_version": 50,
      "backward_compatible": true,
      "restore_rehearsal_passed": true,
      "compatible_release_ids": ["cd-<已验证可以回滚的版本>"]
    }
  }
}
```

版本数字只是示例，目标从实际迁移文件生成，不固定为 V049/V050。审核必须覆盖当前版本及需要保留的回滚版本。脚本会生成 mysqldump 一致性备份、检查非空并记录 SHA-256，再让一个新后端按现有 Flyway 机制执行迁移。破坏性或尚未验证兼容性的迁移不能走普通自动发布。

下载或备份失败不切换旧服务。切换后失败，仅在确认数据库未变或迁移已完整完成且旧版本兼容时回滚程序。MySQL 迁移失败即使账本版本没变，也可能已经执行部分 DDL，此时不会自动重启旧程序。

日常部署不会用旧备份自动覆盖业务库。首次测试切换自动恢复到新副本；开放写入前失败恢复原服务，流量可能开放后只修复新版本。普通部署的 `manual-recovery-required` 要先核对容器和数据库；首次接入中断使用 `recover`，不能删除状态后盲目重跑。

## 五套版本保留规则

保护集合包含：两环境当前版本、各环境最近两次成功旧版本、人工 pin，以及明确登记的保护版本。普通完整版本按时间排序，保护版本之外再保留最近 5 套；24 小时内的新完整产物额外保留。

例如只有一环境，当前 R10，回滚 R09/R08，则额外保留五套普通版本 R07～R03，共八套。两个环境保护版本不同时，总量还可能增加。

远端清理的约束已经在代码中实现：

- 两环境都必须接入并能读取、核对实际容器；缺失、不一致、过期、未完成部署时不删除。
- dry-run 无远端写入；实际删除先在两服务器取得最长 15 分钟的清理锁。标准部署入口在锁有效期内拒绝切换版本；锁不足 60 秒时停止继续删除。
- 完整分页读取两个固定 ERP 镜像包，解析 OCI 索引和关联 manifest。仍被受保护或未知版本引用的内容保留，不能直接删除全部无标签条目。
- 先保存删除计划并为退役版本写入标记，再按父到子的顺序删除。失败后保存的计划支持后续重试；新手工标签、缺失依赖、未知格式等都会阻止不确定的删除。
- 首次接入前遗留的 SHA 标签、未登记构建和其他无明确归属产物默认保留。它们需要单独盘点迁移，不能声称启用脚本后整个账号会立即变成五个镜像。
- 本机成功部署后保留当前 + 最近两次成功旧版本 + 人工保护，回收其他已登记成功/失败候选的未使用镜像；不用全局 prune、不强删被容器使用的镜像、不删除数据卷。

镜像之外，容器日志为 20 MB × 5；后端文件日志为每份 20 MB、14 天、总上限 300 MB；部署日志为 5 MB × 4。备份和业务附件需要独立容量/保留策略，镜像清理不处理它们。

## 验证范围

本地验证覆盖发布来源、环境 URL、固定 digest、测试到生产的版本关联、错误环境和旧产物识别、未完成状态、迁移/回滚失败、备份库归属、五套普通版本及额外保护、多环境不可达、OCI 子 manifest/共享引用、分页、删除中断、dry-run 零删除、清理锁和本机候选回收。

另外校验 GitHub Actions、两个 shell 入口、Compose 设置、首次切换故障恢复、Java 迁移助手和两租户审核查询。测试机通过只读预检和三阶段代理语法检查；真实准备/切换仍以工作流记录为准，不能把本地检查写成已部署成功。
