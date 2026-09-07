@echo off
rem ============================================================
rem bidding-server 本地启动（与其他服务配置方式一致：仓库 application-local.yaml + 运行 env 覆盖）
rem 对照：deploy/docker-compose.yml x-backend-service（容器 env）↔ 本脚本（本地 env），仅地址/端口不同：
rem   - nacos namespace：容器置空(=public)；本地同置空(=public)，与 system/infra/gateway 同一命名空间
rem   - nacos/db/redis：容器用 docker 网络名；本地用 127.0.0.1 映射端口（等价可达）
rem   - SERVER_PORT：容器内统一 48080；本地用 48110（本机 48080 被网关占用；联调口径 48110）
rem 前置：MySQL/Redis/Nacos 已映射在 127.0.0.1（3306/6379/8848）；JDK 25；先 mvn package -DskipTests
rem ============================================================
set JAVA=D:Javajdk-25injava.exe
if not exist "%JAVA%" set JAVA=java
set SPRING_PROFILES_ACTIVE=local
set SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE=
set SPRING_CLOUD_NACOS_CONFIG_NAMESPACE=
set SERVER_PORT=48110
set SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=127.0.0.1:8848
rem openfeign 直连宿主映射端口（方案A；容器映射见 txgycloud/deploy 同目录说明与 override 文件）
set SPRING_CLOUD_OPENFEIGN_CLIENT_CONFIG_SYSTEM-SERVER_URL=http://127.0.0.1:48087
set SPRING_CLOUD_OPENFEIGN_CLIENT_CONFIG_INFRA-SERVER_URL=http://127.0.0.1:48088
set SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=127.0.0.1:8848
"%JAVA%" -Xms512m -Xmx1536m -jar target	xgy-module-bidding-server.jar
