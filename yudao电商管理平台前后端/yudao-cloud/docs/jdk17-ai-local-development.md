# JDK 17 + AI 本地开发指南

本分支将 Yudao 后端升级到 JDK 17 / Spring Boot 3，并启用 AI 模块。所有 JDK 17 命令只在当前子进程中设置 `JAVA_HOME`，不会修改 Windows 默认 Java，现有 JDK 8 项目可继续使用原启动方式。

## 前置条件

- Microsoft/OpenJDK 17（脚本会自动解析本机 JDK 17）
- Maven
- Docker Desktop
- 已运行的 `yudao-mysql-local`、`yudao-redis-local`、`yudao-nacos-local`
- MySQL root 密码通过当前 shell 的 `YUDAO_MYSQL_ROOT_PASSWORD` 环境变量提供；不要把真实密码写入仓库、脚本或命令历史。

以下命令都在 `yudao-cloud` 目录执行。如 PowerShell 限制本地脚本，可先仅对当前终端执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
```

## 构建与测试

确认脚本使用 JDK 17，且不污染父进程环境：

```powershell
.\script\jdk17\tests\Jdk17Toolchain.Tests.ps1
```

运行全量后端测试：

```powershell
.\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('clean', 'test')
```

## AI 数据库迁移

迁移会先生成逻辑备份，再可重复执行地创建/修复 14 张 AI 表、`tenant_id` 租户隔离列、字典、菜单和超级管理员权限。历史 API Key 和模型会保留，但默认禁用。

```powershell
.\script\jdk17\Apply-AiMigration.ps1
.\script\jdk17\tests\AiMigration.Tests.ps1
```

备份目录：`.local-backups/mysql/`。本次已验证的关键备份：

- `ruoyi-vue-pro-20260714-150119.sql`：首次 AI 迁移前
- `ruoyi-vue-pro-20260714-171554.sql`：补齐 AI 多租户列前

## 一键启停

构建并启动默认四个服务：

```powershell
.\script\jdk17\Start-Jdk17Backend.ps1
```

已构建 JAR 时可跳过构建，也可只启动部分服务：

```powershell
.\script\jdk17\Start-Jdk17Backend.ps1 -Services @('system-server', 'product-server', 'ai-server', 'gateway') -SkipBuild
```

| 服务 | 端口 |
| --- | ---: |
| gateway | 48080 |
| system-server | 48081 |
| ai-server | 48090 |
| product-server | 48100 |

环境验收：

```powershell
.\script\jdk17\Verify-AiEnvironment.ps1
```

安全停止：

```powershell
.\script\jdk17\Stop-Jdk17Backend.ps1
```

停止脚本只会终止 `.local-run/jdk17/*.json` 记录、且命令行属于当前仓库 JAR 的 PID；不会停止 Docker 或其他 Java 进程。日志位于 `.local-run/jdk17/logs/`。

## AI 无 Key 与配置 Key

未配置外部 API Key 时：

- AI 服务、菜单、API Key/模型/知识库/工作流管理页可正常启动和访问。
- 新建聊天对话会返回明确的“找不到默认模型”业务错误，不会导致服务退出。
- 启动过程不会连接 OpenAI、DashScope 等外部模型。

启用真实聊天/知识库前，在管理后台执行：

1. 进入「AI > 控制台 > API 密钥」，录入自己的 Key，不要截图或提交到 Git。
2. 进入「模型配置」，分别配置聊天模型和向量模型，设为启用/默认。
3. 手工发送一次聊天，再执行一次知识库文档向量化。
4. 确认 `.local-run/jdk17/logs/ai-server.out.log` 无鉴权或网络错误。

第 3 步依赖用户自有凭证和外网，不纳入无密钥自动验收。

## 回滚

1. 执行 `.\script\jdk17\Stop-Jdk17Backend.ps1`。
2. 选择上述逻辑备份，复制到 MySQL 容器并导入（使用当前 shell 中的密码环境变量）：

```powershell
$backup = '.\.local-backups\mysql\ruoyi-vue-pro-20260714-150119.sql'
docker cp $backup yudao-mysql-local:/tmp/yudao-rollback.sql
docker exec -e "MYSQL_PWD=$env:YUDAO_MYSQL_ROOT_PASSWORD" yudao-mysql-local `
  mysql -uroot --default-character-set=utf8mb4 ruoyi-vue-pro -e "source /tmp/yudao-rollback.sql"
docker exec yudao-mysql-local rm -f /tmp/yudao-rollback.sql
```

3. 按原 JDK 8 流程启动保留的旧服务。JDK 17 脚本没有修改系统 `JAVA_HOME` 或默认 `Path`。
