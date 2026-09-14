# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
print('== data types of interesting cols ==')
print(q("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name IN ('mes_md_item','mes_md_workshop_worker','mes_md_workshop','mes_pro_work_record','mes_wm_warehouse') AND column_name IN ('creator','updater','charge_user_id','post_id','user_id')"))
print('== sample creator values (mes_md_item t1) ==')
print(q("SELECT DISTINCT creator, updater FROM mes_md_item WHERE tenant_id=1"))
print('== sample user_id values (mes_pro_work_record t1) ==')
print(q("SELECT user_id FROM mes_pro_work_record WHERE tenant_id=1"))
print('== t1 posts ==')
print(q("SELECT GROUP_CONCAT(CONCAT(id,':',name) ORDER BY id SEPARATOR ' | ') FROM system_post WHERE tenant_id=1"))
print('== 2010 posts ==')
print(q("SELECT GROUP_CONCAT(CONCAT(id,':',name) ORDER BY id SEPARATOR ' | ') FROM system_post WHERE tenant_id=2010"))
print('== 2010 huahan user-post ==')
print(q("SELECT user_id, post_id FROM system_user_post WHERE user_id=80370"))
print('== mes_md_workstation_worker t1 rows ==')
print(q("SELECT id, workshop_id, workstation_id, user_id, post_id, tenant_id FROM mes_md_workstation_worker WHERE tenant_id=1"))
