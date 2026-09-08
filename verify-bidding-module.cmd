@echo off
REM ========================================
REM 招投标模块合并后验证脚本
REM ========================================

echo ========================================
echo 招投标模块（txgy-module-bidding）验证
echo ========================================
echo.

echo [1/5] 检查模块目录结构...
if exist "txgy-module-bidding\txgy-module-bidding-api" (
    echo   ✓ txgy-module-bidding-api 存在
) else (
    echo   ✗ txgy-module-bidding-api 不存在
)

if exist "txgy-module-bidding\txgy-module-bidding-server" (
    echo   ✓ txgy-module-bidding-server 存在
) else (
    echo   ✗ txgy-module-bidding-server 不存在
)
echo.

echo [2/5] 检查 pom.xml 配置...
findstr /C:"txgy-module-bidding" pom.xml >nul 2>&1
if %errorlevel% == 0 (
    echo   ✓ 主项目 pom.xml 已包含 bidding 模块
) else (
    echo   ✗ 主项目 pom.xml 未包含 bidding 模块
)
echo.

echo [3/5] 检查文档...
if exist "docs\bidding" (
    echo   ✓ bidding 文档目录存在
) else (
    echo   ✗ bidding 文档目录不存在
)
echo.

echo [4/5] 检查 SQL 脚本...
set SQL_COUNT=0
if exist "sql\2026-09-03-notice-menu.sql" set /a SQL_COUNT+=1
if exist "sql\2026-09-08-notice-external-menu.sql" set /a SQL_COUNT+=1
if exist "sql\2026-09-09-subject-menu.sql" set /a SQL_COUNT+=1
if exist "sql\2026-09-11-notice-external-manage-menu.sql" set /a SQL_COUNT+=1
echo   ✓ 找到 %SQL_COUNT%/4 个菜单 SQL 脚本
echo.

echo [5/5] 编译测试...
echo   正在编译 bidding 模块，请稍候...
cd txgy-module-bidding
call mvn clean compile -DskipTests -q >nul 2>&1
if %errorlevel% == 0 (
    echo   ✓ 编译成功
) else (
    echo   ✗ 编译失败，请检查依赖
)
cd ..
echo.

echo ========================================
echo 后续操作提示：
echo ========================================
echo.
echo 1. 配置网关路由（txgy-gateway/src/main/resources/application.yaml）
echo    添加 bidding-server 路由配置
echo.
echo 2. 执行 SQL 脚本初始化菜单：
echo    mysql -uroot -p123456 ruoyi-vue-pro ^< sql\2026-09-03-notice-menu.sql
echo    mysql -uroot -p123456 ruoyi-vue-pro ^< sql\2026-09-08-notice-external-menu.sql
echo    mysql -uroot -p123456 ruoyi-vue-pro ^< sql\2026-09-09-subject-menu.sql
echo    mysql -uroot -p123456 ruoyi-vue-pro ^< sql\2026-09-11-notice-external-manage-menu.sql
echo.
echo 3. 如果菜单有乱码，执行修复脚本：
echo    mysql -uroot -p123456 ruoyi-vue-pro ^< sql\fix-bidding-menu-encoding.sql
echo.
echo 4. 启动 bidding-server 服务：
echo    cd txgy-module-bidding\txgy-module-bidding-server
echo    run-local.cmd
echo.
echo 详细说明请查看：docs\BIDDING_MODULE_MIGRATION.md
echo ========================================

pause
