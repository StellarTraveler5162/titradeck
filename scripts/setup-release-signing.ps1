[CmdletBinding()]
param(
    [string]$DistinguishedName = 'CN=TitraDeck'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'env.ps1')
$signingRoot = Join-Path $buildRoot '.signing'
$keyFile = Join-Path $signingRoot 'titradeck-release.p12'
$propertiesFile = Join-Path $signingRoot 'release.properties'
if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    throw 'Release signing already exists. Back it up and reuse it; this script never overwrites a release key.'
}
New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null

# Newly created secrets are readable only by this Windows user and SYSTEM.
$signingAcl = New-Object System.Security.AccessControl.DirectorySecurity
$userSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$systemSid = New-Object System.Security.Principal.SecurityIdentifier('S-1-5-18')
$signingAcl.SetOwner($userSid)
$signingAcl.SetAccessRuleProtection($true, $false)
foreach ($sid in @($userSid, $systemSid)) {
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $signingAcl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $signingRoot -AclObject $signingAcl

$randomBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
$generatedPassword = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })
$env:TITRADECK_RELEASE_KEY_PASSWORD = $generatedPassword
try {
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -noprompt `
        -storetype PKCS12 -keystore $keyFile -alias 'titradeck-release' `
        -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 `
        -dname $DistinguishedName `
        -storepass:env TITRADECK_RELEASE_KEY_PASSWORD -keypass:env TITRADECK_RELEASE_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed. No signing properties were written.' }

    # PKCS12 uses the same password for the store and private key.
    $properties = @(
        '# Private signing credentials. Back up together with the PKCS12 file. Do not share.'
        'storeFile=titradeck-release.p12'
        "storePassword=$generatedPassword"
        'keyAlias=titradeck-release'
        "keyPassword=$generatedPassword"
    )
    [System.IO.File]::WriteAllLines($propertiesFile, $properties, [System.Text.Encoding]::ASCII)
    & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -exportcert -rfc `
        -keystore $keyFile -alias 'titradeck-release' -storepass:env TITRADECK_RELEASE_KEY_PASSWORD `
        -file (Join-Path $signingRoot 'titradeck-release-cert.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed.' }
    Write-Host 'Created a private release key and local signing properties in .signing/.'
    Write-Host 'Back up BOTH titradeck-release.p12 and release.properties securely before distribution.'
    Write-Host 'The passwords were not printed. Future builds reuse the same signing identity.'
} finally {
    Remove-Item Env:TITRADECK_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $generatedPassword = $null
    $properties = $null
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
