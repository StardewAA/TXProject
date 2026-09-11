# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:600]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print(q("SELECT id, team_id, user_id, tenant_id FROM mes_cal_team_member WHERE tenant_id=1 LIMIT 8"))
print(q("SELECT id, team_id, shift_id, tenant_id FROM mes_cal_team_shift WHERE tenant_id=1 LIMIT 8"))
print(q("SELECT id, shift_id, tenant_id FROM mes_cal_plan_shift WHERE tenant_id=1 LIMIT 8"))
print(q("SELECT id, name FROM mes_cal_team WHERE tenant_id=1 LIMIT 8"))
