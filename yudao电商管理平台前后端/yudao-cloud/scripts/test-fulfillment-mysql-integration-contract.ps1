param(
    [string]$Helper = (Join-Path $PSScriptRoot "fulfillment-mysql-container-cleanup.ps1"),
    [string]$IntegrationScript = (Join-Path $PSScriptRoot "test-fulfillment-mysql-integration.ps1")
)

$ErrorActionPreference = "Stop"
. $Helper

function Assert-Equal([object]$Expected, [object]$Actual, [string]$Message) {
    if ($Expected -ne $Actual) {
        throw "$Message. Expected '$Expected', got '$Actual'"
    }
}

function Assert-Throws([scriptblock]$Action, [string]$Pattern) {
    $caught = $null
    try {
        & $Action
    } catch {
        $caught = $_
    }
    if ($null -eq $caught) {
        throw "Expected action to throw matching '$Pattern'"
    }
    if ($caught.Exception.Message -notmatch $Pattern) {
        throw "Expected error matching '$Pattern', got '$($caught.Exception.Message)'"
    }
}

function New-DockerResult([int]$ExitCode, [string[]]$Output) {
    return [pscustomobject]@{ ExitCode = $ExitCode; Output = $Output }
}

$calls = [System.Collections.Generic.List[string]]::new()
$rmSuccess = {
    param($Action, $Name)
    $calls.Add("$Action|$Name")
    if ($Action -eq "rm") { return New-DockerResult 0 @($Name) }
    return New-DockerResult 1 @("Error: No such object: $Name")
}.GetNewClosure()
Remove-FulfillmentMySqlIntegrationContainer -Name "case-rm-success" -DockerInvoker $rmSuccess
Assert-Equal "rm|case-rm-success,inspect|case-rm-success" ($calls -join ",") "rm success must be verified absent"

$calls.Clear()
$alreadyAbsent = {
    param($Action, $Name)
    $calls.Add("$Action|$Name")
    return New-DockerResult 1 @("Error response from daemon: No such container: $Name")
}.GetNewClosure()
Remove-FulfillmentMySqlIntegrationContainer -Name "case-already-absent" -DockerInvoker $alreadyAbsent
Assert-Equal "rm|case-already-absent,inspect|case-already-absent" ($calls -join ",") "already absent must still be inspected"

$stillExists = {
    param($Action, $Name)
    if ($Action -eq "rm") { return New-DockerResult 1 @("daemon refused removal") }
    return New-DockerResult 0 @("[{ name: '$Name' }]")
}
Assert-Throws {
    Remove-FulfillmentMySqlIntegrationContainer -Name "case-still-exists" -DockerInvoker $stillExists
} "still exists after docker rm -f"

$rmThrows = { param($Action, $Name); throw "docker transport unavailable for $Action $Name" }
Assert-Throws {
    Remove-FulfillmentMySqlIntegrationContainer -Name "case-rm-throws" -DockerInvoker $rmThrows
} "docker rm -f failed.*transport unavailable"

$unknownInspection = {
    param($Action, $Name)
    if ($Action -eq "rm") { return New-DockerResult 1 @("daemon refused removal") }
    return New-DockerResult 1 @("permission denied")
}
Assert-Throws {
    Remove-FulfillmentMySqlIntegrationContainer -Name "case-inspect-unknown" -DockerInvoker $unknownInspection
} "Unable to verify cleanup.*permission denied"

$integrationSource = Get-Content -Raw -LiteralPath $IntegrationScript
if ($integrationSource -notmatch 'fulfillment-mysql-container-cleanup\.ps1' -or
        $integrationSource -notmatch 'Remove-FulfillmentMySqlIntegrationContainer\s+-Name\s+\$container') {
    throw "The real MySQL integration script must dot-source and call the tested cleanup helper"
}

Write-Host "Fulfillment MySQL cleanup behavior passed all fail-closed branches"
