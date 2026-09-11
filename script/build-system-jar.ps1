# 构建 system-server + im-server jar（Maven 容器 + txgy-m2repo 依赖缓存卷）
# system：① 好友搜索忽略数据权限 ② UserSimpleRespVO 增加 username 字段
# im：M1 好友列表增强（管理端 /get、/delete 接口）
$log = 'd:\code\tianxinguanye\txgycloud\build-system-jar.log'
"build started $(Get-Date)" | Out-File $log -Encoding utf8
docker run --rm `
  -v D:\code\tianxinguanye\txgycloud:/workspace `
  -v txgy-m2repo:/root/.m2 `
  -w /workspace `
  -e MAVEN_OPTS="-Xmx1536m -XX:MaxMetaspaceSize=512m" `
  maven:3.9-eclipse-temurin-25 `
  sh -c "mvn -B -Dmaven.test.skip=true -s deploy/backend/settings-test.xml -pl txgy-module-system/txgy-module-system-server,txgy-module-im/txgy-module-im-server -am package" `
  2>&1 | Out-File $log -Append -Encoding utf8
"build exit code: $LASTEXITCODE $(Get-Date)" | Out-File $log -Append -Encoding utf8
Write-Output ("EXIT=" + $LASTEXITCODE)
