# Oakved 数据库版本管理

## 最终结构

`yudao-server` 是数据库版本的唯一执行者。Maven 构建时会把以下 SQL 放进 `yudao-server.jar` 的 `db/migration`：

- `sql/mysql/flyway/Bnnn__oakved_baseline.sql`：空数据库的累计基线检查点；目录会保留历史 B 文件，新空库自动选择最高检查点，再执行其后的 V 文件；
- `sql/mysql/migrations/Vnnn__*.sql`：不可修改的顺序增量。

MySQL Docker 容器只提供数据库进程和持久化数据卷，不再挂载任意分支或 worktree 下的 SQL。后端启动时，Flyway 先校验 `flyway_schema_history`，再只执行尚未成功的版本；迁移失败时后端不会对外提供服务。

## 日常开发流程

1. 从最新本地 `main` 创建任务分支。
2. 需要改数据库时，只新增下一个连续的 `Vnnn__description.sql`；已提交、已执行的 V 文件永不修改。
3. 在 `furniture web` 运行：

   ```powershell
   npm run build:db-baseline
   npm run verify:db-migrations
   ```

4. 提交 V 文件和生成的兼容基线。普通版本无需新增 B：例如保留 `B046` 后，空库会先执行 `B046`，再执行新增的 `V047`。历史 B 文件和 V 文件一样不可修改、不可删除。
5. 合并到 `main` 前由 GitHub Actions 先拒绝修改或删除任何已存在的 V/B 文件，再创建一次性 MySQL，验证空库安装、旧账本接管、重复启动幂等性、JAR 打包和后端构建。

只有当 V 历史已经很长、确实需要新的安装检查点时，才显式运行：

```powershell
npm run build:db-baseline -- --create-flyway-baseline
```

这会新增当前版本的 B 文件，但不会删除旧检查点。

开发分支不需要长期维护一套数据库。CI 数据库在任务结束后销毁；日常稳定运行只启动 `main`。只有主动用启动器运行另一个分支时，才会使用启动器已有的隔离数据库名。

## 旧数据库接管

旧环境仍有 `schema_migrations`、但没有 `flyway_schema_history`。首次使用新版 JAR 时，后端会逐项比较旧账本与 JAR 中的 V 文件：版本、文件名、描述和规范化 SHA-256 必须形成完全一致的连续前缀。通过后，Flyway 从旧账本的最高版本接管并执行更高版本；任何不一致都会停止启动，不会静默跳过。

分支运行启动器会在最后一次旧迁移或接管前保留原有备份保护。生产发布仍应在部署前做数据库备份，并先让同一 JAR 在测试环境通过。

## 本地命令

基础设施：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\yudao电商管理平台前后端\yudao-cloud\script\docker\start-local-infra.ps1"
```

后端：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\start-yudao-all-backend.ps1" -Build
```

稳定 `main` 全栈：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch main
```

数据库当前版本以 `flyway_schema_history` 为准。旧 `invoke-local-migrations.ps1` 已退役，不能再作为第二个迁移写入者。
