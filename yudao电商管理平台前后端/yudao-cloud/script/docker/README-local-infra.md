# Local Infrastructure

This Compose stack runs the MySQL and Redis dependencies used by `yudao-server` in the `local` profile.

## Start safely

From `yudao-cloud` run:

```powershell
powershell -ExecutionPolicy Bypass -File ".\script\docker\start-local-infra.ps1"
```

Normal infrastructure startup never removes Docker volumes and never executes project SQL. `yudao-server` owns database initialization and upgrades through Flyway.

## First database creation

The MySQL container creates an empty database and keeps its data in the named volume. It has no branch/worktree SQL bind mount. When `yudao-server` starts, Flyway reads migrations packaged in the JAR. A new database starts from `sql/mysql/flyway/Bnnn__oakved_baseline.sql`, which contains:

- the platform and Quartz schemas;
- all numbered migrations in version order;
- tenant 121 demo data with 26 mall products;
- the matching ERP products, warehouse stock and mall/ERP mappings;
- a compatibility copy of the retired migration ledger; Flyway records all new history in `flyway_schema_history`.

After adding a new numbered migration, regenerate the compatibility baseline from `furniture web`. The existing Flyway checkpoint remains valid and Flyway applies newer V files after it:

```powershell
npm run build:db-baseline
npm run verify:db-migrations
```

Never edit a published `Vnnn__*.sql` or `Bnnn__*.sql` file. Add the next version instead. Historical Flyway baselines are retained for validation, and all B/V resources are copied into `yudao-server.jar` during Maven resource processing.

## Deliberate local reset

Reset is separate from startup because it deletes the local MySQL and Redis volumes. The reset script first writes and validates a `mysqldump` backup, then requires the exact confirmation text `RESET OAKVED LOCAL DATA`:

```powershell
powershell -ExecutionPolicy Bypass -File ".\script\docker\reset-local-infra.ps1"
```

Backups are written under `script/docker/backups` unless `-BackupDirectory` is supplied.

## Connection details

- MySQL: `127.0.0.1:3306`
- Database: `ruoyi-vue-pro`
- User: `root`
- Password: `123456`
- Redis: `127.0.0.1:6379`

These values match `yudao-server/src/main/resources/application-local.yaml`.

If another service already uses port `3306` or `6379`, stop it before starting this stack.
