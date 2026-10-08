[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repositoryRoot = $PSScriptRoot
$scriptExitCode = 1
$releaseCommitted = $false
$backupReady = $false
$fileBackups = @{}
$releaseNotesTempPath = $null

function Resolve-RepositoryPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $Path))
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$GitArguments,
        [switch]$Capture
    )

    if ($Capture) {
        $output = & git -C $repositoryRoot @GitArguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "git command failed (exit $LASTEXITCODE): git $($GitArguments -join ' ')"
        }
        return ($output -join [Environment]::NewLine).TrimEnd()
    }

    & git -C $repositoryRoot @GitArguments
    if ($LASTEXITCODE -ne 0) {
        throw "git command failed (exit $LASTEXITCODE): git $($GitArguments -join ' ')"
    }
}

function Invoke-Gradle {
    param([Parameter(Mandatory = $true)][string[]]$GradleArguments)

    & (Join-Path $repositoryRoot "gradlew.bat") --project-dir $repositoryRoot @GradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed (exit $LASTEXITCODE): .\gradlew.bat $($GradleArguments -join ' ')"
    }
}

function Read-Utf8Document {
    param([Parameter(Mandatory = $true)][string]$Path)

    $bytes = [System.IO.File]::ReadAllBytes((Resolve-RepositoryPath -Path $Path))
    $hasBom = $bytes.Length -ge 3 -and
        $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasBom) { 3 } else { 0 }
    $text = [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    return [pscustomobject]@{ Text = $text; HasBom = $hasBom; Newline = $newline }
}

function Write-Utf8Document {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][bool]$HasBom
    )

    $encoding = [System.Text.UTF8Encoding]::new($HasBom)
    [System.IO.File]::WriteAllText((Resolve-RepositoryPath -Path $Path), $Text, $encoding)
}

function Update-GradleVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$NewVersion,
        [Parameter(Mandatory = $true)][int]$NewVersionCode
    )

    $document = Read-Utf8Document -Path $Path
    $versionNamePattern = '(?m)^([\t ]*versionName[\t ]*=[\t ]*")[^"\r\n]+("[\t ]*)$'
    $versionCodePattern = '(?m)^([\t ]*versionCode[\t ]*=[\t ]*)\d+([\t ]*)$'
    $versionNameMatches = [regex]::Matches($document.Text, $versionNamePattern)
    $versionCodeMatches = [regex]::Matches($document.Text, $versionCodePattern)
    if ($versionNameMatches.Count -ne 1 -or $versionCodeMatches.Count -ne 1) {
        throw "Expected one versionName and one versionCode in $Path."
    }

    $currentVersion = $versionNameMatches[0].Groups[0].Value -replace '^.*?"([^"]+)".*$', '$1'
    $currentVersionCode = 0
    $versionCodeText = $versionCodeMatches[0].Groups[0].Value -replace '^.*?=\s*(\d+).*$','$1'
    if (-not [int]::TryParse($versionCodeText, [ref]$currentVersionCode)) {
        throw "Could not parse versionCode in $Path."
    }
    if ($currentVersionCode -eq [int]::MaxValue) {
        throw "versionCode cannot be incremented beyond Int32.MaxValue."
    }

    $nameEvaluator = [System.Text.RegularExpressions.MatchEvaluator]{
        param($match)
        return $match.Groups[1].Value + $NewVersion + $match.Groups[2].Value
    }
    $codeEvaluator = [System.Text.RegularExpressions.MatchEvaluator]{
        param($match)
        return $match.Groups[1].Value + $NewVersionCode + $match.Groups[2].Value
    }
    $updated = [regex]::Replace($document.Text, $versionNamePattern, $nameEvaluator, 1)
    $updated = [regex]::Replace($updated, $versionCodePattern, $codeEvaluator, 1)
    Write-Utf8Document -Path $Path -Text $updated -HasBom $document.HasBom

    return [pscustomobject]@{
        CurrentVersion = $currentVersion
        CurrentVersionCode = $currentVersionCode
        NewVersionCode = $NewVersionCode
    }
}

