@echo off
setlocal enabledelayedexpansion
REM 本文件为 GBK 编码: 请勿以 UTF-8 保存, 否则中文注释会被 cmd 拆碎执行
chcp 936 >nul
title TXGY One-Key Start

REM ================================================================
REM  TXGY 一键启动脚本 (本机 E:\txgycloud-master)
REM
REM  用法:
REM    start-all.bat            后端(Docker) + 管理端前端(5173) + 打开浏览器
REM    start-all.bat build      同上, 但重建应用镜像(改了 Java / bid-agent 代码后)
REM    start-all.bat backend    只启动后端 Docker 服务
REM    start-all.bat portal     后端 + 管理端前端 + 门户前端(5180)
REM    start-all.bat stop       停止前后端(容器保留, 数据不丢)
REM    start-all.bat status     查看运行状态
REM
REM  后端拓扑(与本机当前部署一致):
REM    docker-compose.yml           仅 mysql(3307) / redis(6380) / nacos(8848)
REM    models/docker-compose.yml    qdrant(6333/6334)
REM    docker-compose.app.yml       gateway(48080) / system(48081) / infra(48082) / bidding(48110)
REM    docker-compose.bidagent.yml  bid-agent(8199) / mock-llm(8100)
REM
REM  注意:
REM    1. 不要在主 docker-compose.yml 上 up 全部服务, 它的 gateway 会与
REM       docker-compose.app.yml 的 gateway 抢占 48080 端口。
REM    2. deploy/start.bat 是另一台机器(C:\txgy)的全量拓扑脚本, 本机不用它。
REM    3. 脚本假设项目路径不含空格(当前为 E:\txgycloud-master)。
REM    4. LLM key 等敏感配置在 deploy/.env, 不进仓库。
REM ================================================================

set "ROOT=%~dp0"
set "DEPLOY=%ROOT%deploy"
set "WEB=%ROOT%txgy-ui-admin-vben"
set "PORTAL=%ROOT%txgy-cloud-portal"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=start"

if /i "%MODE%"=="stop"   goto :do_stop
if /i "%MODE%"=="status" goto :do_status
if /i "%~2"=="nopause" set "NOPAUSE=1"

set "BUILD="
if /i "%MODE%"=="build" set "BUILD=--build"

echo.
echo ================================================
echo    TXGY 一键启动 [%MODE%]
echo ================================================
echo.

REM ---------- [1/7] 确保 Docker 引擎 ----------
call :ensure_docker
if errorlevel 1 goto :fail

pushd "%DEPLOY%"
if errorlevel 1 goto :fail

REM ---------- [2/7] 基础设施 mysql / redis / nacos ----------
echo [2/7] 启动基础设施 mysql / redis / nacos ...
docker compose -f docker-compose.yml up -d mysql redis nacos
if errorlevel 1 goto :fail
call :wait_healthy docker-compose.yml mysql deploy-mysql-1 180 MySQL
call :wait_healthy docker-compose.yml redis deploy-redis-1 120 Redis
call :wait_healthy docker-compose.yml nacos deploy-nacos-1 120 Nacos

REM ---------- [3/7] 向量库 qdrant ----------
echo [3/7] 启动向量库 qdrant ...
docker compose -f models/docker-compose.yml up -d qdrant
if errorlevel 1 goto :fail

REM ---------- [4/7] 应用服务 gateway / system / infra / bidding ----------
echo [4/7] 启动应用服务 gateway / system / infra / bidding ...
docker compose -f docker-compose.app.yml up -d %BUILD%
if errorlevel 1 goto :fail

REM ---------- [5/7] 标书智能体 bid-agent / mock-llm ----------
if defined BUILD (
    if exist "%ROOT%services\bid-agent-service\package.json" (
        if exist "%ROOT%services\bid-agent-service\node_modules" (
            echo [5/7] build 模式: 先编译 bid-agent-service ^(tsc 到 dist^) ...
            pushd "%ROOT%services\bid-agent-service"
            call npm run build
            popd
        )
    )
)
echo [5/7] 启动标书智能体 bid-agent / mock-llm ...
docker compose -f docker-compose.bidagent.yml up -d %BUILD%
if errorlevel 1 goto :fail

REM ---------- [6/7] 等待网关健康 ----------
echo [6/7] 等待网关就绪(最长 300s)...
call :wait_healthy docker-compose.app.yml gateway txgy-gateway 300 Gateway

popd

REM ---------- [7/7] 前端 ----------
if /i "%MODE%"=="backend" goto :summary

echo [7/7] 启动前端 ...
where pnpm >nul 2>&1
if errorlevel 1 (
    echo     [WARN] 未找到 pnpm, 跳过前端。可执行: npm install -g pnpm
    goto :summary
)
if not exist "%WEB%\node_modules" (
    echo     首次运行, 安装前端依赖, 耗时较长...
    pushd "%WEB%"
    call pnpm install
    popd
    if not exist "%WEB%\node_modules" goto :fail
)
if /i "%MODE%"=="portal" (
    if not exist "%PORTAL%\node_modules" (
        echo     首次运行, 安装门户依赖...
        pushd "%PORTAL%"
        call pnpm install
        popd
    )
)

