# Yudao 一键后端生命周期脚本设计

## 目标

为本地 ERP 开发环境提供一条命令启动、另一条命令关闭主后端和 AI 后端，避免用户分别管理两个终端和两套命令。

日常命令固定为：

```powershell
.\start-yudao-all-backend.ps1
.\stop-yudao-all-backend.ps1
```

## 范围

一键脚本只管理以下两个 Java 进程：

- `yudao-server`，端口 `48080`
- `ai-server`，端口 `48090`

脚本不负责启动前端，不把 AI 模块合并进 `yudao-server`，也不改变现有微服务启动器默认管理的 gateway、system-server 或 product-server。

## 文件和职责

### `start-yudao-all-backend.ps1`

位于工作区根目录，作为用户日常使用的唯一后端启动入口。

它负责：

1. 定位 `yudao-cloud` 项目和现有 JDK 17 工具脚本。
2. 确认 MySQL、Redis、Nacos Docker 容器正在运行。
3. 在启动任何进程前确认 `48080`、`48090` 都未被占用。
4. 解析项目支持的 JDK 17，不依赖系统默认 Java 版本。
5. 默认复用已构建 JAR；使用 `-Build` 时先构建 `yudao-server` 和 `ai-server` 及其依赖。
6. 后台启动 `yudao-server`，将日志和状态记录写入 `.local-run/jdk17`。
7. 复用 `Start-Jdk17Backend.ps1` 启动 `ai-server`。
8. 只有两个 `/actuator/health` 都返回 `UP` 时才报告成功。
9. 如果本次启动过程失败，只回滚本次已经启动的进程，并保留可诊断日志。

### `stop-yudao-all-backend.ps1`

位于工作区根目录，作为用户日常使用的唯一后端关闭入口。

它调用增强后的 `Stop-Jdk17Backend.ps1`，只请求关闭 `yudao-server` 和 `ai-server`。停止前必须同时验证状态文件、PID、JAR 路径位于当前仓库内，并且进程命令行包含记录的 JAR 路径。

### `Stop-Jdk17Backend.ps1`

增加可选的 `-Services` 参数。未传入时保持现有行为，处理运行目录中的全部已记录服务；传入时只处理指定服务的状态文件。这样既保持兼容性，又允许一键关闭脚本精确停止两个目标进程。

## 运行状态与日志

统一运行目录为：

```text
yudao电商管理平台前后端/yudao-cloud/.local-run/jdk17
```

状态文件：

- `yudao-server.json`
- `ai-server.json`

日志文件：

- `logs/yudao-server.out.log`
- `logs/yudao-server.err.log`
- `logs/ai-server.out.log`
- `logs/ai-server.err.log`

每个状态文件记录服务名、PID、JAR 绝对路径、端口和启动时间。关闭逻辑不信任单独的 PID，避免 PID 被系统复用后误关无关进程。

## 参数和用户体验

启动脚本支持：

- 默认模式：直接使用现有 JAR，适合每日启动。
- `-Build`：启动前使用 JDK 17 重新构建两个后端。
- `-StartupTimeoutSeconds`：覆盖单个服务的健康检查等待时间，默认 `180` 秒。
- `-VerifyOnly`：只验证路径、JDK、脚本和 JAR，不启动进程，供自动化测试和诊断使用。

成功时输出两个服务的 PID、端口、健康状态和日志目录。失败信息必须指出失败的服务以及对应日志文件。

关闭脚本应允许重复执行。如果没有状态记录或进程已经退出，清理过期状态并报告，无需报错。

## 错误处理

- 任一端口已占用时，在启动任何新进程前失败，避免半启动状态。
- Docker 依赖未运行时直接失败，并列出缺失容器。
- JAR 不存在时提示使用 `-Build`。
- 主后端启动失败时不尝试启动 AI 后端。
- AI 后端启动失败时停止本次启动的主后端。
- 关闭时发现状态记录与实际命令行不匹配，保留状态记录并给出警告，不终止该进程。

## 测试策略

新增 PowerShell 合约测试，覆盖：

1. 根目录一键启动和关闭脚本存在。
2. `-VerifyOnly` 确认使用 JDK 17、目标端口为 `48080` 和 `48090`、两个 JAR 均可定位。
3. 启动脚本包含启动前端口预检、Actuator `UP` 健康检查和失败回滚。
4. `Stop-Jdk17Backend.ps1 -Services` 只处理指定状态文件，保留未指定服务的状态和进程。
5. 现有 JDK 17 生命周期测试继续通过。
6. 实际执行一键启动，验证两个健康端点为 `UP`；再执行一键关闭，验证两个端口均释放。

## 兼容性与非目标

- 保持现有 `start-yudao-backend.ps1` 行为不变，避免影响已有的单体后端前台启动习惯。
- 保持 `Start-Jdk17Backend.ps1` 默认微服务列表不变。
- 不自动启动 Docker Desktop；只验证所需容器状态。
- 不管理前端 Vite 进程。
