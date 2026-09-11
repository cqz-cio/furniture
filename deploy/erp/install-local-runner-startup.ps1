param([string]$RunnerDirectory = 'D:\furniture web2b\work\erp-actions-runner')
$ErrorActionPreference = 'Stop'
$runnerRoot = [IO.Path]::GetFullPath($RunnerDirectory)
$configuration = Join-Path $runnerRoot '.runner'
if (-not (Test-Path -LiteralPath $configuration)) { throw 'Register the Runner in cqz-cio/furniture before installing startup' }
$runner = Get-Content -LiteralPath $configuration -Raw | ConvertFrom-Json
if ($runner.gitHubUrl.TrimEnd('/') -ne 'https://github.com/cqz-cio/furniture') { throw 'Runner belongs to another repository' }
$launcher = Join-Path $runnerRoot 'start-local-runner.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-local-runner.ps1') -Destination $launcher
$taskName = 'Oakved ERP Local CI Runner'
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { throw 'Startup task already exists; inspect it before changing it' }
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $launcher + '" -RunnerDirectory "' + $runnerRoot + '"')
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 2) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings | Out-Null
& $launcher -RunnerDirectory $runnerRoot
