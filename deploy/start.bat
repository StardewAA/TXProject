@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title TXGY Smart Platform - One Key Start

REM ============================================================
REM  TXGY Smart Platform - One Key Start
REM  Usage:
REM    start.bat            Normal start (reuse existing images, fastest)
REM    start.bat build      Rebuild images and start (after code changes)
REM  Note:
REM    Infrastructure (mysql/redis/nacos) is force-recreated on every
REM    start, because a nacos container that exited abnormally can lose
REM    its network endpoint when simply restarted, which causes
REM    UnknownHostException on all other services.
REM ============================================================

echo.
echo ============================================
echo   TXGY Smart Platform - One Key Start
echo ============================================
echo.

REM ---------- [1/5] Ensure Docker engine is running ----------
docker info >nul 2>&1
if %errorlevel%==0 (
    echo [1/5] Docker engine is ready.
    goto step2
)

echo [1/5] Docker engine not running, starting Docker Desktop...
set "DOCKER_DESKTOP="
if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" set "DOCKER_DESKTOP=C:\Program Files\Docker\Docker\Docker Desktop.exe"
if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" set "DOCKER_DESKTOP=%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
if not defined DOCKER_DESKTOP (
    echo ERROR: Docker Desktop not found. Please start Docker manually.
    pause
    exit /b 1
)
start "" "!DOCKER_DESKTOP!"
set /a TRIES=0

:wait_docker
ping -n 4 127.0.0.1 >nul
docker info >nul 2>&1
if %errorlevel%==0 (
    echo [1/5] Docker engine is ready.
    goto step2
)
set /a TRIES+=1
if %TRIES% lss 80 goto wait_docker
echo ERROR: Docker engine not ready within 240s. Aborting.
pause
exit /b 1

REM ---------- [2/5] Switch to deploy directory ----------
:step2
if exist "C:\txgy\deploy\docker-compose.yml" (
    cd /d C:\txgy\deploy
    echo [2/5] Using project directory: C:\txgy\deploy
) else (
    cd /d "%~dp0"
    echo [2/5] Using project directory: %~dp0
)

REM ---------- [3/5] Start infrastructure cleanly ----------
echo [3/5] Starting infrastructure (mysql / redis / nacos)...
docker compose up -d --force-recreate mysql redis nacos
if %errorlevel% neq 0 (
    echo ERROR: Failed to start infrastructure. Check: docker compose ps
    pause
    exit /b 1
)
echo       Waiting 20s for infrastructure to initialize...
ping -n 21 127.0.0.1 >nul

REM ---------- [4/5] Restart business services, then start all ----------
REM Business containers keep connections/clients bound to the OLD ip of
REM mysql / redis / nacos after those are recreated, which causes
REM RedisException / UnknownHost errors. Restart them so they re-resolve
REM the new addresses.
echo [4/5] Restarting business services to refresh connections...
docker compose restart gateway system infra bpm report erp iot iot-gateway mes wms im ai hrm fms frontend

if /i "%~1"=="build" (
    echo       Building images and starting all services ^(this may take several minutes^)...
    docker compose up -d --build
) else (
    echo       Starting all services...
    docker compose up -d
)
if %errorlevel% neq 0 (
    echo ERROR: docker compose up failed.
    pause
    exit /b 1
)

REM ---------- [5/5] Wait for gateway healthy ----------
echo [5/5] Waiting for core services to become healthy (up to 300s)...
set /a WAITED=0

:wait_health
ping -n 11 127.0.0.1 >nul
set /a WAITED+=10
set "GW_OK="
for /f %%i in ('docker ps --filter "name=deploy-gateway-1" --filter "health=healthy" -q') do set "GW_OK=%%i"
if defined GW_OK goto healthy
if %WAITED% lss 300 goto wait_health
echo WARN: Gateway not healthy within 300s.
echo       Check status : docker ps
echo       Check logs   : docker logs --tail 100 deploy-gateway-1
pause
exit /b 1

:healthy
echo       Gateway is healthy.
echo.
echo ============================================
echo   All services started successfully!
echo.
echo   Frontend : http://localhost:5173
echo   Gateway  : http://localhost:48080
echo   Nacos    : http://localhost:8848/nacos
echo.
echo   Tip: browser hard refresh with Ctrl+F5
echo ============================================
echo.
echo Press any key to close this window (services keep running)...
pause >nul
exit /b 0
