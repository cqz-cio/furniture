# 测试 CD：首次接入与日常更新

日常测试更新的新入口为 [本地 CI 与 SCP 部署](../deploy/erp/LOCAL-CI.md)，不再下载 GitHub 镜像包。下面内容用于保留的旧 GHCR 清单与首次接入操作。

旧清单操作从 GitHub Actions 的 **ERP CD - test** 进入。首次接入不再需要逐条运行本机的备份、恢复、迁移助手。

测试工作流的 `prepare/deploy/rollback` 默认使用 `ERP_IMAGE_TRANSPORT=ssh`：Actions 按已通过 CI 的发布清单下载 GHCR 镜像，校验每个文件层的 SHA-256，打成保留原始根摘要的 OCI 包，通过现有 SSH 连接发送到测试服务器。服务器核对整个包、OCI 内容、镜像摘要、平台和提交标识，导入缓存后才进入原 CD。已有成功 `prepare` 的 `cutover/recover` 继续使用已验证的本机镜像和迁移文件。

无需新增账号或 Secret，也无需服务器直接下载 GitHub 的大镜像文件。测试机已验证使用 Docker 29 的 containerd 镜像存储；镜像加载使用 [Docker 的 load 功能](https://docs.docker.com/reference/cli/docker/image/load/)。传输时临时占用一个镜像包，完成或正常失败后删除；不清理其他镜像、数据库或 Docker 卷。生产工作流仍保持原有入口和下载方式。

每次先核对服务器镜像缓存；两个指定摘要、平台和提交标识均一致时，跳过下载与传输。因此后续数据库演练失败后重试可以复用已导入镜像，容量检查也不会再次计入已占用的镜像空间。

## 操作入口

| operation | 自动执行的工作 | 对现有 ERP 的影响 |
| --- | --- | --- |
| `prepare`（默认） | 核对主机、配置、容量和 CI；拉固定镜像；备份、隔离恢复、V047→V049 演练、租户审核；保存报告 | 原库只读、旧服务继续运行；占用临时磁盘 |
| `cutover` | 阻止外部写入、停止旧服务、备份最新数据并恢复到新库、迁移、启动 Docker、切换并验收代理、开放流量 | 测试 ERP 短暂停用，需要 `confirm_cutover=true` |
| `recover` | 读取中断记录，开放流量前恢复原服务；可能开放流量后只修复新入口 | 需要 `confirm_cutover=true` |
| `deploy` | 已接入后的版本更新、备份、迁移审核、健康检查、条件回滚和镜像清理 | 需 `ERP_CD_ENABLED=true` |
| `rollback` | 回到本机登记过、受保护且数据库兼容的配套镜像 | 需 `ERP_CD_ENABLED=true` |

每次填写完整发布版本，不能填 main、latest 或任意镜像。已有成功 CI 版本示例：

```text
cd-d8ab6721edef297a2333b2d626aa6da177d13bcb-34334861574-1
```

## 首次执行

1. 将 CD 代码推送到 main，让 GitHub 显示新增操作；推送同时触发原 CI。新工作流可以使用此前成功构建且未退役的发布版本。
2. 选择 `prepare`，填写发布版本，保持 `confirm_cutover=false`。检查 Actions Summary 和 `erp-test-deployment-result` 附件。`prepared` 只表示准备成功。
3. 在测试维护时间，用同一入口选择 `cutover`、相同发布版本和 `confirm_cutover=true`。脚本重新备份最新数据，不会把旧演练库直接当作业务库。
4. 切换 `success` 后，将 GitHub **Environment test** 的 `ERP_CD_ENABLED` 改为 `true`，开放日常 `deploy/rollback` 入口。该变量是日常 CD 开关；脚本不擅自修改 GitHub 管理配置。
5. 后续使用 `deploy`。未来出现新数据库版本仍须经过 `erp-cicd.md` 的迁移兼容性审核，本次 V047→V049 演练不能代替未来版本审核。

准备有效期 24 小时，绑定发布清单、旧进程、运行配置、JAR 和代理。相同有效结果直接复用；过期或配置变化后重新 `prepare` 产生新尝试。未完成切换先用 `recover`，不要删除状态文件。已成功切换后重复 `cutover` 只检查健康。

## 适配范围与配置

首次接入严格限定 `ubuntu@124.220.2.69:22`，再次核对主机公钥；根目录 `/opt/oakved-deploy/test`。生产入口不能运行这些首次接入动作。

- 复用 `test` Environment 已有 SSH 变量/Secret；真实 GitHub 检查 run `34423756807` 已通过。无需复制 Secret。
- Linux amd64、Python 3.11+、Docker、Compose 2.30+、MySQL 8 客户端。测试机已安装 Compose 2.40.3，ubuntu 已有非交互 sudo。
- 首次接入通过 sudo 执行可信 main 上的代码；日常部署继续使用普通部署用户。
- 维护账号读取服务器原有 `/etc/mysql/debian.cnf`。临时账号只授权一个精确命名的副本库，并验证不能访问原库，完成后删除。密码不传到 GitHub。
- 原库 `oakved_v032_20260729`、V047、旧服务 `oakved-yudao.service`、旧端口 48080；新后端 48081、后台 18080，仅绑定回环地址。
- 保留原 Redis 连接。本适配器只支持已核实的数据库文件存储；发现外部文件引用或未知挂载会停止。
- 仅修改两个已核实有效代理文件中的 ERP 路由，增加后台及 health/info 代理。网站静态文件保留，额外代理或配置变化会阻止覆盖。

## 数据、验收与恢复

V048 修改分类结构，不能假定旧 V047 程序兼容 V049。首次切换使用**新库接管**：原库不升级、旧 JAR 不替换。保留原配置和代理内容作为恢复依据。

`prepare` 在 `oakved_cd_test_rehearse_<随机标识>` 恢复备份，只运行 CI 镜像内的 Flyway/SQL，不启动完整应用、任务或通知。`cutover` 停止旧写入后重新备份，在 `oakved_cd_test_live_<随机标识>` 恢复并升级，新应用使用仅授权新库的账号。

自动核对备份 SHA-256、恢复表清单、迁移账本/校验和、重复迁移零执行，以及商品、账号、附件数量和附件总字节数。租户 121、162 均执行迁移前后只读审核。已有分类、包装、投影等问题列入报告，脚本不会猜商品类型或自动修复有业务含义的遗留数据。

维护阶段外部 ERP 请求暂时返回 503。脚本用私有探测头做只读验收，核对本机/公开代理的前后端版本标识、依赖健康、匿名访问拒绝、两租户 CMS 公共接口和三个网站首页。

- **开放流量前失败**：停止候选容器，恢复原服务启动状态与代理。失败迁移只影响副本，无须覆盖回原库。
- **流量可能已经开放后失败/断线**：保留新库，用 `recover` 继续修复；不自动切回过时旧库，避免丢失新数据。

技术演练与只读验收不能代替人员登录、商品上架、账号隔离及 CMS 发布的业务验收。

## 日志与容量

测试环境下载方式可通过 Environment 变量 `ERP_IMAGE_TRANSPORT` 选择 `ssh`（默认）或 `ghcr`。新镜像分层、下载续传和上海仓库待接入条件见 [ERP 镜像下载与分层](erp-image-delivery.md)。上海仓库目前尚未创建或启用。

首次接入状态位于 `/opt/oakved-deploy/test/bootstrap/state.json`。`bootstrap/<attempt>/` 保存原配置、`rehearsal.sql`、`cutover.sql`、哈希清单、迁移/审核报告；`bootstrap/commands-*/` 保存命令日志、PID、耗时和退出结果。上述目录只在服务器供 root 读取，Actions 附件只含脱敏结果。

镜像中转的私有日志在 `/opt/oakved-deploy/test/image-relay-logs/`；Actions 每 10 秒显示下载或 SSH 接收的实际字节数。Actions 下载打包上限 300 秒，SSH 传输上限 600 秒，服务器导入上限 300 秒；60 秒没有传输进展会停止。工作流总上限 55 分钟，包含中转及原有数据库演练/恢复预算。临时包位于自建 `/var/tmp/oakved-image-relay-*` 目录，信号中断时也执行本次文件清理。

最终生成 `config/server.json`、`config/backend.env`、`config/mysql.cnf` 和 `releases/<release_id>/`，登记日常 CD 的 `state.json`，配置专用 `/opt/oakved-cd-data/test/{logs,uploads}` 目录权限。

首次成功后清理本次自建且无连接的演练库和 Java 解包临时文件；保留原库、新业务库、备份和审核报告。之前人工建立的演练库及其他 Docker 资源不自动清理。

每阶段有有限超时和进度输出，命令 60 秒无进展会终止进程组。未启用 SSH 中转的直接下载方式通过本机 Docker Engine API 读取下载/解压字节数，每 10 秒报告实际进度；每张镜像总时限 300 秒，60 秒无字节增长或新阶段就关闭请求取消下载。重复状态和重试倒计时不算进展。API 的连接取消行为见 [Docker 官方说明](https://docs.docker.com/reference/api/engine/version/v1.46/#tag/Image/operation/ImageCreate)。两种方式均核对完整镜像 digest 和 linux/amd64 平台，不开放 Docker TCP 端口。

根据镜像压缩/解包大小核算容量，保留至少 10 GiB 余量，占用超过 90% 停止。GHCR 继续保留 **5 套普通版本**，额外保护当前/回滚/pin；生产未接入时保持清理关闭。

## 本次验证记录（2026-09-10）

已通过隔离 Python 单元测试、Java 17 编译和数据库入口拒绝检查、两阶段共 32 条只读审核查询导出、GitHub Actions 语法检查。Linux 测试包含进程超时终止检查。

测试机通过新适配器只读预检；三阶段 Nginx 配置在内存中通过 `nginx -t`。网站端口 80、8081、18081 正常，旧 ERP PID 仍为 3135530、数据库仍为 V047，没有写服务器配置或重启服务。

首次真实 `prepare`（run `34431586588`）在后端镜像下载阶段因 60 秒无 CLI 输出而停止，尚未备份/迁移数据库或切换服务。随后确认原 ERP PID 3135530、健康状态 UP、无运行中的 Docker 容器。下载进度修复已覆盖模拟 Engine 的真实 HTTP 分块响应、持续慢下载、重复状态、无响应、总超时、错误响应、连接中断及 digest 不匹配；这些测试不代替实际镜像下载。

完整隔离迁移和 Docker 切换是否通过，仍以修复后新工作流对应运行记录为准。

第二次 `prepare`（run `34433852337`）仍在下载层时超时。服务器实测从 `pkg-containers.githubusercontent.com` 读取大层的 1 MiB 样本用了 52.56 秒，最大层为 697,913,509 字节，因此改用 Actions 下载后 SSH 中转。已使用本次真实打包、二进制 SSH 传输、服务器校验和导入代码，在测试机完成两个微型探针镜像的导入与摘要检查，随后只删除自建探针镜像，未启动容器。另修正 Docker 29 的根摘要型镜像 ID 校验；仍要求 RepoDigests 和提交标识精确匹配。
