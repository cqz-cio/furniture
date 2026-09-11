# 本地与 GHCR：最新五个完整版本

`ERP image retention` 同一次任务检查本地 OCI 镜像包和 GHCR 已登记镜像，两边各按清单构建时间保留最新 5 个完整 release。同一时间按 CI run ID、attempt 排序。每个 release 包含后端与对应的后台变体，不按单个镜像标签分别取五个。已登记版本不再额外保护 current、rollback、pin，也没有 24 小时宽限。

## 触发与开关

- `ERP local image CI` 整个工作流成功后自动执行，包括镜像已上传成功。
- 每天北京时间 03:30 自动检查；GitHub 调度可能延迟。
- 手动运行默认只预览；勾选 `apply` 才删除。
- 自动清理默认启用。仓库变量 `ERP_IMAGE_CLEANUP_ENABLED=false` 暂停自动清理，也禁止手动实际删除；仍允许手动预览。
- 清理运行在本机 Windows Runner。电脑关机、账户未登录或 Runner 离线时，两边的清理任务都会排队，等 Runner 恢复；生产 CD 仍运行在 GitHub，不依赖本机在线。

两个 CI 工作流继续分开，清理作为 CI 成功后的独立任务，便于独立重试和定时运行。它先获取 `erp-test-local-build-deploy`，再获取 `erp-release-control`，与构建、上传、测试 CD、生产 CD 互斥。锁顺序与 CI 一致，避免互相等待。运行中任务不会主动取消；GitHub 默认并发队列只保留一个待执行任务，待执行任务仍可能被后来的任务替换，必要时手动补跑。

## 实际空间范围

- 本地：`D:\furniture web2b\work\erp-ci-cache/<release-id>/complete/` 中的测试及生产 OCI 包、配套清单。完整包先重命名为 `retiring`，再逐个删除明确文件；中断后下次继续。构建、上传、手动测试部署和清理都获取同一个操作系统文件锁。
- GHCR：仅 `cqz-cio/furniture-erp-backend`、`cqz-cio/furniture-erp-admin` 两个包，按完整 release 清单核对镜像依赖后删除旧版本；先写 retirement 标记，防止后续 CD 选择已淘汰版本。中断后按保存的删除计划继续核对，不盲目重删。
- 未登记的历史镜像、无法归属的镜像、未完成构建、日志不算完整发布版本，不猜测归属删除。计划会列出本地未完成版本及 GHCR 未管理的 digest。共享镜像与仍被保留内容引用的层继续保留；因此“五个版本”不是整个 Docker/注册表恰好五个对象，也不是严格的磁盘容量上限。
- Docker 专用构建器 `oakved-local-ci` 的构建缓存继续由 CI 按 10 GB 目标回收，不按发布版本计数。
- 不操作服务器业务数据、数据卷、附件、数据库备份。服务器自身已导入镜像的回收沿用部署流程，和本地归档/GHCR 保留数量分开。

淘汰的旧版本不能再依赖本地包或 GHCR 重部署、回滚，即使服务器仍有其历史记录。需要日常回滚时应选择仍在最新五版内、已成功部署并满足数据库兼容条件的版本。自动清理不会切换正在运行的服务。

## 查看结果

Actions 的 `erp-retention-plan` 附件包含 `local-retention-plan.json` 和 `retention-plan.json`，列出保留、淘汰、可恢复的中断删除和实际镜像删除计划。本地还报告预计释放字节数。任一侧检查失败显示任务失败，另一侧仍尝试检查；总任务上限 15 分钟，每次网络请求最多 20 秒。清单损坏、未知目录内容、链接路径、共享依赖不完整都会阻止对应侧删除。

只读本地预览：

```powershell
& 'C:\Python314\python.exe' -B deploy/erp/cache_retention.py --cache 'D:\furniture web2b\work\erp-ci-cache'
```

实际删除只允许受信任 main 的清理工作流。清理使用任务临时 `GITHUB_TOKEN`，无需每次个人授权；对应 GHCR 包须授予该仓库管理权限。常规上传的 Write 权限与删除权限不同，删除返回 403 时检查包 Settings → Manage Actions access 的 Admin 权限。
