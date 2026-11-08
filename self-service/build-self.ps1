param(
    [string]$Request,
    [string]$EvidenceDir,
    [Parameter(Mandatory=$true)][string]$WorkDir,
    [string]$JdkPath,
    [string]$SdkPath,
    [string]$DeliveryDir,
    [switch]$Resume
)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Use PowerShell 7 (pwsh), not Windows PowerShell 5.1. Nothing was installed.' }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js is missing. Install it separately after reviewing the resource impact. This script installs nothing.' }
$selfRoot = Split-Path -Parent $PSScriptRoot
$selfPrepare = Join-Path $PSScriptRoot 'prepare-self.cjs'
if ($Resume) {
    $selfWorkspace = [System.IO.Path]::GetFullPath($WorkDir)
    $selfPrepared = Get-Content -LiteralPath (Join-Path $selfWorkspace 'self-workspace.json') -Raw | ConvertFrom-Json
    if ($selfPrepared.schema -ne 'dorm-native-self-workspace/1' -or $selfPrepared.packageId -notmatch '^cn\.dormnative\.self\.k[0-9a-f]{12}$') { throw 'This directory is not a generated self workspace' }
    if (-not (Test-Path -LiteralPath (Join-Path $selfWorkspace 'private/local-migration-signing.pem'))) { throw 'Own issuer key is missing; restore your backup first' }
} else {
    if (-not $Request -or -not $EvidenceDir) { throw 'New preparation requires -Request and -EvidenceDir; repairing an existing workspace uses -Resume' }
    $selfOutput = & node $selfPrepare $Request $EvidenceDir $WorkDir
    if ($LASTEXITCODE -ne 0) { throw ('Self workspace preparation failed: ' + ($selfOutput -join "`n")) }
    $selfPrepared = ($selfOutput -join "`n") | ConvertFrom-Json
    if (-not $selfPrepared.ok) { throw 'Self workspace was not created' }
    $selfWorkspace = $selfPrepared.workspace
}
& (Join-Path $selfWorkspace 'build.ps1') -Public -JdkPath $JdkPath -SdkPath $SdkPath
if ($LASTEXITCODE -ne 0) { throw 'Self APK build failed. Keep this workspace and its keys; rerun with -Resume after repair.' }
$selfResult = Get-Content -LiteralPath (Join-Path $selfWorkspace 'dist/build-result-public.json') -Raw | ConvertFrom-Json
$selfApk = Join-Path $selfWorkspace 'dist/宿舍快开-自助版.apk'
Copy-Item -LiteralPath $selfResult.apk -Destination $selfApk
$selfPhoneFolder = ''
if ($DeliveryDir) {
    $selfPhoneFolder = [System.IO.Path]::GetFullPath($DeliveryDir)
    New-Item -ItemType Directory -Force -Path $selfPhoneFolder | Out-Null
    Copy-Item -LiteralPath $selfApk -Destination (Join-Path $selfPhoneFolder '宿舍快开-自助版.apk')
    Copy-Item -LiteralPath (Join-Path $selfWorkspace 'dist/本人宿舍授权.dormkey') -Destination (Join-Path $selfPhoneFolder '本人宿舍授权.dormkey')
}
$selfDelivery = [pscustomobject]@{
    ok=$true
    apk=$selfApk
    dormkey=(Join-Path $selfWorkspace 'dist/本人宿舍授权.dormkey')
    packageId=$selfPrepared.packageId
    privateIssuerKeyCopied=$false
    publisherRequired=$false
    originalPublicApkCompatible=$false
    apkSha256=(Get-FileHash -LiteralPath $selfApk -Algorithm SHA256).Hash
    lockCommandSent=$false
    deviceRuntime='NOT RUN'
    phoneFilesFolder=$selfPhoneFolder
}
$selfDelivery | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $selfWorkspace 'dist/self-delivery.json') -Encoding utf8
$selfDelivery | ConvertTo-Json
