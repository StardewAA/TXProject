$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$imageDirectory = Join-Path $repoRoot 'txgy-mall-web\public\product-images\kedun-real'
$sqlFile = Join-Path $PSScriptRoot '2026-09-17-kedun-realistic-product-images.sql'
$container = 'deploy-mysql-1'

if (-not (Test-Path $imageDirectory)) {
  throw "Image directory not found: $imageDirectory"
}

docker exec $container sh -lc 'rm -rf /var/lib/mysql-files/kedun-real && mkdir -p /var/lib/mysql-files/kedun-real'
docker cp "$imageDirectory\." "${container}:/var/lib/mysql-files/kedun-real/"
Get-Content -Raw $sqlFile | docker exec -i $container mysql -uroot -proot --database=ruoyi-vue-pro

Write-Host 'Kedun realistic product images applied.'