function Update-ReadmeVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$NewVersion
    )

    $document = Read-Utf8Document -Path $Path
    $startMarker = "<!-- KUIESVOX_VERSION_START -->"
    $endMarker = "<!-- KUIESVOX_VERSION_END -->"
    $startCount = ([regex]::Matches($document.Text, [regex]::Escape($startMarker))).Count
    $endCount = ([regex]::Matches($document.Text, [regex]::Escape($endMarker))).Count
    $versionLine = "目前版本：v$NewVersion Beta（測試版）"

    if ($startCount -eq 0 -and $endCount -eq 0) {
        $oldVersionLine = [regex]::Match($document.Text, '(?m)^\*\*目前版本：[^\r\n]*\*\*$')
        if (-not $oldVersionLine.Success) {
            throw "README.md has no version marker or recognizable current-version line."
        }
        $block = $startMarker + $document.Newline + $versionLine + $document.Newline + $endMarker
        $updated = $document.Text.Substring(0, $oldVersionLine.Index) +
            $block + $document.Text.Substring($oldVersionLine.Index + $oldVersionLine.Length)
    } elseif ($startCount -eq 1 -and $endCount -eq 1) {
        $startIndex = $document.Text.IndexOf($startMarker, [System.StringComparison]::Ordinal)
        $endIndex = $document.Text.IndexOf($endMarker, [System.StringComparison]::Ordinal)
        if ($endIndex -le $startIndex) {
            throw "README version markers are out of order."
        }
        $prefix = $document.Text.Substring(0, $startIndex + $startMarker.Length)
        $suffix = $document.Text.Substring($endIndex)
        $updated = $prefix + $document.Newline + $versionLine + $document.Newline + $suffix
    } else {
        throw "README.md must contain either zero or one complete pair of version markers."
    }

    Write-Utf8Document -Path $Path -Text $updated -HasBom $document.HasBom
}

function Invoke-SecurityScan {
    $paths = Invoke-Git -GitArguments @("ls-files", "--cached", "--others", "--exclude-standard") -Capture
    $pathList = @($paths -split "`r?`n" | Where-Object { $_ })
    $forbiddenPathPattern = '(?i)(^|[\\/])(?:local\.properties|\.env(?:\.[^\\/]*)?|[^\\/]+\.(?:jks|keystore|p12|pfx|pem|key|apk|aab))$'
    $forbiddenPaths = @($pathList | Where-Object { $_ -match $forbiddenPathPattern })
    if ($forbiddenPaths.Count -gt 0) {
        throw "Security scan found files that must not enter Git: $($forbiddenPaths -join ', ')"
    }

    $credentialPatterns = @(
        '(?i)\bgsk_[A-Za-z0-9]{20,}\b',
        '(?i)\bAIza[0-9A-Za-z_-]{30,}\b',
        '(?i)\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\b',
        '(?i)\bgh[pousr]_[A-Za-z0-9]{20,}\b',
        '(?i)\bgithub_pat_[A-Za-z0-9_]{20,}\b',
        '(?i)\bBearer\s+[A-Za-z0-9._~+/-]{16,}=*',
        '(?im)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        '(?im)\b(?:storePassword|keyPassword)\s*=\s*["''][^$][^"'']+["'']',
        '(?im)\b(?:groq|gemini|openai)[_-]?api[_-]?key\s*[:=]\s*["''][A-Za-z0-9_-]{16,}["'']'
    )
    $textExtensions = @(".kt", ".kts", ".xml", ".md", ".yml", ".yaml", ".properties", ".ps1", ".gradle", ".json", ".txt", ".sh", ".bat")
    $credentialHits = [System.Collections.Generic.List[string]]::new()
    foreach ($path in $pathList) {
        $fullPath = Resolve-RepositoryPath -Path $path
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
        if ($textExtensions -notcontains [System.IO.Path]::GetExtension($path).ToLowerInvariant()) { continue }
        $text = [System.IO.File]::ReadAllText($fullPath)
        foreach ($pattern in $credentialPatterns) {
            if ($text -match $pattern) {
                $credentialHits.Add($path)
                break
            }
        }
    }
    if ($credentialHits.Count -gt 0) {
        throw "Security scan found possible credentials in: $($credentialHits -join ', ')"
    }
    Write-Host "Security scan passed: no credential patterns or sensitive artifact paths found."
}

function Restore-ReleaseInputs {
    if (-not $backupReady) { return }
    foreach ($path in $fileBackups.Keys) {
        $fullPath = Join-Path $repositoryRoot $path
        $savedBytes = $fileBackups[$path]
        if ($null -eq $savedBytes) {
            if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
                Remove-Item -LiteralPath $fullPath -Force
            }
        } else {
            [System.IO.File]::WriteAllBytes($fullPath, $savedBytes)
        }
    }
    Write-Host "Cleanup restored the original README and Gradle version file."
}

