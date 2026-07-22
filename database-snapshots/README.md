# Oakved portable development database

`oakved-main-latest.oakveddb` is an encrypted, compressed logical snapshot of the complete launcher-managed `main` MySQL database. It includes schema, table data, routines, triggers, member accounts, mail settings, and mail-template bindings.

The encryption password is intentionally not committed to Git. Keep the password printed by the export command in a separate password manager or in the Codex task that created the snapshot.

## Restore on another Windows development machine

1. Pull `main` and install the Oakved runtime launcher if it is not already installed:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\runtime\install-oakved-runtime.ps1"
   ```

2. Start the current `main` runtime once so the launcher creates and validates its branch database:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\code\.runtime\bin\oakved.ps1" start -Branch main
   ```

3. Restore the snapshot. The script validates the active branch and snapshot, backs up the target database, stops the managed services, replaces only the launcher-managed `main` database, imports the snapshot, and restarts `main`:

   ```powershell
   $env:OAKVED_SNAPSHOT_PASSWORD = '<password stored outside Git>'
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\portable-database\restore-main-database.ps1"
   Remove-Item Env:OAKVED_SNAPSHOT_PASSWORD
   ```

The script requires the exact confirmation text `RESTORE OAKVED MAIN DATABASE`. Use `-Force` only in an unattended, disposable development environment.

## Refresh the snapshot

Start a healthy, current `main` runtime and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\portable-database\export-main-database.ps1" -GeneratePassword
```

The export command prints the new password once. Commit the `.oakveddb` file and its JSON metadata, but never commit the password.

Redis is deliberately not included. It contains disposable sessions, queues, and caches; the launcher recreates it, while all durable member, mail, product, order, and ERP records are restored from MySQL.
