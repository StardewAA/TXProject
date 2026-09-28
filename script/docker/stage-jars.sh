#!/usr/bin/env bash
# 将本地 mvn package 产出的各模块 jar 暂存到 deploy/jars/<service>/app.jar
# 供 deploy/docker-compose.yml 中的薄镜像构建（additional_contexts）使用。
set -e
cd "$(dirname "$0")/../.." || exit 1
ROOT="$(pwd)"
JARS="$ROOT/deploy/jars"
mkdir -p "$JARS"

stage() { # svc jar
  local svc="$1" jar="$2"
  if [ ! -f "$jar" ]; then
    echo "[skip] $svc -> 缺少 $jar"
    return
  fi
  mkdir -p "$JARS/$svc"
  cp -f "$jar" "$JARS/$svc/app.jar"
  echo "[ok]   $svc <- $jar ($(du -h "$JARS/$svc/app.jar" | cut -f1))"
}

stage gateway        txgy-gateway/target/txgy-gateway.jar
stage system         txgy-module-system/txgy-module-system-server/target/txgy-module-system-server.jar
stage infra          txgy-module-infra/txgy-module-infra-server/target/txgy-module-infra-server.jar
stage member         txgy-module-member/txgy-module-member-server/target/txgy-module-member-server.jar
stage bpm            txgy-module-bpm/txgy-module-bpm-server/target/txgy-module-bpm-server.jar
stage pay            txgy-module-pay/txgy-module-pay-server/target/txgy-module-pay-server.jar
stage report         txgy-module-report/txgy-module-report-server/target/txgy-module-report-server.jar
stage mp             txgy-module-mp/txgy-module-mp-server/target/txgy-module-mp-server.jar
stage product        txgy-module-mall/txgy-module-product-server/target/txgy-module-product-server.jar
stage trade          txgy-module-mall/txgy-module-trade-server/target/txgy-module-trade-server.jar
stage promotion      txgy-module-mall/txgy-module-promotion-server/target/txgy-module-promotion-server.jar
stage statistics     txgy-module-mall/txgy-module-statistics-server/target/txgy-module-statistics-server.jar
stage crm            txgy-module-crm/txgy-module-crm-server/target/txgy-module-crm-server.jar
stage erp            txgy-module-erp/txgy-module-erp-server/target/txgy-module-erp-server.jar
stage iot            txgy-module-iot/txgy-module-iot-server/target/txgy-module-iot-server.jar
stage iot-gateway    txgy-module-iot/txgy-module-iot-gateway/target/txgy-module-iot-gateway.jar
stage mes            txgy-module-mes/txgy-module-mes-server/target/txgy-module-mes-server.jar
stage wms            txgy-module-wms/txgy-module-wms-server/target/txgy-module-wms-server.jar
stage im             txgy-module-im/txgy-module-im-server/target/txgy-module-im-server.jar
stage ai             txgy-module-ai/txgy-module-ai-server/target/txgy-module-ai-server.jar
stage hrm            txgy-module-hrm/txgy-module-hrm-server/target/txgy-module-hrm-server.jar
stage fms            txgy-module-fms/txgy-module-fms-server/target/txgy-module-fms-server.jar
echo "完成，jars 目录: $JARS"
