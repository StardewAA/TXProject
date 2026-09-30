#!/usr/bin/env bash
# =============================================================================
# 文件访问控制回归脚本 v3
# 覆盖用例：匿名 / 仅 tenant-id / 本人 / 他人同租户 / 跨租户 / 合法参与方 /
#           非法提权（私有业务类型声明公开）/ 过期孤儿清理 / 后台接口未回退
#
# 通道约定（重要）：
#   后台通道：/admin-api/infra/file/{configId}/get|preview/**  → 仅 ADMIN 身份可用
#   用户端通道：/app-api/infra/file/{configId}/get|preview/**  → 仅 MEMBER 身份可用
#   （框架按 URL 前缀强制校验 userType，会员 token 不能走 /admin-api）
#   公开资源：两个前缀都允许匿名读取
#   私有资源：两个前缀都需登录 + 归属/参与方校验；无法确认归属即拒绝
#
# 语义状态码：网关/框架可能把未登录包成 HTTP 200 + {"code":401}，
#   而文件读取通道在 Controller 内直接返回真实 HTTP 401/403。
#   故 code() = HTTP=200 且响应体含整数 code 时取 code，否则取 HTTP 码。
#
# 数据安全：严禁把空变量拼进 SQL（曾因 url LIKE '%' 造成误伤）；
#   所有 DB 写操作均按精确 id，并带非空守卫。
# =============================================================================
set -u

BASE=http://localhost:48080
PY="C:/Users/ltr33/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL_C=deploy-mysql-1
INFRA_C=deploy-infra-1
MYSQL_PWD_V=root

PASS=0; FAIL=0
TMPD="$(mktemp -d)"
BODY_FILE="$TMPD/resp.body"
TEST_IDS=""
ATOK=""
# 每次运行使用唯一文件名后缀：避免三次上传落到同一 path（同 path 会互相覆盖内容，
# 导致「按 configId+path 读最新记录」串到别的测试文件，产生假失败）
TOK="$$$RANDOM"

ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
no(){ echo "  [FAIL] $1  (期望 $2 实际 $3)"; FAIL=$((FAIL+1)); }
chk(){ [ "$3" = "$2" ] && ok "$1" || no "$1" "$2" "$3"; }
chk_ne(){ [ "$3" != "$2" ] && ok "$1" || no "$1" "≠$2" "$3"; }

