[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. (Join-Path $PSScriptRoot 'env.ps1')
$downloadDirectory = Join-Path $projectRoot '.tools\downloads'
New-Item -ItemType Directory -Force -Path $downloadDirectory | Out-Null

function Get-VerifiedFile {
    param([string]$Uri, [string]$Destination, [string]$Checksum, [string]$Algorithm = 'SHA256')
    if (Test-Path -LiteralPath $Destination) {
        if ((Get-FileHash -LiteralPath $Destination -Algorithm $Algorithm).Hash -ieq $Checksum) { return }
    }
    Write-Host "Downloading $Uri"
    $partial = "$Destination.partial"
    & curl.exe --fail --location --retry 3 --connect-timeout 30 --speed-limit 1024 --speed-time 60 --output $partial $Uri
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Uri" }
    $actual = (Get-FileHash -LiteralPath $partial -Algorithm $Algorithm).Hash
    if ($actual -ine $Checksum) { throw "Checksum mismatch: $Destination" }
    Move-Item -LiteralPath $partial -Destination $Destination -Force
}

$sdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools\19.0\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    $toolsZip = Join-Path $downloadDirectory 'commandlinetools-win-13114758_latest.zip'
    Get-VerifiedFile -Uri 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' `
        -Destination $toolsZip -Checksum '54a582f3bf73e04253602f2d1c80bd5868aac115' -Algorithm SHA1
    $extractDirectory = Join-Path $downloadDirectory 'commandlinetools-19-extracted'
    New-Item -ItemType Directory -Force -Path $extractDirectory | Out-Null
    & tar.exe -xf $toolsZip -C $extractDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Unable to extract Android command-line tools.' }
    $commandLineDirectory = Join-Path $env:ANDROID_HOME 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $commandLineDirectory | Out-Null
    Move-Item -LiteralPath (Join-Path $extractDirectory 'cmdline-tools') -Destination (Join-Path $commandLineDirectory '19.0')
}

Write-Host 'Installing Android SDK packages and accepting the Android SDK license agreements.'
1..20 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$env:ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
foreach ($installedFile in @('platform-tools\adb.exe', 'platforms\android-36\android.jar', 'build-tools\36.0.0\aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $installedFile))) {
        throw "SDK installation is incomplete: $installedFile"
    }
}

$wrapperDirectory = Join-Path $projectRoot 'gradle\wrapper'
New-Item -ItemType Directory -Force -Path $wrapperDirectory | Out-Null
$distributionChecksumFile = Join-Path $downloadDirectory 'gradle-8.13-bin.zip.sha256'
& curl.exe --fail --location --retry 3 --connect-timeout 30 --max-time 120 --output $distributionChecksumFile 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256'
if ($LASTEXITCODE -ne 0) { throw 'Unable to obtain Gradle distribution checksum.' }
$distributionChecksum = (Get-Content -LiteralPath $distributionChecksumFile -Raw).Trim()
if ($distributionChecksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid Gradle distribution checksum.' }
$gradleZip = Join-Path $downloadDirectory 'gradle-8.13-bin.zip'
Get-VerifiedFile -Uri 'https://repo.huaweicloud.com/gradle/gradle-8.13-bin.zip' `
    -Destination $gradleZip -Checksum $distributionChecksum
$gradleLauncher = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradleLauncher)) {
    & tar.exe -xf $gradleZip -C (Join-Path $projectRoot '.tools')
    if ($LASTEXITCODE -ne 0) { throw 'Unable to extract Gradle.' }
}
# Generate official launchers using Gradle itself, without resolving app dependencies.
$bootstrapDirectory = Join-Path $projectRoot '.cache\gradle-bootstrap'
New-Item -ItemType Directory -Force -Path $bootstrapDirectory | Out-Null
Set-Content -LiteralPath (Join-Path $bootstrapDirectory 'settings.gradle') -Value "rootProject.name = 'wrapper-bootstrap'" -Encoding ASCII
& $gradleLauncher '-p' $bootstrapDirectory 'wrapper' '--gradle-version' '8.13' '--distribution-type' 'bin' `
    '--gradle-distribution-url' 'https://repo.huaweicloud.com/gradle/gradle-8.13-bin.zip' `
    '--gradle-distribution-sha256-sum' $distributionChecksum '--no-daemon' '--console=plain'
if ($LASTEXITCODE -ne 0) { throw 'Unable to generate Gradle wrapper.' }
foreach ($launcher in @('gradlew', 'gradlew.bat')) {
    Copy-Item -LiteralPath (Join-Path $bootstrapDirectory $launcher) -Destination (Join-Path $projectRoot $launcher) -Force
}
Copy-Item -LiteralPath (Join-Path $bootstrapDirectory 'gradle\wrapper\gradle-wrapper.jar') -Destination $wrapperDirectory -Force
@"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://repo.huaweicloud.com/gradle/gradle-8.13-bin.zip
distributionSha256Sum=$distributionChecksum
networkTimeout=120000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@ | Set-Content -LiteralPath (Join-Path $wrapperDirectory 'gradle-wrapper.properties') -Encoding ASCII

Write-Host 'Android environment is ready. Run .\scripts\build.ps1 to build the debug APK.'
