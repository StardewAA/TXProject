@echo off
setlocal
chcp 65001 >nul 2>&1

:: 优先使用 ASCII 目录联接（若存在）；否则回退到中文原路径
set "JDK=D:\proj\.tools\jdk25"
set "MVN=D:\proj\.tools\maven"
if not exist "%JDK%\bin\java.exe" set "JDK=D:\招投标项目\.tools\jdk25"
if not exist "%MVN%\bin\mvn.cmd" set "MVN=D:\招投标项目\.tools\maven"

set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%MVN%\bin;%PATH%"

set "REPO=D:\txgy\txgycloud.git"
if not exist "%REPO%\pom.xml" set "REPO=D:\天信管业防腐保温智慧平台\txgycloud.git"

cd /d "%REPO%"

echo [1/2] Building txgy-module-bidding/txgy-module-bidding-server (and dependencies) ...
call mvn -pl txgy-module-bidding/txgy-module-bidding-server -am clean install -DskipTests -Dmaven.test.skip=true
if errorlevel 1 (
  echo [ERROR] Build failed. See log above.
  pause
  exit /b 1
)

echo [2/2] Done.
pause
