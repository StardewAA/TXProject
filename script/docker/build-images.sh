#!/usr/bin/env bash
# 逐个构建 txgy 各模块的 Docker 镜像（薄镜像，基于 deploy/jars 中已打包的 jar）
# 用法: bash script/docker/build-images.sh [service ...]   默认构建全部后端模块
set -u
cd "$(dirname "$0")/../.." || exit 1
cd deploy || exit 1
MODULES=("$@")
if [ "${#MODULES[@]}" -eq 0 ]; then
  MODULES=(gateway system infra member bpm pay report mp product trade promotion statistics crm erp iot iot-gateway mes wms im ai hrm fms)
fi
for svc in "${MODULES[@]}"; do
  echo "### [$svc] 构建镜像 ($(date '+%F %T'))"
  if ! docker compose build "$svc"; then
    echo "[$svc] 构建失败"
  else
    echo "[$svc] 构建完成"
  fi
done
echo "全部构建结束: $(date '+%F %T')"
