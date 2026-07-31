# Branch-Aware Oakved Runtime Launcher Design

## Objective

Provide one stable local launcher that starts the ERP admin UI, furniture storefront,
Yudao backend, and the matching database state from an explicitly selected Git branch
or worktree. The launcher must never infer the requested branch from the caller's
current directory and must fail closed when code, process, port, or database provenance
does not match.

## User Interface

The stable entry point is installed outside all Git worktrees:

```text
D:\code\.runtime\bin\oakved.ps1
```

Supported commands:

```powershell
oakved.ps1 start -Branch main
oakved.ps1 start -Branch codex/seo-foundation
oakved.ps1 start -Worktree D:\code\.worktrees\seo-foundation
oakved.ps1 status
oakved.ps1 stop
```

`-Branch` resolves an existing worktree through `git worktree list --porcelain`.
It fails if the branch is not checked out in exactly one worktree. It never falls
back to `D:\code`. `-Worktree` resolves and validates the branch from that exact path.
Detached worktrees are rejected.

The default operating model permits one active branch runtime at a time. Fixed local
ports remain stable:

- ERP admin UI: `80`
- furniture storefront: `5173`
- Yudao backend: `48080`

Unknown processes occupying these ports cause startup to fail. The launcher stops only
processes recorded in its own runtime manifest.

## Stable and Versioned Components

The canonical, reviewable implementation lives in the repository under:

```text
scripts/runtime/
```

An install script copies the canonical launcher and required modules into
`D:\code\.runtime\bin`. The installed copy is stable when the repository root switches
branches. `AGENTS.md` directs future Codex sessions to the installed launcher for all
ERP, storefront, backend, and database lifecycle requests.

The runtime state is stored outside worktrees:

```text
D:\code\.runtime\state\runtime.json
D:\code\.runtime\logs\<runtime-id>\
D:\code\.runtime\backups\<database>\
D:\code\.runtime\locks\
```

No runtime logs, PIDs, backups, or generated state are committed to Git.

## Worktree Resolution and Provenance

Before any mutation, `start` records:

- requested branch and resolved worktree;
- full commit SHA;
- whether tracked or untracked files make the worktree dirty;
- the furniture, admin UI, backend, and migration directories resolved beneath that
  same worktree;
- dependency lockfile hashes;
- highest numbered migration and the normalized migration catalog hash.

`main` must be clean. Feature branches may be dirty so uncommitted changes can be
previewed, but the manifest and console output display `dirty=true` prominently.

Every child process is started with an explicit working directory beneath the resolved
worktree. No child command contains a hard-coded `D:\code\...` source directory.

## Branch-Scoped Database Strategy

Each branch owns a database inside the existing MySQL 8 container. The database name
uses a readable sanitized branch prefix plus a deterministic hash to prevent collisions:

```text
main                  -> oakved_main_<hash>
codex/agent-rag       -> oakved_codex_agent_rag_<hash>
codex/seo-foundation  -> oakved_codex_seo_foundation_<hash>
```

The mapping is stable across commits on the same branch. Switching to an older or
divergent branch therefore never downgrades or reinterprets another branch's schema.
Redis keys use the same runtime identifier as a namespace.

An empty branch database is initialized from the selected worktree's generated
`oakved-baseline.sql`. A non-empty database is advanced only by the selected worktree's
pending numbered migrations.

## Migration Gate

Database validation and migration complete before backend or frontend startup.

The gate performs these steps in order:

1. Discover and sort `sql/mysql/migrations/V*.sql` from the selected worktree.
2. Validate one file per version, a contiguous catalog, and canonical filenames.
3. Normalize line endings and calculate SHA-256 checksums.
4. Connect to the branch-scoped database and ensure `schema_migrations` exists.
5. Acquire a MySQL advisory lock scoped to the database.
6. Compare every applied ledger row to the selected catalog.
7. Stop if an applied version is missing locally, renamed, or has a checksum mismatch.
8. Stop if the database is ahead of the selected branch.
9. When pending migrations exist, create and validate a `mysqldump` backup before
   executing SQL.
10. Execute pending migrations sequentially and record a ledger row only after the
    corresponding script succeeds.
11. Re-read the ledger and require exact version, filename, description, and checksum
    equivalence with the selected catalog.
