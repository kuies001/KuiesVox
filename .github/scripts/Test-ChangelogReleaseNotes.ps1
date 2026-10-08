[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$extractorPath = Join-Path $PSScriptRoot "Extract-ChangelogReleaseNotes.ps1"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("kuiesvox-changelog-tests-" + [guid]::NewGuid().ToString("N"))
[System.IO.Directory]::CreateDirectory($testRoot) | Out-Null

function Write-TestChangelog {
    param([string]$Text)
    $path = Join-Path $testRoot "CHANGELOG.md"
    [System.IO.File]::WriteAllText($path, $Text, [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Assert-ExtractionRejected {
    param(
        [string]$Version,
        [string]$ChangelogPath,
        [string]$CaseName
    )

    $outputPath = Join-Path $testRoot ("rejected-" + [guid]::NewGuid().ToString("N") + ".md")
    $rejected = $false
    try {
        & $extractorPath -Version $Version -ChangelogPath $ChangelogPath -OutputPath $outputPath
    } catch {
        $rejected = $true
    }
    if (-not $rejected) {
        throw "Expected changelog extraction to reject $CaseName."
    }
    if ([System.IO.File]::Exists($outputPath)) {
        throw "Rejected extraction for $CaseName unexpectedly created an output file."
    }
}

try {
    $sample = @(
        "# Changelog",
        "",
        "## [Unreleased]",
        "",
        "<a id=`"v1-2-3`"></a>",
        "## [1.2.3] - 2026-10-08",
        "",
        "### Added",
        "- 新版本內容。",
        "",
        "## [1.2.2] - 2026-10-01",
        "",
        "### Fixed",
        "- 舊版本內容不得進入新版本說明。"
    ) -join "`r`n"
    $validPath = Write-TestChangelog -Text $sample
    $outputPath = Join-Path $testRoot "valid-release-notes.md"
    & $extractorPath -Version "1.2.3" -ChangelogPath $validPath -OutputPath $outputPath
    $actual = [System.IO.File]::ReadAllText($outputPath, [System.Text.Encoding]::UTF8)
    if ($actual -notmatch '新版本內容' -or $actual -match '舊版本內容') {
        throw "Extraction did not isolate the requested version section."
    }
    $outputBytes = [System.IO.File]::ReadAllBytes($outputPath)
    if ($outputBytes.Length -ge 3 -and $outputBytes[0] -eq 0xEF -and $outputBytes[1] -eq 0xBB -and $outputBytes[2] -eq 0xBF) {
        throw "Extracted release notes unexpectedly contain a UTF-8 BOM."
    }
    if ($actual.Contains("`r")) {
        throw "Extracted release notes did not normalize line endings to LF."
    }

    $repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $repositoryChangelog = Join-Path $repositoryRoot "CHANGELOG.md"
    foreach ($historicalVersion in @("0.12.0", "0.11.0", "0.10.0", "0.9.0", "0.8.0", "0.7.0")) {
        $historicalOutputPath = Join-Path $testRoot ("history-" + $historicalVersion + ".md")
        & $extractorPath -Version $historicalVersion -ChangelogPath $repositoryChangelog -OutputPath $historicalOutputPath
        $historicalNotes = [System.IO.File]::ReadAllText($historicalOutputPath, [System.Text.Encoding]::UTF8)
        if ([string]::IsNullOrWhiteSpace($historicalNotes)) {
            throw "Historical changelog section $historicalVersion unexpectedly extracted no content."
        }
        if ($historicalNotes -match '(?m)^## \[' -or $historicalNotes -match '(?m)^<a id="v\d+-\d+-\d+"></a>$' -or $historicalNotes -match '(?m)^\[[^\]]+\]:') {
            throw "Historical changelog extraction for $historicalVersion included content outside its version section."
        }
    }

    Assert-ExtractionRejected -Version "9.9.9" -ChangelogPath $validPath -CaseName "a missing version"

    $emptyPath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-10-08",
        "## [1.2.2] - 2026-10-01",
        "### Fixed",
        "- Older content."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $emptyPath -CaseName "an empty version section"

    $invalidDatePath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-02-30",
        "### Added",
        "- Invalid release date."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $invalidDatePath -CaseName "an invalid release date"

    $invalidCategoryPath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-10-08",
        "### Random Heading",
        "- Invalid category."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $invalidCategoryPath -CaseName "an invalid section format"

    $placeholderPath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-10-08",
        "### Changed",
        "- TODO: add actual changes."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $placeholderPath -CaseName "a placeholder release entry"

    $duplicatePath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-10-08",
        "### Added",
        "- First entry.",
        "## [1.2.3] - 2026-10-09",
        "### Fixed",
        "- Duplicate entry."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $duplicatePath -CaseName "duplicate version sections"

    $invalidPreamblePath = Write-TestChangelog -Text (@(
        "# Changelog",
        "## [1.2.3] - 2026-10-08",
        "Uncategorized text.",
        "### Changed",
        "- A real change."
    ) -join "`n")
    Assert-ExtractionRejected -Version "1.2.3" -ChangelogPath $invalidPreamblePath -CaseName "text outside a category"

    Write-Output "Changelog extraction tests passed: exact sections, historical versions, missing/duplicate version, empty section, invalid date/category/preamble, placeholders, UTF-8 and line endings."
} finally {
    if ([System.IO.Directory]::Exists($testRoot)) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
