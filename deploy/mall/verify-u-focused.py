#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""聚焦验证：单文件 OSS 补传（retry-oss-promotion, fileId=query）是否真把文件送入公共桶。

与 verify-file-access.py 的 U 系列等价，但：
- 先暂停定时任务干扰（通过先造 stranded，再立即补传，缩短窗口）
- 每次补传后立刻打印 DB 真实状态（config_id / promote_status / promote_last_error）
- 补传成功后直接 GET OSS 对象 URL，比对内容是否为原始 PNG（用户要求的"真实进入 OSS"）
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

GATEWAY = os.environ.get('FILE_ACL_GATEWAY', 'http://localhost:48080')
MYSQL = os.environ.get('FILE_ACL_MYSQL_CONTAINER', 'deploy-mysql-1')
DATABASE = os.environ.get('FILE_ACL_DATABASE', 'ruoyi-vue-pro')
PNG_BYTES = bytes.fromhex(
    '89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489'
    '0000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082')

def sql(stmt):
    p = subprocess.run(['docker','exec','-i',MYSQL,'sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -N --default-character-set=utf8mb4 '+DATABASE],
        input=stmt, text=True, capture_output=True, timeout=120)
    if p.returncode != 0:
        raise RuntimeError('SQL失败: %s\n%s' % (stmt[:200], p.stderr.strip()))
    return p.stdout

def http(method, url, headers=None, body=None, timeout=40):
    req = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(), r.headers.get('Content-Type','')
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get('Content-Type','')

def api(method, path, token=None, tenant=None, payload=None, api_group='admin-api'):
    h = {'terminal':'20'}
    if payload is not None: h['Content-Type']='application/json'
    if token: h['Authorization']='Bearer '+token
    if tenant is not None: h['tenant-id']=str(tenant)
    body = json.dumps(payload).encode() if payload is not None else None
    s,raw,_ = http(method, GATEWAY+'/'+api_group+path, h, body)
    try: parsed=json.loads(raw)
    except ValueError: return s,None,raw[:200].decode('utf-8','replace')
    return s, parsed.get('code'), parsed

def api_query(method, path, token=None, tenant=None, params=None, api_group='admin-api'):
    q = ('?'+urllib.parse.urlencode(params)) if params else ''
    h = {'terminal':'20'}
    if token: h['Authorization']='Bearer '+token
    if tenant is not None: h['tenant-id']=str(tenant)
    s,raw,_ = http(method, GATEWAY+'/'+api_group+path+q, h, None)
    try: parsed=json.loads(raw)
    except ValueError: return s,None,raw[:200].decode('utf-8','replace')
    return s, parsed.get('code'), parsed

def admin_login(u,pw,tenant):
    s,code,parsed=api('POST','/system/auth/login',tenant=tenant,payload={'username':u,'password':pw})
    assert code==0, parsed
    return parsed['data']['accessToken'], parsed['data']['userId']

def upload(token,tenant,directory='mall/shop-logo'):
    boundary='----UFocus'+uuid.uuid4().hex
    fn='u-focus-%s.png'%uuid.uuid4().hex[:10]
    chunks=[
        ('--%s\r\nContent-Disposition: form-data; name="directory"\r\n\r\n%s\r\n'%(boundary,directory)).encode(),
        ('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\nContent-Type: image/png\r\n\r\n'%(boundary,fn)).encode(),
        PNG_BYTES,
        ('\r\n--%s--\r\n'%boundary).encode(),
    ]
    h={'Content-Type':'multipart/form-data; boundary='+boundary,'terminal':'20','Authorization':'Bearer '+token}
    s,raw,_=http('POST',GATEWAY+'/admin-api/infra/file/upload',h,b''.join(chunks))
    parsed=json.loads(raw)
    assert parsed.get('code')==0, parsed
    return parsed['data']

def split_url(url):
    m=re.search if False else __import__('re').search
    import re
    mt=re.search(r'/infra/file/(\d+)/(?:get|preview)/(.+)$',url)
    if mt: return int(mt.group(1)), urllib.parse.unquote(mt.group(2))
    row=sql("SELECT CONCAT_WS('|',config_id,path) FROM infra_file WHERE url='%s' AND deleted=0 LIMIT 1;"%url.replace("'", "''")).strip()
    if row and '|' in row:
        c,p=row.split('|',1); return int(c),p
    raise RuntimeError('cannot parse %s'%url)