12. Release the advisory lock in a `finally` path.

Migration failure prevents all application processes from starting. The launcher never
automatically drops a database, deletes a Docker volume, resets data, edits an applied
migration, or attempts a downgrade. MySQL DDL can auto-commit, so a failed migration is
reported as requiring operator inspection; the launcher does not claim transactional
rollback.

## Build and Dependency Gate

The launcher maintains a build fingerprint from commit SHA, dirty-file metadata, Maven
POM hashes, and frontend lockfile hashes.

- The backend is rebuilt from the selected worktree when its fingerprint differs from
  the last successful build.
- Each frontend uses dependencies installed for that worktree. Missing dependencies or
  a changed lockfile trigger the appropriate frozen dependency install.
- Failed build or dependency installation prevents startup.
- The ERP and storefront Vite servers use fixed ports with strict-port behavior.
- Vite cache directories are worktree-scoped so optimized modules cannot leak between
  branches.

## Startup and Shutdown Flow

`start` runs the following state machine:

```text
resolve target
  -> validate worktree and paths
  -> acquire launcher lock
  -> inspect fixed ports and existing manifest
  -> stop the previously managed runtime
  -> ensure Docker infrastructure is healthy
  -> validate/initialize/migrate the branch database
  -> validate/install dependencies
  -> build backend if required
  -> start backend with branch database and Redis namespace
  -> start ERP admin UI from the same worktree
  -> start furniture storefront from the same worktree
  -> verify health, ports, process identity, database ledger, and HTTP provenance
  -> atomically write the runtime manifest
```

If startup fails after processes were created, only those newly created managed
processes are stopped. The database and its backups remain intact.

`stop` reads the manifest, verifies process identity, stops only matching managed
processes, and marks the runtime stopped. It never kills an arbitrary process solely
because it owns a known port.

`status` reports the selected branch, commit, dirty flag, worktree, process IDs, ports,
database name, database version, migration catalog version, log paths, and health of all
three applications. Stale manifests and PID reuse are reported rather than treated as
healthy.

## Runtime Provenance

The launcher writes an atomic manifest similar to:

```json
{
  "branch": "main",
  "commit": "87c6c7ff9ed7d734a2cf97c021122645d6af016c",
  "dirty": false,
  "worktree": "D:\\code\\.worktrees\\main-runtime",
  "runtimeId": "main_a1b2c3d4",
  "database": "oakved_main_a1b2c3d4",
  "databaseVersion": "023",
  "catalogVersion": "023",
  "backendPort": 48080,
  "adminPort": 80,
  "storefrontPort": 5173
}
```

Console output prints the same provenance before and after startup. The frontend dev
servers expose generated runtime metadata, and the final gate verifies it matches the
backend and manifest. This prevents a healthy but stale browser target from being
mistaken for the requested branch.

## Safety and Credentials

Local database defaults are read from the existing Docker configuration, but secrets
are never written to the runtime manifest or console output. Command invocations avoid
placing passwords in process arguments when a safer defaults file or environment file
is available. Backups are stored locally with restricted access and a bounded retention
policy.

The launcher performs no `git pull`, merge, checkout, reset, commit, or push. It only
reads Git state and starts the exact existing worktree selected by the user.

## Tests and Acceptance Criteria

Automated tests cover:

- exact branch-to-worktree resolution and rejection of missing/ambiguous branches;
- rejection of detached worktrees and dirty `main`;
- feature-branch dirty-state reporting;
- deterministic collision-resistant database/runtime identifiers;
- migration ordering, gaps, duplicates, checksum mismatch, database-ahead state, and
  failed backup/migration behavior;
- empty database initialization and incremental pending migration application;
- advisory-lock exclusion;
- refusal to reuse unknown port owners;
- manifest atomicity, stale PID detection, and safe stop behavior;
- fixed strict ports and worktree-scoped frontend cache/dependency paths;
- backend rebuild fingerprint behavior;
- end-to-end startup from at least `main` and one divergent feature worktree;
- proof that ERP, storefront, backend, and database all report the same runtime ID and
  selected commit.

Acceptance requires a single command to start either tested branch correctly, no source
process under another worktree, an exact migration ledger for that branch, successful
health checks for all applications, and a clean stop that leaves databases and unrelated
processes untouched.
