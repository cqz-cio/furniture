param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$html = Get-Content -LiteralPath $InputPath -Raw -Encoding UTF8

function Get-Matches {
    param(
        [string]$Text,
        [string]$Pattern
    )

    return [regex]::Matches($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline)
}

function Decode-Html {
    param([string]$Value)
    if ($null -eq $Value) { return $null }
    return [System.Net.WebUtility]::HtmlDecode($Value).Trim()
}

function Get-Attr {
    param(
        [string]$Tag,
        [string]$Name
    )

    $match = [regex]::Match($Tag, "(?i)\s$Name\s*=\s*`"([^`"]*)`"|\s$Name\s*=\s*'([^']*)'")
    if (-not $match.Success) { return $null }
    if ($match.Groups[1].Success) { return (Decode-Html $match.Groups[1].Value) }
    return (Decode-Html $match.Groups[2].Value)
}

$bodyTag = [regex]::Match($html, "(?is)<body\b[^>]*>")
$metadata = [ordered]@{
    sourceFile = $InputPath
    extractedAt = (Get-Date).ToString("s")
    htmlBytes = [System.Text.Encoding]::UTF8.GetByteCount($html)
    bodyId = if ($bodyTag.Success) { Get-Attr $bodyTag.Value "id" } else { $null }
    brand = if ($bodyTag.Success) { Get-Attr $bodyTag.Value "data-brand" } else { $null }
    pagePath = if ($bodyTag.Success) { Get-Attr $bodyTag.Value "data-page-path" } else { $null }
    userType = if ($bodyTag.Success) { Get-Attr $bodyTag.Value "data-user-type" } else { $null }
}

$tagCounts = [ordered]@{}
foreach ($m in Get-Matches $html "<([a-zA-Z][a-zA-Z0-9:-]*)\b") {
    $tag = $m.Groups[1].Value.ToLowerInvariant()
    if (-not $tagCounts.Contains($tag)) { $tagCounts[$tag] = 0 }
    $tagCounts[$tag] += 1
}

$anchors = @()
foreach ($m in Get-Matches $html "<a\b[^>]*>.*?</a>") {
    $tag = $m.Value
    $href = Get-Attr $tag "href"
    $aria = Get-Attr $tag "aria-label"
    $text = Decode-Html (([regex]::Replace($tag, "(?is)<script\b.*?</script>|<style\b.*?</style>|<[^>]+>", " ")) -replace "\s+", " ")
    if ($href -or $text -or $aria) {
        $anchors += [ordered]@{
            text = $text
            href = $href
            ariaLabel = $aria
        }
    }
}

$images = @()
foreach ($m in Get-Matches $html "<img\b[^>]*>") {
    $tag = $m.Value
    $src = Get-Attr $tag "src"
    $alt = Get-Attr $tag "alt"
    $width = Get-Attr $tag "width"
    $height = Get-Attr $tag "height"
    $style = Get-Attr $tag "style"
    $images += [ordered]@{
        type = "img"
        src = $src
        alt = $alt
        widthAttr = $width
        heightAttr = $height
        inlineStyle = $style
    }
}

$backgroundUrls = @()
foreach ($m in Get-Matches $html "url\((['`"]?)(.*?)\1\)") {
    $url = Decode-Html $m.Groups[2].Value
    if ($url -and $url -notmatch "^data:") {
        $backgroundUrls += $url
    }
}
$backgroundUrls = $backgroundUrls | Select-Object -Unique

$modules = @()
foreach ($m in Get-Matches $html "<[^>]+authoringname\s*=\s*(['`"])(.*?)\1[^>]*>") {
    $tag = $m.Value
    $modules += [ordered]@{
        authoringName = Decode-Html $m.Groups[2].Value
        id = Get-Attr $tag "id"
        class = Get-Attr $tag "class"
        style = Get-Attr $tag "style"
    }
}

$styles = @()
$styleIndex = 0
foreach ($m in Get-Matches $html "<style\b[^>]*>(.*?)</style>") {
    $content = $m.Groups[1].Value.Trim()
    $styleIndex += 1
    $styles += [ordered]@{
        index = $styleIndex
        length = $content.Length
        preview = if ($content.Length -gt 320) { $content.Substring(0, 320) } else { $content }
    }
}

$landmarks = @()
foreach ($m in Get-Matches $html "<(main|header|nav|footer|section|article|aside)\b[^>]*>") {
    $tag = $m.Value
    $landmarks += [ordered]@{
        tag = $m.Groups[1].Value.ToLowerInvariant()
        id = Get-Attr $tag "id"
        class = Get-Attr $tag "class"
        role = Get-Attr $tag "role"
        ariaLabel = Get-Attr $tag "aria-label"
        style = Get-Attr $tag "style"
    }
}

$result = [ordered]@{
    metadata = $metadata
    summary = [ordered]@{
        totalTags = ($tagCounts.Values | Measure-Object -Sum).Sum
        uniqueTags = $tagCounts.Count
        anchors = $anchors.Count
        images = $images.Count
        backgroundImages = $backgroundUrls.Count
        authoringModules = $modules.Count
        styleBlocks = $styles.Count
        landmarks = $landmarks.Count
    }
    tagCounts = $tagCounts
    anchors = $anchors
    images = $images
    backgroundImages = $backgroundUrls
    authoringModules = $modules
    styleBlocks = $styles
    landmarks = $landmarks
}

$jsonPath = Join-Path $OutputDir "outerhtml-extraction.json"
$result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$navCandidates = $anchors |
    Where-Object {
        $_.href -and (
            $_.href -match "^/us/en|^/sale|^https://rh|^https://rhbabyandchild|^https://rhteen" -or
            $_.text -match "Living|Dining|Bed|Bath|Outdoor|Lighting|Textiles|Rugs|Decor|Décor|Sale|Interior|Baby|Teen"
        )
    } |
    Select-Object -First 120

$imageSpecs = @()
foreach ($url in $backgroundUrls) {
    $imageSpecs += [ordered]@{
        source = "background-image"
        url = $url
        extractedSize = $null
        recommended1x = "Use rendered container size after browser measurement"
        recommended2x = "2x rendered container size"
        recommendedFormat = "WebP for photos, JPG fallback"
        recommendedMaxSize = "Hero 250-600KB; cards 80-180KB"
        objectFit = "cover unless source layout proves contain"
    }
}
foreach ($img in $images) {
    $imageSpecs += [ordered]@{
        source = "img"
        url = $img.src
        alt = $img.alt
        attrSize = if ($img.widthAttr -or $img.heightAttr) { "$($img.widthAttr)x$($img.heightAttr)" } else { $null }
        recommended1x = "Use rendered container size after browser measurement"
        recommended2x = "2x rendered container size"
        recommendedFormat = "WebP for photos, PNG/WebP alpha for transparent assets"
        recommendedMaxSize = "Hero 250-600KB; cards 80-180KB"
        objectFit = "cover unless source layout proves contain"
    }
}

$navCandidates | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDir "navigation-candidates.json") -Encoding UTF8
$imageSpecs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDir "image-spec-candidates.json") -Encoding UTF8

Write-Output "Wrote $jsonPath"
Write-Output "Anchors: $($anchors.Count)"
Write-Output "Images: $($images.Count)"
Write-Output "Background images: $($backgroundUrls.Count)"
Write-Output "Authoring modules: $($modules.Count)"
