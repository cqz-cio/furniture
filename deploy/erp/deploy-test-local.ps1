param(
    [Parameter(Mandatory=$true)][string]$Release,
    [ValidateSet('prepare','cutover','recover','deploy','rollback')][string]$Operation = 'prepare',
    [switch]$ConfirmCutover,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
$pythonPath = if (Test-Path -LiteralPath 'C:\Python314\python.exe') { 'C:\Python314\python.exe' } elseif ($pythonCommand) { $pythonCommand.Source } else { throw 'Python 3.11+ is required' }
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot')) {
    $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
}
if (-not (Get-Command node -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath 'D:\Program Files\nodejs\node.exe')) {
    $env:PATH = 'D:\Program Files\nodejs;' + $env:PATH
}
$deployArgs = @('-u','-B', (Join-Path $PSScriptRoot 'local_test.py'), '--release', $Release, '--operation', $Operation)
if ($ConfirmCutover) { $deployArgs += @('--confirm-cutover','true') }
if ($CheckOnly) { $deployArgs += '--check-only' }
& $pythonPath @deployArgs
exit $LASTEXITCODE
