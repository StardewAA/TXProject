#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""定时补传（OssPromoteRetryJob, 5 分钟周期）验收：不改代码配置，不手动触发。

流程：上传 -> 标记 stranded（公开但未进 OSS）-> 等待定时任务自动补传 -> 校验进 OSS(36)
      -> 直连 OSS 对象核对内容 -> 清理。

用法：python verify-oss-scheduled.py
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
    proc = subprocess.run(
        ['docker', 'exec', '-i', MYSQL, 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -N '
         '--default-character-set=utf8mb4 ' + DATABASE],
        input=statement, text=True, capture_output=True, timeout=120)
    if proc.returncode != 0:
        raise RuntimeError('SQL 失败：%s\n%s' % (statement[:300], proc.stderr.strip()))
    return proc.stdout


def http(method, url, headers=None, body=None, timeout=40):
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), resp.headers.get('Content-Type', '')
    except urllib.error.HTTPError as err:
        return err.code, err.read(), err.headers.get('Content-Type', '')


def api(method, path, token=None, tenant=None, payload=None, api_group='admin-api'):
    headers = {'terminal': '20'}
    if payload is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if tenant is not None:
        headers['tenant-id'] = str(tenant)
    body = None if payload is None else json.dumps(payload).encode()
    status, raw, _ = http(method, GATEWAY + '/' + api_group + path, headers, body)
    try:
        parsed = json.loads(raw)
    except ValueError:
        return status, None, raw[:200].decode('utf-8', 'replace')
    return status, parsed.get('code'), parsed


def admin_login(username, password, tenant):
    status, code, parsed = api('POST', '/system/auth/login', tenant=tenant,
                               payload={'username': username, 'password': password})
    if code != 0:
        raise RuntimeError('登录失败：%s' % parsed)
    return parsed['data']['accessToken'], parsed['data']['userId']


def upload(token, tenant, directory='mall/shop-logo'):
    boundary = '----Sched' + uuid.uuid4().hex
    filename = 'sched-%s.png' % uuid.uuid4().hex[:10]
    chunks = [
        ('--%s\r\nContent-Disposition: form-data; name="directory"\r\n\r\n%s\r\n'
         % (boundary, directory)).encode(),
        ('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\n'
         'Content-Type: image/png\r\n\r\n' % (boundary, filename)).encode(),
        PNG_BYTES,
        ('\r\n--%s--\r\n' % boundary).encode(),
    ]
    headers = {'Content-Type': 'multipart/form-data; boundary=' + boundary,
               'tenant-id': str(tenant), 'terminal': '20'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    status, raw, _ = http('POST', '%s/admin-api/infra/file/upload' % GATEWAY, headers, b''.join(chunks))
    parsed = json.loads(raw)
    if parsed.get('code') != 0:
        raise RuntimeError('上传失败：%s' % parsed)
    return parsed['data']


def split_url(url):
    m = re.search(r'/infra/file/(\d+)/(?:get|preview)/(.+)$', url)
    if m:
        return int(m.group(1)), urllib.parse.unquote(m.group(2))
    raise RuntimeError('无法解析：%s' % url)


def fetch_state(path):
    out = sql("SELECT CONCAT_WS('|', id, config_id, IFNULL(url,''), IFNULL(promote_status,0),"
              " IFNULL(promote_fail_count,0)) FROM infra_file"
              " WHERE path='%s' AND deleted=0 ORDER BY id DESC LIMIT 1;" % path.replace("'", "''")).strip()
    if not out:
        return None
    a = out.split('|')
    return {'id': a[0], 'config_id': int(a[1]), 'url': a[2],
            'promote_status': int(a[3]), 'promote_fail_count': int(a[4])}


def record(case_id, title, passed, detail=''):
    RESULTS.append({'id': case_id, 'title': title, 'passed': passed, 'detail': detail})
    print('[%s] %-5s %s%s' % ('PASS' if passed else 'FAIL', case_id, title,
                              ('  <- ' + detail) if detail else ''))


def main():
    print('=' * 72)
    print('定时补传验收  开始时间 %s' % datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
    token, _ = admin_login('admin', 'admin123', 1)
    u = upload(token, 1, 'mall/shop-logo')
    cfg, path = split_url(u)
    record('S1', '上传落 master(4)', cfg == 4, 'cfg=%s path=%s' % (cfg, path))
    sql("UPDATE infra_file SET access_type=1, bind_status=2, config_id=4, "
        "promote_status=3, promote_fail_count=1, "
        "promote_last_error='simulated OSS handshake reset (test-scoped fault)' "
        "WHERE path='%s' AND deleted=0;" % path.replace("'", "''"))
    st = fetch_state(path)
    record('S2', '已标记 stranded（公开未进 OSS，等定时任务）',
           st['config_id'] == 4 and st['promote_status'] == 3, 'cfg=%s status=%s' % (st['config_id'], st['promote_status']))

    # 等待定时任务（约 5 分钟周期），最多 8 分钟；期间不做任何手动补传
    print('等待定时任务 OssPromoteRetryJob 自动补传（最多 8 分钟，不手动触发）…')
    promoted = False
    deadline = time.time() + 8 * 60
    while time.time() < deadline:
        st = fetch_state(path)
        if st and st['config_id'] == 36:
            promoted = True
            break
        time.sleep(15)
    st = fetch_state(path)
    record('S3', '定时任务自动补传进 OSS(36)', promoted and st['config_id'] == 36,
           'cfg=%s status=%s' % (st['config_id'] if st else None,
                                 st['promote_status'] if st else None))
    if promoted:
        s4, b4, _ = http('GET', st['url'])
        record('S4', '定时补传后直连 OSS 内容一致', s4 == 200 and b4 == PNG_BYTES,
               'HTTP=%s match=%s' % (s4, b4 == PNG_BYTES))

    sql("DELETE FROM infra_file_content WHERE path='%s';" % path.replace("'", "''"))
    sql("DELETE FROM infra_file WHERE path='%s' AND deleted=0;" % path.replace("'", "''"))
    failed = [r for r in RESULTS if not r['passed']]
    print('定时补传验收：%d/%d PASS，结束时间 %s'
          % (len(RESULTS) - len(failed), len(RESULTS), datetime.now().strftime('%Y-%m-%d %H:%M:%S')))
    print('遗留 OSS 对象：%s' % path)
    sys.exit(0 if not failed else 1)


if __name__ == '__main__':
    main()
