# 测试环境：本地 SCP 部署

测试环境停止使用 GitHub Actions CD 工作流，改为在管理员电脑执行 `deploy/erp/deploy-test-local.ps1`。CI 继续构建并验证镜像、发布 GHCR 发行清单；生产 CD 仍使用原流程。

流程：读取指定 CI 发行清单并验证 CI 成功 → 本地下载与校验 OCI 镜像包 → SCP 上传到上海测试服务器的独立临时目录 → 校验 SHA256、镜像摘要和提交标记 → 导入 Docker → 执行现有备份、迁移演练或部署状态机。

## 前提

- Windows PowerShell、Python 3.11+、OpenSSH 的 ssh/scp、已登录的 GitHub CLI。
- 首次接入需要 Node.js 和 JDK 17，用来导出已有租户审计 SQL 和构建迁移辅助程序。启动器识别当前电脑已安装的 Node/JDK；其他电脑应设置 PATH/JAVA_HOME。
- SSH 默认使用用户目录 `.ssh/tripeer_github_actions` 和 `.ssh/known_hosts`。直接引用现有文件，不复制私钥到临时目录。
- 固定目标是 `ubuntu@124.220.2.69:22`，部署目录 `/opt/oakved-deploy/test`。不接受生产目标。
- GitHub 账号需要读取 CI/发行清单，并能写入 test Deployment 状态。只有实际部署或恢复成功才登记成功；prepare 成功不等于上线，也不等于生产发布条件通过。

## 使用

在仓库根目录执行，`<完整发行ID>` 换成 CI 已生成的 `cd-提交-运行编号-重试编号`：

```powershell
# 仅检查本地工具、凭证和 CI 发行版，不上传、不部署
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-local.ps1 -Release '<完整发行ID>' -CheckOnly

# 首次准备：SCP 镜像导入、备份和隔离数据库迁移演练
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-local.ps1 -Release '<完整发行ID>' -Operation prepare

# 上一步成功后，明确允许测试环境维护切换
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-local.ps1 -Release '<同一发行ID>' -Operation cutover -ConfirmCutover

# 首次切换成功后的日常更新
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\erp\deploy-test-local.ps1 -Release '<新发行ID>' -Operation deploy
```

中断后先查看本地日志和服务器部署日志。首次切换的恢复使用 `-Operation recover -ConfirmCutover`；保留现有状态机对写入开放后的恢复约束，不直接还原旧数据库。

## 超时、缓存和清理

- SCP 最长 30 分钟；通过独立 SSH stat 读取实际上传字节，每 10 秒报告进度，连续 60 秒不增长停止。按已测约 716 KB/s，800 MB 首次传输预计约 19 分钟，不能承诺几分钟完成。
- 本地下载保留原来的 300 秒限时；全流程最长 4000 秒，连续 60 秒没有日志则停止本地进程树。服务器部署操作保留自己的有限超时和恢复日志。
- 日志、PID、命令及退出结果保存到仓库 `work/codex-logs/`；镜像缓存位于 `work/local-test-images/<发行ID>/complete`。同一发行版重试先核对缓存哈希，服务器已有完整镜像则直接跳过上传。
- 上传失败不会导入镜像或开始部署。完成或失败后尝试删除本次 SCP 临时文件；网络完全中断导致清理失败时，会打印需要检查的确切临时目录。
- 新发行版仍上传完整归档，SCP 本身不支持本脚本的断点续传。镜像分层不等于 SCP 增量上传。
- 本地缓存不自动删除，避免误删用户文件；GHCR 原有“最近 5 个普通版本，保护当前和回滚版本”策略保持不变。

删除仓库内测试 CD workflow 需要推送后才反映到 GitHub。未推送前请不要使用旧的 `ERP CD - test` 入口。本地部署不需要等待这个脚本修改重新构建镜像，可使用已有的成功 CI 发行版。
