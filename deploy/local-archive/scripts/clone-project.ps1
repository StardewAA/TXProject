$ErrorActionPreference = 'Stop'

$root = 'D:\codexshuju\tianxin-pipe-project'
$gitRoot = Join-Path $root 'tools\mingit-2.53.0'
$git = Join-Path $gitRoot 'cmd\git.exe'
$gitBin = Join-Path $gitRoot 'mingw64\bin'
$credentialSource = 'D:\codexshuju\2026-09-17\git-https-gitee-com-tianxin-pipe\work\.gitee_credentials'
$credentialStore = Join-Path $root 'tools\.git-credentials'
$target = Join-Path $root 'project-base\txgycloud'
$remote = 'https://gitee.com/tianxin-pipe-industry-group/txgycloud.git'

if (-not (Test-Path -LiteralPath $git)) {
    throw "MinGit not found: $git"
}
if (-not (Test-Path -LiteralPath $credentialSource)) {
    throw "Gitee credentials not found: $credentialSource"
}
if (Test-Path -LiteralPath $target) {
    if (Test-Path -LiteralPath (Join-Path $target '.git')) {
        Write-Host "Repository already exists: $target" -ForegroundColor Yellow
        & $git -C $target status -sb
        exit 0
    }
    throw "Target exists but is not a Git repository: $target"
}

$credential = @{}
Get-Content -LiteralPath $credentialSource | ForEach-Object {
    $parts = $_.Split('=', 2)
    if ($parts.Count -eq 2) {
        $credential[$parts[0]] = $parts[1]
    }
}
if (-not $credential['username'] -or -not $credential['password']) {
    throw 'Credential file must contain username= and password='
}

$credentialUrl = 'https://{0}:{1}@gitee.com/' -f $credential['username'], $credential['password']
Set-Content -LiteralPath $credentialStore -Encoding ascii -Value $credentialUrl

$env:GIT_EXEC_PATH = $gitBin
$env:Path = "$gitBin;$env:Path"
$env:GIT_TERMINAL_PROMPT = '0'
$env:GIT_CONFIG_NOSYSTEM = '1'
$storePath = $credentialStore.Replace('\', '/')

Write-Host "Cloning $remote" -ForegroundColor Cyan
Write-Host "Target: $target" -ForegroundColor Cyan

& $git `
    -c http.sslBackend=openssl `
    -c http.version=HTTP/1.1 `
    -c core.longpaths=true `
    -c credential.helper= `
    -c "credential.helper=store --file=$storePath" `
    clone --progress $remote $target

if ($LASTEXITCODE -ne 0) {
    throw "Clone failed with exit code $LASTEXITCODE"
}

Write-Host 'Clone completed.' -ForegroundColor Green
& $git -C $target status -sb
& $git -C $target log -1 --oneline --decorate
