$secureKey = Read-Host "Enter DeepSeek API Key" -AsSecureString

if ($secureKey.Length -eq 0) {
    Write-Error "DEEPSEEK_API_KEY was empty. Nothing was changed."
    exit 1
}

$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
    $envFile = Join-Path $workspace ".env"
    $envLines = @()
    if (Test-Path -LiteralPath $envFile) {
        $envLines = Get-Content -LiteralPath $envFile |
            Where-Object { $_ -notmatch '^\s*DEEPSEEK_API_KEY\s*=' }
    }
    $envLines += "DEEPSEEK_API_KEY=$plainKey"
    Set-Content -LiteralPath $envFile -Value $envLines -Encoding UTF8
    $env:DEEPSEEK_API_KEY = $plainKey
    Write-Host "DEEPSEEK_API_KEY saved to .env."
    Write-Host "Restart the backend with .\start-furniture-agent-backend.cmd."
} finally {
    if ($bstr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    if (Get-Variable plainKey -ErrorAction SilentlyContinue) {
        Remove-Variable plainKey -Force
    }
}