# ---- JSON 取值（从 stdin）-------------------------------------------------
jexpr(){ "$PY" -c "
import sys, json
try:
    d = json.load(sys.stdin)
    v = eval(sys.argv[1], {}, {'d': d})
    print('' if v is None else v)
except Exception:
    print('')" "$1"; }
jdata(){ jexpr "d.get('data') or ''"; }
jtoken(){ jexpr "(d.get('data') or {}).get('accessToken','') if isinstance(d.get('data'),dict) else ''"; }
juserid(){ jexpr "(d.get('data') or {}).get('userId','') if isinstance(d.get('data'),dict) else ''"; }

# ---- 语义状态码 -----------------------------------------------------------
code(){
  local http c
  http=$(curl -sS --max-time 30 -o "$BODY_FILE" -w '%{http_code}' "$@" 2>/dev/null)
  c=""
  if [ "$http" = "200" ]; then
    c=$("$PY" - "$BODY_FILE" <<'PYEOF'
import sys, json
try:
    raw = open(sys.argv[1], 'rb').read().decode('utf-8', 'ignore')
    d = json.loads(raw)
    code = d.get('code') if isinstance(d, dict) else None
    print(code if isinstance(code, int) else '')
except Exception:
    print('')
PYEOF
)
  fi
  if [ -n "$c" ]; then echo "$c"; else echo "$http"; fi
}

# ---- DB 只读 / 精确写 -----------------------------------------------------
mysql_q(){ docker exec -e MYSQL_PWD="$MYSQL_PWD_V" "$MYSQL_C" mysql -h127.0.0.1 -uroot -N ruoyi-vue-pro -e "$1" 2>/dev/null; }

fid_of(){
  local u; u="${1%%\?*}"
  [ -z "$u" ] && return 0
  mysql_q "SELECT id FROM infra_file WHERE url='$u' ORDER BY id DESC LIMIT 1;" | head -1
}
expire_file(){
  [ -z "${1:-}" ] && { echo "expire_file: 空 id，已跳过" >&2; return 1; }
  mysql_q "UPDATE infra_file SET bind_expire_time=NOW()-INTERVAL 1 HOUR WHERE id=$1;"
}

# ---- URL 前缀互转（同一 configId+path，仅前缀不同）------------------------
appof(){ printf '%s' "$1" | sed 's#/admin-api/#/app-api/#'; }
admof(){ printf '%s' "$1" | sed 's#/app-api/#/admin-api/#'; }
prev(){ printf '%s' "$1" | sed 's#/get/#/preview/#'; }

# ---- 登录 / 绑定 ----------------------------------------------------------
login_member(){ curl -sS --max-time 25 -X POST "$BASE/app-api/member/mall-auth/account-login" \
  -H 'Content-Type: application/json' -H "tenant-id: $1" -H 'terminal: 20' \
  -d "{\"tenantId\":$1,\"account\":\"$2\",\"password\":\"admin123\"}"; }
login_admin(){ curl -sS --max-time 25 -X POST "$BASE/admin-api/system/auth/login" \
  -H 'Content-Type: application/json' -H "tenant-id: $1" \
  -d "{\"username\":\"$2\",\"password\":\"admin123\"}"; }
bind(){ docker exec "$INFRA_C" curl -sS --max-time 30 -X POST \
  "http://127.0.0.1:48080/rpc-api/infra/file/bind" -H 'Content-Type: application/json' -d "$1"; }

# ---- 退出清理（仅清理本次测试产物，按精确 id）----------------------------
cleanup_test(){
  [ -z "$TOK" ] && return 0
  if [ -n "$TEST_IDS" ] && [ -n "$ATOK" ]; then
    local ids; ids="$(printf '%s' "$TEST_IDS" | tr ' ' ',' | sed 's/^,//;s/,$//')"
    # 数字校验：只允许纯数字 id 列表，杜绝空变量/异常值进入 SQL
    case "$ids" in ''|*[!0-9,]*) ids="";; esac
    if [ -n "$ids" ]; then
      # 1. 走产品删除接口（删存储对象 + 记录），顺带验证后台删除链路
      curl -sS --max-time 40 -X DELETE \
        "$BASE/admin-api/infra/file/delete-list?ids=$ids" \
        -H "Authorization: Bearer $ATOK" -H 'tenant-id: 1' >/dev/null 2>&1
      # 2. 物理清除本次测试残留（精确 id + 本次唯一 token 后缀）
      mysql_q "DELETE FROM infra_file WHERE id IN ($ids);"
      mysql_q "DELETE FROM infra_file_content WHERE path LIKE 'mall/aftersale/%$TOK%';"
    fi
  fi
}
trap 'cleanup_test' EXIT

# ===========================================================================
echo "=== 准备身份 ==="
OWNER=$(login_member 2009 sight123 | jtoken);   OWNER_UID=$(login_member 2009 sight123 | juserid)
OTHER=$(login_member 2009 kedun_shop | jtoken); OTHER_UID=$(login_member 2009 kedun_shop | juserid)
CROSS=$(login_member 2001 t2001_shop1 | jtoken)
ATOK=$(login_admin 1 admin | jtoken)
echo "  owner(sight123@2009) uid=$OWNER_UID token=${#OWNER}"
echo "  other(kedun_shop@2009) uid=$OTHER_UID token=${#OTHER}"
echo "  cross(t2001_shop1@2001) token=${#CROSS}"
echo "  admin(admin@1) token=${#ATOK}"
{ [ -n "$OWNER" ] && [ -n "$OTHER" ] && [ -n "$CROSS" ] && [ -n "$ATOK" ]; } \
  || { echo "登录失败，终止"; exit 1; }

printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc`\xf8\xcf\xc0\x00\x00\x03\x03\x01\x00\x18\xdd\x8d\xb0\x00\x00\x00\x00IEND\xaeB`\x82' > "$TMPD/sec-test.png"
IMG="$(cygpath -m "$TMPD/sec-test.png")"

