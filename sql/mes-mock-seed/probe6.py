# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print('== mes_md_workstation_worker columns ==')
print(q("SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='mes_md_workstation_worker' ORDER BY ordinal_position"))
print('== rows t1 ==')
print(q("SELECT * FROM mes_md_workstation_worker WHERE tenant_id=1"))
print('== 2010 user_post all ==')
print(q("SELECT user_id, post_id FROM system_user_post WHERE user_id BETWEEN 80370 AND 80407 ORDER BY user_id"))
print('== t1 mes tables containing post_id/dept_id anywhere ==')
print(q("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' AND column_name IN ('post_id','dept_id')"))
