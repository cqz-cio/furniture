# Oakved 本地运行入口

`oakved.ps1` 把“日常开发目录”和“正在运行的代码”分开。正式安装位置为：

```powershell
D:\code\.runtime\bin\oakved.ps1
```

## 日常命令

稳定运行 `main` 当前已经提交的版本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch main
```

查看状态或停止：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" status
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" stop
```

安装或更新入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\install-oakved-runtime.ps1"
```

安装器会先把内容不同的旧入口备份到 `D:\code\.runtime\backups\launcher`，不会创建或占用 `main` 工作树。

## 两种运行模式

### 分支快照模式

`start -Branch main` 先解析本地 `main` 引用所指向的完整 commit，再创建：

```text
D:\code\.runtime\worktrees\main_0d6e4079-<12位commit>
```

这个目录是 detached Git worktree，因此只固定到一次提交，不占用 `main` 或其他真实开发分支。服务启动后，`D:\code` 可以自由切换到 `main`、任务分支或其他分支；已经运行的后端和前端仍从快照目录读取文件。

未提交的文件不会进入分支快照。若该分支当前所在的开发 worktree 有未提交修改，启动命令会明确警告，但仍只运行已经提交的 HEAD。

如果运行期间 `main` 又产生新提交，`status` 会返回 `UpdateAvailable: true`。这只表示可以重启升级，当前固定版本仍可保持健康。

### 实时开发模式

需要让运行服务直接读取某个开发 worktree 时，显式执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Worktree "D:\code\.worktrees\codex-example"
```

此模式适合热更新调试。修改文件、切换该 worktree 的分支或删除目录，都可能影响正在运行的服务。

## 状态与数据边界

- `D:\code\.runtime\runtime.json` 记录实际运行的模式、commit、快照路径、数据库和进程 PID；`status` 与 `stop` 以它为准，不猜测当前 checkout。
- 数据库名称仍按逻辑分支生成。例如 `main` 始终使用 `oakved_main_0d6e4079`，更换 commit 快照不会新建或丢失商品数据。
- 快照只隔离代码和构建产物；MySQL 数据、日志、缓存和启动器都保存在 `D:\code\.runtime` 的受管目录中。
- `start -Branch` 只接受本地分支已经提交的内容。需要运行最新改动时，先提交，再停止并重新启动相应分支。
