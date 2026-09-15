#!/usr/bin/env bash
# 本地逐个验证 txgy 各模块：java -jar 启动 -> 健康检查 -> 停止
# 依赖：Docker 中 mysql(3306)/redis(6379)/nacos(8848) 已运行且数据库已初始化
set -u
JAVA="/c/Users/离不开牛奶/.jdks/graalvm-jdk-25/bin/java"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JARS="$ROOT/deploy/jars"
OUT=/tmp/txgy-local-verify.log
: > "$OUT"

# svc|jar-name|port(0=无http健康检查)
MODULES=(
  "gateway|gateway|48080"
  "system|system|48081"
  "infra|infra|48082"
  "bpm|bpm|48083"
  "report|report|48084"
  "pay|pay|48085"
  "mp|mp|48086"
  "member|member|48087"
  "erp|erp|48088"
  "crm|crm|48089"
  "ai|ai|48090"
  "iot|iot|48091"
  "mes|mes|48091"
  "hrm|hrm|48092"
  "wms|wms|48092"
  "fms|fms|48093"
  "im|im|48093"
  "product|product|48100"
  "promotion|promotion|48101"
  "trade|trade|48102"
  "statistics|statistics|48103"
  "iot-gateway|iot-gateway|0"
)

echo "开始: $(date '+%F %T')" >> "$OUT"
for entry in "${MODULES[@]}"; do
  svc="${entry%%|*}"; rest="${entry#*|}"; jn="${rest%%|*}"; port="${rest##*|}"
  jar="$JARS/$jn/app.jar"
  echo "============================================================" >> "$OUT"
  echo "### [$svc] 本地启动验证 $(date '+%F %T')" >> "$OUT"
  if [ ! -f "$jar" ]; then echo "[$svc] 缺少 jar: $jar" >> "$OUT"; continue; fi

  LOG="/tmp/verify-$svc.log"
  "$JAVA" -Xmx1024m -jar "$jar" > "$LOG" 2>&1 &
  PID=$!
  ok=0
  for i in $(seq 1 48); do  # 最多等 240s
    if ! kill -0 $PID 2>/dev/null; then break; fi
    if grep -q "Started .*Application in" "$LOG" 2>/dev/null; then ok=1; break; fi
    if [ "$port" != "0" ] && [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$port/actuator/health" 2>/dev/null)" = "200" ]; then ok=1; break; fi
    sleep 5
  done
  if [ "$ok" = "1" ]; then
    started=$(grep -m1 "Started .*Application in" "$LOG" | sed 's/^[0-9:.\-]* *//')
    echo "[$svc] 启动成功: $started" >> "$OUT"
  else
    echo "[$svc] 启动失败/超时 (pid=$PID alive=$(kill -0 $PID 2>/dev/null && echo yes || echo no))" >> "$OUT"
    tail -25 "$LOG" >> "$OUT"
  fi
  kill $PID 2>/dev/null; wait $PID 2>/dev/null
  sleep 3
done
echo "结束: $(date '+%F %T')" >> "$OUT"
