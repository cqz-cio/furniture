# 统一本地 CI 与手动测试、生产 CD

CI 流程：推送 `main` → GitHub `ERP full-stack CI` 完成部署脚本、数据库/后端、管理端检查 → 本机 `ERP local image CI` 检出同一提交 → Docker 构建一个后端、测试后台和生产后台两个变体 → 校验并保存同一 release ID 的 OCI 包。CI 不连接服务器、不上传镜像、不创建部署记录。

CD 流程：用户手动运行 `ERP CD - test`，填写 CI 输出的 release-id 和 deploy/rollback → 核对云端检查、本机构建均已成功 → SCP 上传指定的本地包 → 服务器导入镜像并更新服务 → 健康检查，失败自动恢复旧版本。生产使用独立的手动 `ERP CD - production` 入口。

测试 CD 直接 SCP 本地包，不依赖 GHCR。手动生产 CD 才将通过测试的配套镜像上传 GHCR，由正式服务器按 digest 拉取。首次构建仍需下载 Maven、Node、JRE 等基础镜像和依赖，之后使用本机构建缓存。服务器无需安装编译工具。

## 启用前提

- 本机 Windows 账户 `admin` 已登录，Docker Desktop 的 Linux 引擎正常运行。
- Python `C:\Python314\python.exe`、Git `D:\Git\cmd`、Windows OpenSSH 可用。
- GitHub 官方 Windows x64 Runner 安装在 `D:\furniture web2b\work\erp-actions-runner`，注册到 `cqz-cio/furniture`，自定义标签 `erp-test-local`。使用交互账户运行，不安装为 SYSTEM 服务。
- 现有 SSH 私钥 `~/.ssh/tripeer_github_actions` 和 `known_hosts` 沿用；目标固定为测试服务器 `ubuntu@124.220.2.69:22`。
- 测试服务器已完成首次 Docker 接入；此流程使用日常 `deploy`。新增数据库版本仍需原有兼容性审核及恢复演练记录。
- 工作流文件需进入远端默认分支才能触发。注册本机 Runner 和推送工作流是启用动作，不会因本地文件已修改而自动完成。

仓库目前为公开仓库。本机 Runner 会赋予被调度的仓库工作流本机代码执行能力，并能使用该账户的 Docker 和 SSH 权限。这里只允许受信任 `main` 的成功 CI 触发构建，禁止将 PR/fork 的代码调度到这台 Runner；仓库其他工作流也必须遵守这个限制。Runner 注册和自动启动需单独确认启用。

Runner 注册完成后，以当前用户执行一次 `deploy/erp/install-local-runner-startup.ps1`，安装登录时启动任务 `Oakved ERP Local CI Runner`。它启动 Docker Desktop 和官方 Runner 启动器，日志和 PID 写入 Runner 目录的 `local-logs`。电脑关机或用户未登录时本机构建会排队。脚本用 `run.cmd` 保留官方 Runner 自动更新后的重启行为。

## 版本一致性

入口核对上游 CI 的提交、分支、仓库、尝试编号、工作流路径，以及三个必需检查均为 `success`。PR、fork、跳过/失败的检查不能生成可部署版本。检出目录必须干净。

构建使用 `linux/amd64`，输出 OCI 文件而非推送仓库。镜像配置中的提交标签、后端非 root 用户、数据库迁移内容、前端 release 回执和测试 API 地址均要验证；合并镜像包时逐个校验 SHA-256。服务器导入后再次核对镜像 digest 和提交标签。

本地清单采用 schema 2，`localhost/...@sha256:...` 仅为导入后的镜像名称，不需要启动 localhost 镜像仓库。schema 2 只允许测试环境，必须预先导入。新增 `production` 字段记录同次构建的生产后台 digest 和公开地址；测试部署记录的完整清单指纹包含此字段。生产入口从已验收清单派生 schema 1，将镜像分发地址换为 GHCR，保留原始 digest，并嵌入完整 `source_test`。不能仅凭相同提交或相同版本号绕过测试门禁。旧 schema 1 的 GHCR 清单继续可用。

本机构建、测试 CD 和生产镜像上传共用串行锁，不取消正在进行的部署。构建前检查 `main` 是否已有新提交，过时提交跳过构建。手动 CD 允许明确选择已成功构建的旧版本，不会擅自改为最新版本；回滚仍受服务器保护版本和数据库兼容性检查约束。

## 日志、空间与回滚

持久缓存：`D:\furniture web2b\work\erp-ci-cache`。

- `logs/`：每个构建步骤独立日志、PID、耗时及退出码。
- `<release-id>/complete/`：`release.json`、`header.json`、测试用 `images.oci.tar`、生产上传用 `production.oci.tar`，仅在全部校验完成后原子发布。
- `latest-result.json`：最近构建结果；`latest-deployment.json`：最近手动部署结果。构建页面 Summary 提供可复制的 release-id。
- 手动成功部署后获取服务器清理锁及新鲜状态，保留最近 5 个普通版本，额外保留当前、最近两次回滚和指定保护版本。CI 不连接服务器执行清理；只构建不部署时版本包会累积。状态不完整或服务器不可达时跳过清理。
- 专用构建器 `oakved-local-ci` 的可回收缓存目标上限 10 GB；不会执行全局 Docker prune、删除卷或清理其他项目。构建和归档可能临时超过该值，开始前要求至少 12 GiB 空闲。失败构建的残留包和日志保留供排查，不纳入自动删除。
- 每个子进程有总时限；无进展 60 秒终止。完整单镜像构建上限 30 分钟，SCP 上限 30 分钟；本地 CI 工作流上限 100 分钟，手动测试 CD 上限 70 分钟。

首选在 GitHub Actions 手动运行 `ERP CD - test`；也可在 PowerShell 手动部署已经生成的本地版本（替换实际 release-id）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-built.ps1 -Release '<release-id>' -Operation deploy
```

回滚到服务器登记的受保护成功版本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-built.ps1 -Release '<release-id>' -Operation rollback
```

添加 `-CheckOnly` 仅核对本地包和上游 CI。重试不会重新构建镜像或下载 GHCR 包。已有服务器镜像验证通过时跳过 SCP。旧 schema 1 版本仍使用原 `deploy-test-local.ps1` 入口。不要用日常回滚脚本代替数据库降级；原有兼容性保护保持有效。

## 生产流程

测试 CD 和业务验收通过后，手动运行 `ERP CD - production`，选择相同 release ID。默认 `preflight` 会在本机 Runner 校验云端检查、本机构建及精确测试记录，将现有 OCI 字节上传 GHCR 并登记派生清单，再检查正式服务器；不会重新构建镜像或切换服务。选择 `deploy` 才会切换。生产 API 固定为 `https://api.vanzhome.com`，官网地址为 `https://www.vanzhome.com`。

原 `publish_production=true` 独立构建入口已移除。旧测试产物没有生产变体时必须重新运行共享 CI，再手动测试新 release；不能在 CD 阶段补建。首次生产上传要求原缓存仍在，已发布版本可复用 GHCR 清单和镜像，回滚不依赖本地缓存。详见 [生产 CD 说明](../../docs/erp-production-cd.md)。

GitHub 镜像保留策略继续适用于已有 GHCR 版本；本地清单不参与 GHCR 清理。
