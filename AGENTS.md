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

# Local Backend Lifecycle Commands

These rules apply regardless of the currently checked-out branch:

- When the user asks how to start or stop the Yudao/ERP backend, first check for `start-yudao-all-backend.ps1` and `stop-yudao-all-backend.ps1` at the current workspace root.
- If the scripts exist, prefer the one-command lifecycle scripts over separate commands for `yudao-server` and `ai-server`.
- The preferred start command is `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\start-yudao-all-backend.ps1"`; add `-Build` only when the user needs to rebuild updated backend code.
- The preferred stop command is `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\stop-yudao-all-backend.ps1"`.
- If the current branch does not contain the scripts, say so explicitly and use this lifecycle design as the reference for adding them; do not silently fall back to presenting two separate daily startup commands.

# Spreadsheet Presentation Defaults

These rules apply to every spreadsheet created or modified in this workspace unless the user explicitly requests a different visual style:

- Use black text for all spreadsheet content.
- Use a solid white background for every worksheet and table cell, including titles, section headers, table headers, data cells, status cells, priority cells, and conditional-formatting results.
- Do not use black, gray, or colored cell fills. Keep every cell background white.
- Create hierarchy with bold text, font size, spacing, and black or light-gray borders instead of filled backgrounds.
- Do not use colored accents such as green, red, amber, or blue by default.
- When the intended audience is non-technical, use plain business-language column names and explanations; place technical fields and source locations in supporting columns or appendix sheets.
