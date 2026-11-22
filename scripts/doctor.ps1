$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
Write-Host "Project:      $projectRoot"
Write-Host "JAVA_HOME:    $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
Write-Host "Gradle cache: $env:GRADLE_USER_HOME"
& java -version
if ($LASTEXITCODE -ne 0) { throw 'Java check failed.' }
$requiredFiles = @(
    '.tools\android-sdk\cmdline-tools\19.0\bin\sdkmanager.bat',
    '.tools\android-sdk\platform-tools\adb.exe',
    '.tools\android-sdk\platforms\android-36\android.jar',
    '.tools\android-sdk\build-tools\36.0.0\aapt2.exe',
    'gradle\wrapper\gradle-wrapper.jar',
    'gradlew.bat'
)
foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $file))) { throw "Missing: $file. Run scripts\setup-android.ps1." }
    Write-Host "OK: $file"
}
& (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') version
if ($LASTEXITCODE -ne 0) { throw 'ADB check failed.' }
& (Join-Path $projectRoot 'gradlew.bat') '--version'
if ($LASTEXITCODE -ne 0) { throw 'Gradle check failed.' }
