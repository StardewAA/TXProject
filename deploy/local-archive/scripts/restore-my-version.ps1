# 恢复到「拉取老师 master 之前」的运行环境（你的 CMS 版本）
# 运行：powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\codexshuju\tianxin-pipe-project\restore-my-version.ps1"

$ErrorActionPreference = 'Stop'
$repo = 'D:\codexshuju\tianxin-pipe-project\project-base\txgycloud'
$deploy = Join-Path $repo 'deploy'
$docker = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe"
$mvn = 'D:\codexshuju\tianxin-pipe-project\tools\apache-maven-3.9.11\bin\mvn.cmd'
$thinDockerfile = 'D:\codexshuju\tianxin-pipe-project\tools\Dockerfile.thin'
$env:JAVA_HOME = 'D:\JDK'

function Step($t) {
  Write-Host ''
  Write-Host ('=== ' + $t + ' ===') -ForegroundColor Cyan
}

Step '0) 检查 Docker 与分支'
& $docker version --format '{{.Server.Version}}' | Out-Null
Set-Location $repo
$branch = (git branch --show-current).Trim()
Write-Host ('当前分支: ' + $branch)
if ($branch -ne 'my-cms-portal-20260921') {
  Write-Host '提示：当前不在你的分支 my-cms-portal-20260921' -ForegroundColor Yellow
}

Step '1) 前端镜像切回你的版本'
$hasMy = & $docker images --format '{{.Repository}}:{{.Tag}}' | Select-String -SimpleMatch 'local-frontend:my-portal'
if ($hasMy) {
  & $docker tag local-frontend:my-portal local-frontend:modern
  Write-Host '已使用备份镜像 local-frontend:my-portal'
} else {
  Write-Host '未找到备份镜像，用当前分支代码重新构建前端（约 2-3 分钟）'
  & $docker build -f deploy/frontend/Dockerfile.localnode -t local-frontend:modern $repo
}

Step '1.5) 先停掉运行的容器，腾出内存给 Maven（稍后会重新启动）'
$running = & $docker ps --filter "name=local-" --format "{{.Names}}"
if ($running) { & $docker stop $running | Out-Null; Write-Host ("已停止 " + $running.Count + " 个容器") } else { Write-Host "没有运行中的容器" }

Step '2) 用当前分支重新打包后端 jar（约 2 分钟）'
& $mvn "-Dmaven.test.skip=true" "package"
if ($LASTEXITCODE -ne 0) { throw 'Maven 打包失败' }

Step '3) 暂存 jar 并生成后端镜像（约 7 分钟）'
$map = [ordered]@{
  gateway       = 'txgy-gateway/target/txgy-gateway.jar'
  system        = 'txgy-module-system/txgy-module-system-server/target/txgy-module-system-server.jar'
  infra         = 'txgy-module-infra/txgy-module-infra-server/target/txgy-module-infra-server.jar'
  member        = 'txgy-module-member/txgy-module-member-server/target/txgy-module-member-server.jar'
  bpm           = 'txgy-module-bpm/txgy-module-bpm-server/target/txgy-module-bpm-server.jar'
  pay           = 'txgy-module-pay/txgy-module-pay-server/target/txgy-module-pay-server.jar'
  report        = 'txgy-module-report/txgy-module-report-server/target/txgy-module-report-server.jar'
  mp            = 'txgy-module-mp/txgy-module-mp-server/target/txgy-module-mp-server.jar'
  product       = 'txgy-module-mall/txgy-module-product-server/target/txgy-module-product-server.jar'
  trade         = 'txgy-module-mall/txgy-module-trade-server/target/txgy-module-trade-server.jar'
  promotion     = 'txgy-module-mall/txgy-module-promotion-server/target/txgy-module-promotion-server.jar'
  statistics    = 'txgy-module-mall/txgy-module-statistics-server/target/txgy-module-statistics-server.jar'
  crm           = 'txgy-module-crm/txgy-module-crm-server/target/txgy-module-crm-server.jar'
  erp           = 'txgy-module-erp/txgy-module-erp-server/target/txgy-module-erp-server.jar'
  iot           = 'txgy-module-iot/txgy-module-iot-server/target/txgy-module-iot-server.jar'
  'iot-gateway'   = 'txgy-module-iot/txgy-module-iot-gateway/target/txgy-module-iot-gateway.jar'
  mes           = 'txgy-module-mes/txgy-module-mes-server/target/txgy-module-mes-server.jar'
  wms           = 'txgy-module-wms/txgy-module-wms-server/target/txgy-module-wms-server.jar'
  im            = 'txgy-module-im/txgy-module-im-server/target/txgy-module-im-server.jar'
  ai            = 'txgy-module-ai/txgy-module-ai-server/target/txgy-module-ai-server.jar'
  hrm           = 'txgy-module-hrm/txgy-module-hrm-server/target/txgy-module-hrm-server.jar'
  fms           = 'txgy-module-fms/txgy-module-fms-server/target/txgy-module-fms-server.jar'
  cms           = 'txgy-module-cms/txgy-module-cms-server/target/txgy-module-cms-server.jar'
}

$thinLines = @(
  'ARG BASE'
  'FROM ${BASE}'
  'WORKDIR /app'
  'COPY app.jar /app/app.jar'
  'ENV JAVA_OPTS=""'
  'ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]'
)
Set-Content -LiteralPath $thinDockerfile -Value ($thinLines -join [char]10) -Encoding UTF8

foreach ($svc in $map.Keys) {
  $src = Join-Path $repo ($map[$svc] -replace '/', '\')
  if (-not (Test-Path -LiteralPath $src)) {
    Write-Host ('跳过 ' + $svc + '（缺 jar）') -ForegroundColor Yellow
    continue
  }
  $dir = Join-Path $deploy ('jars\' + $svc)
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  Copy-Item -LiteralPath $src -Destination (Join-Path $dir 'app.jar') -Force
  $baseTag = 'local-' + $svc + ':latest'
  & $docker build --quiet --build-arg ('BASE=' + $baseTag) -f $thinDockerfile -t $baseTag $dir | Out-Null
  if ($LASTEXITCODE -eq 0) { Write-Host ('ok: ' + $svc) } else { Write-Host ('失败: ' + $svc) -ForegroundColor Red }
}

Step '4) 启动全部服务'
Set-Location $deploy
& $docker start local-mysql-1 local-redis-1 local-nacos-1 2>$null | Out-Null
$svcs = @('gateway','system','infra','member','bpm','pay','report','mp','product','trade','promotion','statistics','crm','erp','iot','iot-gateway','mes','wms','im','ai','hrm','fms','cms','frontend')
& $docker compose -p local -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.all.override.yml --env-file .env up -d --no-build --no-deps --force-recreate @svcs

Step '完成'
Write-Host '等 1-2 分钟后访问 http://127.0.0.1:8080（企业登录 admin/admin123）' -ForegroundColor Green
Read-Host '按回车关闭'
