#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 文件访问控制整改：已有联调栈的补丁镜像构建（不等于整套商城从零部署）。
# 新机器统一使用 deploy/mall/start.ps1 或 start.sh，包含全部依赖服务。
#
# 背景：本次整改以「补丁镜像」方式交付 —— 在既有基础镜像之上只替换 jar / 前端产物，
# 因此**这些 tag 无法从任何仓库拉取**，第三方必须按本脚本从源码重建。
#
# 用法（在仓库根目录执行）：
#   bash deploy/patch-image/build-all.sh           # 全量重建
#   bash deploy/patch-image/build-all.sh backend   # 只重建后端
#   bash deploy/patch-image/build-all.sh frontend  # 只重建前端
#
# 关键开关（踩过坑，不要删）：
#   * MSYS_NO_PATHCONV=1 —— Git Bash 会把 `SHOPRO_API_PATH=/app-api` 这类以 / 开头的值
#     做 POSIX→Windows 路径转换，污染成 `C:/Users/.../PortableGit/.../app-api`，
#     导致 H5/admin 产物里所有请求发往 file://（页面全白、登录不可用）。
#   * 补丁 Dockerfile 里不要写 `# syntax=docker/dockerfile:1`（会去 Docker Hub 拉取前端镜像）。
#   * product 补丁显式统一复制和启动 /app/app.jar，兼容两类基础镜像。
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# ---- 基础镜像（补丁链的起点）----
# 这些基础镜像可经 deploy/patch-image/build-base.sh 从源码重建（不依赖任何本机历史镜像）。
# 若本机已存在历史基础镜像也可直接复用（脚本仅做存在性预检）。
BASE_INFRA="${BASE_INFRA:-deploy-infra:latest}"
BASE_TRADE="${BASE_TRADE:-mall-trade:cross-tenant-refund-20260923-3}"
BASE_PRODUCT="${BASE_PRODUCT:-mall-product:opening-stock-20260923-1}"
BASE_MEMBER="${BASE_MEMBER:-mall-member:address-map-20260922-1}"
BASE_PC="${BASE_PC:-txgy-mall-pc:android-102-20260924-2}"
BASE_H5="${BASE_H5:-txgy-mall-h5:mobile-fixes-20260924-2}"
# 注意：admin 基础镜像不再依赖历史镜像 —— 由 build-all.sh 用 Dockerfile.admin-base 从源码打包，
# 故此处不再设置 BASE_ADMIN 预检。

# ---- 产出 tag ----
# 注意：这里必须与 docker-compose.file-acl-rollout.yml（后端）以及实际部署版本保持一致，
# 否则 build-all.sh 重建出的镜像 tag 与上线覆盖层对不上。当前线上版本（2026-09-25 核对）：
#   infra -7 / trade -4 / product -3 / member -2 / pc -1 / h5 -4 / admin -2
TAG_INFRA="deploy-infra:file-acl-20260924-7"
TAG_TRADE="mall-trade:file-acl-20260924-4"
TAG_PRODUCT="mall-product:file-acl-20260924-3"
TAG_MEMBER="mall-member:file-acl-20260924-2"
TAG_PC="txgy-mall-pc:file-acl-20260924-1"
TAG_H5="txgy-mall-h5:file-acl-20260924-4"
TAG_ADMIN="txgy-mall-admin:file-acl-20260924-2"

SCOPE="${1:-all}"
case "$SCOPE" in
  all|backend|frontend) ;;
  *) echo "未知 scope: $SCOPE (可选 all/backend/frontend)"; exit 2 ;;
esac
BASE_IMAGES=()
if [[ "$SCOPE" == "all" || "$SCOPE" == "backend" ]]; then
  BASE_IMAGES+=("$BASE_INFRA" "$BASE_TRADE" "$BASE_PRODUCT" "$BASE_MEMBER")
fi
if [[ "$SCOPE" == "all" || "$SCOPE" == "frontend" ]]; then
  BASE_IMAGES+=("$BASE_PC" "$BASE_H5")
fi

