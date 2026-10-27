param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android\hop-singapore-release')
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Keystore = Join-Path $SigningDirectory 'hop-release.p12'
$PasswordFile = Join-Path $SigningDirectory 'password.dpapi'
if (-not (Test-Path -LiteralPath $Keystore) -or -not (Test-Path -LiteralPath $PasswordFile)) {
    throw 'The persistent hop release key is missing. Restore the original signing key; do not generate a replacement for an existing app.'
}
$SecurePassword = Get-Content -LiteralPath $PasswordFile | ConvertTo-SecureString
$SigningCredential = [PSCredential]::new('hop-release', $SecurePassword)
$env:HOP_SIGNING_STORE_FILE = $Keystore
$env:HOP_SIGNING_STORE_PASSWORD = $SigningCredential.GetNetworkCredential().Password
$env:HOP_SIGNING_KEY_ALIAS = 'hop-release'
$env:HOP_SIGNING_KEY_PASSWORD = $env:HOP_SIGNING_STORE_PASSWORD
try {
    Push-Location $ProjectRoot
    & '.\gradlew.bat' ':app:assembleRelease' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    $GradleText = [IO.File]::ReadAllText((Join-Path $ProjectRoot 'app\build.gradle.kts'))
    $Version = [regex]::Match($GradleText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $OutputDirectory = Join-Path $ProjectRoot 'deliverables'
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $OutputApk = Join-Path $OutputDirectory "hop-$Version.apk"
    $SdkRoot = $env:ANDROID_HOME
    if (-not $SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    $ApkSigner = Join-Path $SdkRoot 'build-tools\36.0.0\apksigner.bat'
    $BuildApk = Join-Path $ProjectRoot 'app\build\outputs\apk\release\app-release.apk'
    & $ApkSigner verify $BuildApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature validation failed.' }
    Copy-Item -LiteralPath $BuildApk -Destination $OutputApk -Force
    $Checksum = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLower()
    "$Checksum  hop-$Version.apk" | Set-Content -LiteralPath "$OutputApk.sha256" -Encoding ASCII
    Write-Output "Signed APK: $OutputApk"
} finally {
    Pop-Location
    Remove-Item Env:HOP_SIGNING_STORE_FILE,Env:HOP_SIGNING_STORE_PASSWORD,Env:HOP_SIGNING_KEY_ALIAS,Env:HOP_SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
}
