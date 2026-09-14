$log = 'D:\txgy\deploy\build-mes-jar.log'
$err = 'D:\txgy\deploy\build-mes-jar-err.log'
if (Test-Path $log) { Remove-Item $log -Force }
if (Test-Path $err) { Remove-Item $err -Force }
$jdk = 'D:\Apps\DevTools\jdk-25'
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
$env:MAVEN_OPTS = '-Xmx4096m -XX:MaxMetaspaceSize=1024m'
Write-Output ("jdk exists: " + (Test-Path $jdk))
Start-Process -FilePath 'mvn.cmd' -ArgumentList 'install','-Dmaven.test.skip=true' -WorkingDirectory 'D:\txgy' -RedirectStandardOutput $log -RedirectStandardError $err -WindowStyle Hidden
Write-Output 'BUILD_STARTED'