# ===========================================================================
echo ""
echo "=== A. 匿名上传入口（漏洞是否已修复）==="
chk "A1 匿名+tenant-id 上传 app-api 应 401" 401 "$(code -X POST "$BASE/app-api/infra/file/upload" -H 'tenant-id: 1' -F "file=@$IMG;type=image/png" -F 'directory=mall/aftersale')"
chk "A2 完全无头 上传 app-api 应 401"       401 "$(code -X POST "$BASE/app-api/infra/file/upload" -F "file=@$IMG;type=image/png")"
chk "A3 匿名+tenant-id 创建记录 app-api 应 401" 401 "$(code -X POST "$BASE/app-api/infra/file/create" -H 'tenant-id: 1' -H 'Content-Type: application/json' -d '{"configId":4,"path":"x/y.png","name":"y.png","url":"http://x/y.png","type":"image/png","size":1}')"
chk "A4 匿名 app-api 预签名地址应 401"      401 "$(code "$BASE/app-api/infra/file/presigned-url?name=a.png")"
chk "A5 匿名+tenant-id 上传 admin-api 应 401" 401 "$(code -X POST "$BASE/admin-api/infra/file/upload" -H 'tenant-id: 1' -F "file=@$IMG;type=image/png")"

# ===========================================================================
echo ""
echo "=== B. 已确认公开图片仍可匿名访问（两个前缀都要通）==="
PUBURL=$(mysql_q "SELECT url FROM infra_file WHERE access_type=1 AND deleted=0 AND config_id=4 ORDER BY id LIMIT 1;")
PUB_APP="$(appof "$PUBURL")"
echo "  公开样本 admin-api: $PUBURL"
echo "  公开样本 app-api  : $PUB_APP"
chk "B1 匿名 GET 公开图 admin-api /get 应 200"     200 "$(code "$PUBURL")"
chk "B2 匿名 GET 公开图 admin-api /preview 应 200" 200 "$(code "$(prev "$PUBURL")")"
chk "B3 匿名 GET 公开图 app-api /get 应 200"       200 "$(code "$PUB_APP")"
chk "B4 匿名 GET 公开图 app-api /preview 应 200"   200 "$(code "$(prev "$PUB_APP")")"
chk "B5 匿名 GET OSS 公开图（直连）应 200" 200 "$(code 'https://java-ai-comency.oss-cn-beijing.aliyuncs.com/mall-demo/v1/product-images/910001.jpg')"
# 公开面引用完整性：商城对外展示列引用的文件里，不应存在「内容仍在但当前非公开」的待处置项
SCAN_TODO=$(cd "$(dirname "$0")" && "$PY" scan-public-file-refs.py 2>/dev/null | grep '合计待处置' | tail -1)
chk "B6 公开面引用完整性：无待处置项" "合计待处置: 0" "${SCAN_TODO:-<扫描失败>}"

# ===========================================================================
echo ""
echo "=== C. 私有文件匿名访问应被拒 ==="
PRIVURL=$(mysql_q "SELECT url FROM infra_file WHERE access_type=2 AND deleted=0 AND config_id=4 ORDER BY id LIMIT 1;")
PRIV_APP="$(appof "$PRIVURL")"
echo "  私有样本 admin-api: $PRIVURL"
echo "  私有样本 app-api  : $PRIV_APP"
chk "C1 匿名 GET 私有文件 admin-api 应 401"       401 "$(code "$PRIVURL")"
chk "C2 仅 tenant-id 头 GET 私有文件应 401"        401 "$(code -H 'tenant-id: 2009' "$PRIVURL")"
chk "C3 匿名 GET 私有文件 app-api 应 401"          401 "$(code "$PRIV_APP")"
chk "C4 匿名 GET 不存在的 path 应 401（无法确认归属即拒绝）" 401 "$(code "$BASE/app-api/infra/file/4/get/__nope__/x.png")"

