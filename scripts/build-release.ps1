[CmdletBinding()]
param([switch]$Bundle)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $buildRoot '.signing\release.properties'))) {
    throw 'Release signing is missing. For a FIRST release, run scripts/setup-release-signing.ps1; otherwise restore the original .signing files.'
}
$releaseTasks = @('testReleaseUnitTest', 'assembleRelease', 'lintRelease')
if ($Bundle) { $releaseTasks += 'bundleRelease' }
& (Join-Path $PSScriptRoot 'build.ps1') @releaseTasks
if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
$releaseApk = Join-Path $buildRoot 'app\build\outputs\apk\release\app-release.apk'
& (Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $releaseApk
if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
& (Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\zipalign.exe') -c -P 16 4 $releaseApk
if ($LASTEXITCODE -ne 0) { throw 'Release APK alignment verification failed.' }
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
Write-Host "Signed release APK: $projectRoot\app\build\outputs\apk\release\app-release.apk"
Write-Host 'Retain app/build/outputs/mapping/release/mapping.txt with this release for crash decoding.'
