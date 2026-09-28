@echo off
rem local launcher for infra (port 48088); DB/Redis 走 compose 容器 3307/6380
setlocal
set "JAVA_HOME=D:\天信管业防腐保温智慧平台\.tools\jdk25\jdk-25.0.4.1+1"
set "REPO=D:\天信管业防腐保温智慧平台\txgycloud.git"
set "MODULE=%REPO%\txgy-module-infra\txgy-module-infra-server"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%MODULE%"
"%JAVA_HOME%\bin\java.exe" "-Duser.home=%REPO%\.tools\home" -jar "target\txgy-module-infra-server.jar" --spring.profiles.active=local --server.port=48088 --spring.datasource.dynamic.datasource.master.url="jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true" --spring.datasource.dynamic.datasource.master.username=root --spring.datasource.dynamic.datasource.master.password=Lzt1015070815 --spring.datasource.dynamic.datasource.slave.url="jdbc:mysql://127.0.0.1:3307/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true" --spring.datasource.dynamic.datasource.slave.password=Lzt1015070815 --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6380 > "target\infra-run.log" 2>&1
endlocal