# ---- 前置检查：基础镜像必须已存在（这些是历史迭代的产物，任何仓库都拉不到）----
echo "==== 基础镜像预检 ===="
MISSING=0
for IMG in "${BASE_IMAGES[@]}"; do
  if docker image inspect "$IMG" > /dev/null 2>&1; then
    echo "OK    $IMG"
  else
    echo "MISS  $IMG   <- 可先运行 bash deploy/patch-image/build-base.sh 从源码重建"
    MISSING=1
  fi
done
if [[ "$MISSING" == "1" ]]; then
  echo
  echo "!!! 缺少基础镜像。可用以下任一方式补齐："
  echo "    1) 从源码重建：bash deploy/patch-image/build-base.sh"
  echo "    2) 直接复用本机已有的历史基础镜像（脚本仅做存在性预检）"
  exit 1
fi

# Maven 默认离线（-o）：依赖已在 ~/.m2。干净机器上首次构建请用 MVN_OFFLINE=0 在线拉依赖。
MVN_FLAGS=(-B -DskipTests)
if [[ "${MVN_OFFLINE:-1}" == "1" ]]; then
  MVN_FLAGS=(-o -B -DskipTests)
fi

echo "==================== 1/3 后端 jar ===================="
if [[ "$SCOPE" == "all" || "$SCOPE" == "backend" ]]; then
  # 离线构建：依赖已在 ~/.m2，无需外网（干净机器用 MVN_OFFLINE=0）
  mvn "${MVN_FLAGS[@]}" \
    -pl txgy-module-infra/txgy-module-infra-api,txgy-module-infra/txgy-module-infra-server,\
txgy-module-mall/txgy-module-trade-server,txgy-module-mall/txgy-module-product-server,\
txgy-module-member/txgy-module-member-server -am package

  mkdir -p deploy/patch-image/stage-infra deploy/patch-image/stage-trade \
           deploy/patch-image/stage-product deploy/patch-image/stage-member
  cp txgy-module-infra/txgy-module-infra-server/target/txgy-module-infra-server.jar         deploy/patch-image/stage-infra/app.jar
  cp txgy-module-mall/txgy-module-trade-server/target/txgy-module-trade-server.jar          deploy/patch-image/stage-trade/app.jar
  cp txgy-module-mall/txgy-module-product-server/target/txgy-module-product-server.jar      deploy/patch-image/stage-product/app.jar
  cp txgy-module-member/txgy-module-member-server/target/txgy-module-member-server.jar      deploy/patch-image/stage-member/app.jar

  docker build --build-arg BASE_IMAGE="$BASE_INFRA"   -t "$TAG_INFRA"   -f deploy/patch-image/Dockerfile.patch-app      deploy/patch-image/stage-infra
  docker build --build-arg BASE_IMAGE="$BASE_TRADE"   -t "$TAG_TRADE"   -f deploy/patch-image/Dockerfile.patch-app      deploy/patch-image/stage-trade
  docker build --build-arg BASE_IMAGE="$BASE_MEMBER"  -t "$TAG_MEMBER"  -f deploy/patch-image/Dockerfile.patch-app      deploy/patch-image/stage-member
  # product 补丁覆盖 ENTRYPOINT，确保复制的 jar 就是启动的 jar。
  docker build --build-arg BASE_IMAGE="$BASE_PRODUCT" -t "$TAG_PRODUCT" -f deploy/patch-image/Dockerfile.patch-product  deploy/patch-image/stage-product
fi

