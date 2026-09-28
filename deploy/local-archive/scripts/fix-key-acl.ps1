$ErrorActionPreference = 'Stop'
$key = 'D:\codexshuju\tianxin-pipe-project\.ssh\txgycloud_ed25519'
$identity = New-Object System.Security.Principal.NTAccount($env:USERDOMAIN, $env:USERNAME)

$acl = New-Object System.Security.AccessControl.FileSecurity
$acl.SetAccessRuleProtection($true, $false)
$rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
    $identity,
    [System.Security.AccessControl.FileSystemRights]::Read,
    [System.Security.AccessControl.AccessControlType]::Allow
)
$acl.AddAccessRule($rule)
$acl.SetOwner($identity)
Set-Acl -LiteralPath $key -AclObject $acl

Write-Host "Private key ACL rebuilt for $($identity.Value)." -ForegroundColor Green
& icacls.exe $key
