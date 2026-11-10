param([switch]$TestsOnly,[switch]$Public)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskJdk = 'D:\APPs\Android Studio\jbr'
$taskSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$taskPlatform = Join-Path $taskSdk 'platforms\android-37.0\android.jar'
$taskTools = Join-Path $taskSdk 'build-tools\36.0.0'
$taskBuild = Join-Path $taskRoot ('build\run-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff'))
$taskClasses = Join-Path $taskBuild 'classes'
$taskDex = Join-Path $taskBuild 'dex'
$taskTestClasses = Join-Path $taskBuild 'tests'
$taskDist = Join-Path $taskRoot 'dist'
$taskManifest = [xml](Get-Content -LiteralPath "$taskRoot\android\AndroidManifest.xml" -Raw)
$taskVersion = $taskManifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
if ($taskVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid manifest version' }
foreach ($taskRequired in @("$taskJdk\bin\javac.exe", "$taskJdk\bin\java.exe", $taskPlatform, "$taskTools\aapt2.exe", "$taskTools\lib\d8.jar", "$taskTools\lib\apksigner.jar")) {
    if (-not (Test-Path -LiteralPath $taskRequired)) { throw "Missing existing build tool: $taskRequired" }
}
New-Item -ItemType Directory -Force -Path $taskClasses,$taskDex,$taskTestClasses,$taskDist | Out-Null
$taskCore = @("$taskRoot\android\src\cn\dormnative\app\ConnectionRetry.java", "$taskRoot\android\src\cn\dormnative\app\ScanBudget.java", "$taskRoot\android\src\cn\dormnative\app\ArrivalGate.java", "$taskRoot\android\src\cn\dormnative\app\Protocol.java", "$taskRoot\android\src\cn\dormnative\app\ProximityGate.java", "$taskRoot\android\src\cn\dormnative\app\SignalCalibration.java", "$taskRoot\android\src\cn\dormnative\app\OperationGuard.java", "$taskRoot\android\src\cn\dormnative\app\ScanSession.java", "$taskRoot\android\src\cn\dormnative\app\MonitorPolicy.java", "$taskRoot\tests\ProtocolTests.java", "$taskRoot\tests\AutomationTests.java")
& "$taskJdk\bin\javac.exe" -encoding UTF-8 --release 8 -d $taskTestClasses $taskCore
if ($LASTEXITCODE -ne 0) { throw 'Core test compilation failed' }
$taskTestOutput = & "$taskJdk\bin\java.exe" -cp $taskTestClasses cn.dormnative.app.ProtocolTests
$taskTestExit = $LASTEXITCODE
$taskTestOutput | Set-Content -LiteralPath "$taskDist\core-tests.json" -Encoding UTF8
$taskTestOutput
if ($taskTestExit -ne 0) { throw 'Core tests failed' }
$taskAutoOutput = & "$taskJdk\bin\java.exe" -cp $taskTestClasses cn.dormnative.app.AutomationTests
$taskAutoExit = $LASTEXITCODE
$taskAutoOutput | Set-Content -LiteralPath "$taskDist\automation-tests.json" -Encoding UTF8
$taskAutoOutput
if ($taskAutoExit -ne 0) { throw 'Automation tests failed' }
if ($TestsOnly) { return }
$taskSources = @(Get-ChildItem -LiteralPath "$taskRoot\android\src" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
& "$taskJdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $taskPlatform -d $taskClasses $taskSources
if ($LASTEXITCODE -ne 0) { throw 'Android SDK compilation failed' }
$taskClassFiles = @(Get-ChildItem -LiteralPath $taskClasses -Filter '*.class' -Recurse | Select-Object -ExpandProperty FullName)
& "$taskJdk\bin\java.exe" -cp "$taskTools\lib\d8.jar" com.android.tools.r8.D8 --min-api 26 --lib $taskPlatform --output $taskDex $taskClassFiles
if ($LASTEXITCODE -ne 0) { throw 'DEX generation failed' }
& "$taskTools\aapt2.exe" compile --dir "$taskRoot\android\res" -o "$taskBuild\resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
& "$taskTools\aapt2.exe" link -I $taskPlatform --manifest "$taskRoot\android\AndroidManifest.xml" -o "$taskBuild\unsigned.apk" "$taskBuild\resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'APK resource linking failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [System.IO.Compression.ZipFile]::Open("$taskBuild\unsigned.apk",[System.IO.Compression.ZipArchiveMode]::Update)
try { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,"$taskDex\classes.dex",'classes.dex') | Out-Null } finally { $taskArchive.Dispose() }
$taskArchive = [System.IO.Compression.ZipFile]::Open("$taskBuild\unsigned.apk",[System.IO.Compression.ZipArchiveMode]::Update)
try {
    $taskAssets = Join-Path $taskRoot 'android\assets'
    foreach ($taskAsset in Get-ChildItem -LiteralPath $taskAssets -Recurse -File) {
        $taskEntry = 'assets/' + [System.IO.Path]::GetRelativePath($taskAssets,$taskAsset.FullName).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskAsset.FullName,$taskEntry) | Out-Null
    }
} finally { $taskArchive.Dispose() }
& "$taskTools\zipalign.exe" -f -p 4 "$taskBuild\unsigned.apk" "$taskBuild\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
$taskSigning = Join-Path $taskRoot 'android\signing'
New-Item -ItemType Directory -Force -Path $taskSigning | Out-Null
$taskKey = Join-Path $taskSigning 'local-test.keystore'
if (-not (Test-Path -LiteralPath $taskKey)) {
    & "$taskJdk\bin\keytool.exe" -genkeypair -keystore $taskKey -storepass android -keypass android -alias dormquick -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Dorm Quick Open Local Test,O=Local Development,C=CN' -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Local test signing key generation failed' }
}
$taskFlavor = if ($Public) { 'public' } else { 'test' }
$taskApk = Join-Path $taskDist "dorm-native-open-$taskVersion-$taskFlavor.apk"
& "$taskJdk\bin\java.exe" -jar "$taskTools\lib\apksigner.jar" sign --ks $taskKey --ks-key-alias dormquick --ks-pass pass:android --key-pass pass:android --out $taskApk "$taskBuild\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
$taskVerification = & "$taskJdk\bin\java.exe" -jar "$taskTools\lib\apksigner.jar" verify --verbose --print-certs $taskApk
$taskVerifyExit = $LASTEXITCODE
$taskVerification | Set-Content -LiteralPath "$taskDist\apk-signature.txt" -Encoding UTF8
if ($taskVerifyExit -ne 0) { throw 'APK signature verification failed' }
$taskBadging = & "$taskTools\aapt2.exe" dump badging $taskApk
$taskBadgingExit = $LASTEXITCODE
$taskBadging | Set-Content -LiteralPath "$taskDist\apk-badging.txt" -Encoding UTF8
if ($taskBadgingExit -ne 0) { throw 'APK metadata check failed' }
$taskItem = Get-Item -LiteralPath $taskApk
$taskBuildResult = [pscustomobject]@{apk=$taskItem.FullName;buildDirectory=$taskBuild;version=$taskVersion;personalBuild=$false;publicBuild=[bool]$Public;bytes=$taskItem.Length;sha256=(Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash;signatureVerified=$true;deviceRuntime='NOT RUN';realDoorUnlock='NOT RUN'} | ConvertTo-Json
$taskBuildResult | Set-Content -LiteralPath "$taskDist\build-result.json" -Encoding UTF8
$taskBuildResult | Set-Content -LiteralPath "$taskDist\build-result-$taskFlavor.json" -Encoding UTF8
Get-Content -LiteralPath "$taskDist\build-result.json" -Raw