# ===========================================================================
echo ""
echo "=== D. 登录上传 + 归属记录 ==="
UP=$(curl -sS --max-time 40 -X POST "$BASE/app-api/infra/file/upload" \
  -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' \
  -F "file=@$IMG;type=image/png;filename=sec-d-$TOK.png" -F 'directory=mall/aftersale')
FURL=$(printf '%s' "$UP" | jdata)
F_APP="$(appof "$FURL")"
echo "  上传结果: $(printf '%s' "$UP" | head -c 200)"
[ -n "$FURL" ] && ok "D1 已登录会员上传成功" || no "D1 已登录会员上传成功" "有 data" "空"
FID="$(fid_of "$FURL")"
TEST_IDS="$TEST_IDS $FID"
echo "  新文件 id=$FID"
echo "  记录字段:"
mysql_q "SELECT CONCAT('   tenant_id=',IFNULL(tenant_id,'NULL'),' owner_user_type=',IFNULL(owner_user_type,'NULL'),' owner_user_id=',IFNULL(owner_user_id,'NULL'),' access_type=',access_type,' bind_status=',bind_status,' biz_type=',IFNULL(biz_type,'NULL'),' expire_set=',bind_expire_time IS NOT NULL) FROM infra_file WHERE id=$FID;"
REC=$(mysql_q "SELECT CONCAT(IFNULL(tenant_id,'NULL'),'|',IFNULL(owner_user_type,'NULL'),'|',IFNULL(owner_user_id,'NULL'),'|',access_type,'|',bind_status,'|',IFNULL(biz_type,'NULL'),'|',(bind_expire_time IS NOT NULL)) FROM infra_file WHERE id=$FID;")
chk "D2 记录=租户2009/会员/本人/私有/待绑定/售后/有过期窗口" "2009|1|$OWNER_UID|2|0|AFTER_SALE_IMAGE|1" "$REC"

# ===========================================================================
echo ""
echo "=== E. 本人 / 他人 / 跨租户 读取私有文件（会员走 app-api）==="
chk "E1 本人 读取应 200"                    200 "$(code -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' "$F_APP")"
chk "E2 他人（同租户 2009）应 403"           403 "$(code -H "Authorization: Bearer $OTHER" -H 'tenant-id: 2009' "$F_APP")"
chk "E3 跨租户（租户 2001）应 403"           403 "$(code -H "Authorization: Bearer $CROSS" -H 'tenant-id: 2001' "$F_APP")"
chk "E4 匿名应 401"                        401 "$(code "$F_APP")"
chk "E5 只有 tenant-id 无 token 应 401"      401 "$(code -H 'tenant-id: 2009' "$F_APP")"
chk "E6 后台文件管理权限用户(admin-api)应 200" 200 "$(code -H "Authorization: Bearer $ATOK" -H 'tenant-id: 1' "$FURL")"
chk "E7 本人 preview 应 200"                200 "$(code -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' "$(prev "$F_APP")")"
chk "E8 匿名 preview 应 401"                401 "$(code "$(prev "$F_APP")")"
chk_ne "E9 会员 token 走 admin-api 不应放行（框架层 userType 校验）" 200 "$(code -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' "$FURL")"

# ===========================================================================
echo ""
echo "=== F. 服务端绑定：确认为业务资源后可读 ==="
BIND=$(bind "{\"urls\":[\"$FURL\"],\"bizType\":\"AFTER_SALE_IMAGE\",\"bizId\":\"AS-TEST-1\",\"accessType\":2,\"tenantId\":2009,\"participants\":[{\"userType\":1,\"userId\":$OTHER_UID}]}")
echo "  绑定(私有 + 参与方 kedun_shop uid=$OTHER_UID) = $BIND"
chk "F1 参与方读取应 200"          200 "$(code -H "Authorization: Bearer $OTHER" -H 'tenant-id: 2009' "$F_APP")"
chk "F2 非参与方（跨租户）应 403"   403 "$(code -H "Authorization: Bearer $CROSS" -H 'tenant-id: 2001' "$F_APP")"
chk "F3 匿名仍应 401"              401 "$(code "$F_APP")"

BIND2=$(bind "{\"urls\":[\"$FURL\"],\"bizType\":\"PRODUCT_IMAGE\",\"bizId\":\"SPU-TEST-1\",\"accessType\":1}")
echo "  绑定(公开) = $BIND2"
chk "F4 确认为公开后匿名 admin-api 应 200" 200 "$(code "$FURL")"
chk "F5 确认为公开后匿名 app-api 应 200"   200 "$(code "$F_APP")"

# ===========================================================================
echo ""
echo "=== G. 非法提权：私有业务类型不得声明为公开 ==="
UP2=$(curl -sS --max-time 40 -X POST "$BASE/app-api/infra/file/upload" \
  -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' \
  -F "file=@$IMG;type=image/png;filename=sec-g-$TOK.png" -F 'directory=mall/aftersale')
FURL2=$(printf '%s' "$UP2" | jdata); F2_APP="$(appof "$FURL2")"
FID2="$(fid_of "$FURL2")"; TEST_IDS="$TEST_IDS $FID2"
G=$(bind "{\"urls\":[\"$FURL2\"],\"bizType\":\"AFTER_SALE_IMAGE\",\"accessType\":1}")
echo "  以 AFTER_SALE_IMAGE 请求公开 = $G"
chk "G1 售后凭证被强制私有：匿名应 401" 401 "$(code "$F2_APP")"
chk "G2 售后凭证被强制私有：本人应 200" 200 "$(code -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' "$F2_APP")"
GREC=$(mysql_q "SELECT CONCAT(access_type,'|',bind_status) FROM infra_file WHERE id=$FID2;")
chk "G3 落库仍为 私有|已绑定(2|1)" "2|1" "$GREC"

# ===========================================================================
echo ""
echo "=== H. 过期孤儿文件清理 ==="
UP3=$(curl -sS --max-time 40 -X POST "$BASE/app-api/infra/file/upload" \
  -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' \
  -F "file=@$IMG;type=image/png;filename=sec-h-$TOK.png" -F 'directory=mall/aftersale')
FURL3=$(printf '%s' "$UP3" | jdata); F3_APP="$(appof "$FURL3")"
FID3="$(fid_of "$FURL3")"; TEST_IDS="$TEST_IDS $FID3"
echo "  孤儿文件 id=$FID3"

PRE_EXPIRED=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE deleted=0 AND bind_status=0 AND bind_expire_time IS NOT NULL AND bind_expire_time < NOW();")
chk "H0 清理前全库过期待绑定文件数=0（守卫）" "0" "${PRE_EXPIRED:-x}"

expire_file "$FID3" >/dev/null
POST_EXPIRED=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE deleted=0 AND bind_status=0 AND bind_expire_time IS NOT NULL AND bind_expire_time < NOW();")
chk "H0b 仅测试文件被置为过期(=1)" "1" "${POST_EXPIRED:-x}"

CL=$(curl -sS --max-time 40 -X POST "$BASE/admin-api/infra/file/clean-expired-orphan?limit=50" \
  -H "Authorization: Bearer $ATOK" -H 'tenant-id: 1')
echo "  清理结果: $CL"
chk "H1 过期孤儿匿名 GET 应 401（记录已清理）" 401 "$(code "$F3_APP")"
chk "H2 过期孤儿本人 GET 应 403（记录已清理）" 403 "$(code -H "Authorization: Bearer $OWNER" -H 'tenant-id: 2009' "$F3_APP")"
H3_DEL=$(mysql_q "SELECT CAST(deleted AS UNSIGNED) FROM infra_file WHERE id=$FID3;")
chk "H3 记录已软删(deleted=1)" "1" "${H3_DEL:-none}"
H4_CNT=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE path LIKE 'mall/aftersale/%sec-h-$TOK%' AND deleted=0;")
chk "H4 该路径已无存活记录(=0)" "0" "${H4_CNT:-x}"

# ===========================================================================
echo ""
echo "=== I. 后台 / 其它入口鉴权未回退 ==="
chk "I1 匿名 admin-api 文件分页应 401"   401 "$(code "$BASE/admin-api/infra/file/page?pageNo=1&pageSize=1")"
chk "I2 匿名 admin-api 删除文件应 401"   401 "$(code -X DELETE "$BASE/admin-api/infra/file/delete?id=1")"
chk "I3 匿名 admin-api 清理孤儿应 401"   401 "$(code -X POST "$BASE/admin-api/infra/file/clean-expired-orphan")"
chk "I4 匿名 admin-api 上传应 401"       401 "$(code -X POST "$BASE/admin-api/infra/file/upload" -F "file=@$IMG;type=image/png")"
chk "I5 匿名 admin-api 文件详情应 401"   401 "$(code "$BASE/admin-api/infra/file/get?id=1")"

# ===========================================================================
echo ""
echo "=== Z. 测试产物清理 + 库状态自检 ==="
cleanup_test
Z1=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE url LIKE '%sec-$TOK%';")
chk "Z1 本次测试记录已清除(=0)" "0" "${Z1:-x}"
Z2=$(mysql_q "SELECT COUNT(*) FROM infra_file_content WHERE path LIKE 'mall/aftersale/%$TOK%';")
chk "Z2 本次测试内容已清除(=0)" "0" "${Z2:-x}"
Z3=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE deleted=0 AND bind_status=0 AND bind_expire_time IS NOT NULL AND bind_expire_time < NOW();")
chk "Z3 全库无过期待绑定孤儿残留(=0)" "0" "${Z3:-x}"
Z4=$(mysql_q "SELECT COUNT(*) FROM infra_file WHERE access_type=1 AND deleted=0;")
echo "  存活公开文件数=$Z4（历史基线 208，+本次已清理）"

# ===========================================================================
echo ""
echo "================================================"
echo " 结果: PASS=$PASS  FAIL=$FAIL"
echo "================================================"
[ "$FAIL" -eq 0 ] && echo "ALL PASS" || echo "HAS FAILURE"
exit 0
