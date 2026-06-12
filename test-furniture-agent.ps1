param(
    [string] $BaseUrl = "http://127.0.0.1:48080",
    [string] $TenantId = "121",
    [switch] $RequireModel
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function From-Base64Utf8 {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )
    return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

$cases = @(
    [pscustomobject]@{
        Name = "Chinese cream fabric sofa under budget"
        Message = From-Base64Utf8 "ODAwMOS7peWGheeahOexs+eZveiJsuW4g+iJuuaymeWPkQ=="
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese leading budget phrase"
        Message = From-Base64Utf8 "5LiN6LaF6L+HODAwMOWFg+eahOW4g+iJuuaymeWPkQ=="
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese membership coupon policy"
        Message = From-Base64Utf8 "5Lya5ZGY5Lu36IO95ZKM5LyY5oOg5Yi45Y+g5Yqg5ZCX77yf"
        MinProducts = 0
        RequiredSources = @("knowledge")
        AnswerContains = @(From-Base64Utf8 "5LyY5oOg5Yi4")
    },
    [pscustomobject]@{
        Name = "English sofa product intent"
        Message = "cream fabric sofa under 8000"
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese black dining table product intent"
        Message = From-Base64Utf8 "5om+5LiA5byg6buR6Imy5bKp5p2/6aSQ5qGM"
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese rug product intent"
        Message = From-Base64Utf8 "5om+5LiA5byg57Gz6Imy5Zyw5q+v"
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese nightstand product intent"
        Message = From-Base64Utf8 "5om+5LiA5Liq5bqK5aS05p+c"
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    },
    [pscustomobject]@{
        Name = "Chinese table lamp product intent"
        Message = From-Base64Utf8 "5om+5LiA55uP5Y+w54Gv"
        MinProducts = 1
        RequiredSources = @("product-api")
        AnswerContains = @()
    }
)

function Invoke-FurnitureAssistantCase {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject] $Case
    )

    $uri = "$BaseUrl/app-api/ai/furniture-assistant/chat"
    $json = @{ message = $Case.Message } | ConvertTo-Json -Compress
    $body = [System.Text.Encoding]::UTF8.GetBytes($json)
    $response = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ "tenant-id" = $TenantId } `
        -ContentType "application/json; charset=utf-8" -Body $body -TimeoutSec 30

    if ($response.code -ne 0) {
        throw "[$($Case.Name)] API returned code=$($response.code), msg=$($response.msg)"
    }

    $data = $response.data
    if ([string]::IsNullOrWhiteSpace($data.answer)) {
        throw "[$($Case.Name)] answer is empty"
    }

    $productCount = @($data.products).Count
    if ($productCount -lt $Case.MinProducts) {
        throw "[$($Case.Name)] expected at least $($Case.MinProducts) products, got $productCount"
    }

    $sourceTypes = @($data.sources | ForEach-Object { $_.type })
    foreach ($requiredSource in $Case.RequiredSources) {
        if ($sourceTypes -notcontains $requiredSource) {
            throw "[$($Case.Name)] missing source '$requiredSource'; got: $($sourceTypes -join ', ')"
        }
    }

    if ($RequireModel -and $sourceTypes -notcontains "model") {
        throw "[$($Case.Name)] missing model source; got: $($sourceTypes -join ', ')"
    }

    foreach ($text in $Case.AnswerContains) {
        if ($data.answer -notlike "*$text*") {
            throw "[$($Case.Name)] answer does not contain '$text': $($data.answer)"
        }
    }

    if ($data.answer.Contains("**") -or $data.answer.Contains('`')) {
        throw "[$($Case.Name)] answer contains markdown markers: $($data.answer)"
    }

    if ($data.answer.Length -gt 260) {
        throw "[$($Case.Name)] answer is too long for the chat bubble: $($data.answer.Length) chars"
    }

    [pscustomobject]@{
        Case = $Case.Name
        Products = $productCount
        Sources = $sourceTypes -join ", "
        Answer = $data.answer
    }
}

Write-Host "Furniture agent regression tests"
Write-Host "BaseUrl: $BaseUrl"
Write-Host "TenantId: $TenantId"
if ($RequireModel) {
    Write-Host "RequireModel: enabled"
}
Write-Host ""

$results = @()
foreach ($testCase in $cases) {
    Write-Host "Running: $($testCase.Name)"
    $results += Invoke-FurnitureAssistantCase -Case $testCase
}

Write-Host ""
Write-Host "PASS: $($results.Count) furniture agent cases passed."
$results | Format-Table -AutoSize Case, Products, Sources, Answer
