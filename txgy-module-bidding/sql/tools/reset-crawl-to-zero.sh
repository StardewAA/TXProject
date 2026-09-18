#!/usr/bin/env bash
# ============================================================================
# 文件：sql/tools/reset-crawl-to-zero.sh
# 用途：外部信息获取（爬虫）归零重置的「Redis + ES + 触发全量」部分，
#       与 reset-crawl-to-zero.sql（DB 部分）配套；三者必须成组执行，理由见该 SQL 文件头。
# 规范依据：《外部信息获取 v1.0》§五（Redis Stream 仅为轻信号，Redis 可整清、任务不丢）
#           §7.6（ES 索引 bidding-notice-external / bidding-notice，全量重建端点 /reindex）
# 业务目标：清掉「已抓过」的判定记忆（布隆位图）+ 索引残留，使全量窗口内历史公告重新入队并重新建索引。
# 顺序（本脚本只做 2~5 步；第 1 步是 SQL 文件）：
#   1) DB 归零        mysql ... < sql/tools/reset-crawl-to-zero.sql      （含关源、清任务/公告、切 AI 解析器）
#   2) Redis 归零     DEL 布隆位图 + 清 Stream（本脚本 Step A）
#   3) ES 归零        DELETE 两个索引（本脚本 Step B；应用启动会自动重建索引映射）
#   4) 启动应用       见 docs/开发联调与运维/部署与启动方式.md
#   5) 触发全量爬     源无 SUCCESS 历史 → runNow 自动 full=true 走 fullLookbackDays 全量窗口（本脚本 Step C）
# 前置：Docker 已启动（txgy-mysql / txgy-redis / elasticsearch 健康）；脚本执行前请确认已备份。
# 用法：ES_PASSWORD='<密码>' MYSQL_PWD_OK=1 bash sql/tools/reset-crawl-to-zero.sh
#       未设置 CONFIRM=YES 时只打印将要执行的动作，不产生任何写入。
# ============================================================================
set -euo pipefail

GATEWAY="${GATEWAY:-http://127.0.0.1:48080/admin-api}"
ES="${ES:-http://127.0.0.1:9200}"
ES_USER="${ES_USER:-elastic}"
ES_PASSWORD="${ES_PASSWORD:-}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"
TENANT_ID="${TENANT_ID:-1}"
SOURCES="${SOURCES:-QYZB GX}"          # 归零后要触发全量的源（EBIDDING 待补栏目后再加）
CONFIRM="${CONFIRM:-NO}"

# 布隆过滤器 key（RedisUrlBloomFilter：bidding.ext-notice.bloom.key 默认值）
BLOOM_KEY="bidding:ext-notice:url-bloom"
# Redis Stream key = 消息类 simple name（AbstractRedisMessage.getStreamKey）；消费组 = spring.application.name
STREAM_KEY="CrawlNoticeFetchMessage"
# 未 ACK 消息重投锁（RedisPendingMessageResendJob）：一并清理，避免清流后残留 pending 记录
RESEND_LOCK="redis:stream:pending-message-resend:lock"

log() { printf '%s %s\n' "[reset-crawl]" "$*"; }
run() {
  if [[ "$CONFIRM" == "YES" ]]; then
    log "执行: $*"
    eval "$@"
  else
    log "预演（未执行，设置 CONFIRM=YES 生效）: $*"
  fi
}

log "目标：gateway=$GATEWAY es=$ES sources='$SOURCES' confirm=$CONFIRM"

# ---------- Step A：Redis 归零（布隆位图 + 任务轻信号流 + 重投锁）----------
# 说明：布隆位图是「URL 已见」的跨轮记忆，不清则全量爬取会把历史 URL 全部判为「已见」而跳过；
#       任务真相在 MySQL（task.payload_json），清 Stream 只丢唤醒信号，启动自愈会按 PENDING 重发。
run "docker exec txgy-redis redis-cli DEL '$BLOOM_KEY'"
run "docker exec txgy-redis redis-cli DEL '$STREAM_KEY'"
run "docker exec txgy-redis redis-cli DEL '$RESEND_LOCK'"
run "docker exec txgy-redis redis-cli --scan --pattern 'bidding:*bloom*' | xargs -r docker exec -i txgy-redis redis-cli DEL"
run "docker exec txgy-redis redis-cli KEYS '$BLOOM_KEY'"

# ---------- Step B：ES 归零（外部公告索引 + 站内公告索引）----------
# 说明：索引为采集产出（审核转 ACTIVE 时写入）。删除后由重新采集 + 审核 + /reindex 重建；
#       不清会出现「DB 无记录但检索命中」的悬空文档（表现为点开 404）。
if [[ -z "$ES_PASSWORD" ]]; then
  log "警告：ES_PASSWORD 为空，将按无认证访问 ES（若集群开启 xpack 安全会 401）"
fi
ES_AUTH=()
[[ -n "$ES_PASSWORD" ]] && ES_AUTH=(-u "$ES_USER:$ES_PASSWORD")

run "curl -s -o /dev/null -w '%{http_code}\\n' ${ES_AUTH[*]:-} -X DELETE '$ES/bidding-notice-external'"
run "curl -s -o /dev/null -w '%{http_code}\\n' ${ES_AUTH[*]:-} -X DELETE '$ES/bidding-notice'"
run "curl -s ${ES_AUTH[*]:-} '$ES/_cat/indices/bidding-*?v&h=index,docs.count,store.size'"

# ---------- Step C：触发全量爬取（源无 SUCCESS 历史 ⇒ runNow 自动按 full 窗口回溯）----------
if [[ "$CONFIRM" == "YES" ]]; then
  TOKEN=$(curl -s -X POST "$GATEWAY/system/auth/login" \
      -H 'Content-Type: application/json' -H "tenant-id: $TENANT_ID" \
      -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
      | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  if [[ -z "$TOKEN" ]]; then
    log "登录失败：未取得 accessToken（检查网关/账号/tenant-id）"; exit 1
  fi
  log "登录成功，开始触发全量"
  for s in $SOURCES; do
    log "触发 $s 一轮采集（async 模式：仅入队，消费端每 2 分钟一条慢慢爬；2026-09-10 由 5 分钟调整为 2 分钟）"
    curl -s -X POST "$GATEWAY/bidding/crawl-source/run?sourceCode=$s" \
      -H "Authorization: Bearer $TOKEN" -H "tenant-id: $TENANT_ID" ; echo
  done
else
  log "预演：登录并 POST /bidding/crawl-source/run?sourceCode=<源>（设置 CONFIRM=YES 生效）"
fi

cat <<'EOF'

后续（爬取入队后）：
  1) 观察入队与消费：SELECT status, COUNT(*) FROM bidding_fetch_task GROUP BY status;
     —— PENDING 多属正常（consumer-delay-ms=120000，每 2 分钟消费一条；慢是要求，不是缺陷）。
     —— 下载速率/完整性/存储状态看审计台账：SELECT * FROM bidding_crawl_download_audit ORDER BY id DESC LIMIT 20;
  2) 公告审核转 ACTIVE 后建索引；批量补偿用：
     POST /admin-api/bidding/notice-external/reindex     （外部公告，需 bidding:notice-external:update）
     POST /admin-api/bidding/notice/reindex              （本租户站内公告）
     POST /admin-api/bidding/notice/reindex-all          （平台级全租户，仅平台租户）
  3) 校验：GET /admin-api/bidding/notice-external/page?keyword=防腐 （应能命中新采集公告）
EOF
