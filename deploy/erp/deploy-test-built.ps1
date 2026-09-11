param(
    [Parameter(Mandatory=$true)][string]$Release,
    [ValidateSet('deploy','rollback')][string]$Operation = 'deploy',
    [string]$Cache = 'D:\furniture web2b\work\erp-ci-cache',
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
$arguments = @('-u','-B',(Join-Path $PSScriptRoot 'local_test.py'),'--local-built',
               '--release',$Release,'--operation',$Operation,'--cache',$Cache)
if ($CheckOnly) { $arguments += '--check-only' }
& 'C:\Python314\python.exe' @arguments
exit $LASTEXITCODE
