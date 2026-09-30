#!/usr/bin/env bash
# ==============================================================
# txgycloud 一键初始化数据库（适用于已有旧数据的环境，不清库）
#
# 作用：
#   1. 导入全量初始化 SQL（业务表 + AI 角色/工具/模型/密钥/账号权限）
#   2. 克隆 AI 全套数据到全部租户
#   3. 克隆 MES 演示业务数据到全部租户
#   4. 清理 Redis 缓存
#   幂等：可重复运行，不会产生重复数据
#
# 用法：
#   cd deploy
#   bash init-db.sh            # 智能模式：表已存在则只补克隆数据
#   bash init-db.sh --force    # 强制重新导入全量 SQL
# ==============================================================
set -euo pipefail
cd "$(dirname "$0")"

MYSQL_CONTAINER="txgy-slim-mysql-1"
REDIS_CONTAINER="txgy-slim-redis-1"
AI_CONTAINER="txgy-slim-ai-1"
SYSTEM_CONTAINER="txgy-slim-system-1"
DB="ruoyi-vue-pro"

SQL_FULL="mysql/init/0099-full-ai-mes-init.sql"
SQL_CLONE_AI="../sql/clone_ai_to_all_tenants.sql"
SQL_CLONE_BIZ="../sql/clone_biz_demo_to_all_tenants.sql"

echo "[1/5] 检查容器..."
if ! docker inspect "$MYSQL_CONTAINER" >/dev/null 2>&1; then
  echo "错误：MySQL 容器未运行。请先在 deploy 目录执行："
  echo "  docker compose -f docker-compose.yml -f docker-compose.slim.yml up -d"
  exit 1
fi

PW=""
if [ -f .env ]; then
  PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' .env 2>/dev/null | cut -d= -f2- || true)
fi
PW="${PW:-root}"

echo "[2/5] 检测数据库当前状态（密码使用 .env 或默认 root）..."
TABLES=$(docker exec "$MYSQL_CONTAINER" mysql -uroot -p"$PW" -N -B \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB'" 2>/dev/null || echo 0)
echo "      当前数据库表数量：$TABLES"

if [ "$TABLES" -gt 100 ] && [ "${1:-}" != "--force" ]; then
  echo "      检测到数据库已初始化：跳过全量 SQL（如需强制重导请加 --force）"
else
  echo "[3/5] 导入全量初始化 SQL（覆盖同名表，不动其他表）..."
  docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$PW" --default-character-set=utf8mb4 "$DB" < "$SQL_FULL"
fi

echo "[4/5] 克隆 AI 数据到全部租户（幂等）..."
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$PW" --default-character-set=utf8mb4 "$DB" < "$SQL_CLONE_AI" 2>/dev/null | tail -3

echo "      克隆 MES 演示数据到全部租户（幂等）..."
docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$PW" --default-character-set=utf8mb4 "$DB" < "$SQL_CLONE_BIZ" 2>/dev/null | tail -3

echo "[5/5] 清理 Redis 缓存..."
docker exec "$REDIS_CONTAINER" redis-cli FLUSHALL >/dev/null 2>&1 || true

echo ""
echo "初始化完成！建议重启后端服务使缓存彻底刷新："
echo "  docker restart $AI_CONTAINER $SYSTEM_CONTAINER"
echo "然后浏览器访问 http://localhost:5173 ，用 huahan / admin123（租户 2010）登录即可使用 AI 助手。"
