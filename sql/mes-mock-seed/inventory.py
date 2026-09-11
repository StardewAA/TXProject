# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
tbs = q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split()
print('== has tenant_id column? + counts t1 vs t2010 ==')
withten = []
for tb in tbs:
    col = q("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' AND column_name='tenant_id'" % tb).strip()
    if col == '0':
        continue
    withten.append(tb)
    c1 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb).strip()
    c2 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=2010" % tb).strip()
    flag = ''
    if c1 != '0' and c2 == '0':
        flag = '  <-- 2010缺'
    elif c1 == c2:
        flag = ''
    print('%-38s t1=%-5s t2010=%s%s' % (tb, c1, c2, flag))
print()
print('== 有 tenant_id 列的 mes 表数: %d / %d' % (len(withten), len(tbs)))
nohas = [t for t in tbs if t not in withten]
print('无 tenant_id 列的 mes 表:', ', '.join(nohas))
print()
print('== 2010 租户用户 / 角色 ==')
print(q("SELECT id,username,nickname,dept_id FROM system_users WHERE tenant_id=2010 AND deleted=0"))
