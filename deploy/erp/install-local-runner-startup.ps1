param(
    [string]$RunnerDirectory = 'D:\furniture web2b\work\erp-actions-runner',
    [switch]$UpdateExisting,
    [switch]$SkipStart
)
$ErrorActionPreference = 'Stop'
$runnerRoot = [IO.Path]::GetFullPath($RunnerDirectory)
$configuration = Join-Path $runnerRoot '.runner'
if (-not (Test-Path -LiteralPath $configuration)) { throw 'Register the Runner in cqz-cio/furniture before installing startup' }
$runner = Get-Content -LiteralPath $configuration -Raw | ConvertFrom-Json
if ($runner.gitHubUrl.TrimEnd('/') -ne 'https://github.com/cqz-cio/furniture') { throw 'Runner belongs to another repository' }
$launcher = Join-Path $runnerRoot 'start-local-runner.ps1'
$taskName = 'Oakved ERP Local CI Runner'
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing -and -not $UpdateExisting) { throw 'Startup task already exists; inspect it, then use -UpdateExisting' }
if ($existing -and ($existing.Actions.Count -ne 1 -or $existing.Actions[0].Arguments -notlike ('*"' + $launcher + '"*'))) {
    throw 'Existing task uses another launcher; refusing to change it'
}
if ($existing) {
    $backup = Join-Path $runnerRoot ('startup-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $backup | Out-Null
    Export-ScheduledTask -TaskName $taskName | Set-Content -LiteralPath (Join-Path $backup 'task.xml')
    foreach ($file in @('start-local-runner.ps1', 'runner_network.py')) {
        $installed = Join-Path $runnerRoot $file
        if (Test-Path -LiteralPath $installed) { Copy-Item -LiteralPath $installed -Destination $backup }
    }
    Write-Output "Startup backup: $backup"
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-local-runner.ps1') -Destination $launcher
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'runner_network.py') -Destination (Join-Path $runnerRoot 'runner_network.py')
if ($existing) {
    # Preserve the existing account, trigger, action and other settings.
    $existing.Settings.ExecutionTimeLimit = 'PT25M'
    Set-ScheduledTask -TaskName $taskName -Settings $existing.Settings | Out-Null
    if (-not $SkipStart) { & $launcher -RunnerDirectory $runnerRoot }
    return
}
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $launcher + '" -RunnerDirectory "' + $runnerRoot + '"')
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 25) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings | Out-Null
if (-not $SkipStart) { & $launcher -RunnerDirectory $runnerRoot }
