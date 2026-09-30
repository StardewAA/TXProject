@echo off
rem ============================================================================
rem  run-local.cmd — bidding-server 纯启动（不打包、不杀进程；等价运行参数）
rem  依据：docs/开发联调与运维/部署与启动方式.md §5 B-1 / B-2（本机专用，gitignore，不入库）
rem  用法：run-local.cmd           # 前置条件：target\txgy-module-bidding-server.jar 已构建
rem  注意：本脚本不杀旧进程，48110 已被占用时启动会失败（请先用 bidding-dev.cmd）
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

rem ---- 1. 校验 jar 已构建 ----
if not exist "%~dp0target\txgy-module-bidding-server.jar" (
    echo [ERROR] 未找到 target\txgy-module-bidding-server.jar，请先 mvn package 或运行 bidding-dev.cmd。
    exit /b 1
)

rem ---- 1.5 Nacos 配置守卫（防止命名空间被改回 dev / override 被 git 回滚，导致网关找不到 bidding-server）----
rem   守卫脚本：deploy/guard-nacos-config.ps1（检测 yaml/override/容器三处命名空间 + skip-worktree 标记）
rem   跳过本次检查：set SKIP_NACOS_GUARD=1
if not defined SKIP_NACOS_GUARD (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\..\deploy\guard-nacos-config.ps1"
    if errorlevel 1 (
        echo [WARN] 检测到 Nacos 配置漂移（多为 pull/checkout 覆盖了本地 public），尝试自动修复...
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\..\deploy\guard-nacos-config.ps1" -Fix
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\..\deploy\guard-nacos-config.ps1"
        if errorlevel 1 (
            echo [ERROR] 自动修复后仍未通过，请人工检查。
            echo         手动修复: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\..\deploy\guard-nacos-config.ps1" -Fix
            echo         临时跳过: set SKIP_NACOS_GUARD=1
            exit /b 1
        )
        echo [OK] 已自动修复为 public（本机口径；远端 dev 不受影响）
    )
)

rem ---- 2. 启动（参数与 IDEA 配置等价，见部署文档 §5 B-2）----
echo 启动 bidding-server（port=48110, profiles=local, namespace=public）...
if not exist "%USERPROFILE%\logs" mkdir "%USERPROFILE%\logs"
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0target\txgy-module-bidding-server.jar" ^
    --server.port=48110 ^
    --spring.profiles.active=local ^
    --spring.cloud.nacos.discovery.namespace= ^
    --spring.cloud.nacos.config.namespace= ^
    --spring.cloud.nacos.discovery.ip=host.docker.internal ^
    --spring.cloud.openfeign.client.config.system-server.url=http://127.0.0.1:48087 ^
    --spring.cloud.openfeign.client.config.infra-server.url=http://127.0.0.1:48088