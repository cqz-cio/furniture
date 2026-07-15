# Local Infrastructure

This Compose stack runs the MySQL and Redis dependencies used by `yudao-server` in the `local` profile.

## Start safely

From `yudao-cloud` run:

```powershell
powershell -ExecutionPolicy Bypass -File ".\script\docker\start-local-infra.ps1"
```

Normal startup never removes Docker volumes. It starts the services and applies only pending numbered migrations from `sql/mysql/migrations`. Applied versions and normalized SHA-256 checksums are stored in `schema_migrations`; changing an already-applied migration is treated as an error.

## First database creation

An empty MySQL volume imports only `sql/mysql/oakved-baseline.sql`. This generated baseline contains:

- the platform and Quartz schemas;
- all numbered migrations in version order;
- tenant 121 demo data with 26 mall products;
- the matching ERP products, warehouse stock and mall/ERP mappings;
- the migration ledger and checksums.

After adding a new numbered migration, regenerate the first-install script from `furniture web`:

```powershell
npm run build:db-baseline
npm run verify:db-migrations
```

Never edit a published `Vnnn__*.sql` file. Add the next version instead.

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
