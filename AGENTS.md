# Repository Git Workflow

These rules apply to every task and conversation in this repository:

- Use `codex/agent-rag` as the default working branch.
- Commit completed, verified changes to `codex/agent-rag`.
- When the user asks to push, or when pushing is the established final step of an explicitly requested implementation, push to `origin/codex/agent-rag`.
- Do not propose, merge into, push to, check out, reset, or otherwise modify `main` unless the user explicitly requests that exact action.
- Do not ask whether work should be pushed to `main` during normal task completion.
- Do not create a pull request unless the user explicitly requests one.
- Preserve unrelated user changes and untracked files.

# Local Backend Lifecycle Commands

These rules apply regardless of the currently checked-out branch:

- When the user asks how to start or stop the Yudao/ERP backend, first check for `start-yudao-all-backend.ps1` and `stop-yudao-all-backend.ps1` at the current workspace root.
- If the scripts exist, prefer the one-command lifecycle scripts over separate commands for `yudao-server` and `ai-server`.
- The preferred start command is `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\start-yudao-all-backend.ps1"`; add `-Build` only when the user needs to rebuild updated backend code.
- The preferred stop command is `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\stop-yudao-all-backend.ps1"`.
- If the current branch does not contain the scripts, say so explicitly and use this lifecycle design as the reference for adding them; do not silently fall back to presenting two separate daily startup commands.
