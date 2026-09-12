# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return p.stdout.decode('utf-8', 'replace').strip() if p.returncode == 0 else 'ERR ' + p.stderr.decode('utf-8', 'replace')[:300]
for T in ('1', '2010'):
    print('tenant', T)
    print(' stock dangling(w/a/l):',
          q("SELECT COUNT(*) FROM mes_wm_material_stock s LEFT JOIN mes_wm_warehouse w ON w.id=s.warehouse_id AND w.tenant_id=%s LEFT JOIN mes_wm_warehouse_area a ON a.id=s.area_id AND a.tenant_id=%s LEFT JOIN mes_wm_warehouse_location l ON l.id=s.location_id AND l.tenant_id=%s WHERE s.tenant_id=%s AND (w.id IS NULL OR a.id IS NULL OR l.id IS NULL)" % (T, T, T, T)))
    print(' stock item null:',
          q("SELECT COUNT(*) FROM mes_wm_material_stock s LEFT JOIN mes_md_item i ON i.id=s.item_id AND i.tenant_id=%s WHERE s.tenant_id=%s AND i.id IS NULL" % (T, T)))
print('== barcode/biz & misc untouched cols (tenant2010 same values as 1, sanity) ==')
print(q("SELECT COUNT(*) FROM mes_wm_misc_receipt WHERE tenant_id=2010 AND code NOT LIKE '%%-2010'"))
print('== qc defect record 2010 qc_id reachable to any qc doc? (informational) ==')
print(q("SELECT COUNT(*) FROM mes_qc_defect_record WHERE tenant_id=2010"))
print('== total 2010 MES rows ==')
print(q("SELECT SUM(c) FROM (SELECT COUNT(*) c FROM information_schema.tables t WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%') x"))