Push-Location $repositoryRoot
try {
    if ($Version -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        throw "Version must use X.Y.Z with numeric SemVer components, for example 0.10.0."
    }
    try {
        $requestedVersion = [System.Version]::Parse($Version)
    } catch {
        throw "Version components are outside the supported numeric range."
    }

    $branch = Invoke-Git -GitArguments @("branch", "--show-current") -Capture
    if ($branch -ne "main") {
        throw "Releases must be prepared on branch main; current branch is '$branch'."
    }
    $worktree = Invoke-Git -GitArguments @("status", "--porcelain=v1", "--untracked-files=all") -Capture
    $releaseChangePaths = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in @($worktree -split '\r?\n' | Where-Object { $_ })) {
        if ($entry.Length -lt 4) {
            throw "Could not safely interpret a Git worktree entry; commit or move unrelated changes before releasing."
        }
        $changePath = $entry.Substring(3)
        if ($changePath -match " -> ") {
            throw "Renamed paths must be committed separately before releasing; existing changes were left untouched."
        }
        $normalizedPath = $changePath.Replace("\", "/")
        $isReleaseChange =
            $normalizedPath -match '(?i)^app/(?!build/)' -or
            $normalizedPath -match '(?i)^(README\.md|CHANGELOG\.md|build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties)$' -or
            $normalizedPath -match '(?i)^gradle/'
        if (-not $isReleaseChange) {
            throw "Unrelated uncommitted change '$changePath' found. Commit or move it before releasing; existing changes were left untouched."
        }
        $releaseChangePaths.Add($changePath)
    }

    Invoke-Git -GitArguments @("fetch", "--prune", "--tags", "origin")
    $remoteMain = Invoke-Git -GitArguments @("rev-parse", "--verify", "refs/remotes/origin/main") -Capture
    $counts = Invoke-Git -GitArguments @("rev-list", "--left-right", "--count", "main...origin/main") -Capture
    $countParts = @($counts -split '\s+' | Where-Object { $_ })
    if ($countParts.Count -ne 2 -or [int]$countParts[1] -gt 0) {
        throw "Local main is behind origin/main. Pull and resolve changes before releasing."
    }

    $tag = "v$Version"
    & git show-ref --verify --quiet "refs/tags/$tag"
    if ($LASTEXITCODE -eq 0) {
        throw "Tag $tag already exists locally; tags are never overwritten."
    } elseif ($LASTEXITCODE -ne 1) {
        throw "Could not check whether local tag $tag exists."
    }
    $remoteTag = Invoke-Git -GitArguments @("ls-remote", "--tags", "origin", "refs/tags/$tag") -Capture
    if (-not [string]::IsNullOrWhiteSpace($remoteTag)) {
        throw "Tag $tag already exists on origin; tags are never overwritten."
    }

    $gradlePath = "app/build.gradle.kts"
    $readmePath = "README.md"
    $changelogPath = "CHANGELOG.md"
    foreach ($path in @($gradlePath, $readmePath)) {
        $fullPath = Join-Path $repositoryRoot $path
        $fileBackups[$path] = if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
            [System.IO.File]::ReadAllBytes($fullPath)
        } else {
            $null
        }
    }
    $backupReady = $true

    $extractorPath = Join-Path $repositoryRoot ".github\scripts\Extract-ChangelogReleaseNotes.ps1"
    $releaseNotesTempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("kuiesvox-release-notes-$([guid]::NewGuid().ToString('N')).md")
    & $extractorPath -Version $Version -ChangelogPath (Join-Path $repositoryRoot $changelogPath) -OutputPath $releaseNotesTempPath
    if (-not (Test-Path -LiteralPath $releaseNotesTempPath -PathType Leaf) -or (Get-Item -LiteralPath $releaseNotesTempPath).Length -eq 0) {
        throw "Could not extract non-empty release notes for $Version from CHANGELOG.md."
    }

    $gradleText = (Read-Utf8Document -Path $gradlePath).Text
    $currentVersionMatch = [regex]::Match($gradleText, '(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$')
    $currentCodeMatch = [regex]::Match($gradleText, '(?m)^\s*versionCode\s*=\s*(\d+)\s*$')
    if (-not $currentVersionMatch.Success -or -not $currentCodeMatch.Success) {
        throw "Could not read versionName/versionCode from app/build.gradle.kts."
    }
    $currentVersion = [System.Version]::Parse($currentVersionMatch.Groups[1].Value)
    if (-not $DryRun -and $requestedVersion -le $currentVersion) {
        throw "Release version $Version must be greater than current version $($currentVersionMatch.Groups[1].Value)."
    }
    $currentVersionCode = [int]::Parse($currentCodeMatch.Groups[1].Value)
    if ($currentVersionCode -eq [int]::MaxValue) {
        throw "versionCode cannot be incremented beyond Int32.MaxValue."
    }
    $newVersionCode = $currentVersionCode + 1

    $versionChange = Update-GradleVersion -Path $gradlePath -NewVersion $Version -NewVersionCode $newVersionCode
    Update-ReadmeVersion -Path $readmePath -NewVersion $Version

    Write-Host "Release tag: $tag"
    Write-Host "versionName: $($versionChange.CurrentVersion) -> $Version"
    Write-Host "versionCode: $($versionChange.CurrentVersionCode) -> $newVersionCode"
    Write-Host "README version block: updated"
    Write-Host "Release notes extracted from CHANGELOG.md section $Version."
    Invoke-Git -GitArguments @("diff", "--check")
    Invoke-SecurityScan

    Write-Host "Compiling release app and JVM test sources..."
    Invoke-Gradle -GradleArguments @(":app:compileReleaseKotlin", ":app:compileDebugUnitTestKotlin", "--no-configuration-cache")
    Write-Host "Running JVM tests..."
    Invoke-Gradle -GradleArguments @("test", "--no-configuration-cache")
    Write-Host "Building Release APK..."
    Invoke-Gradle -GradleArguments @("assembleRelease", "--no-configuration-cache")

    $releaseApkCandidates = @(
        "app/build/outputs/apk/release/app-release.apk",
        "app/build/outputs/apk/release/app-release-unsigned.apk"
    )
    $releaseApkPath = $releaseApkCandidates |
        Where-Object {
            $candidatePath = Resolve-RepositoryPath -Path $_
            (Test-Path -LiteralPath $candidatePath -PathType Leaf) -and (Get-Item -LiteralPath $candidatePath).Length -gt 0
        } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($releaseApkPath)) {
        throw "Release APK was not produced at $releaseApkPath."
    }
    Write-Host "Release APK produced: $releaseApkPath"
    if ($releaseApkPath.EndsWith("-unsigned.apk", [System.StringComparison]::OrdinalIgnoreCase)) {
        Write-Warning "No local release signing variables were supplied; GitHub Actions will sign the published APK from repository secrets."
    }

    Invoke-SecurityScan
    if ($DryRun) {
        Write-Host "DryRun passed all available validation steps. No commit, push, tag or GitHub Release was created."
        Write-Host "Release change paths to include:"
        if ($releaseChangePaths.Count -eq 0) {
            Write-Host "  (no pre-existing feature changes)"
        } else {
            foreach ($path in $releaseChangePaths) { Write-Host "  $path" }
        }
        Invoke-Git -GitArguments @("diff", "--stat")
        Invoke-Git -GitArguments @("diff", "--", $gradlePath, $readmePath)
        Write-Host "Release notes preview extracted from CHANGELOG.md:"
        Get-Content -LiteralPath $releaseNotesTempPath -Raw -Encoding UTF8
        Write-Host "The original files will be restored when DryRun exits."
    } else {
        $stagePaths = @($releaseChangePaths) + @($gradlePath, $readmePath, $changelogPath) | Select-Object -Unique
        $addArguments = @("add", "--") + @($stagePaths)
        Invoke-Git -GitArguments $addArguments
        Invoke-Git -GitArguments @("diff", "--cached", "--check")
        Invoke-SecurityScan
        Invoke-Git -GitArguments @("commit", "-m", "Release $tag")
        $releaseCommitted = $true
        Invoke-Git -GitArguments @("push", "origin", "main")

        & git show-ref --verify --quiet "refs/tags/$tag"
        if ($LASTEXITCODE -eq 0) {
            throw "Tag $tag appeared locally during release; it was not overwritten."
        }
        $remoteTag = Invoke-Git -GitArguments @("ls-remote", "--tags", "origin", "refs/tags/$tag") -Capture
        if (-not [string]::IsNullOrWhiteSpace($remoteTag)) {
            throw "Tag $tag appeared on origin during release; it was not overwritten."
        }
        Invoke-Git -GitArguments @("tag", "-a", $tag, "-m", "KuiesVox $tag")
        Invoke-Git -GitArguments @("push", "origin", $tag)
        Write-Host "Pushed main and $tag. GitHub Actions will build and publish the signed Release."
    }

    $scriptExitCode = 0
} catch {
    [Console]::Error.WriteLine("Release stopped: $($_.Exception.Message)")
} finally {
    if ($DryRun) {
        try {
            Restore-ReleaseInputs
        } catch {
            [Console]::Error.WriteLine("DryRun restore failed: $($_.Exception.Message)")
            $scriptExitCode = 1
        }
    } elseif ($backupReady -and -not $releaseCommitted -and $scriptExitCode -ne 0) {
        try {
            Restore-ReleaseInputs
        } catch {
            [Console]::Error.WriteLine("Release cleanup failed: $($_.Exception.Message)")
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($releaseNotesTempPath) -and (Test-Path -LiteralPath $releaseNotesTempPath -PathType Leaf)) {
        try {
            Remove-Item -LiteralPath $releaseNotesTempPath -Force
        } catch {
            Write-Warning "Could not remove temporary release notes file '$releaseNotesTempPath': $($_.Exception.Message)"
            $scriptExitCode = 1
        }
    }
    Pop-Location
}

exit $scriptExitCode
