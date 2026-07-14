# Local Infrastructure

This Compose file starts only the local dependencies required by `yudao-server` in the `local` profile.

## Start

Run from `yudao-cloud`:

```powershell
powershell -ExecutionPolicy Bypass -File ".\script\docker\start-local-infra.ps1"
```

Or run Docker Compose directly:

```powershell
docker compose -f ".\script\docker\docker-compose-local-infra.yml" up -d
```

## Services

- MySQL: `127.0.0.1:3306`
- Database: `ruoyi-vue-pro`
- User: `root`
- Password: `123456`
- Redis: `127.0.0.1:6379`

These values match `yudao-server/src/main/resources/application-local.yaml`.

## SQL Initialization

MySQL imports these files on first container volume creation:

- `sql/mysql/ruoyi-vue-pro.sql`
- `sql/mysql/quartz.sql`
- `sql/mysql/yudao-module-tables.sql`
- `sql/mysql/member-email-auth.sql`
- `sql/mysql/member-trade-application.sql`
- `sql/mysql/member-membership.sql`
- `sql/mysql/member-gift-registry.sql`
- `sql/mysql/trade-gift-registry-context.sql`

The startup script also reapplies local feature migrations after MySQL is ready:

- `sql/mysql/yudao-module-tables.sql`
- `sql/mysql/member-email-auth.sql`
- `sql/mysql/member-trade-application.sql`
- `sql/mysql/member-membership.sql`
- `sql/mysql/member-gift-registry.sql`
- `sql/mysql/trade-gift-registry-context.sql`

If you need to reimport SQL from scratch, this removes the local MySQL and Redis volumes:

```powershell
powershell -ExecutionPolicy Bypass -File ".\script\docker\start-local-infra.ps1" -Recreate
```

## Port Conflicts

If another container is already using `3306` or `6379`, stop it in Docker Desktop before starting this Compose stack.
