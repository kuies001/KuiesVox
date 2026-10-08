[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$ChangelogPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"

if ($Version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
    throw "Version '$Version' is invalid; expected X.Y.Z."
}

$changelogFullPath = [System.IO.Path]::GetFullPath($ChangelogPath)
if (-not [System.IO.File]::Exists($changelogFullPath)) {
    throw "Changelog file was not found: $ChangelogPath"
}

$utf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$bytes = [System.IO.File]::ReadAllBytes($changelogFullPath)
$offset = if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { 3 } else { 0 }
$changelog = $utf8Strict.GetString($bytes, $offset, $bytes.Length - $offset)
$changelog = $changelog.Replace("`r`n", "`n").Replace("`r", "`n")

$escapedVersion = [regex]::Escape($Version)
$candidatePattern = '(?m)^## \[' + $escapedVersion + '\](?:[ \t].*)?$'
$candidates = [regex]::Matches($changelog, $candidatePattern)
if ($candidates.Count -eq 0) {
    throw "CHANGELOG.md has no section for version $Version. Add its dated version heading and actual changes before releasing."
}
if ($candidates.Count -ne 1) {
    throw "CHANGELOG.md contains $($candidates.Count) sections for version $Version; exactly one is required."
}

$headingPattern = '(?m)^## \[' + $escapedVersion + '\] - (\d{4}-\d{2}-\d{2})[ \t]*$'
$heading = [regex]::Match($candidates[0].Value, $headingPattern)
if (-not $heading.Success) {
    throw "The CHANGELOG.md heading for $Version is malformed; expected '## [$Version] - YYYY-MM-DD'."
}
$releaseDate = [System.DateTime]::MinValue
if (-not [System.DateTime]::TryParseExact(
    $heading.Groups[1].Value,
    'yyyy-MM-dd',
    [System.Globalization.CultureInfo]::InvariantCulture,
    [System.Globalization.DateTimeStyles]::None,
    [ref]$releaseDate
)) {
    throw "The CHANGELOG.md release date for $Version is invalid: $($heading.Groups[1].Value)."
}

$sectionStart = $candidates[0].Index + $candidates[0].Length
$nextSection = [regex]::new('(?m)^## [^\r\n]*$').Match($changelog, $sectionStart)
$sectionEnd = if ($nextSection.Success) { $nextSection.Index } else { $changelog.Length }
$referenceDefinition = [regex]::new('(?m)^\[[^\]\r\n]+\]:[ \t]*\S.*$').Match($changelog, $sectionStart)
if ($referenceDefinition.Success -and $referenceDefinition.Index -lt $sectionEnd) {
    $sectionEnd = $referenceDefinition.Index
}
$sectionBody = $changelog.Substring($sectionStart, $sectionEnd - $sectionStart).Trim()
$sectionBody = [regex]::Replace($sectionBody, '(?:\n)*<a id="v\d+-\d+-\d+"></a>[ \t]*\z', '').Trim()
if ([string]::IsNullOrWhiteSpace($sectionBody)) {
    throw "The CHANGELOG.md section for $Version is empty. Add actual changes before releasing."
}

$categories = [regex]::Matches($sectionBody, '(?m)^### ([^\r\n]+)$')
if ($categories.Count -eq 0) {
    throw "The CHANGELOG.md section for $Version has no Keep a Changelog category headings."
}
$preamble = $sectionBody.Substring(0, $categories[0].Index)
foreach ($line in ($preamble -split "`n")) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line -match '^\s*>') { continue }
    throw "The CHANGELOG.md section for $Version contains text outside a category: '$line'."
}
$allowedCategories = @("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security", "Known Limitations", "Links")
for ($categoryIndex = 0; $categoryIndex -lt $categories.Count; $categoryIndex++) {
    $category = $categories[$categoryIndex]
    if ($allowedCategories -notcontains $category.Groups[1].Value) {
        throw "Unsupported CHANGELOG.md category '$($category.Groups[1].Value)' in version $Version."
    }
    $contentStart = $category.Index + $category.Length
    $contentEnd = if ($categoryIndex + 1 -lt $categories.Count) { $categories[$categoryIndex + 1].Index } else { $sectionBody.Length }
    $categoryBody = $sectionBody.Substring($contentStart, $contentEnd - $contentStart)
    $hasEntry = $false
    foreach ($line in ($categoryBody -split "`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -match '^\s*-\s+\S') {
            if ($line -match '(?i)^\s*-\s*(?:TODO|TBD|FIXME)(?:\b|[:：])|^\s*-\s*(?:待填寫|待補充|尚待整理|請填寫)') {
                throw "The CHANGELOG.md '$($category.Groups[1].Value)' category for $Version contains a placeholder entry. Replace it with actual release changes."
            }
            $hasEntry = $true
            continue
        }
        if ($hasEntry -and $line -match '^\s+\S') { continue }
        throw "The CHANGELOG.md '$($category.Groups[1].Value)' category for $Version must contain bullet entries only; invalid line: '$line'."
    }
    if (-not $hasEntry) {
        throw "The CHANGELOG.md '$($category.Groups[1].Value)' category for $Version is empty."
    }
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
if ([string]::Equals($outputFullPath, $changelogFullPath, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Release notes output must not overwrite CHANGELOG.md."
}
$outputDirectory = [System.IO.Path]::GetDirectoryName($outputFullPath)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
[System.IO.File]::WriteAllText($outputFullPath, $sectionBody + "`n", [System.Text.UTF8Encoding]::new($false))
