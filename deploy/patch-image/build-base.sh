#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 从源码构建本地补丁流程需要的 7 个基础镜像。
# 新机器整套商城（含网关、支付、WMS 等）请用 deploy/mall/start.sh 或 start.ps1。
# 本脚本不启动服务，也不是完整的从零部署入口。
#
# 背景：deploy/patch-image/build-all.sh 是在既有基础镜像之上只替换 jar / 前端产物，
# 这些基础镜像（deploy-infra:latest、mall-trade:... 等）无法从任何仓库拉取，属于
# "本机历史镜像"。本脚本从源码把它们重建出来，使「从零部署」不再依赖这些历史镜像。
#
# 用法（在仓库根目录执行）：
#   bash deploy/patch-image/build-base.sh            # 构建全部 7 个基础镜像
#   bash deploy/patch-image/build-base.sh backend    # 仅后端 4 个
#   bash deploy/patch-image/build-base.sh frontend   # 仅前端 3 个
#
# 关键开关（踩过坑，不要删）：
#   * MSYS_NO_PATHCONV=1 —— Git Bash 会把 /app-api 这类以 / 开头的值做路径转换，
#     污染前端产物里的请求地址。
#   * deploy/backend/Dockerfile 内部用 maven 多阶段构建（容器内编译），因此后端基础镜像
#     完全从源码生成，不需要本机预装 maven 依赖、也不依赖任何历史镜像。
#   * 前端基础镜像都是 "nginx + 源码构建产物"，同样不依赖历史镜像：
#       - PC / H5 用项目自带 Dockerfile（容器内 pnpm 构建）；
#       - admin(web-antd) 在宿主机用与 build-all.sh 完全一致的 VITE 参数构建 dist，
#         再用 Dockerfile.admin-base（自带 80 端口 + SPA 回退的 nginx 配置）打包。
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SCOPE="${1:-all}"

# ---- 后端基础镜像：deploy/backend/Dockerfile（maven 容器内构建，纯从源码）----
build_backend() {
  echo "==== 后端基础镜像（maven 容器内构建，从源码）===="
  docker build -f deploy/backend/Dockerfile \
    --build-arg MODULE_PATH=txgy-module-infra/txgy-module-infra-server \
    -t deploy-infra:latest .
  docker build -f deploy/backend/Dockerfile \
    --build-arg MODULE_PATH=txgy-module-mall/txgy-module-trade-server \
    -t mall-trade:cross-tenant-refund-20260923-3 .
  docker build -f deploy/backend/Dockerfile \
    --build-arg MODULE_PATH=txgy-module-mall/txgy-module-product-server \
    -t mall-product:opening-stock-20260923-1 .
  docker build -f deploy/backend/Dockerfile \
    --build-arg MODULE_PATH=txgy-module-member/txgy-module-member-server \
    -t mall-member:address-map-20260922-1 .
  echo "==== 后端基础镜像完成 ===="
}

# ---- 前端基础镜像 ----
build_frontend() {
  echo "==== 前端基础镜像（从源码）===="

  # PC：项目自带 Dockerfile（容器内 pnpm 构建）
  echo "-- PC (txgy-mall-web) --"
  docker build -f txgy-mall-web/Dockerfile -t txgy-mall-pc:android-102-20260924-2 txgy-mall-web

  # H5：项目自带 Dockerfile（容器内 pnpm 构建）
  echo "-- H5 (txgy-mall-uniapp-master) --"
  docker build -f deploy/uniapp/Dockerfile.mall -t txgy-mall-h5:mobile-fixes-20260924-2 .

  # admin（web-antd）：宿主机用与 build-all.sh 一致的 VITE 参数构建 dist，再打包
  echo "-- admin (web-antd) --"
  ( cd txgy-ui-admin-vben/apps/web-antd
    pnpm install --frozen-lockfile
    MSYS_NO_PATHCONV=1 \
    VITE_BASE_URL= VITE_GLOB_API_URL=/admin-api \
    VITE_APP_CAPTCHA_ENABLE=false VITE_APP_DOCALERT_ENABLE=false \
    VITE_MALL_PC_URL=http://localhost:15175 \
    pnpm exec vite build --mode production --outDir dist-facl2 )
  # 保留既有构建结果；只在本脚本确定的暂存子目录内移动，不做递归删除。
  if [ -d deploy/patch-image/stage-admin/html ]; then
    mv deploy/patch-image/stage-admin/html "deploy/patch-image/stage-admin/html.previous.$(date +%s).$$"
  fi
  mkdir -p deploy/patch-image/stage-admin/html
  cp -r txgy-ui-admin-vben/apps/web-antd/dist-facl2/. deploy/patch-image/stage-admin/html/
  cp deploy/patch-image/nginx-spa.conf deploy/patch-image/stage-admin/nginx-spa.conf
  docker build -f deploy/patch-image/Dockerfile.admin-base \
    -t txgy-mall-admin:pc-entry-20260924 deploy/patch-image/stage-admin
  echo "-- admin 完成 --"

  echo "==== 前端基础镜像完成 ===="
}

case "$SCOPE" in
  all)      build_backend; build_frontend ;;
  backend)  build_backend ;;
  frontend) build_frontend ;;
  *) echo "未知 scope: $SCOPE (可选 all/backend/frontend)"; exit 1 ;;
esac

echo
echo "基础镜像已从源码构建完成。接下来执行补丁叠加："
echo "  bash deploy/patch-image/build-all.sh            # 全量（jar + 前端产物覆盖到 file-acl-* 镜像）"
echo "或仅后端："
echo "  bash deploy/patch-image/build-all.sh backend"
echo "然后按最终文档 §5 用 rollout 覆盖层 up -d。"
