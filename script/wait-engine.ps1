# 等待 Docker 引擎就绪（最多 4 分钟）
for ($i = 1; $i -le 24; $i++) {
    Start-Sleep -Seconds 10
    docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Output ("ENGINE-READY " + ($i * 10) + "s")
        docker ps --format "table {{.Names}}\t{{.Status}}"
        exit 0
    }
    Write-Output ("waiting " + ($i * 10) + "s")
}
Write-Output "ENGINE-NOT-READY"
exit 1
