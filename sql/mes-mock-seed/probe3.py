# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print('== unique indexes mentioning user_id ==')
print(q("SELECT DISTINCT table_name FROM information_schema.statistics WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND column_name='user_id' AND non_unique=0"))
print('== full t1 users ==')
print(q("SELECT GROUP_CONCAT(id ORDER BY id) FROM (SELECT DISTINCT id FROM system_users WHERE tenant_id=1 AND deleted=0) x"))
print('== full 2010 users (id:nickname) ==')
print(q("SELECT GROUP_CONCAT(CONCAT(id,':',nickname) ORDER BY id SEPARATOR ' | ') FROM system_users WHERE tenant_id=2010 AND deleted=0"))
print('== mes_pro_work_record t1 rows + distinct user_id ==')
print(q("SELECT COUNT(*), COUNT(DISTINCT user_id) FROM mes_pro_work_record WHERE tenant_id=1"))
print('== t1 mes_ tables with data but tenant col excluded candidate check: list tables where t1>0 group by domain ==')
print(q("SELECT SUBSTRING_INDEX(table_name,'_',2) grp, COUNT(*) n FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' GROUP BY grp"))
