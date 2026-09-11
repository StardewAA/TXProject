# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')

tbs = [t for t in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split() if t]
out = []
for tb in tbs:
    col = q("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' AND column_name='tenant_id'" % tb).strip()
    if col == '0':
        continue
    c1 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb).strip()
    c2 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=2010" % tb).strip()
    out.append('%-40s t1=%-6s t2010=%s' % (tb, c1, c2))
with io.open('inventory_result.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))
print('tables=%d saved=inventory_result.txt' % len(tbs))

print('\n== FK constraints on mes_ tables ==')
fks = q("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='ruoyi-vue-pro' AND (table_name LIKE 'mes_%%' OR referenced_table_name LIKE 'mes_%%')")
print('fk count:', fks)

print('\n== columns referencing users (name contains user_id/charge_user/operator/audit) ==')
rows = q("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND (column_name LIKE '%user_id%' OR column_name LIKE '%charge_user%' OR column_name LIKE '%operator%' OR column_name LIKE '%audit_user%' OR column_name LIKE '%inspector%' OR column_name LIKE '%worker%' OR column_name LIKE '%receiver%' OR column_name LIKE '%issuer%') ORDER BY table_name,column_name")
print(rows)
print('\n== users of tenant 1 (top 30) ==')
print(q("SELECT id,username,nickname FROM system_users WHERE tenant_id=1 AND deleted=0 ORDER BY id LIMIT 30"))
print('\n== does tenant 1 have user 103/104/107? ==')
print(q("SELECT id,username,nickname FROM system_users WHERE tenant_id=1 AND id IN (103,104,107)"))
