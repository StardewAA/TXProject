[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'TxgyMall\android-signing'),
    [switch]$Publish
)

# No Gradle/cloud account/dependency download. Requires Android platform 35,
# build-tools 35.0.0, and JDK 17+. Private signing files stay outside the repo.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $SdkRoot) { throw 'Pass -SdkRoot or set ANDROID_HOME (Android SDK platform 35 and build-tools 35.0.0).' }
$project = $PSScriptRoot
$repository = Split-Path $project -Parent
$androidJar = Join-Path $SdkRoot 'platforms\android-35\android.jar'
$buildTools = @('build-tools\35.0.0', 'build-tools\android-15') |
    ForEach-Object { Join-Path $SdkRoot $_ } | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'aapt2.exe') } | Select-Object -First 1
if (-not (Test-Path -LiteralPath $androidJar) -or -not $buildTools) { throw 'Android SDK platform 35 or build-tools 35.0.0 not found.' }
$java = (Get-Command java.exe -ErrorAction Stop).Source
$javac = (Get-Command javac.exe -ErrorAction Stop).Source
$jar = (Get-Command jar.exe -ErrorAction Stop).Source
$keystore = Join-Path $SigningDirectory 'cn.txgy.mall.keystore'
$passwordFile = Join-Path $SigningDirectory 'cn.txgy.mall.password.dpapi'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $passwordFile)) {
    throw 'Signing identity missing. Run txgy-mall-uniapp-master/scripts/prepare-android-signing.ps1 once. Do not replace a certificate used for an existing release.'
}

function Invoke-BuildTool {
    param([string]$Executable, [string[]]$ToolArguments)
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = (& $Executable @ToolArguments 2>&1 | ForEach-Object { $_.ToString() } | Out-String)
        $exit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($exit -ne 0) { throw "$Executable failed (exit $exit). $output" }
    if ($output.Trim()) { Write-Host $output.Trim() }
}

$work = Join-Path $project ('build\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
foreach ($folder in @($work, "$work\generated", "$work\classes", "$work\test-classes", "$work\dex")) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
$source = Join-Path $project 'app\src\main'
Invoke-BuildTool $javac @('-J-Duser.language=en', '-J-Duser.country=US', '-Xlint:-options', '--release', '8', '-encoding', 'UTF-8', '-d', "$work\test-classes",
    "$source\java\cn\txgy\mall\ConnectionTarget.java", "$project\tests\ConnectionTargetTest.java",
    "$source\java\cn\txgy\mall\PageLoadPolicy.java", "$project\tests\PageLoadPolicyTest.java")
Invoke-BuildTool $java @('-cp', "$work\test-classes", 'ConnectionTargetTest')
Invoke-BuildTool $java @('-cp', "$work\test-classes", 'PageLoadPolicyTest')
Invoke-BuildTool "$buildTools\aapt2.exe" @('compile', '--dir', "$source\res", '-o', "$work\compiled-res.zip")
Invoke-BuildTool "$buildTools\aapt2.exe" @('link', '--manifest', "$source\AndroidManifest.xml", '-I', $androidJar,
    '--java', "$work\generated", '-o', "$work\unsigned.apk", "$work\compiled-res.zip")
$sources = @(Get-ChildItem -LiteralPath "$source\java", "$work\generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-BuildTool $javac (@('-J-Duser.language=en', '-J-Duser.country=US', '-Xlint:-options', '--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar, '-d', "$work\classes") + $sources)
Invoke-BuildTool $jar @('cf', "$work\classes.jar", '-C', "$work\classes", '.')
Invoke-BuildTool $java @('-cp', "$buildTools\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--lib', $androidJar,
    '--min-api', '23', '--output', "$work\dex", "$work\classes.jar")
Invoke-BuildTool $jar @('uf', "$work\unsigned.apk", '-C', "$work\dex", 'classes.dex')
Invoke-BuildTool "$buildTools\zipalign.exe" @('-f', '4', "$work\unsigned.apk", "$work\aligned.apk")

$securePassword = ConvertTo-SecureString ([IO.File]::ReadAllText($passwordFile).Trim())
$previousPassword = [Environment]::GetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', ([Net.NetworkCredential]::new('', $securePassword).Password), 'Process')
    Invoke-BuildTool $java @('-jar', "$buildTools\lib\apksigner.jar", 'sign', '--ks', $keystore, '--ks-key-alias', 'txgymall',
        '--ks-pass', 'env:TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', '--key-pass', 'env:TXGY_MALL_ANDROID_KEYSTORE_PASSWORD',
        '--out', "$work\signed.apk", "$work\aligned.apk")
} finally {
    [Environment]::SetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', $previousPassword, 'Process')
    $securePassword.Dispose()
}
Invoke-BuildTool $java @('-jar', "$buildTools\lib\apksigner.jar", 'verify', '--verbose', '--print-certs', "$work\signed.apk")
Invoke-BuildTool "$buildTools\zipalign.exe" @('-c', '4', "$work\signed.apk")
Invoke-BuildTool "$buildTools\aapt.exe" @('dump', 'badging', "$work\signed.apk")

[xml]$manifest = Get-Content -LiteralPath "$source\AndroidManifest.xml" -Raw -Encoding UTF8
$namespace = 'http://schemas.android.com/apk/res/android'
$version = $manifest.manifest.GetAttribute('versionName', $namespace)
$versionCode = [int]$manifest.manifest.GetAttribute('versionCode', $namespace)
$sha = (Get-FileHash -LiteralPath "$work\signed.apk" -Algorithm SHA256).Hash.ToLowerInvariant()
$filename = "txgy-mall-$version-$($sha.Substring(0, 12)).apk"
$metadata = [ordered]@{
    schemaVersion = 1
    packageName = 'cn.txgy.mall'
    version = $version
    versionCode = $versionCode
    apkUrl = "/downloads/$filename"
    fileSizeBytes = (Get-Item -LiteralPath "$work\signed.apk").Length
    releaseDate = (Get-Date -Format 'yyyy-MM-dd')
    minimumAndroidVersion = 'Android 6.0'
    sha256 = $sha
    permissionsSummary = '网络访问、网络状态；文件上传通过系统选择器，不读取通讯录或短信'
    distribution = 'classroom-demo'
}
[IO.File]::WriteAllText("$work\android-release.json", ($metadata | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
if ($Publish) {
    $public = Join-Path $repository 'txgy-mall-web\public\downloads'
    New-Item -ItemType Directory -Path $public -Force | Out-Null
    $destination = Join-Path $public $filename
    if ((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha) {
        throw 'Release filename collision. Existing APK will not be overwritten.'
    }
    Copy-Item -LiteralPath "$work\signed.apk" -Destination $destination
    # Publish metadata only after the matching, verified APK is in place.
    Copy-Item -LiteralPath "$work\android-release.json" -Destination (Join-Path $public 'android-release.json')
    Write-Host "Published: $destination"
}
Write-Output "APK: $work\signed.apk"
Write-Output "SHA-256: $sha"
