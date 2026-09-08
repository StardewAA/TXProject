@echo off
setlocal
cd /d %~dp0
rem 投标模块本地服务 一键重启（根治启动踩坑：jar锁/错java/namespace/就绪等待）。用法：bidding-dev.cmd [-s 跳过打包]
set "JH=D:\Java\jdk-25"
if not exist "%JH%\bin\java.exe" set "JH=%JAVA_HOME%"
if not exist "%JH%\bin\java.exe" (echo 未找到 JDK25，请设置 BIDDING_JAVA 或 JAVA_HOME & exit /b 1)
set "PORT=48110"
echo [1/4] 停止旧实例 port %PORT% ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do taskkill /F /PID %%p >nul 2>&1
timeout /t 2 /nobreak >nul
if /I not "%~1"=="-s" (
  echo [2/4] 打包 mvn package -DskipTests ...
  call mvn -q package -DskipTests > target\package-dev.log 2>&1
  if errorlevel 1 (echo 打包失败，见 target\package-dev.log & exit /b 1)
) else echo [2/4] 跳过打包
echo [3/4] 启动 JDK25 + local + public 命名空间 + lazy ...
set "SPRING_PROFILES_ACTIVE=local"
set "SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE="
start "bidding-48110" /b "%JH%\bin\java.exe" -jar target\txgy-module-bidding-server.jar --spring.cloud.nacos.discovery.ip=192.168.236.203 --spring.main.lazy-initialization=true > target\bidding-dev.log 2>&1
echo [4/4] 等待端口 %PORT% 就绪 ...
:waitloop
timeout /t 2 /nobreak >nul
netstat -ano | findstr ":%PORT% " | findstr LISTENING >nul
if errorlevel 1 goto waitloop
echo READY on %PORT% （日志 target\bidding-dev.log）
endlocal
