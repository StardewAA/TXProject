#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""单轮云端验收（不修改任何应用代码/配置）。

一轮完整链路（忠实对应「上传先落 master → 服务端业务保存确认公开 → 转存 OSS →
数据库指向配置 36 → 直接访问 OSS 对象核对内容」）：

  1. 后台上传无敏感信息测试图 -> 必须落 master(config_id=4)
  2. 后台保存店铺 Logo（/product/shop/update，真实页面保存端点）
     -> 服务端 bindFiles 确认公开并提升 OSS
  3. 轮询 DB：config_id=36、url 改为 OSS 直连
  4. 直接 GET OSS 对象 URL（DB 中 url）-> 200 且内容 == 原始 PNG 字节（硬验收）
  5. 按 bucket+path 直连 OSS -> 200 且内容一致（双保险）
  6. 自清理：还原店铺 Logo、删除测试文件 DB 记录

OSS 对象无法用本环境凭据删除（DELETE 返回 403），遗留测试对象列入交付记录。

用法：python verify-oss-cloud-round.py [轮次标签]
退出码 0 = 本轮通过；非 0 = 本轮失败。
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
MYSQL = os.environ.get('FILE_ACL_MYSQL_CONTAINER', 'deploy-mysql-1')
DATABASE = os.environ.get('FILE_ACL_DATABASE', 'ruoyi-vue-pro')
ROUND_TAG = sys.argv[1] if len(sys.argv) > 1 else '?'

PNG_BYTES = bytes.fromhex(
    '89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489'
    '0000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082')

RESULTS = []


