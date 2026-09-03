@echo off
rem One-click build for txgy-module-bidding (standalone).
rem Uses absolute JDK/Maven paths, no reliance on PATH/JAVA_HOME.
rem Usage:  .\build.cmd   (PowerShell/CMD)  or double-click in Explorer.
setlocal
set JAVA_HOME=D:\Java\jdk-25
set "PATH=%JAVA_HOME%\bin;D:\maven\apache-maven-3.9.11\bin;%PATH%"
cd /d "%~dp0"
echo [1/2] JAVA_HOME=%JAVA_HOME%
call "D:\maven\apache-maven-3.9.11\bin\mvn.cmd" clean install -DskipTests %*
set RC=%ERRORLEVEL%
echo.
echo [2/2] BUILD FINISHED, exit code: %RC%  ^(0 = SUCCESS^)
rem Keep window open when double-clicked from Explorer (cmd /c); no-op otherwise.
echo %cmdcmdline% | findstr /i "cmd.exe /c" >nul
if not errorlevel 1 pause
endlocal & exit /b %RC%
