# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-uroot', '-p123456', '--default-character-set=utf8mb4', '-t', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return p.stdout.decode('utf-8', 'replace')
print(q("SELECT id,username,nickname,tenant_id FROM system_users WHERE username LIKE '%hua%' OR nickname LIKE '%huahan%' OR username LIKE '%han%';"))
print(q("SELECT id,username,nickname,tenant_id FROM system_users ORDER BY tenant_id,id LIMIT 200;"))
