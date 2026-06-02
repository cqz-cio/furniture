# Phase B Harness

This harness protects the Yudao + Furniture Web Phase B implementation.

Run from the Furniture Web repo root:

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

## What It Checks

- Git changed files stay inside the Phase B allowlist.
- Pre-existing dirty files listed in `baseline-dirty-files.txt` are acknowledged.
- `npm.cmd test` passes.
- Vite can build into a temporary harness directory.

## Common Commands

Boundary only:

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1 -SkipTests -SkipBuild
```

Tests and boundary, no build:

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1 -SkipBuild
```

Full harness:

```powershell
powershell -ExecutionPolicy Bypass -File harness/phase-b/run-harness.ps1
```

## Files

- `boundary-allowlist.txt`: paths Phase B may modify.
- `baseline-dirty-files.txt`: dirty paths that existed before this Phase B planning bundle.
- `scenarios.json`: manual smoke scenarios.
- `run-harness.ps1`: executable verification script.

