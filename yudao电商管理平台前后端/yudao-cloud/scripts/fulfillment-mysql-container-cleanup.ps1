function Invoke-FulfillmentCleanupDocker([string]$Action, [string]$Name) {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        if ($Action -eq "rm") {
            $output = @(docker rm -f $Name 2>&1)
        } elseif ($Action -eq "inspect") {
            $output = @(docker inspect $Name 2>&1)
        } else {
            throw "Unsupported Docker cleanup action: $Action"
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
}

function Remove-FulfillmentMySqlIntegrationContainer {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [scriptblock]$DockerInvoker
    )

    if ($null -eq $DockerInvoker) {
        $DockerInvoker = { param($Action, $ContainerName)
            Invoke-FulfillmentCleanupDocker $Action $ContainerName
        }
    }

    try {
        $removeResult = & $DockerInvoker "rm" $Name
    } catch {
        throw "docker rm -f failed for temporary container ${Name}: $($_.Exception.Message)"
    }
    if ($null -eq $removeResult -or $null -eq $removeResult.ExitCode) {
        throw "docker rm -f returned an invalid result for temporary container $Name"
    }

    try {
        $inspectResult = & $DockerInvoker "inspect" $Name
    } catch {
        throw "Unable to verify cleanup for temporary container ${Name}: $($_.Exception.Message)"
    }
    if ($null -eq $inspectResult -or $null -eq $inspectResult.ExitCode) {
        throw "Unable to verify cleanup for temporary container ${Name}: docker inspect returned an invalid result"
    }

    $inspectText = @($inspectResult.Output) -join [Environment]::NewLine
    if ($inspectResult.ExitCode -eq 0) {
        throw "Temporary container $Name still exists after docker rm -f"
    }
    if ($inspectText -notmatch "No such (object|container)") {
        throw "Unable to verify cleanup for temporary container ${Name}: $inspectText"
    }

    if ($removeResult.ExitCode -eq 0) {
        Write-Host "Removed temporary container $Name"
    } else {
        Write-Host "Temporary container $Name was already absent"
    }
}
