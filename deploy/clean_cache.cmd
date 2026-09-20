@echo off
echo Cleaning pip cache...
del /f /s /q "%LOCALAPPDATA%\pip\cache\*.*" >nul 2>&1
for /d %%i in ("%LOCALAPPDATA%\pip\cache\*") do rd /s /q "%%i" >nul 2>&1
echo Cleaning npm cache...
del /f /s /q "%LOCALAPPDATA%\npm-cache\*.*" >nul 2>&1
for /d %%i in ("%LOCALAPPDATA%\npm-cache\*") do rd /s /q "%%i" >nul 2>&1
echo Cleaning user Temp...
del /f /s /q "%LOCALAPPDATA%\Temp\*.*" >nul 2>&1
for /d %%i in ("%LOCALAPPDATA%\Temp\*") do rd /s /q "%%i" >nul 2>&1
echo ALL_DONE
