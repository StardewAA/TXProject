#!/usr/bin/env bash
# 投标模块本地服务 一键构建+重启（根治每次启动踩坑：jar 锁/错 java/namespace 不一致/就绪探测）
# 用法：bash bidding-dev.sh   （或 ./bidding-dev.sh）
set -e
export JAVA_HOME="${JAVA_HOME:-D:/Java/jdk-25}"
cd "$(dirname "$0")"
SKIP=0; LAZY=1; CDS=0; CDS_CREATE=0
for a in "$@"; do case "$a" in -s|--skip-package) SKIP=1 ;; -f|--full) LAZY=0 ;; --cds) CDS=1 ;; --cds-create) CDS=1; CDS_CREATE=1 ;; esac; done
SECONDS=0
PORT="${BIDDING_PORT:-48110}"
JAVA="${BIDDING_JAVA:-D:/Java/jdk-25/bin/java.exe}"
DISCOVERY_IP="${BIDDING_DISCOVERY_IP:-192.168.236.203}"
JAR=target/txgy-module-bidding-server.jar
LOG=target/bidding-dev.log

echo "[1/4] 停止旧实例 (port $PORT) ..."
PID=$(netstat -ano 2>/dev/null | grep ":$PORT " | grep LISTENING | awk '{print $NF}' | head -1)
if [ -n "$PID" ]; then
  echo "   kill $PID"; taskkill //F //PID "$PID" >/dev/null 2>&1 || powershell -NoProfile -Command "Stop-Process -Id $PID -Force" >/dev/null 2>&1 || true
  sleep 3
else echo "   无残留"; fi

if [ "$SKIP" = 0 ]; then
  echo "[2/4] 打包 (mvn package -DskipTests) ..."
  mvn -q package -DskipTests > target/package-dev.log 2>&1 || { echo "打包失败，见 target/package-dev.log"; tail -20 target/package-dev.log; exit 1; }
else
  echo "[2/4] 跳过打包 (-s)"
fi

echo "[3/4] 启动 (JDK25 + profile local + discovery public 命名空间) ..."
LAZY_ARG=""
[ "$LAZY" = 1 ] && LAZY_ARG="--spring.main.lazy-initialization=true"
CDS_ARG=""
if [ "$CDS" = 1 ] && [ -f target/bidding.jsa ]; then CDS_ARG="-XX:SharedArchiveFile=target/bidding.jsa"; fi
if [ "$CDS_CREATE" = 1 ] && [ ! -f target/bidding.jsa ]; then CDS_ARG="-XX:ArchiveClassesAtExit=target/bidding.jsa"; fi
SPRING_PROFILES_ACTIVE=local SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE='' "$JAVA" $CDS_ARG -jar "$JAR" --spring.cloud.nacos.discovery.ip="$DISCOVERY_IP" $LAZY_ARG > "$LOG" 2>&1 &
echo "   pid=$!";

echo "[4/4] 等待端口 $PORT 就绪 ..."
for i in $(seq 1 120); do
  if netstat -ano 2>/dev/null | grep ":$PORT " | grep -q LISTENING; then
    echo "READY after ${SECONDS}s on port $PORT (log=$LOG)"; exit 0;
  fi
  if ! kill -0 $! 2>/dev/null; then echo "进程已退出，日志尾部："; tail -20 "$LOG"; exit 1; fi
  sleep 1;
done
echo "TIMEOUT 120s (log=$LOG)"; tail -10 "$LOG"; exit 1;