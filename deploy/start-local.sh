#!/usr/bin/env bash
# 天信防腐保温平台 —— 本地一键启动（单体模式 / standalone）
#
# 用法（在 deploy 目录下运行）：
#   ./start-local.sh          # 构建并后台启动整套本地环境
#   ./start-local.sh --no-build # 不重新构建镜像，直接启动已有镜像
#
# 启动后访问：
#   管理后台： http://localhost:5173
#   后端接口： http://localhost:48081/doc.html
set -euo pipefail
cd "$(dirname "$0")"

# 前置检查：数据库初始化脚本必须存在
if [ ! -f mysql/standalone/001-ruoyi-vue-pro.sql ]; then
  echo "错误：缺少数据库初始化脚本 deploy/mysql/standalone/001-ruoyi-vue-pro.sql。" >&2
  echo "请确认已提交该目录，或先准备本地初始化脚本。" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "错误：未检测到 docker 命令，请先安装 Docker 并启动。" >&2
  exit 1
fi

BUILD_ARGS="--build"
if [ "${1:-}" = "--no-build" ]; then
  BUILD_ARGS=""
fi

echo "==> 启动本地单体环境（txgy-local）..."
# 用 --env-file 显式指定 .env，确保变量被读取
docker compose --env-file .env -f docker-compose.standalone.yml up -d ${BUILD_ARGS}

echo ""
echo "==> 容器状态"
docker compose -f docker-compose.standalone.yml ps

echo ""
echo "==> 访问地址"
echo "    管理后台： http://localhost:5173"
echo "    后端接口： http://localhost:48081/doc.html"
echo ""
echo "提示：MySQL 首次启动会从 deploy/mysql/standalone 初始化，约需 1-2 分钟；可观察 txgy-server 日志确认就绪。"

# 确保 WMS AI 助手就绪：新库 init 会自动装好；已有库补跑一次（脚本幂等，无副作用）
echo ""
echo "==> 检查 WMS AI 助手配置..."
ENABLE_SCRIPT="$(cd "$(dirname "$0")" && pwd)/scripts/enable-wms-ai.js"
if command -v node >/dev/null 2>&1 && [ -f "$ENABLE_SCRIPT" ]; then
  node "$ENABLE_SCRIPT" --wait 180 || \
    echo "WARN: WMS AI 配置检查失败，详见 docs/wms-ai-deployment.md"
else
  echo "WARN: 未找到 Node.js 或脚本，已跳过。可稍后手动执行："
  echo "      node deploy/scripts/enable-wms-ai.js"
fi
