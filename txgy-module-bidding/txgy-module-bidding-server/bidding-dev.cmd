@echo off
rem ============================================================================
rem  bidding-dev.cmd — bidding-server 本地一键启动/重启（宿主机 JVM，端口 48110）
rem  依据：docs/开发联调与运维/部署与启动方式.md §5 B-1 / B-2（本机专用，gitignore，不入库）
rem  用法：bidding-dev.cmd          # 杀旧进程 → mvn package → 启动 → 等 48110 就绪
rem        bidding-dev.cmd -s       # 跳过打包（代码没变时更快）
rem  日志：%USERPROFILE%\logs\bidding-server.log（Spring 主日志）
rem        target\bidding-dev.log（本脚本 stdout）
rem ============================================================================
setlocal
cd /d "%~dp0"

rem ---- 0. 工具链：JDK25 优先，回退 JAVA_HOME（部署文档 §3）----
if exist "D:\Java\jdk-25\bin\java.exe" (
    set "JAVA_HOME=D:\Java\jdk-25"
) else if not defined JAVA_HOME (
    echo [ERROR] 未找到 D:\Java\jdk-25，且 JAVA_HOME 未设置，无法启动。请先安装 JDK25 或设置 JAVA_HOME。
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_HOME=D:\maven\apache-maven-3.9.11"

rem ---- 1. 杀旧进程（Windows 下运行中的 jar 会锁文件，必须先停）----
echo [1/4] 停止占用 48110 的旧进程...
for /f "tokens=5" %%p in ('netstat -ano -p tcp ^| findstr ":48110" ^| findstr "LISTENING"') do (
    echo   停止 PID %%p ...
    taskkill /F /PID %%p >nul 2>&1
)
timeout /t 2 /nobreak >nul

rem ---- 2. 打包（-s 跳过）----
if /i not "%~1"=="-s" (
    echo [2/4] mvn package -DskipTests ...
    call "%MAVEN_HOME%\bin\mvn.cmd" package -DskipTests
    if errorlevel 1 (
        echo [ERROR] mvn package 失败，终止。
        exit /b 1
    )
) else (
    echo [2/4] 跳过打包（-s）
)

rem ---- 3. 启动（参数与 IDEA 配置等价，见部署文档 §5 B-2）----
echo [3/4] 启动 bidding-server（port=48110, profiles=local, namespace=public）...
if not exist "%USERPROFILE%\logs" mkdir "%USERPROFILE%\logs"
start "bidding-server" "%JAVA_HOME%\bin\java.exe" ^
    -jar "%~dp0target\txgy-module-bidding-server.jar" ^
    --server.port=48110 ^
    --spring.profiles.active=local ^
    --spring.cloud.nacos.discovery.namespace= ^
    --spring.cloud.nacos.config.namespace= ^
    --spring.cloud.nacos.discovery.ip=host.docker.internal ^
    --spring.cloud.openfeign.client.config.system-server.url=http://127.0.0.1:48087 ^
    --spring.cloud.openfeign.client.config.infra-server.url=http://127.0.0.1:48088
echo   日志：%USERPROFILE%\logs\bidding-server.log

rem ---- 4. 等 48110 就绪（最多 90 秒）----
echo [4/4] 等待 48110 就绪...
set /a N=0
:wait_loop
set /a N+=1
timeout /t 2 /nobreak >nul
netstat -ano -p tcp | findstr ":48110" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [OK] bidding-server 已就绪：http://127.0.0.1:48110/actuator/health
    exit /b 0
)
if %N% GEQ 45 (
    echo [WARN] 90 秒内未监测到 48110 监听，请查看 %USERPROFILE%\logs\bidding-server.log
    exit /b 2
)
goto :wait_loop