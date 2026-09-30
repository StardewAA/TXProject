#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""失败补传路径验收（不修改任何应用代码/配置；故障模拟限定测试范围）。

覆盖：
  A. 手动补传：发布失败后有状态(3)+错误记录 -> 手动 /retry-oss-promotion 恢复 -> 进 OSS(36)
  E. 幂等：重复补传不产生重复行、返回 alreadyPromoted
  B. 再次保存补传：已公开但未进 OSS 的文件，再次业务保存时 bindFiles 补做提升（修复"再次保存就跳过"）
  D. 私有文件不被补传：retry 返回 notPublic，config_id 保持 4、内容不进公共桶

用法：python verify-oss-fail-retry.py
退出码 0 = 全部通过。
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


def api_query(method, path, token=None, tenant=None, params=None, api_group='admin-api', timeout=40):
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


def admin_login(username, password, tenant):
    status, code, parsed = api('POST', '/system/auth/login',
                               tenant=tenant, payload={'username': username, 'password': password})
    if code != 0:
        raise RuntimeError('后台登录失败 %s@%s: %s' % (username, tenant, parsed))
    return parsed['data']['accessToken'], parsed['data']['userId']


def upload(token, tenant, directory='mall/shop-logo'):
    boundary = '----FailRetry' + uuid.uuid4().hex
    filename = 'fail-retry-%s.png' % uuid.uuid4().hex[:10]
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


def row_count_by_path(path):
    return int(sql_value("SELECT COUNT(*) FROM infra_file WHERE path='%s' AND deleted=0;"
                         % path.replace("'", "''"), '0'))


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


def cleanup(path):
    sql("DELETE FROM infra_file_content WHERE path='%s';" % path.replace("'", "''"))
    sql("DELETE FROM infra_file WHERE path='%s' AND deleted=0;" % path.replace("'", "''"))


def mark_stranded(path, error='simulated OSS handshake reset (test-scoped fault)'):
    sql("UPDATE infra_file SET access_type=1, bind_status=2, config_id=4, "
        "promote_status=3, promote_fail_count=5, promote_last_error='%s' "
        "WHERE path='%s' AND deleted=0;" % (error.replace("'", "''"), path.replace("'", "''")))


def record(case_id, title, passed, detail=''):
    RESULTS.append({'id': case_id, 'title': title, 'passed': passed, 'detail': detail})
    flag = 'PASS' if passed else 'FAIL'
    print('[%s] %-5s %s%s' % (flag, case_id, title, ('  <- ' + detail) if detail else ''))


def expect(case_id, title, condition, detail=''):
    record(case_id, title, bool(condition), detail)
    return bool(condition)


