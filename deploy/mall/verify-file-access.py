#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文件访问控制回归（2026-09-24 复评整改）

直接打本地联调网关（http://localhost:48080），用真实 HTTP 通道 + 真实数据库状态校验，
覆盖复评五点：

  ① 登记接口冒领        -> C7 / C8 / C9
  ② 身份与租户校验顺序  -> C4 / C5 / C6 / C10
  ③ 历史回填口径        -> C14
  ④ 绑定核验 / 重绑冲突 -> C11（护栏）+ 代码走查
  ⑤ 业务接通 + 可读性   -> C3 / C12 / C13

用法（宿主机，需能访问 docker）：
    python deploy/mall/verify-file-access.py

退出码 0 = 全部通过；非 0 = 有失败项（stdout 会打印明细）。
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime

GATEWAY = os.environ.get('FILE_ACL_GATEWAY', 'http://localhost:48080')
MYSQL_CONTAINER = os.environ.get('FILE_ACL_MYSQL_CONTAINER', 'deploy-mysql-1')
DATABASE = os.environ.get('FILE_ACL_DATABASE', 'ruoyi-vue-pro')
DIRECTORY = 'fileacl'

# 1x1 透明 PNG
PNG_BYTES = bytes.fromhex(
    '89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489'
    '0000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082'
)

RESULTS = []


# --------------------------------------------------------------------------- #
# 基础设施
# --------------------------------------------------------------------------- #
def sql(statement):
    """通过 stdin 执行 SQL（避免 shell 引号/空变量拼接问题）。"""
    if not statement or not statement.strip():
        raise ValueError('拒绝执行空 SQL')
    proc = subprocess.run(
        ['docker', 'exec', '-i', MYSQL_CONTAINER, 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -N '
         '--default-character-set=utf8mb4 ' + DATABASE],
        input=statement, text=True, capture_output=True, timeout=120,
    )
    if proc.returncode != 0:
        raise RuntimeError('SQL 失败：%s\n%s' % (statement[:300], proc.stderr.strip()))
    return proc.stdout


def sql_value(statement, default=None):
    out = sql(statement).strip().splitlines()
    if not out or out[0] == 'NULL':
        return default
    return out[0]


def http(method, url, headers=None, body=None, timeout=40):
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), resp.headers.get('Content-Type', '')
    except urllib.error.HTTPError as err:
        return err.code, err.read(), err.headers.get('Content-Type', '')


def api(method, path, token=None, tenant=None, payload=None, api_group='admin-api', timeout=40):
    headers = {'terminal': '20'}
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if tenant is not None:
        headers['tenant-id'] = str(tenant)
    body = None if payload is None else json.dumps(payload).encode()
    status, raw, ctype = http(method, GATEWAY + '/' + api_group + path, headers, body, timeout)
    try:
        parsed = json.loads(raw)
    except ValueError:
        return status, None, raw[:200].decode('utf-8', 'replace')
    return status, parsed.get('code'), parsed


