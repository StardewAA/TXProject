<#
    repair-file-locks.ps1 -- grant the interactive account write access to files
    the sandboxed agent session cannot modify.

    WHY
      Some repo files are owned by BUILTIN\Administrators and carry an ACL that
      grants the interactive user NOTHING (Owner: BUILTIN\Administrators, single
      ACE "Administrators Allow FullControl"). The agent session runs under a
      UAC-filtered token, so it has no WRITE_DAC and cannot take ownership, and
      therefore cannot edit, delete or replace those files.

      An ELEVATED process CAN write them, which is why a writability probe inside
      this elevated script always reports "nothing to repair". The fix must be
      applied UNCONDITIONALLY -- never driven by such a probe.

    WHAT
      Grants <principal>:M (inheritable) on the repository root, then recursively
      on every source directory, then explicitly on every file that still lacks an
      ACE for the account. It never touches file contents.
      Dependency trees (node_modules / .pnpm-store / .git / target / dist) are
      skipped, which is what keeps this at seconds instead of hanging.

    HOW TO RUN  (ELEVATED PowerShell -- "Run as Administrator")
      powershell -ExecutionPolicy Bypass -File D:\tianxinfangfu\deploy\repair-file-locks.ps1

    Options:
      -Root <path>       repository root (default D:\tianxinfangfu)
      -Principal <acct>  account to grant (default the current user)

    NOTE: only an EXPLICIT ACE for the exact account makes the sandboxed session
    able to write. Generic groups are deliberately not used.
#>
param(
    [string]$Root = 'D:\tianxinfangfu',
    [string]$Principal
)

$ErrorActionPreference = 'Continue'

if (-not $Principal) { $Principal = "$env:USERDOMAIN\$env:USERNAME" }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: not elevated. Open PowerShell as Administrator and re-run." -ForegroundColor Red
    exit 1
}

Write-Host "root      = $Root"
Write-Host "principal = $Principal"
Write-Host ""

$skipDir = '\\(node_modules|\.pnpm-store|\.git|target|dist|\.turbo|\.dsh-build|\.agents|\.claude|snapshots|vendor)\\'

function Test-HasAce([string]$Path) {
    try {
        $acl = Get-Acl $Path -ErrorAction Stop
    } catch { return $false }
    foreach ($a in $acl.Access) {
        if ($a.AccessControlType -eq 'Allow' -and "$($a.IdentityReference)" -eq $Principal -and
            ($a.FileSystemRights -band [System.Security.AccessControl.FileSystemRights]::Modify)) { return $true }
    }
    return $false
}

# --- 1. root (inheritable, so newly created files are writable too) ---
Write-Host "=== 1/3 root grant ===" -ForegroundColor Cyan
& icacls "$Root" /grant "${Principal}:(OI)(CI)M" /C /Q *> $null

# --- 2. recursive grant on source directories only ---
Write-Host ""
Write-Host "=== 2/3 recursive grant on source directories ===" -ForegroundColor Cyan
$topDirs = Get-ChildItem $Root -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '^(node_modules|\.pnpm-store|\.git|\.turbo|\.dsh-build|\.agents|\.claude|snapshots|vendor)$' }

$dirFail = 0
foreach ($d in $topDirs) {
    & icacls "$($d.FullName)" /grant "${Principal}:(OI)(CI)M" /T /C /Q *> $null
    if ($LASTEXITCODE -ne 0) { $dirFail++ }
    Write-Host ("  {0,-42} {1}" -f $d.Name, $(if ($LASTEXITCODE -eq 0) { 'ok' } else { 'FAIL' }))
}
Write-Host "directory grant failures: $dirFail"

# --- 3. explicit per-file grant for anything still lacking the ACE ---
Write-Host ""
Write-Host "=== 3/3 explicit per-file grant ===" -ForegroundColor Cyan
$total = 0; $granted = 0; $still = 0
Get-ChildItem $Root -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch $skipDir } |
    ForEach-Object {
        $total++
        if (Test-HasAce $_.FullName) { return }
        & icacls "$($_.FullName)" /grant "${Principal}:M" /C /Q *> $null
        if (Test-HasAce $_.FullName) { $granted++ }
        else { $still++; if ($still -le 20) { Write-Host "  still blocked: $($_.FullName)" -ForegroundColor Yellow } }
    }

Write-Host ""
Write-Host "files scanned = $total"
Write-Host "newly granted = $granted"
Write-Host "still blocked = $still" -ForegroundColor $(if ($still -eq 0) { 'Green' } else { 'Yellow' })
Write-Host ""
Write-Host "done. Tell the agent to re-verify."
