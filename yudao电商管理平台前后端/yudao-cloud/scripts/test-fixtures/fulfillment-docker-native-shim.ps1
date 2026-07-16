param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$DockerArguments
)

Write-Output "shim-stdout:$($DockerArguments -join '|')"
[Console]::Error.WriteLine("shim-stderr:$($DockerArguments -join '|')")
exit [int]$env:FULFILLMENT_DOCKER_SHIM_EXIT
