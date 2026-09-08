# 构建 pay-server jar（Maven 容器 + txgy-m2repo 依赖缓存卷）
$log = 'd:\code\tianxinguanye\txgycloud\build-pay-jar.log'
"build started $(Get-Date)" | Out-File $log -Encoding utf8
docker run --rm `
  -v D:\code\tianxinguanye\txgycloud:/workspace `
  -v txgy-m2repo:/root/.m2 `
  -w /workspace `
  maven:3.9-eclipse-temurin-25 `
  sh -c "mvn -B -Dmaven.test.skip=true -s deploy/backend/settings-test.xml -pl txgy-module-pay/txgy-module-pay-server -am package" `
  2>&1 | Out-File $log -Append -Encoding utf8
"build exit code: $LASTEXITCODE $(Get-Date)" | Out-File $log -Append -Encoding utf8
Write-Output ("EXIT=" + $LASTEXITCODE)
