$ErrorActionPreference = 'Stop'

$root = 'D:\codexshuju\tianxin-pipe-project'
$gitRoot = Join-Path $root 'tools\mingit-2.53.0'
$git = Join-Path $gitRoot 'cmd\git.exe'
$gitBin = Join-Path $gitRoot 'mingw64\bin'
$repo = Join-Path $root 'project-base\txgycloud'
$credentialStore = Join-Path $root 'tools\.git-credentials'
$storePath = $credentialStore.Replace('\', '/')
$branch = 'codex/verify-push-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$verificationFile = Join-Path $repo '.codex-push-verification.txt'

$env:GIT_EXEC_PATH = $gitBin
$env:Path = "$gitBin;$env:Path"
$env:GIT_TERMINAL_PROMPT = '0'
$env:GIT_CONFIG_NOSYSTEM = '1'
$env:GIT_PAGER = 'cat'

$common = @(
    '-c', "safe.directory=$repo",
    '-c', 'http.sslBackend=openssl',
    '-c', 'http.version=HTTP/1.1',
    '-c', 'credential.helper=',
    '-c', "credential.helper=store --file=$storePath"
)

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & $git -C $repo @common @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

Set-Location -LiteralPath $repo

Write-Host '=== 1. pull verification ===' -ForegroundColor Cyan
Invoke-Git fetch --all --prune
Invoke-Git pull --ff-only
Invoke-Git status -sb

Write-Host '=== 2. push verification ===' -ForegroundColor Cyan
$status = & $git -C $repo @common status --porcelain
if ($status) {
    throw 'Working tree is not clean. Commit or stash local changes before push verification.'
}

try {
    Invoke-Git switch -c $branch
    Set-Content -LiteralPath $verificationFile -Encoding ascii -Value "Codex push verification $(Get-Date -Format o)"
    Invoke-Git add -- $verificationFile
    Invoke-Git commit --no-verify -m 'chore: verify Gitee push access'
    Invoke-Git push -u origin $branch
    Write-Host "Push verification succeeded: $branch" -ForegroundColor Green
}
finally {
    $current = (& $git -C $repo @common branch --show-current).Trim()
    if ($current -eq $branch) {
        & $git -C $repo @common switch master
    }
    if (Test-Path -LiteralPath $verificationFile) {
        Remove-Item -LiteralPath $verificationFile -Force
    }
    & $git -C $repo @common branch -D $branch 2>$null
}

Write-Host '=== 3. cleaning remote verification branch ===' -ForegroundColor Cyan
& $git -C $repo @common push origin --delete $branch
if ($LASTEXITCODE -ne 0) {
    Write-Host "Remote verification branch could not be deleted: $branch" -ForegroundColor Yellow
    Write-Host 'Please delete it manually from Gitee.' -ForegroundColor Yellow
}

Write-Host '=== final status ===' -ForegroundColor Cyan
Invoke-Git status -sb
Write-Host 'Git pull and push verification completed.' -ForegroundColor Green
