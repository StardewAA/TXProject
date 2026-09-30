[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'TxgyMall\android-signing'),
    [switch]$CopyPassword
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This helper uses Windows DPAPI. Run it on Windows with keytool available.'
}

$keytool = (Get-Command keytool -ErrorAction Stop).Source
function Invoke-SigningKeytool {
    param([string[]]$KeytoolArguments)
    # keytool writes progress and JKS format warnings to stderr even on success.
    # Windows PowerShell 5 must not turn those messages into terminating errors.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = (& $keytool @KeytoolArguments 2>&1 | Out-String)
        $toolExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($toolExitCode -ne 0) { throw "keytool failed (exit $toolExitCode). $output" }
    return $output
}
$packageName = 'cn.txgy.mall'
$aliasName = 'txgymall'
$signingPath = [IO.Path]::GetFullPath($SigningDirectory)
$keystorePath = Join-Path $signingPath "$packageName.keystore"
$passwordPath = Join-Path $signingPath "$packageName.password.dpapi"
$infoPath = Join-Path $signingPath 'certificate-info.json'
$certExists = Test-Path -LiteralPath $keystorePath
$passwordExists = Test-Path -LiteralPath $passwordPath
if ($certExists -and -not $passwordExists) {
    throw 'Signing files are incomplete. Restore the existing certificate/password pair; do not replace an existing signing identity.'
}
if ($CopyPassword -and -not $certExists) {
    throw 'Run without -CopyPassword once to prepare the signing certificate.'
}

if (-not $certExists) {
    New-Item -ItemType Directory -Path $signingPath -Force | Out-Null
    # Private key and DPAPI data are accessible only to this Windows user and SYSTEM.
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    & icacls.exe $signingPath /inheritance:r /grant:r "*$($currentSid.Value):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict access to the signing directory.' }
}
if ($passwordExists) {
    $securePassword = ConvertTo-SecureString ([IO.File]::ReadAllText($passwordPath).Trim())
} else {
    $randomBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $securePassword = ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    # Save before generation so an interrupted keytool can resume with the same password.
    [IO.File]::WriteAllText($passwordPath, (ConvertFrom-SecureString $securePassword))
}

$previousPassword = [Environment]::GetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', 'Process')
try {
    $plainPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
    [Environment]::SetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', $plainPassword, 'Process')
    if (-not $certExists) {
        # Use JKS explicitly for compatibility with HBuilderX's Java signing tools.
        Invoke-SigningKeytool @('-genkeypair', '-noprompt', '-storetype', 'JKS',
            '-keystore', $keystorePath, '-alias', $aliasName, '-keyalg', 'RSA', '-keysize', '2048',
            '-sigalg', 'SHA256withRSA', '-validity', '10950',
            '-dname', 'CN=TXGY Mall, OU=Course Demo, O=TXGY Course Project, C=CN',
            '-storepass:env', 'TXGY_MALL_ANDROID_KEYSTORE_PASSWORD',
            '-keypass:env', 'TXGY_MALL_ANDROID_KEYSTORE_PASSWORD') | Out-Null
    }
    $certificateOutput = Invoke-SigningKeytool @('-J-Duser.language=en', '-J-Duser.country=US',
        '-list', '-v', '-storetype', 'JKS', '-keystore', $keystorePath, '-alias', $aliasName,
        '-storepass:env', 'TXGY_MALL_ANDROID_KEYSTORE_PASSWORD')
    $sha256 = [regex]::Match($certificateOutput, 'SHA256:\s*([A-F0-9:]+)').Groups[1].Value
    $sha1 = [regex]::Match($certificateOutput, 'SHA1:\s*([A-F0-9:]+)').Groups[1].Value
    if ($sha256 -notmatch '^([A-F0-9]{2}:){31}[A-F0-9]{2}$' -or $certificateOutput -notmatch 'PrivateKeyEntry') {
        throw 'The certificate does not contain the expected private key and SHA-256 fingerprint.'
    }
    $info = [ordered]@{
        packageName = $packageName
        alias = $aliasName
        keystore = $keystorePath
        algorithm = 'RSA-2048 / SHA256withRSA'
        sha256 = $sha256
        sha1 = $sha1
        passwordProtection = 'Windows DPAPI, current user'
    }
    [IO.File]::WriteAllText($infoPath, ($info | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    if ($CopyPassword) {
        Set-Clipboard -Value $plainPassword
        Write-Output 'Signing password copied to the clipboard. Paste into both HBuilderX password fields, then clear the clipboard.'
    }
    Write-Output "Package: $packageName"
    Write-Output "Certificate: $keystorePath"
    Write-Output "Alias: $aliasName"
    Write-Output "SHA-256: $sha256"
    Write-Output "Certificate information: $infoPath"
    Write-Output 'No cloud packaging task has been submitted.'
} finally {
    [Environment]::SetEnvironmentVariable('TXGY_MALL_ANDROID_KEYSTORE_PASSWORD', $previousPassword, 'Process')
    $plainPassword = $null
    $securePassword.Dispose()
}