def fetch_row_by_path(path):
    out=sql("SELECT CONCAT_WS('|',id,config_id,access_type,bind_status,IFNULL(promote_status,0),IFNULL(promote_fail_count,0),IFNULL(promote_last_error,'')) FROM infra_file WHERE path='%s' AND deleted=0 LIMIT 1;"%path.replace("'", "''")).strip()
    if not out: return None
    a=out.split('|')
    return {'id':a[0],'config_id':int(a[1]),'access_type':int(a[2]),'bind_status':int(a[3]),
            'promote_status':int(a[4]),'promote_fail_count':int(a[5]),'promote_last_error':a[6]}

# ---------- 主流程 ----------
token,_=admin_login('admin','admin123',1)
u=upload(token,1,'mall/shop-logo')
cfg,path=split_url(u)
print('上传落 master: cfg=%s path=%s'%(cfg,path))
assert cfg==4, 'upload should land on master(4)'

# 模拟 stranded：已确认公开但 OSS 提升失败，停在 master
sql("UPDATE infra_file SET access_type=1, bind_status=2, config_id=4, promote_status=3, "
    "promote_fail_count=5, promote_last_error='simulated OSS TLS handshake reset' WHERE path='%s' AND deleted=0;"
    % path.replace("'", "''"))
row=fetch_row_by_path(path)
fid=int(row['id'])
print('stranded: id=%s config_id=%s access_type=%s bind_status=%s'%(fid,row['config_id'],row['access_type'],row['bind_status']))
assert row['access_type']==1 and row['bind_status']==2 and row['config_id']==4

# 运维清单应能追踪
s,code,parsed=api('GET','/infra/file/public-unpromoted?limit=200',token=token,tenant=1)
ids=[d.get('id') for d in ((parsed or {}).get('data') or [])]
print('public-unpromoted 含该文件:', fid in ids)

# 关键：单文件补传（fileId 作为 query 参数）
print('\n--- 单文件补传 retry-oss-promotion (fileId=%s) ---'%fid)
for i in range(1,7):
    st,cd,pr=api_query('POST','/infra/file/retry-oss-promotion',token=token,tenant=1,params={'fileId':fid})
    row=fetch_row_by_path(path)
    print('尝试%d: HTTP=%s code=%s -> config_id=%s promote_status=%s fail=%s err=%s'
          %(i,st,cd,row['config_id'],row['promote_status'],row['promote_fail_count'],row['promote_last_error'][:80]))
    if row['config_id']==36:
        print('>>> 已进入 OSS (config_id=36)')
        break
    time.sleep(3)

row=fetch_row_by_path(path)
if row['config_id']==36:
    oss_url=sql("SELECT url FROM infra_file WHERE id=%s;"%fid).strip()
    print('\nOSS url: %s'%oss_url)
    # 用户要求的硬验收：直接 GET OSS 对象 URL，确认对象真实存在且内容一致
    st,body,ct=http('GET',oss_url)
    print('U6 直连 GET: HTTP=%s len=%s 内容一致=%s'%(st,len(body),body==PNG_BYTES))
    oss_direct='https://java-ai-comency.oss-cn-beijing.aliyuncs.com/%s'%path
    st2,body2,_=http('GET',oss_direct)
    print('U7 bucket+path 直连: HTTP=%s len=%s 内容一致=%s'%(st2,len(body2),body2==PNG_BYTES))
    ok = st==200 and body==PNG_BYTES and st2==200 and body2==PNG_BYTES
else:
    print('\n!!! 仍未进入 OSS，末次错误: %s'%row['promote_last_error'])
    ok=False

# 闭环：清单应不再包含
s,code,parsed=api('GET','/infra/file/public-unpromoted?limit=200',token=token,tenant=1)
ids2=[d.get('id') for d in ((parsed or {}).get('data') or [])]
print('补传后清单仍含该文件:', fid in ids2, '(False=闭环)')

# 清理
sql("DELETE FROM infra_file_content WHERE path='%s';"%path.replace("'", "''"))
sql("DELETE FROM infra_file WHERE path='%s' AND deleted=0;"%path.replace("'", "''"))
print('\n清理完成。结论: %s' % ('PASS' if ok else 'FAIL'))
sys.exit(0 if ok else 1)