def sql(statement):
    if not statement or not statement.strip():
        raise ValueError('拒绝执行空 SQL')
    proc = subprocess.run(
        ['docker', 'exec', '-i', MYSQL, 'sh', '-c',
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


def admin_login(username, password, tenant):
    status, code, parsed = api('POST', '/system/auth/login',
                               tenant=tenant, payload={'username': username, 'password': password})
    if code != 0:
        raise RuntimeError('后台登录失败 %s@%s: %s' % (username, tenant, parsed))
    return parsed['data']['accessToken'], parsed['data']['userId']


def upload(token, tenant, directory='mall/shop-logo'):
    boundary = '----OssRound' + uuid.uuid4().hex
    filename = 'oss-round-%s.png' % uuid.uuid4().hex[:10]
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
    status, raw, _ = http('POST', '%s/admin-api/infra/file/upload' % GATEWAY, headers, b''.join(chunks))
    try:
        parsed = json.loads(raw)
    except ValueError:
        raise RuntimeError('上传返回非 JSON：%s' % raw[:200])
    if parsed.get('code') != 0:
        raise RuntimeError('上传失败：%s' % parsed)
    return parsed['data']


def split_url(url):
    m = re.search(r'/infra/file/(\d+)/(?:get|preview)/(.+)$', url)
    if m:
        return int(m.group(1)), urllib.parse.unquote(m.group(2))
    row = sql("SELECT CONCAT_WS('|', config_id, path) FROM infra_file"
              " WHERE url='%s' AND deleted=0 ORDER BY id ASC LIMIT 1;" % url.replace("'", "''")).strip()
    if row and '|' in row:
        cfg, path = row.split('|', 1)
        return int(cfg), path
    raise RuntimeError('无法解析 URL：%s' % url)


def fetch_oss_state(path):
    """按 path 取 OSS 提升状态（config_id / url / promote_status / fail / last_error）。"""
    out = sql("SELECT CONCAT_WS('|', id, config_id, IFNULL(url,''), access_type, bind_status,"
              " IFNULL(promote_status,0), IFNULL(promote_fail_count,0), IFNULL(promote_last_error,''))"
              " FROM infra_file WHERE path='%s' AND deleted=0 ORDER BY id DESC LIMIT 1;"
              % path.replace("'", "''")).strip()
    if not out:
        return None
    a = out.split('|')
    return {'id': a[0], 'config_id': int(a[1]), 'url': a[2], 'access_type': int(a[3]),
            'bind_status': int(a[4]), 'promote_status': int(a[5]),
            'promote_fail_count': int(a[6]), 'promote_last_error': a[7]}


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


def record(case_id, title, passed, detail=''):
    RESULTS.append({'id': case_id, 'title': title, 'passed': passed, 'detail': detail})
    flag = 'PASS' if passed else 'FAIL'
    print('[%s] %-5s %s%s' % (flag, case_id, title, ('  <- ' + detail) if detail else ''))


def expect(case_id, title, condition, detail=''):
    record(case_id, title, bool(condition), detail)
    return bool(condition)


def shop_payload(shop, logo_url):
    p = {'id': shop['id'], 'enterpriseId': shop['enterpriseId'], 'name': shop['name'],
         'status': shop['status']}
    if logo_url:
        p['logoUrl'] = logo_url
    if shop['managerUserId'] is not None:
        p['managerUserId'] = shop['managerUserId']
    if shop['contactName']:
        p['contactName'] = shop['contactName']
    if shop['contactMobile']:
        p['contactMobile'] = shop['contactMobile']
    if shop['introduction']:
        p['introduction'] = shop['introduction']
    return p


def main():
    print('=' * 72)
    print('云端验收轮次 %s  开始时间 %s' % (ROUND_TAG, datetime.now().strftime('%Y-%m-%d %H:%M:%S')))
    print('=' * 72)

    token, _ = admin_login('admin', 'admin123', 1)

    # 1. 上传落 master
    u = upload(token, 1, 'mall/shop-logo')
    cfg, path = split_url(u)
    expect('R1', '上传落 master（config_id=4）', cfg == 4, 'cfg=%s path=%s' % (cfg, path))

    # 2. 业务保存确认公开（店铺 Logo）
    shop = get_first_shop(1)
    if not shop:
        expect('R2', '存在可操作店铺（tenant 1）', False, '未找到店铺')
        print('\n结论: FAIL（无店铺）')
        sys.exit(1)
    st, code, parsed = api('PUT', '/product/shop/update', token=token, tenant=1,
                           payload=shop_payload(shop, u))
    expect('R2', '后台保存店铺 Logo 成功（触发服务端绑定）', code == 0,
           'code=%s msg=%s' % (code, (parsed or {}).get('msg')))

    # 3. 轮询 OSS 提升结果
    st_row = None
    for _ in range(24):  # 最多约 2 分钟
        st_row = fetch_oss_state(path)
        if st_row and st_row['config_id'] == 36:
            break
        time.sleep(5)
    expect('R3', 'DB 指向配置 36 且 url 改为 OSS 直连',
           st_row and st_row['config_id'] == 36 and 'oss-cn-beijing.aliyuncs.com' in st_row.get('url', ''),
           'cfg=%s url=%s' % (st_row['config_id'] if st_row else None,
                              (st_row.get('url') or '')[:90] if st_row else None))

    # 4/5. 直接访问 OSS 对象并核对内容（硬验收，非仅网关 200）
    oss_ok = False
    if st_row and st_row['config_id'] == 36 and st_row['url']:
        s1, b1, _ = http('GET', st_row['url'])
        content_match1 = (b1 == PNG_BYTES)
        expect('R4', '直接 GET OSS 对象 url -> 200 且内容一致', s1 == 200 and content_match1,
               'HTTP=%s len=%s match=%s' % (s1, len(b1), content_match1))
        oss_direct = 'https://java-ai-comency.oss-cn-beijing.aliyuncs.com/%s' % path
        s2, b2, _ = http('GET', oss_direct)
        content_match2 = (b2 == PNG_BYTES)
        expect('R5', 'bucket+path 直连 OSS -> 200 且内容一致', s2 == 200 and content_match2,
               'HTTP=%s len=%s match=%s' % (s2, len(b2), content_match2))
        oss_ok = (s1 == 200 and content_match1 and s2 == 200 and content_match2)

    # 6. 自清理：还原店铺 Logo + 删测试文件记录
    try:
        api('PUT', '/product/shop/update', token=token, tenant=1,
            payload=shop_payload(shop, shop['logoUrl']))
        print('已还原店铺 %d 的 Logo。' % shop['id'])
    except Exception as exc:  # noqa: BLE001
        print('[警告] 还原店铺 Logo 失败：%s' % exc)
    sql("DELETE FROM infra_file_content WHERE path='%s';" % path.replace("'", "''"))
    sql("DELETE FROM infra_file WHERE path='%s' AND deleted=0;" % path.replace("'", "''"))

    failed = [r for r in RESULTS if not r['passed']]
    print('-' * 72)
    print('轮次 %s 结果：%d/%d PASS，结束时间 %s'
          % (ROUND_TAG, len(RESULTS) - len(failed), len(RESULTS),
             datetime.now().strftime('%Y-%m-%d %H:%M:%S')))
    if failed:
        print('失败项：')
        for r in failed:
            print('  - [%s] %s  <- %s' % (r['id'], r['title'], r['detail']))
    print('遗留 OSS 对象（本环境无删除权限）：%s' % path)
    print('=' * 72)
    sys.exit(0 if not failed else 1)


if __name__ == '__main__':
    main()
