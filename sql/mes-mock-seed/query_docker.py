# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
def q(sql, port):
    MYSQL = ['mysql', '-h127.0.0.1', '-P' + str(port), '-uroot', '-p123456', '--default-character-set=utf8mb4', '-t', 'ruoyi-vue-pro']
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return p.stdout.decode('utf-8', 'replace')
print("== 租户(华翰相关) ==")
print(q("SELECT id,name,status,expire_time FROM system_tenant WHERE name LIKE '%华翰%' OR name LIKE '%节能%' OR name LIKE '%建材%';", 13306))
print("== huahan 用户 ==")
print(q("SELECT u.id,u.username,u.nickname,u.tenant_id,t.name tenant_name FROM system_users u LEFT JOIN system_tenant t ON t.id=u.tenant_id WHERE u.username='huahan';", 13306))
print("== 全部租户 ==")
print(q("SELECT id,name,status FROM system_tenant ORDER BY id;", 13306))
print("== MES 物料(按租户分组) ==")
print(q("SELECT tenant_id,COUNT(*) cnt FROM mes_md_item GROUP BY tenant_id;", 13306))
