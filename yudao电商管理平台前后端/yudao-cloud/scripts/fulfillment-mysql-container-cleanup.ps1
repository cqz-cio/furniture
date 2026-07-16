function ConvertTo-FulfillmentNativeArgument([string]$Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') {
        return $Value
    }
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-FulfillmentCleanupDocker {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("rm", "inspect")]
        [string]$Action,
        [Parameter(Mandatory = $true)]
        [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')]
        [string]$Name,
        [string]$DockerExecutable = "docker",
        [string[]]$DockerPrefixArguments = @()
    )

    $arguments = [System.Collections.Generic.List[string]]::new()
    foreach ($prefixArgument in $DockerPrefixArguments) {
        $arguments.Add($prefixArgument)
    }
    $arguments.Add($Action)
    if ($Action -eq "rm") {
        $arguments.Add("-f")
    }
    $arguments.Add($Name)
    $argumentLine = ($arguments | ForEach-Object { ConvertTo-FulfillmentNativeArgument $_ }) -join " "

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $DockerExecutable
    $startInfo.Arguments = $argumentLine
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Unable to start Docker cleanup process: $DockerExecutable"
        }
        $stdoutRead = $process.StandardOutput.ReadToEndAsync()
        $stderrRead = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdoutText = $stdoutRead.GetAwaiter().GetResult()
        $stderrText = $stderrRead.GetAwaiter().GetResult()
        $standardOutput = if ([string]::IsNullOrEmpty($stdoutText)) { @() } else {
            @($stdoutText.TrimEnd("`r", "`n") -split "`r?`n")
        }
        $standardError = if ([string]::IsNullOrEmpty($stderrText)) { @() } else {
            @($stderrText.TrimEnd("`r", "`n") -split "`r?`n")
        }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Output = @($standardOutput) + @($standardError)
            StandardOutput = $standardOutput
            StandardError = $standardError
        }
    } finally {
        $process.Dispose()
    }
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
