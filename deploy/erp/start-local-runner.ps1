param([string]$RunnerDirectory = 'D:\furniture web2b\work\erp-actions-runner')
$ErrorActionPreference = 'Stop'
$runnerRoot = [IO.Path]::GetFullPath($RunnerDirectory)
if (-not (Test-Path -LiteralPath (Join-Path $runnerRoot '.runner'))) { throw 'Configure the repository Runner first' }
if (-not (Test-Path -LiteralPath (Join-Path $runnerRoot 'bin\Runner.Listener.exe'))) { throw 'Runner executable missing' }
$runnerLogs = Join-Path $runnerRoot 'local-logs'
New-Item -ItemType Directory -Path $runnerLogs -Force | Out-Null
$runnerStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runnerExe = Join-Path $runnerRoot 'bin\Runner.Listener.exe'
$existingRunner = Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe'" | Where-Object { $_.ExecutablePath -eq $runnerExe }
if ($existingRunner) { $existingRunner | Select-Object ProcessId; exit 0 }
$env:PATH = 'C:\Python314;C:\Program Files\Docker\Docker\resources\bin;D:\Git\cmd;C:\Windows\System32\OpenSSH;' + $env:PATH
if (-not (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
    Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden
}
# The official launcher restarts the listener after an automatic Runner update.
$runnerCommand = '""' + (Join-Path $runnerRoot 'run.cmd') + '""'
$runnerProcess = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/s','/c',$runnerCommand) -WorkingDirectory $runnerRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runnerLogs "$runnerStamp-stdout.log") -RedirectStandardError (Join-Path $runnerLogs "$runnerStamp-stderr.log")
@{ pid = $runnerProcess.Id; started = (Get-Date).ToString('o'); logDirectory = $runnerLogs } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runnerLogs 'process.json')
Write-Output "Runner PID: $($runnerProcess.Id); logs: $runnerLogs"