def api_query(method, path, token=None, tenant=None, params=None, api_group='admin-api', timeout=40):
    """与 api() 类似，但参数走 query string（适配 @RequestParam 端点，如 /infra/file/retry-oss-promotion）。"""
    q = ('?' + urllib.parse.urlencode(params)) if params else ''
    headers = {'terminal': '20'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if tenant is not None:
        headers['tenant-id'] = str(tenant)
    status, raw, _ = http(method, GATEWAY + '/' + api_group + path + q, headers, None, timeout)
    try:
        parsed = json.loads(raw)
    except ValueError:
        return status, None, raw[:200].decode('utf-8', 'replace')
    return status, parsed.get('code'), parsed


def member_login(tenant, mobile, password='admin123'):
    """新版商城会员登录。返回 (accessToken, userId)；失败抛异常。"""
    status, code, parsed = api('POST', '/member/mall-auth/login',
                               payload={'tenantId': tenant, 'mobile': mobile, 'password': password},
                               api_group='app-api')
    if code != 0:
        raise RuntimeError('会员登录失败 tenant=%s mobile=%s: code=%s msg=%s'
                           % (tenant, mobile, code, (parsed or {}).get('msg')))
    return parsed['data']['accessToken'], parsed['data']['userId']


def admin_login(username, password, tenant):
    status, code, parsed = api('POST', '/system/auth/login',
                               tenant=tenant, payload={'username': username, 'password': password})
    if code != 0:
        raise RuntimeError('后台登录失败 %s@%s: %s' % (username, tenant, parsed))
    return parsed['data']['accessToken'], parsed['data']['userId']


def upload(token, tenant, api_group='admin-api', directory=DIRECTORY, name=None, api_prefix='/infra/file'):
    boundary = '----FileAcl' + uuid.uuid4().hex
    filename = name or ('acl-%s.png' % uuid.uuid4().hex[:10])
    chunks = [
        ('--%s\r\nContent-Disposition: form-data; name="directory"\r\n\r\n%s\r\n'
         % (boundary, directory)).encode(),
        ('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\n'
         'Content-Type: image/png\r\n\r\n' % (boundary, filename)).encode(),
        PNG_BYTES,
        ('\r\n--%s--\r\n' % boundary).encode(),
    ]
    headers = {
        'Content-Type': 'multipart/form-data; boundary=' + boundary,
        'tenant-id': str(tenant),
        'terminal': '20',
    }
    if token:
        headers['Authorization'] = 'Bearer ' + token
    status, raw, _ = http('POST', '%s/%s%s/upload' % (GATEWAY, api_group, api_prefix), headers, b''.join(chunks))
    try:
        parsed = json.loads(raw)
    except ValueError:
        raise RuntimeError('上传返回非 JSON：%s' % raw[:200])
    if parsed.get('code') != 0:
        raise RuntimeError('上传失败：%s' % parsed)
    return parsed['data']


def split_url(url):
    """从文件 URL 解析 (configId, path)。

    两种形态都要支持：
      1) 网关形态  {domain}/{group}/infra/file/{configId}/get/{path}  （master / 私有存储）
      2) OSS 直连  {oss-domain}/{path}                                （公开口径分流到 OSS）
    形态 2 无法从 URL 反推 configId，改为按 url 反查 DB。
    """
    match = re.search(r'/infra/file/(\d+)/(?:get|preview)/(.+)$', url)
    if match:
        return int(match.group(1)), urllib.parse.unquote(match.group(2))
    row = sql("SELECT CONCAT_WS('|', config_id, path) FROM infra_file"
              " WHERE url='%s' AND deleted=0 ORDER BY id ASC LIMIT 1;"
              % url.replace("'", "''")).strip()
    if row and '|' in row:
        cfg, path = row.split('|', 1)
        return int(cfg), path
    raise RuntimeError('无法从 URL 解析 (configId, path)：%s' % url)


def read_file(config_id, path, api_group='admin-api', token=None, tenant=None):
    quoted = urllib.parse.quote(path, safe='/')
    headers = {'terminal': '20'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if tenant is not None:
        headers['tenant-id'] = str(tenant)
    return http('GET', '%s/%s/infra/file/%d/get/%s' % (GATEWAY, api_group, config_id, quoted), headers)


def fetch_row(config_id, path):
    out = sql("SELECT CONCAT_WS('|', id, IFNULL(tenant_id,'NULL'), access_type, bind_status,"
              " IFNULL(owner_user_type,'NULL'), IFNULL(owner_user_id,'NULL'),"
              " IFNULL(biz_type,'NULL'), IFNULL(biz_id,'NULL'),"
              " IF(date IS NULL,'NULL','Y'))"
              " FROM (SELECT id, tenant_id, access_type, bind_status, owner_user_type,"
              " owner_user_id, biz_type, biz_id, bind_expire_time AS date"
              " FROM infra_file WHERE config_id=%d AND path='%s' AND deleted=0"
              " ORDER BY id ASC LIMIT 1) t;"
              % (config_id, path.replace("'", "''"))).strip()
    if not out:
        return None
    parts = out.split('|')
    return {
        'id': parts[0],
        'tenant_id': None if parts[1] == 'NULL' else int(parts[1]),
        'access_type': int(parts[2]),
        'bind_status': int(parts[3]),
        'owner_user_type': None if parts[4] == 'NULL' else int(parts[4]),
        'owner_user_id': None if parts[5] == 'NULL' else int(parts[5]),
        'biz_type': None if parts[6] == 'NULL' else parts[6],
        'biz_id': None if parts[7] == 'NULL' else parts[7],
        'has_expire': parts[8] == 'Y',
    }


def fetch_row_by_path(path):
    """按 path 取文件记录（与 config_id 无关）。

    分流到公开 OSS 后 config_id 会变成 36，但 path 不变；
    因此「确认公开后提升」这类断言必须用 path 查，不能用 (config_id=4, path)。
    """
    out = sql("SELECT CONCAT_WS('|', id, IFNULL(tenant_id,'NULL'), access_type, bind_status,"
              " IFNULL(owner_user_type,'NULL'), IFNULL(owner_user_id,'NULL'),"
              " IFNULL(biz_type,'NULL'), IFNULL(biz_id,'NULL'),"
              " IF(date IS NULL,'NULL','Y'))"
              " FROM (SELECT id, tenant_id, access_type, bind_status, owner_user_type,"
              " owner_user_id, biz_type, biz_id, bind_expire_time AS date"
              " FROM infra_file WHERE path='%s' AND deleted=0"
              " ORDER BY id ASC LIMIT 1) t;"
              % path.replace("'", "''")).strip()
    if not out:
        return None
    parts = out.split('|')
    return {
        'id': parts[0],
        'tenant_id': None if parts[1] == 'NULL' else int(parts[1]),
        'access_type': int(parts[2]),
        'bind_status': int(parts[3]),
        'owner_user_type': None if parts[4] == 'NULL' else int(parts[4]),
        'owner_user_id': None if parts[5] == 'NULL' else int(parts[5]),
        'biz_type': None if parts[6] == 'NULL' else parts[6],
        'biz_id': None if parts[7] == 'NULL' else parts[7],
        'has_expire': parts[8] == 'Y',
    }


def patch_row(config_id, path, assignments):
    if not assignments:
        raise ValueError('patch_row 需要至少一个赋值')
    sql("UPDATE infra_file SET %s WHERE config_id=%d AND path='%s' AND deleted=0;"
        % (', '.join(assignments), config_id, path.replace("'", "''")))


def soft_delete_row(config_id, path):
    """彻底清理测试产物：记录 + 存储内容一并删除（避免重复运行堆积软删行）。"""
    safe = path.replace("'", "''")
    sql("DELETE FROM infra_file_content WHERE config_id=%d AND path='%s';" % (config_id, safe))
    sql("DELETE FROM infra_file WHERE config_id=%d AND path='%s';" % (config_id, safe))


def soft_delete_row_by_path(path):
    """按 path 删除（忽略 config_id）：分流到 OSS(36) 后 config_id 会变，
    用原 (config_id, path) 删不到，必须按 path。"""
    safe = path.replace("'", "''")
    sql("DELETE FROM infra_file_content WHERE path='%s';" % safe)
    sql("DELETE FROM infra_file WHERE path='%s' AND deleted=0;" % safe)


def record(case_id, title, passed, detail=''):
    RESULTS.append({'id': case_id, 'title': title, 'passed': passed, 'detail': detail})
    flag = 'PASS' if passed else 'FAIL'
    print('[%s] %-6s %s%s' % (flag, case_id, title, ('  <- ' + detail) if detail else ''))


def expect(case_id, title, condition, detail=''):
    record(case_id, title, bool(condition), detail)
    return bool(condition)


# --------------------------------------------------------------------------- #
# 用例
# --------------------------------------------------------------------------- #
def main():
    print('网关：%s   数据库容器：%s\n' % (GATEWAY, MYSQL_CONTAINER))

    # ---------- C0 前置：存储配置口径 ----------
    master = sql_value("SELECT id FROM infra_file_config WHERE master=1 AND deleted=0;")
    oss = sql_value("SELECT (master+0) FROM infra_file_config WHERE id=36 AND deleted=0;")
    master4 = sql_value("SELECT (master+0) FROM infra_file_config WHERE id=4 AND deleted=0;")
    storage4 = sql_value("SELECT storage FROM infra_file_config WHERE id=4 AND deleted=0;")
    expect('C0', 'ID 4 仍为 master、ID 36 仍为非 master',
           master == '4' and master4 == '1' and oss == '0',
           'master配置=%s id4.master=%s id36.master=%s id4.storage=%s'
           % (master, master4, oss, storage4))
    if master != '4':
        print('\n前置不成立，终止。')
        return 1
    CONFIG_ID = int(master)

    admin_token, admin_id = admin_login('admin', 'admin123', 1)
    comedy_token, comedy_id = admin_login('comency', 'admin123', 1)
    t2001_token, t2001_id = admin_login('xibeihengtai', 'admin123', 2001)
    print('身份：admin(1,tenant1) / comedy(%d,tenant1,无file:query) / xibeihengtai(%d,tenant2001)\n'
          % (comedy_id, t2001_id))

    # 确认 comedy 确实没有 infra:file:query（否则 C4/C10 的负例无意义）
    comedy_perm = sql_value(
        "SELECT COUNT(*) FROM system_user_role ur JOIN system_role_menu rm ON rm.role_id=ur.role_id"
        " JOIN system_menu m ON m.id=rm.menu_id"
        " WHERE ur.user_id=%d AND m.permission='infra:file:query';" % comedy_id)
    expect('C0b', '负例主体 comedy 确无 infra:file:query 权限', comedy_perm == '0',
           '匹配权限数=%s' % comedy_perm)

    created = []

    # ---------- C1 公开面不回退 ----------
    public_rows = sql("SELECT config_id, path FROM infra_file"
                      " WHERE deleted=0 AND access_type=1 ORDER BY id;").strip().splitlines()
    public_total = len(public_rows)
    ok_count = 0
    bad = []
    for line in public_rows:
        if not line.strip():
            continue
        cfg, _, path = line.partition('\t')
        status, body, _ = read_file(int(cfg), path, api_group='app-api')
        if status == 200 and len(body) > 0:
            ok_count += 1
        else:
            bad.append('%s:%s->%s' % (cfg, path, status))
    expect('C1', '公开资源匿名可读全量回归（%d 条）' % public_total,
           ok_count == public_total and public_total >= 150,
           '成功 %d/%d%s' % (ok_count, public_total, ('，失败样例 ' + '; '.join(bad[:3])) if bad else ''))

    # ---------- 造私有文件（归属 admin） ----------
    priv_url = upload(admin_token, 1)
    cfg, priv_path = split_url(priv_url)
    created.append((cfg, priv_path))
    row = fetch_row(cfg, priv_path)
    expect('C2', '上传结果一律先落为「私有 + 待绑定」',
           row and row['access_type'] == 2 and row['bind_status'] == 0,
           'row=%s' % row)
    expect('C2b', '归属信息取自登录上下文（tenant/owner 落库）',
           row and row['tenant_id'] == 1 and row['owner_user_type'] == 2 and row['owner_user_id'] == admin_id,
           'tenant=%s type=%s id=%s' % (row['tenant_id'], row['owner_user_type'], row['owner_user_id']))

    # ---------- C3 匿名读私有 -> 401 ----------
    status, body, _ = read_file(cfg, priv_path, api_group='app-api')
    expect('C3a', '匿名读私有文件被拒（401）', status == 401, 'HTTP %s' % status)

    # ---------- C4 归属本人 >60min 仍可读（待绑定窗口内） ----------
    patch_row(cfg, priv_path, [
        "create_time=DATE_SUB(NOW(), INTERVAL 90 MINUTE)",
        "bind_expire_time=DATE_SUB(NOW(), INTERVAL 90 MINUTE) + INTERVAL 1440 MINUTE",
    ])
    status, body, ctype = read_file(cfg, priv_path, api_group='admin-api',
                                    token=admin_token, tenant=1)
    expect('C4', '已保存图片超过 1 小时，本人仍可读（待绑定窗口 24h）',
           status == 200 and len(body) > 0,
           'HTTP %s len=%d ctype=%s' % (status, len(body), ctype))

    # ---------- C4b 超期草稿：本人不再走「本人分支」，但同租户特权后台仍可读 ----------
    # 这一步用于区分联调环境是否真的跑上了本轮逻辑（旧实现会在「本人 + 超期」处直接 403）。
    patch_row(cfg, priv_path, ["bind_expire_time=DATE_SUB(NOW(), INTERVAL 1 MINUTE)"])
    status, _, _ = read_file(cfg, priv_path, api_group='app-api')
    expect('C4b', '超期待绑定草稿仍对匿名拒绝（401）', status == 401, 'HTTP %s' % status)
    status, body, _ = read_file(cfg, priv_path, api_group='admin-api', token=admin_token, tenant=1)
    expect('C4c', '超期待绑定草稿对同租户持 file:query 后台仍可读（200，非「本人」通道）',
           status == 200 and len(body) > 0, 'HTTP %s' % status)

    # ---------- C5 同租户无权限后台：类型严格匹配 ----------
    # 5.1 owner_user_type=MEMBER(1) 但 id 相同 -> 必须拒绝（旧实现只比 id 会放行）
    member_url = upload(admin_token, 1)
    mcfg, mpath = split_url(member_url)
    created.append((mcfg, mpath))
    patch_row(mcfg, mpath, ["owner_user_type=1", "owner_user_id=%d" % comedy_id,
                            "bind_status=0", "access_type=2",
                            "bind_expire_time=DATE_ADD(NOW(), INTERVAL 1440 MINUTE)"])
    status, _, _ = read_file(mcfg, mpath, api_group='admin-api', token=comedy_token, tenant=1)
    expect('C5', '同 ID 跨身份不放行：owner=会员类型 + 相同编号 -> 403 ★②',
           status == 403, 'HTTP %s' % status)

    # 5.2 对照：owner_user_type=ADMIN(2) 且 id 相同 -> 放行
    patch_row(mcfg, mpath, ["owner_user_type=2", "owner_user_id=%d" % comedy_id])
    status, body, _ = read_file(mcfg, mpath, api_group='admin-api', token=comedy_token, tenant=1)
    expect('C5b', '对照：owner=后台类型 + 相同编号 -> 200',
           status == 200 and len(body) > 0, 'HTTP %s' % status)

    # ---------- C6 历史数据 owner_user_type=NULL 不放行 ----------
    null_url = upload(admin_token, 1)
    ncfg, npath = split_url(null_url)
    created.append((ncfg, npath))
    patch_row(ncfg, npath, ["owner_user_type=NULL", "owner_user_id=%d" % comedy_id,
                            "bind_status=0", "access_type=2",
                            "bind_expire_time=DATE_ADD(NOW(), INTERVAL 1440 MINUTE)"])
    status, _, _ = read_file(ncfg, npath, api_group='admin-api', token=comedy_token, tenant=1)
    expect('C6', '历史数据 owner_user_type 为空时不放行 -> 403 ★②',
           status == 403, 'HTTP %s' % status)
    status, body, _ = read_file(ncfg, npath, api_group='admin-api', token=admin_token, tenant=1)
    expect('C6b', '同租户且持 file:query 的运维通道仍可读 -> 200',
           status == 200 and len(body) > 0, 'HTTP %s' % status)

    # ---------- C7 租户边界先于特权 ----------
    bound_url = upload(admin_token, 1)
    bcfg, bpath = split_url(bound_url)
    created.append((bcfg, bpath))
    patch_row(bcfg, bpath, ["access_type=2", "bind_status=1",
                            "tenant_id=1", "owner_user_type=2", "owner_user_id=%d" % admin_id])
    status, _, _ = read_file(bcfg, bpath, api_group='admin-api', token=t2001_token, tenant=2001)
    expect('C7', '外租户后台（持 infra:file:query）读本租户私有 -> 403 ★②',
           status == 403, 'HTTP %s' % status)
    status, body, _ = read_file(bcfg, bpath, api_group='admin-api', token=admin_token, tenant=1)
    expect('C7b', '对照：本租户归属者可读 -> 200', status == 200 and len(body) > 0, 'HTTP %s' % status)

    # C7c 收紧：已绑定私有文件不再对「同租户任意后台账号」放行（旧第 7 步已删除）
    status, _, _ = read_file(bcfg, bpath, api_group='admin-api', token=comedy_token, tenant=1)
    expect('C7c', '同租户后台但无 infra:file:query 读他人已绑定私有文件 -> 403 ★②',
           status == 403, 'HTTP %s' % status)

    # ---------- C8 冒领：无凭证登记 ----------
    payload = {'configId': bcfg, 'path': bpath, 'name': 'steal.png',
               'url': bound_url, 'type': 'image/png', 'size': len(PNG_BYTES)}
    status, code, parsed = api('POST', '/infra/file/create', payload=payload, api_group='app-api')
    # 匿名调用在框架安全层就被拦（业务码 401），比业务层的 NO_TICKET 更早；两种都算「被拒」。
    expect('C8', '匿名调用 /file/create 被拒（框架 401 / 业务 NO_TICKET）',
           status == 401 or code in (401, 1001003010),
           'HTTP %s code=%s msg=%s' % (status, code, (parsed or {}).get('msg')))

    # 无凭证的「全新登记」必须被拒（业务层 NO_TICKET）
    status, code, parsed = api('POST', '/infra/file/create', token=admin_token, tenant=1, payload={
        'configId': CONFIG_ID, 'path': '%s/no-ticket-%s.png' % (DIRECTORY, uuid.uuid4().hex[:10]),
        'name': 'no-ticket.png', 'url': '%s/admin-api/infra/file/%d/get/none.png' % (GATEWAY, CONFIG_ID),
        'type': 'image/png', 'size': len(PNG_BYTES)})
    expect('C8b', '已登录但无上传凭证的登记被拒（NO_TICKET 1001003010）',
           code == 1001003010, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))

    # ---------- C9 冒领：他人 path 重新登记 ----------
    status, code, parsed = api('POST', '/infra/file/create', token=t2001_token, tenant=2001, payload=payload)
    expect('C9', '外租户用户用他人 path 登记 -> OWNER_CONFLICT（1001003011）★①',
           code == 1001003011, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
    after = fetch_row(bcfg, bpath)
    expect('C9b', '原记录归属未被遮蔽/改写 ★①',
           after and after['owner_user_id'] == admin_id and after['tenant_id'] == 1
           and after['id'] == str(fetch_row(bcfg, bpath)['id']),
           'row=%s' % after)

    # ---------- C10 本人重复登记幂等 ----------
    status, code, parsed = api('POST', '/infra/file/create', token=admin_token, tenant=1, payload=payload)
    expect('C10', '本人重复登记同一 path 幂等成功（code=0）',
           code == 0, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))

    # ---------- C11 孤儿清理：正例（过期未绑定且无业务引用 -> 清理） ----------
    orph_url = upload(admin_token, 1)
    ocfg, opath = split_url(orph_url)
    created.append((ocfg, opath))
    patch_row(ocfg, opath, ["bind_status=0", "access_type=2", "biz_id=NULL",
                            "biz_type='OTHER'",
                            "bind_expire_time=DATE_SUB(NOW(), INTERVAL 25 HOUR)",
                            "create_time=DATE_SUB(NOW(), INTERVAL 25 HOUR)"])
    api('POST', '/infra/file/clean-expired-orphan?limit=500', token=admin_token, tenant=1)
    gone = fetch_row(ocfg, opath) is None
    expect('C11', '过期未绑定且无业务引用 -> 被清理（正例）', gone)

    # ---------- C12 孤儿清理：护栏（过期未绑定但被业务引用 -> 保留） ----------
    biz_url = upload(admin_token, 1)
    zcfg, zpath = split_url(biz_url)
    created.append((zcfg, zpath))
    patch_row(zcfg, zpath, ["bind_status=0", "access_type=2", "biz_id=NULL",
                            "biz_type='OTHER'",
                            "bind_expire_time=DATE_SUB(NOW(), INTERVAL 25 HOUR)",
                            "create_time=DATE_SUB(NOW(), INTERVAL 25 HOUR)"])
    brand_id = uuid.uuid4().int % 900000000 + 910000000
    sql("INSERT INTO product_brand (id, name, pic_url, sort, description, status, tenant_id,"
        " creator, create_time, updater, update_time, deleted)"
        " VALUES (%d, 'acl-guard', '%s', 0, 'acl', 0, 1, 'acl', NOW(), 'acl', NOW(), 0);"
        % (brand_id, biz_url))
    api('POST', '/infra/file/clean-expired-orphan?limit=500', token=admin_token, tenant=1)
    survived = fetch_row(zcfg, zpath) is not None
    expect('C12', '已保存业务图片过期未回绑也不被孤儿清理误删 ★⑤',
           survived, '业务引用 brand=%d' % brand_id)

    # ---------- C13 历史行（bind_expire_time=NULL）永不被清理 ----------
    status, code, _ = api('POST', '/infra/file/clean-expired-orphan?limit=500',
                          token=admin_token, tenant=1)
    legacy_priv = sql_value("SELECT COUNT(*) FROM infra_file WHERE deleted=0 AND access_type=2"
                            " AND bind_expire_time IS NULL;")
    status, code, parsed = api('POST', '/infra/file/clean-expired-orphan?limit=500',
                               token=admin_token, tenant=1)
    legacy_priv_after = sql_value("SELECT COUNT(*) FROM infra_file WHERE deleted=0 AND access_type=2"
                                  " AND bind_expire_time IS NULL;")
    expect('C13', '历史文件（无过期时间）不在清理候选内 ★',
           legacy_priv == legacy_priv_after, '清理前 %s -> 清理后 %s' % (legacy_priv, legacy_priv_after))

    # ---------- C14 公开口径完全由业务引用支撑 ----------
    # 与 009 迁移同款算法：把公开面各列汇入临时表（utf8mb4_bin），再按 URL 子串匹配。
    # 必须在同一个 SQL 会话内完成（TEMPORARY 表不跨会话），因此一次性下发。
    c14_out = sql("""
DROP TEMPORARY TABLE IF EXISTS `tmp_acl_public_ref`;
CREATE TEMPORARY TABLE `tmp_acl_public_ref` (`v` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin);
INSERT INTO `tmp_acl_public_ref` (`v`)
SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`slider_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE `slider_pic_urls` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`description` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE `description` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_sku` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_brand` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_category` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`logo_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_shop` WHERE `logo_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE `pic_urls` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`sku_pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE `sku_pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`user_avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE `user_avatar` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_banner` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_article` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_article_category` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`slider_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_seckill_config` WHERE `slider_pic_urls` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`preview_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_diy_page` WHERE `preview_pic_urls` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`preview_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_diy_template` WHERE `preview_pic_urls` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_combination_record` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_combination_record` WHERE `avatar` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`background_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_level` WHERE `background_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`icon` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_level` WHERE `icon` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_order_item` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`shop_logo_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_order` WHERE `shop_logo_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_after_sale` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`logo` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_delivery_express` WHERE `logo` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`logo` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_delivery_pick_up_store` WHERE `logo` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_platform_checkout_plan_item` WHERE `pic_url` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_user` WHERE `avatar` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `system_users` WHERE `avatar` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `ai_chat_role` WHERE `avatar` LIKE '%/infra/file/%'
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `txgy_demo01_contact` WHERE `avatar` LIKE '%/infra/file/%';
SELECT COUNT(*) FROM `infra_file` f
WHERE f.`deleted` = 0 AND f.`access_type` = 1 AND f.`biz_id` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `tmp_acl_public_ref` t
                  WHERE t.`v` LIKE CONCAT('%',
                      REPLACE(REPLACE(REPLACE(f.`path`, '!', '!!'), '_', '!_'), '%', '!%'), '%') ESCAPE '!');
DROP TEMPORARY TABLE IF EXISTS `tmp_acl_public_ref`;
""")
    orphan_public = next((ln.strip() for ln in c14_out.splitlines() if re.fullmatch(r'\d+', ln.strip())), None)
    expect('C14', '公开集合不存在「无业务引用」的记录（按 path 口径，兼容 OSS 提升后 url 改写）★③',
           orphan_public == '0', '无引用却公开的记录数=%s' % orphan_public)

    # ---------- M 会员通道真实回归（不再用后台账号代替） ----------
    try:
        m1_token, m1_id = member_login(2001, '13920010001')
        m2_token, m2_id = member_login(2001, '13920010002')
        m3_token, m3_id = member_login(2002, '13920020001')
        expect('M0', '会员登录可用（tenant 2001/2002 真实账号）',
               bool(m1_token and m2_token and m3_token),
               'm1=%s m2=%s m3=%s' % (m1_id, m2_id, m3_id))
        member_ok = m1_token and m2_token and m3_token
        m1_avatar_before = sql_value("SELECT IFNULL(avatar,'') FROM member_user WHERE id=%d;" % m1_id) if member_ok else ''
    except RuntimeError as exc:
        expect('M0', '会员登录可用', False, str(exc))
        member_ok = False

    # 采购成员临时夹具的还原信息（用例结束后必须恢复）
    buyer_fixture_applied = False
    buyer_mobile_before = ''
    buyer_pwd_before = ''

    if member_ok:
        # M1 会员上传私有文件（售后目录）→ 落库 owner=(MEMBER, m1)
        m_url = upload(m1_token, 2001, api_group='app-api', directory='mall/aftersale')
        m_cfg, m_path = split_url(m_url)
        created.append((m_cfg, m_path))
        mrow = fetch_row(m_cfg, m_path)
        expect('M1', '会员上传 → 私有+待绑定，owner=会员本人，租户=2001',
               mrow and mrow['access_type'] == 2 and mrow['bind_status'] == 0
               and mrow['owner_user_type'] == 1 and mrow['owner_user_id'] == m1_id
               and mrow['tenant_id'] == 2001,
               'row=%s' % mrow)
        # M2 本人带 token 读（app-api 通道）
        status, body, _ = read_file(m_cfg, m_path, api_group='app-api', token=m1_token, tenant=2001)
        expect('M2', '会员本人带 token 读私有文件 -> 200',
               status == 200 and len(body) > 0, 'HTTP %s' % status)
        # M3 同租户他人
        status, _, _ = read_file(m_cfg, m_path, api_group='app-api', token=m2_token, tenant=2001)
        expect('M3', '同租户其他会员读他人私有文件 -> 403', status == 403, 'HTTP %s' % status)
        # M4 跨租户会员
        status, _, _ = read_file(m_cfg, m_path, api_group='app-api', token=m3_token, tenant=2002)
        expect('M4', '跨租户会员读私有文件 -> 403', status == 403, 'HTTP %s' % status)
        # M5 匿名
        status, _, _ = read_file(m_cfg, m_path, api_group='app-api')
        expect('M5', '匿名读会员私有文件 -> 401', status == 401, 'HTTP %s' % status)
        # M6 会员 token 打 admin-api 通道（框架按前缀强校验 userType，必须被拒）
        status, _, _ = read_file(m_cfg, m_path, api_group='admin-api', token=m1_token, tenant=2001)
        expect('M6', '会员 token 请求 /admin-api 读取通道被拒（不能混用通道）',
               status in (401, 403, 500), 'HTTP %s' % status)
        # M7 他人会员用该 path 重新登记 → OWNER_CONFLICT（冒领）
        m_payload = {'configId': m_cfg, 'path': m_path, 'name': 'steal.png',
                     'url': m_url, 'type': 'image/png', 'size': len(PNG_BYTES)}
        status, code, parsed = api('POST', '/infra/file/create', token=m2_token, tenant=2001, payload=m_payload,
                                   api_group='app-api')
        expect('M7', '会员用他人 path 重新登记 -> OWNER_CONFLICT（1001003011）★①',
               code == 1001003011, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
        # M8 头像公开抬升收紧：把「他人上传的私有图」当头像提交 → 必须被拒且不被抬公开
        status, code, parsed = api('PUT', '/member/user/update', token=m2_token, tenant=2001,
                                   payload={'avatar': m_url}, api_group='app-api')
        expect('M8', '把他人私有图设为头像被拒（上传者不一致 FILE_BIND_OWNER_MISMATCH 1001003014）★①',
               code == 1001003014, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
        mrow_after = fetch_row(m_cfg, m_path)
        expect('M8b', '被他人私有的图片未被抬为公开（access_type 仍=2）★①',
               mrow_after and mrow_after['access_type'] == 2, 'row=%s' % mrow_after)
        m2_avatar_after = sql_value("SELECT IFNULL(avatar,'') FROM member_user WHERE id=%d;" % m2_id) or ''
        expect('M8c', '绑定失败后会员资料整体回滚（m2 头像未被写入）★⑤',
               m_url not in m2_avatar_after, 'm2.avatar=%s' % m2_avatar_after)
        # M9 本人头像绑定成功（对照）：本人用自己的私有图做头像 → 成功并抬为公开（头像公开面）
        m9_url = upload(m1_token, 2001, api_group='app-api', directory='member/avatar')
        m9_cfg, m9_path = split_url(m9_url)
        created.append((m9_cfg, m9_path))
        status, code, parsed = api('PUT', '/member/user/update', token=m1_token, tenant=2001,
                                   payload={'avatar': m9_url}, api_group='app-api')
        expect('M9', '本人用自己的图设为头像 -> 成功（code=0）',
               code == 0, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
        m9row = fetch_row_by_path(m9_path)
        expect('M9b', '本人头像绑定后抬为公开（access_type=1 / bind_status=2）',
               m9row and m9row['access_type'] == 1 and m9row['bind_status'] == 2,
               'row=%s' % m9row)
        # 服务端确认公开之后才允许提升到公开 OSS（M9 是真实业务链路触发的绑定，非脚本造数）
        m9_after = sql_value("SELECT CONCAT_WS('|', config_id, url) FROM infra_file"
                             " WHERE id=%s;" % m9row['id']) if m9row else None
        m9_cfg_after, m9_url_after = (m9_after.split('|', 1) if m9_after and '|' in m9_after
                                      else (None, None))
        expect('M9c', '确认公开后提升到公开 OSS（config_id=36 / url 改为 OSS 直连）★OSS 正确发布',
               m9_cfg_after == '36' and m9_url_after is not None
               and 'oss-cn-beijing.aliyuncs.com' in m9_url_after,
               'cfg=%s url=%s' % (m9_cfg_after, m9_url_after))
        if m9_url_after:
            status, body, ctype = http('GET', m9_url_after)
            expect('M9d', '提升后的公开资源匿名直连可读（HTTP 200）',
                   status == 200 and len(body) > 0, 'HTTP %s len=%s' % (status, len(body)))

        # ---------- N 询价端到端：真实业务提交 → bindFile → 私有归属/参与方 ----------
        # 前置：询价提交要求「企业采购成员」（member_identity=buyer + 已审核企业成员关系 +
        # 企业 audit_status=1）。演示库中唯一满足的账号是 tenant 2009 的 member 300040122，
        # 但它没有手机号无法登录。这里临时借用同库已知口令的密码哈希 + 临时手机号，
        # 用例结束后**原值还原**（可回滚夹具），不改变演示数据的语义。
        BUYER_ID = 300040122
        buyer_before = sql("SELECT IFNULL(mobile,'__NULL__'), password FROM member_user WHERE id=%d;"
                           % BUYER_ID).strip().split('\t')
        buyer_mobile_before = buyer_before[0] if buyer_before else '__NULL__'
        buyer_pwd_before = buyer_before[1] if len(buyer_before) > 1 else ''
        fixture_password = sql_value("SELECT password FROM member_user WHERE id=%d;" % m1_id)
        temp_mobile = '13900002999'
        try:
            sql("UPDATE member_user SET mobile='%s', password='%s' WHERE id=%d;"
                % (temp_mobile, fixture_password, BUYER_ID))
            buyer_fixture_applied = True
            buyer_token, buyer_id = member_login(2009, temp_mobile)
            expect('N0', '临时启用企业采购成员夹具（tenant2009 buyer 可登录）',
                   str(buyer_id) == str(BUYER_ID), 'buyerId=%s' % buyer_id)
        except Exception as exc:  # noqa: BLE001
            buyer_token = None
            expect('N0', '临时启用企业采购成员夹具', False, str(exc))

        if buyer_token:
            def inquiry_payload(request_key, pic_url, mobile, title):
                return {
                    'requestKey': request_key,
                    'title': title,
                    'contactName': 'acl-regression',
                    'contactMobile': mobile,
                    'address': '联调回归地址',
                    'deadline': int(time.time() * 1000) + 7 * 86400 * 1000,
                    'items': [{'picUrl': pic_url, 'name': '回归物料', 'category': '回归',
                               'quantity': 1, 'unit': '件'}],
                }

            # N1 采购成员提交询价（引用自己上传的私有图）
            n_url = upload(buyer_token, 2009, api_group='app-api', directory='mall/inquiry')
            n_cfg, n_path = split_url(n_url)
            created.append((n_cfg, n_path))
            n_key = uuid.uuid4().hex[:24]
            n_title = 'ACL-INQUIRY-%s' % uuid.uuid4().hex[:8]
            status, code, parsed = api('POST', '/product/purchase-inquiry/save', token=buyer_token, tenant=2009,
                                       payload=inquiry_payload(n_key, n_url, temp_mobile, n_title),
                                       api_group='app-api')
            inquiry_id = (parsed or {}).get('data') if code == 0 else None
            expect('N1', '企业采购成员提交询价成功（单据落库）', code == 0 and inquiry_id,
                   'code=%s msg=%s id=%s' % (code, (parsed or {}).get('msg'), inquiry_id))
            if inquiry_id:
                expected_item = sql_value(
                    "SELECT pic_url FROM product_purchase_inquiry_item WHERE inquiry_id=%s LIMIT 1;"
                    % str(inquiry_id))
                expect('N1b', '询价明细已保存该图片地址', expected_item == n_url,
                       'item.pic_url=%s' % expected_item)
                nrow = fetch_row(n_cfg, n_path)
                expect('N2', '提交后文件被服务端绑定：INQUIRY_IMAGE + 私有 BOUND + 归属上传者 ★⑤',
                       nrow and nrow['biz_type'] == 'INQUIRY_IMAGE' and nrow['biz_id'] == str(inquiry_id)
                       and nrow['access_type'] == 2 and nrow['bind_status'] == 1
                       and nrow['owner_user_type'] == 1 and nrow['owner_user_id'] == BUYER_ID,
                       'row=%s' % nrow)
                participants = sql_value("SELECT IFNULL(bind_participants,'') FROM infra_file"
                                         " WHERE config_id=%d AND path='%s' AND deleted=0 LIMIT 1;"
                                         % (n_cfg, n_path.replace("'", "''"))) or ''
                expect('N2b', '参与方含提交会员（1:%d）' % BUYER_ID, ('1:%d' % BUYER_ID) in participants,
                       'participants=%s' % participants)
                status, body, _ = read_file(n_cfg, n_path, api_group='app-api',
                                            token=buyer_token, tenant=2009)
                expect('N3', '绑定后本人可读 -> 200', status == 200 and len(body) > 0, 'HTTP %s' % status)
                status, _, _ = read_file(n_cfg, n_path, api_group='app-api', token=m1_token, tenant=2001)
                expect('N3b', '绑定后其他会员仍不可读 -> 403', status == 403, 'HTTP %s' % status)

            # N4 绑定失败必须回滚：引用的私有图**不属于提交方** → 拒绝且不留下询价单
            #     （用 tenant2001 会员 m1 上传的私有图，跨租户引用会被 bindFiles 拒绝）
            foreign_url = upload(m1_token, 2001, api_group='app-api', directory='mall/inquiry')
            f_cfg, f_path = split_url(foreign_url)
            created.append((f_cfg, f_path))
            r_key = uuid.uuid4().hex[:24]
            r_title = 'ACL-ROLLBACK-%s' % uuid.uuid4().hex[:8]
            status, code, parsed = api('POST', '/product/purchase-inquiry/save', token=buyer_token, tenant=2009,
                                       payload=inquiry_payload(r_key, foreign_url, temp_mobile, r_title),
                                       api_group='app-api')
            expect('N4', '引用他人私有图提交询价被拒（绑定失败即报错）★⑤',
                   code in (1001003013, 1001003014),
                   'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
            leftover = sql_value("SELECT COUNT(*) FROM product_purchase_inquiry WHERE request_key='%s';" % r_key)
            expect('N4b', '绑定失败后询价单整体回滚（无残留单据）★⑤',
                   leftover == '0', '残留单据数=%s' % leftover)
            frow = fetch_row(f_cfg, f_path)
            expect('N4c', '被拒绝的文件未被改写（仍为私有待绑定）★⑤',
                   frow and frow['access_type'] == 2 and frow['bind_status'] == 0, 'row=%s' % frow)

            # ---------- P1 同一地址在不同账号下必须重新鉴权（H5 缓存会话隔离的服务端不变量） ----------
            p_url = upload(m1_token, 2001, api_group='app-api', directory='mall/aftersale')
            p_cfg, p_path = split_url(p_url)
            created.append((p_cfg, p_path))
            status_a, body_a, _ = read_file(p_cfg, p_path, api_group='app-api', token=m1_token, tenant=2001)
            expect('P1', '账号 A 读同一地址 -> 200（可达并取到内容）',
                   status_a == 200 and len(body_a) > 0, 'HTTP %s' % status_a)
            status_b, _, _ = read_file(p_cfg, p_path, api_group='app-api', token=m2_token, tenant=2001)
            expect('P1b', '切换账号 B 读**同一地址** -> 403（不得复用 A 的结果）★缓存隔离',
                   status_b == 403, 'HTTP %s' % status_b)

            # ---------- P2 缺少归属的历史私有文件：禁止普通业务"声明式认领" ----------
            # 造一条 owner 为空的私有文件（模拟历史/异常数据），再用业务入口声称"是我的"
            h_url = upload(m1_token, 2001, api_group='app-api', directory='member/avatar')
            h_cfg, h_path = split_url(h_url)
            created.append((h_cfg, h_path))
            patch_row(h_cfg, h_path, ["owner_user_type=NULL", "owner_user_id=NULL",
                                      "bind_status=0", "access_type=2", "biz_id=NULL",
                                      "bind_expire_time=DATE_ADD(NOW(), INTERVAL 1440 MINUTE)"])
            status, code, parsed = api('PUT', '/member/user/update', token=m1_token, tenant=2001,
                                       payload={'avatar': h_url}, api_group='app-api')
            expect('P2', '无归属文件被"声明是我"认领 -> 拒绝（FILE_BIND_OWNER_UNKNOWN 1001003015）★边界',
                   code == 1001003015, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
            hrow = fetch_row(h_cfg, h_path)
            expect('P2b', '被拒文件未被改写（owner 仍为空、仍私有待绑定）★边界',
                   hrow and hrow['owner_user_id'] is None and hrow['access_type'] == 2
                   and hrow['bind_status'] == 0, 'row=%s' % hrow)
            # 同租户**另一个**账号同样不能认领（跨租户会先命中租户边界，测不到归属校验）
            status, code, parsed = api('PUT', '/member/user/update', token=m2_token, tenant=2001,
                                       payload={'avatar': h_url}, api_group='app-api')
            expect('P2c', '同租户其他账号也无法认领无归属文件 ★边界',
                   code == 1001003015, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))

            # ---------- Q 售后端到端 + 合法店铺客服读取凭证 ----------
            # 真实业务链路：tenant 2009 的真实订单(940701) / 订单项(940801) / 店铺(940501)。
            # 对照设计：80334 与 80335 **同一角色(kedun_60167)、同样不具备 infra:file:query**，
            #           唯一差别是"是否为该店铺的后台负责人" —— 差别即授权来源。
            Q_ORDER_ITEM_ID, Q_SHOP_ID, Q_MEMBER_ID = 940801, 940501, 940001
            Q_MANAGER_USERNAME, Q_PEER_USERNAME = 'kedun_songkai_1', 'kedun_zhangwei_2'
            q_fixture_applied = False
            q_member_pwd_before = None
            q_shop_manager_before = '__NULL__'
            q_item_status_before = '0'
            q_item_after_sale_id_before = '__NULL__'
            q_after_sale_id = None
            try:
                # 夹具原值（结束后必须逐项还原）
                q_member_pwd_before = sql_value(
                    "SELECT password FROM member_user WHERE id=%d;" % Q_MEMBER_ID)
                q_shop_manager_before = sql_value(
                    "SELECT IFNULL(manager_user_id,'__NULL__') FROM product_shop WHERE id=%d;" % Q_SHOP_ID)
                q_item_status_before = sql_value(
                    "SELECT after_sale_status FROM trade_order_item WHERE id=%d;" % Q_ORDER_ITEM_ID, '0')
                q_item_after_sale_id_before = sql_value(
                    "SELECT IFNULL(after_sale_id,'__NULL__') FROM trade_order_item WHERE id=%d;"
                    % Q_ORDER_ITEM_ID, '__NULL__')
                fixture_password = sql_value("SELECT password FROM member_user WHERE id=%d;" % m1_id)
                sql("UPDATE member_user SET password='%s' WHERE id=%d;"
                    % (fixture_password.replace("'", "''"), Q_MEMBER_ID))
                sql("UPDATE product_shop SET manager_user_id=%d WHERE id=%d;"
                    % (80334, Q_SHOP_ID))
                sql("UPDATE trade_order_item SET after_sale_status=0, after_sale_id=NULL WHERE id=%d;"
                    % Q_ORDER_ITEM_ID)
                q_fixture_applied = True

                q_token, q_member_uid = member_login(2009, '13900002201')
                expect('Q0', '售后链路会员登录（tenant 2009 fixture）', str(q_member_uid) == str(Q_MEMBER_ID),
                       'uid=%s' % q_member_uid)
                manager_token, manager_uid = admin_login(Q_MANAGER_USERNAME, 'admin123', 2009)
                peer_token, peer_uid = admin_login(Q_PEER_USERNAME, 'admin123', 2009)
                expect('Q0b', '两个同角色后台账号可用（80334 负责人 / 80335 同事）',
                       str(manager_uid) == '80334' and str(peer_uid) == '80335',
                       'manager=%s peer=%s' % (manager_uid, peer_uid))

                q_url = upload(q_token, 2009, api_group='app-api', directory='mall/aftersale')
                q_cfg, q_path = split_url(q_url)
                created.append((q_cfg, q_path))
                status, code, parsed = api('POST', '/trade/after-sale/create', token=q_token, tenant=2009,
                                           payload={'orderItemId': Q_ORDER_ITEM_ID, 'way': 10,
                                                    'refundPrice': 1, 'applyReason': '访问控制回归',
                                                    'applyDescription': '访问控制回归',
                                                    'applyPicUrls': [q_url]},
                                           api_group='app-api')
                q_after_sale_id = (parsed or {}).get('data') if code == 0 else None
                expect('Q1', '会员提交售后申请成功（真实订单项 940801）',
                       code == 0 and q_after_sale_id, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))

                qrow = fetch_row(q_cfg, q_path)
                expect('Q2', '凭证被服务端绑定：AFTER_SALE_IMAGE + 私有 BOUND + 归属买家 ★售后',
                       qrow and qrow['biz_type'] == 'AFTER_SALE_IMAGE'
                       and qrow['biz_id'] == str(q_after_sale_id)
                       and qrow['access_type'] == 2 and qrow['bind_status'] == 1
                       and qrow['owner_user_type'] == 1 and qrow['owner_user_id'] == Q_MEMBER_ID,
                       'row=%s' % qrow)
                q_participants = sql_value(
                    "SELECT IFNULL(bind_participants,'') FROM infra_file"
                    " WHERE config_id=%d AND path='%s' AND deleted=0 LIMIT 1;"
                    % (q_cfg, q_path.replace("'", "''"))) or ''
                expect('Q2b', '参与方含买家本人（1:<买家>）', ('1:%d' % Q_MEMBER_ID) in q_participants,
                       'participants=%s' % q_participants)
                expect('Q2c', '参与方含**店铺后台负责人**（2:80334）★店铺客服授权',
                       '2:80334' in q_participants, 'participants=%s' % q_participants)

                status, body, _ = read_file(q_cfg, q_path, api_group='app-api', token=q_token, tenant=2009)
                expect('Q3', '买家本人读凭证 -> 200', status == 200 and len(body) > 0, 'HTTP %s' % status)
                status, body, _ = read_file(q_cfg, q_path, api_group='admin-api',
                                            token=manager_token, tenant=2009)
                expect('Q4', '**店铺负责人读凭证 -> 200**（合法客服，无需 infra:file:query）★店铺客服授权',
                       status == 200 and len(body) > 0, 'HTTP %s' % status)
                status, _, _ = read_file(q_cfg, q_path, api_group='admin-api', token=peer_token, tenant=2009)
                expect('Q5', '同角色但非本店负责人的后台账号 -> 403（不是本店的客服）★最小授权',
                       status == 403, 'HTTP %s' % status)
                # 该会员属 tenant 2001、文件属 tenant 2009：命中**框架租户边界**，
                # 其返回形态是 HTTP 200 + body code 403（不同于文件模块自身的 HTTP 403），两种都算拒绝。
                status, q_body, _ = read_file(q_cfg, q_path, api_group='app-api', token=m2_token, tenant=2009)
                expect('Q5b', '跨租户其他会员 -> 被租户边界拒绝（403）',
                       status == 403 or (status == 200 and b'"code":403' in q_body),
                       'HTTP %s body=%s' % (status, q_body[:80]))
                status, _, _ = read_file(q_cfg, q_path, api_group='app-api', token=None, tenant=2009)
                expect('Q5c', '匿名 -> 401', status == 401, 'HTTP %s' % status)

                # ---------- S 合法客服：不能只用"店铺负责人"代言 ----------
                # 授权来源 = 店铺负责人 + product_shop_admin_authorization 中被授权到该店的账号。
                # 840001(kedun_shop, mall_shop_merchant) 对店铺 940501 **有**授权记录，
                # 840011(kedun_kefu01, mall_shop_kefu) 只有客服角色、**没有**该店授权记录。
                # 两者都不具备 infra:file:query —— 差别只在"是否被授权到这个店铺"。
                auth_token, auth_uid = admin_login('kedun_shop', 'admin123', 2009)
                kefu_token, kefu_uid = admin_login('kedun_kefu01', 'admin123', 2009)
                expect('S0', '两个真实店铺账号可用（840001 已授权 / 840011 仅客服角色）',
                       str(auth_uid) == '840001' and str(kefu_uid) == '840011',
                       'auth=%s kefu=%s' % (auth_uid, kefu_uid))
                expect('S1', '**被授权**到该店铺的后台账号进入参与方（2:840001）★客服授权',
                       '2:840001' in q_participants, 'participants=%s' % q_participants)
                status, body, _ = read_file(q_cfg, q_path, api_group='admin-api',
                                            token=auth_token, tenant=2009)
                expect('S2', '被授权的店铺账号读凭证 -> 200（无 infra:file:query）★客服授权',
                       status == 200 and len(body) > 0, 'HTTP %s' % status)
                status, _, _ = read_file(q_cfg, q_path, api_group='admin-api',
                                         token=kefu_token, tenant=2009)
                expect('S3', '仅具客服角色但**未授权到该店**的账号 -> 403 ★最小授权',
                       status == 403, 'HTTP %s' % status)
                # 数据面自检：840001 对 940501 确有授权记录；840011 没有
                auth_rows = sql_value("SELECT COUNT(*) FROM product_shop_admin_authorization"
                                      " WHERE shop_id=%d AND admin_user_id=840001 AND status=0 AND deleted=0;"
                                      % Q_SHOP_ID)
                kefu_rows = sql_value("SELECT COUNT(*) FROM product_shop_admin_authorization"
                                      " WHERE shop_id=%d AND admin_user_id=840011 AND status=0 AND deleted=0;"
                                      % Q_SHOP_ID)
                expect('S4', '对照前提成立：840001 有授权记录、840011 无',
                       auth_rows == '1' and kefu_rows == '0',
                       '840001行=%s 840011行=%s' % (auth_rows, kefu_rows))
            finally:
                if q_after_sale_id:
                    sql("DELETE FROM trade_after_sale WHERE id=%s;" % str(q_after_sale_id))
                if q_fixture_applied:
                    sql("UPDATE member_user SET password='%s' WHERE id=%d;"
                        % (q_member_pwd_before.replace("'", "''"), Q_MEMBER_ID))
                    manager_sql = 'NULL' if q_shop_manager_before == '__NULL__' else q_shop_manager_before
                    sql("UPDATE product_shop SET manager_user_id=%s WHERE id=%d;" % (manager_sql, Q_SHOP_ID))
                    asid_sql = ('NULL' if q_item_after_sale_id_before == '__NULL__'
                                else q_item_after_sale_id_before)
                    sql("UPDATE trade_order_item SET after_sale_status=%s, after_sale_id=%s WHERE id=%d;"
                        % (q_item_status_before, asid_sql, Q_ORDER_ITEM_ID))
                    print('已还原售后链路夹具（会员口令 / 店铺负责人 / 订单项售后状态）。')

    # ---------- R OSS 分流：公开业务图走 OSS(36)，私有留 master(4)，master 未切换 ----------
    # 口径：分流由「服务端口径（FileBizTypeEnum 公开能力）+ 目录」决定，不是客户端传参，
    #       也**不是**切换 master：未命中公开口径的上传行为必须完全不变。
    PUBLIC_DIR = 'mall/shop-logo'      # SHOP_LOGO，publicCapable=true
    PRIVATE_DIR = 'mall/aftersale'     # AFTER_SALE_IMAGE，publicCapable=false

    r_pub_url = upload(admin_token, 1, directory=PUBLIC_DIR)
    r_pub_cfg, r_pub_path = split_url(r_pub_url)
    created.append((r_pub_cfg, r_pub_path))
    expect('R1', '公开业务目录上传 -> **仍落 master**（config_id=4）★不由客户端 directory 决定存储',
           r_pub_cfg == 4 and '/infra/file/4/get/' in r_pub_url,
           'cfg=%s url=%s' % (r_pub_cfg, r_pub_url))
    expect('R1b', '「待绑定」的公开口径文件**不在**公开 OSS（url 仍是网关地址）★修复"私有待绑定却已公开"',
           'oss-cn-beijing' not in r_pub_url, 'url=%s' % r_pub_url)

    # 关键反例：客户端把"私有凭证"伪造成公开目录上传，不得因此进入公开存储
    r_fake_url = upload(admin_token, 1, directory='mall/product')
    r_fake_cfg, r_fake_path = split_url(r_fake_url)
    created.append((r_fake_cfg, r_fake_path))
    expect('R1c', '客户端伪造 directory=mall/product -> 仍落 master（不能自选公开存储）★安全',
           r_fake_cfg == 4 and 'oss-cn-beijing' not in r_fake_url,
           'cfg=%s url=%s' % (r_fake_cfg, r_fake_url))

    r_pri_url = upload(admin_token, 1, directory=PRIVATE_DIR)
    r_pri_cfg, r_pri_path = split_url(r_pri_url)
    created.append((r_pri_cfg, r_pri_path))
    expect('R2', '私有目录上传 -> 仍走 master（config_id=4，URL 经网关）',
           r_pri_cfg == 4 and '/infra/file/4/get/' in r_pri_url, 'cfg=%s url=%s' % (r_pri_cfg, r_pri_url))

    r_master = sql_value("SELECT id FROM infra_file_config WHERE master=1 AND deleted=0;")
    expect('R2b', '全局 master 仍为 ID 4（分流 ≠ 切 master）★①', r_master == '4', 'master=%s' % r_master)

    # 上传后仍是「私有 + 待绑定」，公开必须等服务端绑定确认
    r_row = sql_value("SELECT CONCAT(access_type,'/',bind_status) FROM infra_file"
                      " WHERE config_id=%s AND path='%s' AND deleted=0 LIMIT 1;"
                      % (r_pub_cfg, r_pub_path.replace("'", "''")))
    expect('R3', '上传后仍为 私有(2)/待绑定(0)，公开需服务端绑定确认',
           r_row == '2/0', 'access/bind=%s' % r_row)
    # 「待绑定」文件匿名必须读不到（证明它真的没被放到公开 OSS 上）
    status, _, _ = read_file(r_pub_cfg, r_pub_path, api_group='admin-api', token=None, tenant=1)
    expect('R3b', '待绑定的公开口径文件匿名读取 -> 401（未被提前公开）★修复关键矛盾',
           status == 401, 'HTTP %s' % status)
    # 私有目录下的文件仍必须鉴权（匿名 401）
    status, _, _ = read_file(r_pri_cfg, r_pri_path, api_group='admin-api', token=None, tenant=1)
    expect('R4', '私有目录文件匿名读取 -> 401（未被分流带偏）', status == 401, 'HTTP %s' % status)
    # 说明：OSS 对象无法用本环境凭据删除（DELETE 返回 403），DB 记录会清理，
    #       OSS 上的测试对象需手动清理 —— 见验收文档的对象清单。

    # ---------- T 店铺 Logo 真实后台保存链路（上传→保存→重新打开→匿名访问） ----------
    # 验证"真实页面上传"语义：用后台页面实际调用的上传端点 + 保存端点，
    # 不靠脚本指定 directory 来证明分流；而是证明"保存后服务端绑定为公开并提升到 OSS、匿名可读"。
    def get_first_shop(tenant):
        row = sql("SELECT id, enterprise_id, name, status, manager_user_id,"
                  " IFNULL(logo_url,''), IFNULL(contact_name,''), IFNULL(contact_mobile,''),"
                  " IFNULL(introduction,'') FROM product_shop"
                  " WHERE tenant_id=%d AND deleted=0 ORDER BY id ASC LIMIT 1;" % tenant).strip()
        if not row:
            return None
        p = row.split('\t')
        return {'id': int(p[0]), 'enterpriseId': int(p[1]), 'name': p[2], 'status': int(p[3]),
                'managerUserId': (int(p[4]) if p[4] not in ('', 'NULL') else None),
                'logoUrl': p[5], 'contactName': p[6], 'contactMobile': p[7], 'introduction': p[8]}

    shop = get_first_shop(1)
    t_logo_url = None
    if shop:
        # T1 上传 Logo：后台上传组件实际调用的端点；directory 用页面真实目录 mall/shop-logo。
        # 上传一律落 master，存储位置不再由客户端 directory 决定（已修复）。
        t_logo_url = upload(admin_token, 1, directory='mall/shop-logo')
        t_cfg, t_path = split_url(t_logo_url)
        created.append((t_cfg, t_path))
        expect('T1', '后台上传店铺 Logo -> 落 master（config_id=4），URL 经网关（不由 directory 决定存储）',
               t_cfg == 4 and '/infra/file/4/get/' in t_logo_url, 'cfg=%s url=%s' % (t_cfg, t_logo_url))
        t_row0 = fetch_row_by_path(t_path)
        expect('T1b', '上传后仍为 私有(2)/待绑定(0)（公开需保存时服务端确认）',
               t_row0 and t_row0['access_type'] == 2 and t_row0['bind_status'] == 0, 'row=%s' % t_row0)
        # T2 保存店铺（页面"编辑店铺"调用的真实端点），把上传的 Logo 设进去
        shop_payload = {
            'id': shop['id'], 'enterpriseId': shop['enterpriseId'], 'name': shop['name'],
            'status': shop['status'], 'logoUrl': t_logo_url,
        }
        if shop['managerUserId'] is not None:
            shop_payload['managerUserId'] = shop['managerUserId']
        if shop['contactName']:
            shop_payload['contactName'] = shop['contactName']
        if shop['contactMobile']:
            shop_payload['contactMobile'] = shop['contactMobile']
        if shop['introduction']:
            shop_payload['introduction'] = shop['introduction']
        status, code, parsed = api('PUT', '/product/shop/update', token=admin_token, tenant=1,
                                   payload=shop_payload)
        expect('T2', '后台保存店铺（含新 Logo）-> 成功（触发服务端绑定为公开）',
               code == 0, 'code=%s msg=%s' % (code, (parsed or {}).get('msg')))
        # T3 绑定后文件被服务端抬为公开 + 提升到 OSS(36)
        t_row = fetch_row_by_path(t_path)
        expect('T3', '保存后文件绑定为公开（access_type=1 / bind_status=2）★真实页面上传',
               t_row and t_row['access_type'] == 1 and t_row['bind_status'] == 2, 'row=%s' % t_row)
        t_after = sql_value("SELECT CONCAT_WS('|', config_id, url) FROM infra_file"
                            " WHERE path='%s' AND deleted=0 LIMIT 1;" % t_path.replace("'", "''"))
        t_cfg_after, t_url_after = (t_after.split('|', 1) if t_after and '|' in t_after
                                    else (None, None))
        expect('T3b', '确认公开后提升到公开 OSS（config_id=36 / url 改为 OSS 直连）★OSS 正确发布',
               t_cfg_after == '36' and t_url_after is not None
               and 'oss-cn-beijing.aliyuncs.com' in t_url_after, 'cfg=%s url=%s' % (t_cfg_after, t_url_after))
        # T4 重新打开店铺（页面"查看店铺"），Logo 地址应为保存时写入的网关地址：
        # 业务实体不感知 OSS 提升（仍持有上传期返回的网关 URL），但经「路径兜底」该地址
        # 仍能取回已提升到 OSS 的内容，页面图片不会 404。
        if t_url_after:
            status, code, parsed = api('GET', '/product/shop/get?id=%d' % shop['id'],
                                       token=admin_token, tenant=1)
            reopen_logo = ((parsed or {}).get('data') or {}).get('logoUrl')
            expect('T4', '重新打开店铺，Logo 地址为保存时写入的网关地址（业务不感知 OSS 提升）★重新打开',
                   reopen_logo == t_logo_url, 'reopen.logo=%s expect=%s' % (reopen_logo, t_logo_url))
            # T4b 匿名访问该网关地址（含原 master config_id）应可取回已提升内容（HTTP 200）
            status, body, ctype = http('GET', reopen_logo)
            expect('T4b', '重新打开的网关地址匿名可取回已提升的公开 Logo（HTTP 200）★匿名访问检查',
                   status == 200 and len(body) > 0, 'HTTP %s len=%s ctype=%s' % (status, len(body), ctype))
            # T5 匿名访问 OSS 直连地址（无需任何凭证）
            status, body, ctype = http('GET', t_url_after)
            expect('T5', '提升后的公开 Logo OSS 直连匿名可读（HTTP 200）★匿名访问检查',
                   status == 200 and len(body) > 0, 'HTTP %s len=%s' % (status, len(body)))
            # 还原：把店铺 Logo 改回原值（或置空），并清理测试文件记录
            try:
                restore_payload = {
                    'id': shop['id'], 'enterpriseId': shop['enterpriseId'], 'name': shop['name'],
                    'status': shop['status'],
                }
                if shop['logoUrl']:
                    restore_payload['logoUrl'] = shop['logoUrl']
                if shop['managerUserId'] is not None:
                    restore_payload['managerUserId'] = shop['managerUserId']
                if shop['contactName']:
                    restore_payload['contactName'] = shop['contactName']
                if shop['contactMobile']:
                    restore_payload['contactMobile'] = shop['contactMobile']
                if shop['introduction']:
                    restore_payload['introduction'] = shop['introduction']
                api('PUT', '/product/shop/update', token=admin_token, tenant=1, payload=restore_payload)
                print('已还原店铺 %d 的 Logo（原始值）。' % shop['id'])
            except Exception as exc:  # noqa: BLE001
                print('[警告] 还原店铺 Logo 失败：%s' % exc)
            soft_delete_row_by_path(t_path)
    else:
        expect('T1', '存在可操作的店铺（tenant 1）用于真实上传链路验收', False, '未找到店铺')

    # ---------- U OSS 提升补传机制：确认文件真实进入公共桶（非仅网关 200） ----------
    # 背景：已确认公开的文件，若 OSS 提升曾因网络抖动失败，会停在 master（config_id=4），
    # 但仍经网关匿名可读，页面不会坏。整改要求"可追踪、可安全重试"的补传，且验收必须确认
    # 文件实际进入 OSS（直接 GET OSS 对象 URL 返回 200 且内容一致），不能只看网关图片 200。
    u_url = upload(admin_token, 1, directory='mall/shop-logo')
    u_cfg, u_path = split_url(u_url)
    created.append((u_cfg, u_path))
    expect('U1', '构造补传用例：后台上传文件落 master（config_id=4）',
           u_cfg == 4, 'cfg=%s' % u_cfg)
    # 模拟"已确认公开但 OSS 提升失败"：置为公开绑定、停在 master、promote_status=3
    safe = u_path.replace("'", "''")
    sql("UPDATE infra_file SET access_type=1, bind_status=2, config_id=4, "
        "promote_status=3, promote_fail_count=5, "
        "promote_last_error='simulated OSS TLS handshake reset' "
        "WHERE path='%s' AND deleted=0;" % safe)
    u_row = fetch_row_by_path(u_path)
    u_id = int(u_row['id']) if u_row and u_row.get('id') else None
    u_cfg_stranded = sql_value("SELECT config_id FROM infra_file WHERE id=%s;" % u_id) if u_id else None
    expect('U2', '模拟 stranded：access_type=1/bind_status=2 但 config_id 仍为 4（未进 OSS）★补传前',
           u_row and u_row['access_type'] == 1 and u_row['bind_status'] == 2
           and str(u_cfg_stranded) == '4', 'row=%s cfg=%s' % (u_row, u_cfg_stranded))
    # 运维可追踪：列出"已确认公开但未进 OSS"清单应含该文件
    status, code, parsed = api('GET', '/infra/file/public-unpromoted?limit=200',
                              token=admin_token, tenant=1)
    unpromoted_ids = [(d.get('id')) for d in ((parsed or {}).get('data') or [])]
    expect('U3', '运维清单 public-unpromoted 可追踪到该 stranded 文件 ★可追踪',
           u_id in unpromoted_ids, 'u_id=%s in=%s' % (u_id, unpromoted_ids[:10]))
    # 补传前：文件在 master 上仍可经网关匿名读取（功能不受影响）
    status, body_before, _ = http('GET', u_url)
    expect('U4', '补传前：文件在 master 上仍可经网关匿名读取（HTTP 200）',
           status == 200 and len(body_before) > 0, 'HTTP %s len=%s' % (status, len(body_before)))

    # 调用补传（确认"可安全重试"且最终真实进入 OSS）。
    # 注意：控制器 retry-oss-promotion 的 fileId 是 @RequestParam（query 参数），
    # 必须用 api_query（而非 api 的 JSON body），否则会落入批量模式、单文件补传不生效。
    # 手动触发一次后轮询：手动补传或定时任务（OssPromoteRetryJob）任一成功即可，
    # 轮询窗口覆盖一个定时周期，避免与后台任务竞态导致漏判。
    # 注意：config_id 需直查（fetch_row_by_path 不含 config_id 字段）。
    u_promoted_cfg = None
    u_promoted_url = None
    api_query('POST', '/infra/file/retry-oss-promotion', token=admin_token, tenant=1,
              params={'fileId': u_id})
    for _ in range(72):  # 最多约 6 分钟
        cfg_now = sql_value("SELECT config_id FROM infra_file WHERE id=%s;" % u_id)
        if cfg_now == '36':
            u_promoted_cfg = 36
            u_promoted_url = sql_value("SELECT url FROM infra_file WHERE id=%s;" % u_id)
            break
        time.sleep(5)
    expect('U5', '补传后文件进入公开 OSS（config_id=36 / url 改为 OSS 直连）★补传成功',
           u_promoted_cfg == 36 and u_promoted_url and 'oss-cn-beijing.aliyuncs.com' in u_promoted_url,
           'cfg=%s url=%s' % (u_promoted_cfg, u_promoted_url))

    if u_promoted_url:
        # 关键验收：直接 GET OSS 对象 URL（非网关）确认对象真实存在于公共桶
        status, body, ctype = http('GET', u_promoted_url)
        expect('U6', 'OSS 直连匿名可读（HTTP 200，且内容为原 PNG）★真实进入 OSS',
               status == 200 and body == PNG_BYTES,
               'HTTP %s len=%s ctype=%s' % (status, len(body), ctype))
        # 双保险：按 bucket+endpoint+path 直接构造 OSS URL 再探一次
        oss_direct = 'https://java-ai-comency.oss-cn-beijing.aliyuncs.com/%s' % u_path
        status2, body2, _ = http('GET', oss_direct)
        expect('U7', '按 bucket+path 构造 OSS URL 直连可读（200，内容一致）★真实进入 OSS 双保险',
               status2 == 200 and body2 == PNG_BYTES,
               'HTTP %s len=%s' % (status2, len(body2)))
    # 补传后运维清单应不再包含该文件（可追踪闭环）
    status, code, parsed = api('GET', '/infra/file/public-unpromoted?limit=200',
                              token=admin_token, tenant=1)
    unpromoted_ids2 = [(d.get('id')) for d in ((parsed or {}).get('data') or [])]
    # 防止 API 异常导致 data=None 时空列表误判为"已闭环"
    expect('U8', '补传后运维清单不再包含该文件（已入 OSS）★可追踪闭环',
           code == 0 and parsed is not None and parsed.get('data') is not None
           and u_id not in unpromoted_ids2,
           'code=%s data_len=%s u_id=%s in=%s' % (code, len(unpromoted_ids2), u_id, unpromoted_ids2[:10]))
    # 还原：清理测试文件记录（OSS 对象无删除权限，留待手动清理）
    soft_delete_row_by_path(u_path)

    # ---------- 清理 ----------
    for cfg_id, path in created:
        soft_delete_row(cfg_id, path)
    sql("DELETE FROM product_brand WHERE id=%d;" % brand_id)
    # 还原会员头像（M9 会把 m1 的头像改成测试图）
    if member_ok and m1_avatar_before is not None:
        escaped_avatar = m1_avatar_before.replace("'", "''")
        sql("UPDATE member_user SET avatar=%s WHERE id=%d;"
            % ("'%s'" % escaped_avatar if m1_avatar_before else 'NULL', m1_id))
    # 清理 N 系列创建的询价单（明细 + 主单）
    for row in sql("SELECT id FROM product_purchase_inquiry WHERE title LIKE 'ACL-INQUIRY-%';"
                   ).strip().splitlines():
        inquiry_row_id = row.strip()
        if not inquiry_row_id:
            continue
        sql("DELETE FROM product_purchase_inquiry_item WHERE inquiry_id=%s;" % inquiry_row_id)
        sql("DELETE FROM product_purchase_inquiry WHERE id=%s;" % inquiry_row_id)
    # 还原企业采购成员夹具（手机号 + 口令原值）
    if buyer_fixture_applied:
        mobile_sql = 'NULL' if buyer_mobile_before == '__NULL__' else "'%s'" % buyer_mobile_before
        sql("UPDATE member_user SET mobile=%s, password='%s' WHERE id=300040122;"
            % (mobile_sql, buyer_pwd_before.replace("'", "''")))
        print('已还原企业采购成员夹具（member 300040122 手机号/口令）。')
    print('\n已清理本轮构造的 %d 个测试文件记录与 1 条业务引用行。' % len(created))
    # 分流到 OSS 的文件：DB 记录已删，但对象仍在 OSS 上（本环境凭据无 DELETE 权限）。
    oss_keys = [p for cfg, p in created if cfg == 36]
    if oss_keys:
        print('\n[注意] 本轮在 OSS(36) 上留下了 %d 个测试对象，DB 记录已删除，'
              '对象需手动清理（凭据无删除权限，DELETE 返回 403）：' % len(oss_keys))
        for key in oss_keys:
            print('  https://java-ai-comency.oss-cn-beijing.aliyuncs.com/%s' % key)

    failed = [r for r in RESULTS if not r['passed']]
    print('\n===== 汇总：%d/%d PASS =====' % (len(RESULTS) - len(failed), len(RESULTS)))
    for item in failed:
        print('  FAIL %s %s  %s' % (item['id'], item['title'], item['detail']))
    return 1 if failed else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:  # noqa: BLE001
        print('\n执行异常：%s: %s' % (type(exc).__name__, exc))
        sys.exit(2)
