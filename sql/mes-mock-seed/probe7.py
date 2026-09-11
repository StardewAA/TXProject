# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:600]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print('total rows:', q("SELECT COUNT(*) FROM mes_cal_holiday"))
print('tenant null rows:', q("SELECT COUNT(*) FROM mes_cal_holiday WHERE tenant_id IS NULL"))
print('id=1 rows:', q("SELECT id, tenant_id, deleted FROM mes_cal_holiday WHERE id=1"))
print('all id/distinct tenant:', q("SELECT id, tenant_id FROM mes_cal_holiday ORDER BY id"))
print('min/max id:', q("SELECT MIN(id), MAX(id) FROM mes_cal_holiday"))
