[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Tasks = @('assembleDebug')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$wrapper = Join-Path $buildRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper)) { throw 'Run .\scripts\setup-android.ps1 first.' }
Push-Location $buildRoot
try {
    & $wrapper @Tasks '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
