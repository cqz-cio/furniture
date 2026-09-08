# ERP 测试环境检查

测试目标固定为 `ubuntu@124.220.2.69:22`。执行入口为 GitHub Actions 中的 **ERP test environment check**，仅支持从 `main` 手动运行，并引用 GitHub Environment `test`。测试不发布程序、不执行 Flyway、不更改账号、不写入业务数据，也不发送询盘通知。

## GitHub 配置

Environment `test` 需要以下 Secrets：

- `TENCENT_SSH_PRIVATE_KEY`
- `TENCENT_SSH_KNOWN_HOSTS`
- `YUDAO_DB_PASSWORD`
- `YUDAO_REDIS_PASSWORD`
- `VANZ_WEBSITE_INQUIRY_SHARED_SECRET`

Environment Variables：`TENCENT_SSH_HOST=124.220.2.69`、`TENCENT_SSH_USER=ubuntu`、`TENCENT_SSH_PORT=22`。环境的部署分支策略应仅允许 `main`。无需在仓库文件中填写这些 Secrets 的值。

## 检查范围

1. 使用已保存的 SSH 私钥连接测试服务器，严格验证已保存的主机公钥。
2. 比较提供的三个应用密钥与正在运行的 ERP 配置；使用提供的数据库、Redis 密码实际执行 `SELECT 1`、`PING`。Redis 的 Spring 配置别名也必须一致。
3. 验证询盘密钥已绑定到部署包配置且功能启用，不发送真实通知。
4. 检查后端健康、登录页、官网、匿名管理接口拒绝访问，以及现有租户 `162`、`121` 的 CMS 导航和文章公共接口。VANZ 有已发布文章时检查文章详情；没有文章时明确标为跳过。
5. 检查官网端口 `18081` 访问 CMS 的跨域预检与响应，检查当前管理后台文件是否含生产 API 域名。
6. 比较数据库、部署包与当前仓库的迁移版本，报告差异。

SSH 私钥和已知主机公钥仅暂存于 runner 的受限临时目录，结束后清理。应用密码通过 SSH 加密通道的标准输入传递，不写入服务器文件，不放进命令参数。日志与 job summary 只显示检查结果和迁移版本，不输出密码、命令原始错误、数据库内容或 CMS 正文。每个子命令和整个远程检查都有超时。

`passed=true` 表示本次现有服务与凭据检查没有失败；`warn` 是需要处理的差异，`skip` 是没有文章样本时未执行详情检查。`release_ready` 始终为 `false`，因为本任务不做新版本发布验收。检查成功不能证明员工账号登录、编辑、发布或 TRIPEER 官网已经完成 CMS 对接。

## 首次检查时的发布前事项

- 测试服务器数据库和部署包为 V047，当前仓库包含 V048，服务器 `SPRING_FLYWAY_ENABLED=false`。发布前应完成当前测试数据的 Phase 0 审核、备份与迁移演练；本检查不会自动开启迁移。
- 现有 full-stack CI 的后台构建目标是生产 API。测试部署须使用测试环境的构建参数；不能直接将现有生产后台镜像部署到测试站。
- TRIPEER 专属 CMS 站点、品牌运营账号、官网数据读取和编辑发布流程仍需另行实现并验收。

## 本地维护

无凭据单元验证：

```text
python -B -m unittest discover -s scripts/test-environment -p "test_*.py" -v
```

本地已获授权的只读检查可设置三个目标 Variables，并运行以下命令（使用实际已有的本地路径）：

```text
python -B scripts/test-environment/check.py --server-config --ssh-key-file <private-key-path> --known-hosts-file <trusted-known-hosts-path> --report <report-path>
```

`--server-config` 只验证服务器当前配置，报告中的 `credential_source=active_server` 会明确区分这一点。只有 GitHub 中默认模式的实际运行，才能验证 GitHub 保存的 Secrets 是否被正确读取使用。本地运行同样须由外层进程设置不超过 60 秒的超时。
