# Yudao AI JDK 17 Enablement Design

## Goal

Enable the complete Yudao `yudao-module-ai` capability on the existing commerce platform while preserving the furniture storefront, shopping assistant, ERP integration, membership, checkout, and commerce dashboard work already merged into the repository.

The finished local environment must expose the AI administration menu and support API key management, model configuration, chat, knowledge-base management, and workflow administration. The system must also start safely when no model API key or external network is available.

## Current State

- The backend baseline is JDK 8 with Spring Boot 2.7.
- The root Maven reactor comments out `yudao-module-ai` because Spring AI 1.1.x requires Java 17.
- Only JDK 8 is currently installed for command-line builds.
- MySQL, Redis, and Nacos already run locally in Docker.
- Gateway routes for `ai-server` already exist, and `ai-server` is designed to listen on port `48090`.
- The database contains the AI menu tree and only three `ai_*` tables, so the schema is incomplete.
- The previous `codex/agent-rag` work implemented a JDK-8-compatible furniture assistant in `product-server`; it did not enable the full AI module.
- The AI module's checked-in YAML contains demonstration provider values. They must not be treated as production credentials or used implicitly.

## Architecture

### Runtime baseline

Install JDK 17 alongside JDK 8. Do not remove JDK 8 and do not replace the machine-wide `JAVA_HOME` or `PATH`. All JDK 17 Maven builds and service launches will use repository scripts that set `JAVA_HOME` only for the child process.

Align the Yudao backend foundation with the official `master-jdk17` architecture (JDK 17 and Spring Boot 3.x) while replaying and retaining all repository-specific commerce changes. The migration will be performed on `codex/agent-rag`; `main` remains untouched until the user explicitly chooses to merge a verified result.

### Services

The migrated local backend consists of the existing Gateway and business services plus `ai-server`:

```text
Admin UI
  -> Gateway
     -> system-server (login, menus, permissions)
     -> product/member/trade/statistics services
     -> ai-server (model, chat, knowledge, workflow)

ai-server
  -> MySQL (AI configuration and business records)
  -> Redis (cache and optional vector data)
  -> Nacos (discovery and configuration)
  -> external model provider only when a configured feature is invoked
```

The repository will provide a one-command JDK 17 backend launcher. It will verify prerequisites, start only the requested services, prevent duplicate ports, and report health endpoints. The frontend and Docker commands remain unchanged.

## Keyless and Offline Behavior

`ai-server` must start without an API key and without access to an external model provider.

- Menu, permission, API-key administration, and model administration endpoints remain available.
- Chat, image, music, embedding, reranking, and other provider-backed calls return a clear configuration or connectivity error for that request only.
- A failed provider request does not terminate `ai-server` and does not affect commerce services.
- Knowledge-base metadata and CRUD remain available. Document vectorization and semantic retrieval remain unavailable until a valid embedding model is configured.
- The furniture shopping assistant keeps its deterministic product and keyword-knowledge fallback when DeepSeek is unavailable.
- No secret is committed to Git, printed in logs, or placed in frontend environment files.

All demonstration provider values in checked-in backend YAML will be removed, disabled, or replaced by empty environment-variable references. Existing database API-key records will be preserved by the backup but disabled for runtime use unless explicitly verified and enabled by the user.

## Database Migration

Before schema changes, create a timestamped logical backup of the local `ruoyi-vue-pro` database under a git-ignored workspace backup directory.

Create a repeatable MySQL migration dedicated to AI enablement. It will:

- create every missing `ai_*` table required by the current AI module;
- preserve the three existing AI tables and their rows;
- upsert required dictionaries and the complete AI menu tree;
- preserve unrelated menus, roles, tenants, and business data;
- grant the AI menu tree to `super_admin` and the currently used administrator account through normal role mappings;
- avoid inserting usable provider secrets or fake enabled models.

The migration must be safe to rerun. Verification will compare the required table set with `information_schema`, validate parent-child menu integrity, and verify role-menu coverage.

## Compatibility Migration

The JDK 17 migration must preserve repository-specific behavior rather than replacing the backend wholesale. Compatibility work is limited to changes required by the official JDK 17 baseline, including:

- Maven parent, dependency-management, compiler, and plugin versions;
- Spring Boot 3 and Jakarta namespace compatibility;
- Spring Security, validation, servlet, MyBatis, Redis, Nacos, and Feign integration changes;
- application configuration properties whose names changed in the JDK 17 baseline;
- tests and fixtures affected by framework migration.

No unrelated UI redesign, business feature, database cleanup, or refactor is in scope.

## Error Handling and Safety

- Detect JDK 8 accidentally used for a JDK 17 build and stop with an actionable message.
- Detect occupied service ports before launch.
- Do not run old and new instances with the same Nacos service name simultaneously.
- Do not mutate or delete Docker volumes during enablement.
- Stop on the first failed schema migration, build, or health check.
- Keep the database backup, original JDK 8 installation, and old startup commands available for rollback.
- Redact keys, passwords, tokens, and provider authorization headers from command output and logs.

## Verification

Verification is staged so framework migration failures are isolated:

1. Confirm repository-scoped Java and Maven commands report JDK 17 while the system default JDK 8 remains unchanged.
2. Build the shared framework and dependency modules.
3. Build and test `system-server`, Gateway, commerce modules, statistics, and `ai-server`.
4. Verify the incremental AI migration and menu/role integrity against local MySQL.
5. Start the required services and verify Nacos registration and health endpoints.
6. Log in through the admin UI and verify the AI menu, API-key page, model page, chat page, knowledge page, and workflow page load through Gateway.
7. Verify keyless chat returns a controlled configuration response without crashing the service.
8. After a user-supplied provider key is entered through the backend administration UI, verify one model chat call and one embedding-backed knowledge operation.
9. Re-run the existing furniture assistant, product, checkout, ERP, and dashboard regression suites.

## Completion Criteria

The enablement is complete when:

- the backend builds and runs with repository-scoped JDK 17;
- the machine-wide JDK 8 installation and default remain available;
- the full required AI schema and menu permissions exist;
- `ai-server` registers and is reachable through Gateway;
- admin AI pages load for the administrator account;
- the platform starts cleanly without external provider credentials;
- missing credentials and offline provider calls fail only at request scope with clear messages;
- a securely configured provider can complete chat and knowledge operations;
- existing commerce regression checks pass;
- a one-command launcher and rollback instructions are documented.

## Rollback

If migration verification fails, stop the JDK 17 services, restore the timestamped MySQL backup if schema changes were applied, and restart the original JDK 8 services with their existing commands. Because JDK 17 is installed side by side and `main` is not modified, rollback does not require uninstalling Java or rewriting Git history.