start "TXGY-Admin-Frontend 5173" cmd /k "pushd %WEB% && pnpm dev:antd"
if /i "%MODE%"=="portal" start "TXGY-Portal 5180" cmd /k "pushd %PORTAL% && pnpm dev"

echo     等待前端 5173 就绪(最长 120s)...
set /a _FT=0
:wait_front
curl -s -o nul --max-time 2 http://localhost:5173
if not errorlevel 1 goto :front_ok
set /a _FT+=1
if !_FT! geq 60 goto :front_timeout
ping -n 3 127.0.0.1 >nul
goto :wait_front
:front_ok
echo     [OK] 前端已就绪
start "" http://localhost:5173
goto :summary
:front_timeout
echo     [WARN] 前端 120s 内未就绪, 请查看前端窗口日志

:summary
echo.
echo ================================================
echo    启动完成!
echo     管理端前端 : http://localhost:5173  (admin / admin123)
if /i "%MODE%"=="portal" echo     门户前端   : http://localhost:5180
echo     网关       : http://localhost:48080
echo     Nacos      : http://localhost:8848/nacos
echo     bid-agent  : http://localhost:8199
echo     Qdrant     : http://localhost:6333
echo ================================================
echo.
echo 提示: 页面异常时浏览器硬刷新 Ctrl+F5; 查看状态: start-all.bat status
if not defined NOPAUSE pause
exit /b 0

REM ================================================================
REM  子程序
REM ================================================================

:ensure_docker
docker info >nul 2>&1
if not errorlevel 1 exit /b 0
echo [1/7] Docker 未运行, 尝试启动 Docker Desktop ...
set "DD="
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" set "DD=C:\Program Files\Docker\Docker\Docker Desktop.exe"
if not defined DD if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" set "DD=%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
if not defined DD (
    echo [ERROR] 未找到 Docker Desktop, 请手动启动 Docker。
    exit /b 1
)
start "" "!DD!"
set /a _DT=0
:wait_dd
ping -n 4 127.0.0.1 >nul
docker info >nul 2>&1
if not errorlevel 1 (
    echo      Docker 引擎就绪。
    exit /b 0
)
set /a _DT+=3
if !_DT! lss 240 goto :wait_dd
echo [ERROR] Docker 引擎 240s 内未就绪。
exit /b 1

REM %1=compose 文件 %2=服务名 %3=兜底容器名 %4=超时秒 %5=显示名
:wait_healthy
set "CID="
for /f %%i in ('docker compose -f "%1" ps -q %2 2^>nul') do set "CID=%%i"
if not defined CID set "CID=%3"
if not defined CID (
    echo     [WARN] %5 容器未创建, 跳过等待
    exit /b 0
)
set /a _WT=0
:wait_h_loop
set "HST="
for /f %%i in ('docker inspect -f "{{.State.Health.Status}}" !CID! 2^>nul') do set "HST=%%i"
if "!HST!"=="healthy" (
    echo     [OK] %5 已就绪
    exit /b 0
)
set /a _WT+=5
if !_WT! geq %4 (
    echo     [WARN] %5 在 %4s 内未就绪, 继续后续步骤
    exit /b 0
)
ping -n 6 127.0.0.1 >nul
goto :wait_h_loop

:fail
echo.
echo [ERROR] 启动失败, 请查看上方日志。
echo         排查: cd deploy ^&^& docker compose -f docker-compose.yml ps
if not defined NOPAUSE pause
exit /b 1

:do_stop
echo.
echo ================================================
echo    TXGY 一键停止
echo ================================================
echo.
echo [1/2] 停止前端 dev server (5173 / 5180) ...
for %%p in (5173 5180) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%p " ^| findstr LISTENING') do (
        taskkill /f /pid %%a >nul 2>&1
    )
)
echo [2/2] 停止 Docker 服务(容器保留, 数据不丢) ...
docker info >nul 2>&1
if errorlevel 1 (
    echo     Docker 未运行, 跳过。
    goto :stop_done
)
pushd "%DEPLOY%"
docker compose -f docker-compose.bidagent.yml stop
docker compose -f docker-compose.app.yml stop
docker compose -f models/docker-compose.yml stop
docker compose -f docker-compose.yml stop
popd
:stop_done
echo.
echo 已停止。再次启动: start-all.bat
if not defined NOPAUSE pause
exit /b 0

:do_status
echo.
echo ================================================
echo    TXGY 运行状态
echo ================================================
echo.
echo ---- 运行中的容器 ----
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>nul
echo.
echo ---- 端口探测 ----
curl -s -o nul -w "gateway   48080 -> HTTP %%{http_code}\n" --max-time 3 http://localhost:48080/actuator/health 2>nul
curl -s -o nul -w "frontend  5173 -> HTTP %%{http_code}\n" --max-time 3 http://localhost:5173 2>nul
curl -s -o nul -w "bid-agent 8199 -> HTTP %%{http_code}\n" --max-time 3 http://localhost:8199 2>nul
echo.
if not defined NOPAUSE pause
exit /b 0