def main():
    print('=' * 72)
    print('失败补传路径验收  开始时间 %s' % datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
    print('=' * 72)
    token, _ = admin_login('admin', 'admin123', 1)

    # ============ A + E：手动补传 + 幂等（无重复行） ============
    print('\n--- A. 手动补传（含失败状态/错误记录 + 幂等） ---')
    u = upload(token, 1, 'mall/shop-logo')
    cfg, path = split_url(u)
    expect('A1', '上传落 master(4)', cfg == 4, 'cfg=%s' % cfg)
    mark_stranded(path)
    st = fetch_oss_state(path)
    fid = int(st['id'])
    expect('A2', '发布失败后有状态(3)+错误记录',
           st['promote_status'] == 3 and st['promote_fail_count'] == 5 and st['promote_last_error'] != '',
           'status=%s fail=%s err=%s' % (st['promote_status'], st['promote_fail_count'],
                                         st['promote_last_error'][:40]))
    s, code, parsed = api('GET', '/infra/file/public-unpromoted?limit=200', token=token, tenant=1)
    unpromoted = [(d.get('id')) for d in ((parsed or {}).get('data') or [])]
    expect('A3', '运维清单可追踪该 stranded 文件', fid in unpromoted, 'fid=%s' % fid)

    cnt_before = row_count_by_path(path)
    st2, code2, parsed2 = api_query('POST', '/infra/file/retry-oss-promotion', token=token, tenant=1,
                                    params={'fileId': fid})
    data2 = (parsed2 or {}).get('data') or {}
    expect('A4', '手动补传接口返回成功', code2 == 0 and data2.get('success') == 1,
           'code=%s data=%s' % (code2, data2))
    st3 = fetch_oss_state(path)
    expect('A5', '手动补传后进 OSS(36) 且状态=2',
           st3['config_id'] == 36 and st3['promote_status'] == 2 and 'oss-cn-beijing.aliyuncs.com' in st3['url'],
           'cfg=%s status=%s' % (st3['config_id'], st3['promote_status']))
    if st3['config_id'] == 36:
        s4, b4, _ = http('GET', st3['url'])
        expect('A6', '直连 OSS 对象 200 且内容一致', s4 == 200 and b4 == PNG_BYTES,
               'HTTP=%s len=%s match=%s' % (s4, len(b4), b4 == PNG_BYTES))
    # E 幂等：再次补传 -> alreadyPromoted，行数不增
    st5, code5, parsed5 = api_query('POST', '/infra/file/retry-oss-promotion', token=token, tenant=1,
                                    params={'fileId': fid})
    data5 = (parsed5 or {}).get('data') or {}
    cnt_after = row_count_by_path(path)
    expect('E1', '重复补传返回 alreadyPromoted（skipped=1）且不重复生成行',
           code5 == 0 and data5.get('skipped') == 1 and cnt_after == cnt_before == 1,
           'data=%s rows_before=%s rows_after=%s' % (data5, cnt_before, cnt_after))
    cleanup(path)

    # ============ B：再次保存补传（bindFiles 修复"再次保存就跳过"） ============
    print('\n--- B. 再次保存补传（bindFiles 修复） ---')
    shop = get_first_shop(1)
    if not shop:
        expect('B1', '存在可操作店铺', False, '未找到店铺')
    else:
        u = upload(token, 1, 'mall/shop-logo')
        cfg, path = split_url(u)
        expect('B1', '上传落 master(4)', cfg == 4, 'cfg=%s' % cfg)
        mark_stranded(path)
        st = fetch_oss_state(path)
        expect('B2', '已公开但未进 OSS（stranded）', st['config_id'] == 4 and st['access_type'] == 1,
               'cfg=%s access=%s' % (st['config_id'], st['access_type']))
        # 再次业务保存（店铺 Logo 引用该文件）应触发 bindFiles 补提升
        s, code, parsed = api('PUT', '/product/shop/update', token=token, tenant=1,
                              payload=shop_payload(shop, u))
        st2 = fetch_oss_state(path)
        expect('B3', '再次保存后补传进 OSS(36)（不再"保存就跳过"）',
               code == 0 and st2['config_id'] == 36 and 'oss-cn-beijing.aliyuncs.com' in st2['url'],
               'code=%s cfg=%s url=%s' % (code, st2['config_id'], (st2['url'] or '')[:70]))
        if st2['config_id'] == 36:
            s4, b4, _ = http('GET', st2['url'])
            expect('B4', '再次保存补传后直连 OSS 内容一致', s4 == 200 and b4 == PNG_BYTES,
                   'HTTP=%s match=%s' % (s4, b4 == PNG_BYTES))
        api('PUT', '/product/shop/update', token=token, tenant=1,
            payload=shop_payload(shop, shop['logoUrl']))
        print('已还原店铺 %d 的 Logo。' % shop['id'])
        cleanup(path)

    # ============ D：私有文件不被补传 ============
    print('\n--- D. 私有文件不被补传 ---')
    u = upload(token, 1, 'mall/aftersale')
    cfg, path = split_url(u)
    fid = int(fetch_oss_state(path)['id'])
    expect('D1', '上传后为私有(2)/待绑定(0)', cfg == 4, 'cfg=%s' % cfg)
    st = fetch_oss_state(path)
    expect('D2', '私有文件未被公开', st['access_type'] == 2 and st['bind_status'] == 0,
           'access=%s bind=%s' % (st['access_type'], st['bind_status']))
    s, code, parsed = api_query('POST', '/infra/file/retry-oss-promotion', token=token, tenant=1,
                                params={'fileId': fid})
    data = (parsed or {}).get('data') or {}
    msg = data.get('message') or ''
    st2 = fetch_oss_state(path)
    expect('D3', '补传接口拒绝私有文件（notPublic）且不迁移',
           code == 0 and '拒绝补传' in msg and st2['config_id'] == 4 and st2['access_type'] == 2,
           'msg=%s cfg=%s access=%s' % (msg, st2['config_id'], st2['access_type']))
    s4, b4, _ = http('GET', 'https://java-ai-comency.oss-cn-beijing.aliyuncs.com/%s' % path)
    expect('D4', '私有文件内容未出现在公共桶（直连非 200）', s4 != 200,
           'HTTP=%s' % s4)
    cleanup(path)

    failed = [r for r in RESULTS if not r['passed']]
    print('\n' + '-' * 72)
    print('失败补传路径验收：%d/%d PASS，结束时间 %s'
          % (len(RESULTS) - len(failed), len(RESULTS), datetime.now().strftime('%Y-%m-%d %H:%M:%S')))
    if failed:
        for r in failed:
            print('  FAIL [%s] %s  <- %s' % (r['id'], r['title'], r['detail']))
    sys.exit(0 if not failed else 1)


if __name__ == '__main__':
    main()
