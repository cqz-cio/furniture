# Repository Git Workflow

These rules apply to every task and conversation in this repository:

- Treat `main` as the stable integration branch. Do not use `codex/agent-rag` or any other long-lived branch as the default working branch.
- For each independent change, start from the current local `main` and create a fresh, task-specific branch named `codex/<short-task-name>`.
- Keep one coherent change set per task branch. Do not reuse an old task branch for unrelated work.
- Run the relevant tests, builds, and runtime checks on the task branch before integration.
- After the task branch is verified, merge it into the local `main` as the normal completion step. Check both worktrees first and stop if unrelated changes or merge conflicts would be overwritten.
- Direct work on `main` is allowed when the user explicitly asks to work directly on `main` or explicitly chooses the quicker direct-edit workflow for that task.
- Do not push any branch or `main` unless the user asks, or pushing is already an explicitly established final step for the task.
- Do not create a pull request unless the user explicitly requests one.
- Preserve unrelated user changes and untracked files.

# Branch-Aware Local Runtime

These rules apply regardless of the current terminal directory or checked-out branch:

- Use `D:\code\.runtime\bin\oakved.ps1` as the normal full-stack lifecycle entry point. It is installed from the tracked source in `scripts/runtime`.
- For stable branch-based operation, use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch <branch>`. Branch mode runs the branch's committed HEAD in a managed detached snapshot under `D:\code\.runtime\worktrees`; it does not require that branch to be checked out and does not occupy the branch.
- Use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" status` and `... stop` for status and shutdown. These actions resolve the active runtime from `D:\code\.runtime\runtime.json`, not from the branch currently checked out in `D:\code`.
- The `D:\code` worktree may switch branches while a branch snapshot is running. A later branch commit is reported as `UpdateAvailable`; it does not make the pinned running snapshot unhealthy.
- Use `start -Worktree <absolute-path>` only for intentional live-development mode. In that mode, edits or branch changes in the selected worktree can affect the running services.
- The repository-root `start-yudao-all-backend.ps1` and `stop-yudao-all-backend.ps1` remain the one-command backend-only entry points for a deliberately selected live worktree. Prefer them over separate daily commands for `yudao-server` and `ai-server` when backend-only operation is requested.
- If the installed launcher is missing, install it from the selected verified source worktree with `scripts\runtime\install-oakved-runtime.ps1`; do not recreate a permanent `main-runtime` worktree.

# Spreadsheet Presentation Defaults

These rules apply to every spreadsheet created or modified in this workspace unless the user explicitly requests a different visual style:

- Use black text for all spreadsheet content.
- Use a solid white background for every worksheet and table cell, including titles, section headers, table headers, data cells, status cells, priority cells, and conditional-formatting results.
- Do not use black, gray, or colored cell fills. Keep every cell background white.
- Create hierarchy with bold text, font size, spacing, and black or light-gray borders instead of filled backgrounds.
- Do not use colored accents such as green, red, amber, or blue by default.
- When the intended audience is non-technical, use plain business-language column names and explanations; place technical fields and source locations in supporting columns or appendix sheets.
