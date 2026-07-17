# Repository Git Workflow

These rules apply to every task and conversation in this repository:

- Use `codex/agent-rag` as the default working branch.
- Commit completed, verified changes to `codex/agent-rag`.
- When the user asks to push, or when pushing is the established final step of an explicitly requested implementation, push to `origin/codex/agent-rag`.
- Do not propose, merge into, push to, check out, reset, or otherwise modify `main` unless the user explicitly requests that exact action.
- Do not ask whether work should be pushed to `main` during normal task completion.
- Do not create a pull request unless the user explicitly requests one.
- Preserve unrelated user changes and untracked files.

# Branch-Aware Local Runtime

These rules apply regardless of the current terminal directory or checked-out branch:

- For ERP admin, furniture storefront, Yudao backend, or local database lifecycle requests, use `D:\code\.runtime\bin\oakved.ps1`.
- Never infer the requested branch from the current directory and never bypass the launcher with a worktree's Vite/backend scripts when the installed launcher exists.
- Starting `main` uses `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch main`.
- Starting another branch must pass its exact name with `-Branch`, or its absolute registered path with `-Worktree`; resolution failure must stop rather than fall back to `D:\code`.
- Use `status` as the source of truth for branch, commit, worktree, database, migration version, ports, and PIDs; use `stop` for managed shutdown.
- The launcher owns database migration validation, backend build, ERP admin startup, and furniture storefront startup. Do not bypass its database gate.

# Spreadsheet Presentation Defaults

These rules apply to every spreadsheet created or modified in this workspace unless the user explicitly requests a different visual style:

- Use black text for all spreadsheet content.
- Use a solid white background for every worksheet and table cell, including titles, section headers, table headers, data cells, status cells, priority cells, and conditional-formatting results.
- Do not use black, gray, or colored cell fills. Keep every cell background white.
- Create hierarchy with bold text, font size, spacing, and black or light-gray borders instead of filled backgrounds.
- Do not use colored accents such as green, red, amber, or blue by default.
- When the intended audience is non-technical, use plain business-language column names and explanations; place technical fields and source locations in supporting columns or appendix sheets.