echo "==================== 2/3 前端产物 ===================="
if [[ "$SCOPE" == "all" || "$SCOPE" == "frontend" ]]; then
  # H5：必须关闭 MSYS 路径转换
  ( cd txgy-mall-uniapp-master
    MSYS_NO_PATHCONV=1 \
    SHOPRO_BASE_URL="" SHOPRO_API_PATH=/app-api SHOPRO_STATIC_URL="" \
    SHOPRO_TENANT_ID=1 SHOPRO_H5_URL="" SHOPRO_WEBSOCKET_PATH=/infra/ws \
    NODE_OPTIONS=--max-old-space-size=3072 \
    UNI_INPUT_DIR="$ROOT/txgy-mall-uniapp-master" pnpm build:h5 )

  # PC：参数取自 deploy/mall/compose.yaml 的 canonical 值
  # 注意：构建前若 dist 已存在，vite 清目录会被批量删除护栏拦下 ——
  #       请先 `mv txgy-mall-web/dist /tmp/old-pc-dist`（不要 rm -rf）。
  ( cd txgy-mall-web
    VITE_PAYMENT_PREVIEW=true VITE_MALL_PUBLIC_BASE_URL=http://localhost:15175 \
    VITE_MALL_DEMO_MODE=true VITE_MALL_H5_PORT=3000 \
    VITE_PUBLIC_IMAGE_ORIGIN=https://java-ai-comency.oss-cn-beijing.aliyuncs.com \
    pnpm build )

  # admin：必须关闭路径转换；输出到新目录 dist-facl2 以绕开清目录护栏
  ( cd txgy-ui-admin-vben/apps/web-antd
    MSYS_NO_PATHCONV=1 \
    VITE_BASE_URL= VITE_GLOB_API_URL=/admin-api \
    VITE_APP_CAPTCHA_ENABLE=false VITE_APP_DOCALERT_ENABLE=false \
    VITE_MALL_PC_URL=http://localhost:15175 \
    pnpm exec vite build --mode production --outDir dist-facl2 )
fi

echo "==================== 3/3 前端补丁镜像 ===================="
if [[ "$SCOPE" == "all" || "$SCOPE" == "frontend" ]]; then
  mkdir -p deploy/patch-image/stage-pc/html deploy/patch-image/stage-h5/html deploy/patch-image/stage-admin/html
  cp -r txgy-mall-web/dist/.                          deploy/patch-image/stage-pc/html/
  cp -r txgy-mall-uniapp-master/dist/build/h5/.        deploy/patch-image/stage-h5/html/
  cp -r txgy-ui-admin-vben/apps/web-antd/dist-facl2/.  deploy/patch-image/stage-admin/html/
  cp deploy/patch-image/nginx-spa.conf                 deploy/patch-image/stage-admin/nginx-spa.conf

  docker build --build-arg BASE_IMAGE="$BASE_PC"    -t "$TAG_PC"    -f deploy/patch-image/Dockerfile.patch-web deploy/patch-image/stage-pc
  docker build --build-arg BASE_IMAGE="$BASE_H5"    -t "$TAG_H5"    -f deploy/patch-image/Dockerfile.patch-web deploy/patch-image/stage-h5
  # admin 从源码打包：Dockerfile.admin-base 自带 80 端口 + SPA 回退的 nginx 配置，无需历史基础镜像
  docker build -t "$TAG_ADMIN" -f deploy/patch-image/Dockerfile.admin-base deploy/patch-image/stage-admin
fi

echo "==================== 产物体检 ===================="
# 注意：grep 在"没有匹配"时返回 1，在 `set -euo pipefail` 下会让脚本直接退出 ——
#       而这恰恰是"体检通过"的情况。必须加 `|| true`，否则永远跑不到最后一句。
count_polluted() { grep -rl "PortableGit/versions/1.2.0" "$1" 2>/dev/null | wc -l | tr -d ' ' || true; }
echo "-- H5 产物残留盘符路径的文件数（应为 0）--"
count_polluted txgy-mall-uniapp-master/dist/build/h5/assets
echo "-- admin 产物残留盘符路径的文件数（应为 0）--"
count_polluted txgy-ui-admin-vben/apps/web-antd/dist-facl2

echo
echo "重建完成。上线："
echo "  cd deploy && docker compose -p deploy \\"
echo "    -f docker-compose.yml -f docker-compose.mall-rollout.yml \\"
echo "    -f docker-compose.mall-android-rollout.yml \\"
echo "    -f docker-compose.file-acl-rollout.yml -f docker-compose.file-acl-frontend.yml \\"
echo "    up -d --no-build infra trade product member mall-pc mall-h5 frontend"
echo
echo "回滚：去掉最后两个 -f 覆盖层后重新 up -d 即可。"
