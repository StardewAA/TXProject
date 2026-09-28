@echo off
rem local launcher for vben web-antd dev server (前台门户 + 管理后台, port 5666)
setlocal
set "NODE=D:\招投标项目\.tools\node"
set "REPO=D:\天信管业防腐保温智慧平台\txgycloud.git"
set "PATH=%NODE%;%PATH%"
cd /d "%REPO%\txgy-ui-admin-vben"
call "%NODE%\pnpm.cmd" dev:antd > "%REPO%\.tools\setup\web-antd.log" 2>&1
endlocal