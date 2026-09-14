#!/usr/bin/env bash
# txgy 模块逐个启动/验证/停止脚本
# 用法: bash script/docker/verify-modules.sh [service1 service2 ...]
# 默认按顺序验证 deploy/docker-compose.yml 中的全部后端模块。
# 前提: mysql / redis / nacos 已在 deploy 目录下启动且健康。
set -u
cd "$(dirname "$0")/../.." || exit 1
cd deploy || exit 1

MODULES=("$@")
if [ "${#MODULES[@]}" -eq 0 ]; then
  MODULES=(gateway system infra member bpm pay report mp product trade promotion statistics crm erp iot iot-gateway mes wms im ai hrm fms)
fi

echo "### 开始时间: $(date '+%F %T')"

for svc in "${MODULES[@]}"; do
  cname="txgy-$svc"
  echo "============================================================"
  echo "### [$svc] 启动 ($(date '+%F %T'))"
  if ! docker compose up -d --no-deps "$svc" >> /tmp/txgy-module-loop.log 2>&1; then
    echo "[$svc] compose up 失败"
    docker logs --tail 20 "$cname" 2>&1 | tail -20
    continue
  fi
  status=""
  waited=0
  if [ "$svc" = "iot-gateway" ]; then
    # iot-gateway 无 actuator 健康端点，改用日志判断
    for i in $(seq 1 36); do
      if docker logs "$cname" 2>&1 | grep -q "Started IotGatewayServerApplication"; then status="healthy"; break; fi
      waited=$((waited+10)); sleep 10
    done
  else
    for i in $(seq 1 60); do
      status=$(docker inspect -f '{{.State.Health.Status}}' "$cname" 2>/dev/null)
      if [ "$status" = "healthy" ]; then break; fi
      if [ "$status" = "unhealthy" ]; then break; fi
      waited=$((waited+10)); sleep 10
    done
  fi
  if [ "$status" = "healthy" ]; then
    echo "[$svc] 启动成功 healthy (等待约 $waited s)"
    # 关键日志摘要
    docker logs --tail 8 "$cname" 2>&1 | grep -E "Started .*Application|Tomcat started|Netty started|registered|ERROR" | tail -5
  else
    echo "[$svc] 未达到 healthy (状态=$status, 等待约 $waited s)"
    docker logs --tail 40 "$cname" 2>&1 | tail -40
  fi
  echo "[$svc] 停止以释放内存"
  docker compose stop "$svc" > /dev/null 2>&1
done

echo "### 结束时间: $(date '+%F %T')"
echo "### 全部模块验证完成。当前容器状态:"
docker compose ps --format "{{.Name}}\t{{.Status}}"
