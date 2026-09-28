$ErrorActionPreference = "Continue"
$path = "D:\codexshuju\tianxin-pipe-project"
$userSid = "S-1-5-21-2444423791-1674344525-378683360-1001"
Write-Host "Taking ownership as Administrators..."
takeown.exe /F $path /R /A /D Y
Write-Host "Setting owner to real KARRY SID $userSid ..."
icacls.exe $path /setowner "*$userSid" /T /C
Write-Host "Granting full control to real KARRY SID..."
$grantArg = "*${userSid}:(OI)(CI)F"
icacls.exe $path /grant $grantArg /T /C
Write-Host "Ownership repair completed."
Read-Host "Press Enter to close"