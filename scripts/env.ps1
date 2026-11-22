# Dot-source this file: . .\scripts\env.ps1
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Java's Windows launcher decodes @argument files using the native code page.
# Gradle writes those files as UTF-8, which breaks worker classpaths containing
# non-ASCII paths. Use one verified ASCII junction for the entire build path:
# both the dependency cache and app/build remain physically inside this project.
$buildRoot = $projectRoot
if ($projectRoot -match '[^\x00-\x7F]') {
    $aliasParent = Join-Path ([System.IO.Path]::GetTempPath()) 'titradeck-build-paths'
    if ($aliasParent -match '[^\x00-\x7F]') {
        throw 'Gradle requires an ASCII temporary path on Windows. Set TEMP to an ASCII directory for this shell and retry.'
    }
    if (Test-Path -LiteralPath $aliasParent) {
        $parentItem = Get-Item -LiteralPath $aliasParent -Force
        if (-not $parentItem.PSIsContainer -or ($parentItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
            throw "Build alias parent must be an ordinary directory: $aliasParent"
        }
    } else {
        New-Item -ItemType Directory -Path $aliasParent | Out-Null
    }
    $normalizedRoot = [System.IO.Path]::GetFullPath($projectRoot).TrimEnd('\')
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = -join ($hasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($normalizedRoot.ToLowerInvariant())) | ForEach-Object { $_.ToString('x2') })
    } finally {
        $hasher.Dispose()
    }
    $buildRoot = Join-Path $aliasParent ('workspace-' + $hash.Substring(0, 24))
    if (Test-Path -LiteralPath $buildRoot) {
        $aliasItem = Get-Item -LiteralPath $buildRoot -Force
        $aliasTarget = @($aliasItem.Target)[0]
        if ($aliasItem.LinkType -ne 'Junction' -or -not $aliasTarget -or
            -not [string]::Equals([System.IO.Path]::GetFullPath($aliasTarget).TrimEnd('\'), $normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to replace an existing path that is not this project's build junction: $buildRoot"
        }
    } else {
        New-Item -ItemType Junction -Path $buildRoot -Target $normalizedRoot | Out-Null
    }
}
$jdkCandidates = @(
    (Join-Path $projectRoot '.tools\jdk'),
    'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot'
)
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$selectedJdk = $null
foreach ($candidate in $jdkCandidates) {
    $releaseFile = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and (Test-Path -LiteralPath $releaseFile)) {
        $version = Get-Content -LiteralPath $releaseFile | Select-String '^JAVA_VERSION="21[.\"]'
        if ($version) { $selectedJdk = $candidate; break }
    }
}
if (-not $selectedJdk) {
    throw 'JDK 21 is required. Install JDK 21 and set JAVA_HOME, or extract it into .tools\jdk.'
}
$env:JAVA_HOME = $selectedJdk
$env:ANDROID_HOME = Join-Path $buildRoot '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $buildRoot '.cache\android'
$env:GRADLE_USER_HOME = Join-Path $buildRoot '.cache\gradle'
$pathEntries = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME 'cmdline-tools\19.0\bin')
)
$existingPath = $env:Path -split ';' | Where-Object { $_ -and $_ -notin $pathEntries }
$env:Path = ($pathEntries + $existingPath) -join ';'
foreach ($directory in @($env:ANDROID_HOME, $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

# AGP resolves sdk.dir against the root project. A relative path also avoids
# the Lint PropertyEscape false positive on valid Java Unicode escapes.
Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Value 'sdk.dir=.tools/android-sdk' -Encoding ASCII
