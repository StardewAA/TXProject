# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        return 'ERR ' + p.stderr.decode('utf-8', 'replace')[:300]
    return p.stdout.decode('utf-8', 'replace').strip()
mismatch = 0
total = 0
out = []
for tb in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split():
    c1 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb)
    c2 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=2010" % tb)
    total += 1
    if c1 != c2:
        mismatch += 1
        out.append('%s t1=%s t2010=%s  <-- MISMATCH' % (tb, c1, c2))
print('tables checked=%d mismatch=%d' % (total, mismatch))
for l in out[:60]:
    print(' ', l)
print('\n== sample 2010 references ==')
print('material_stock w/l/a existence:')
print(q("SELECT (SELECT COUNT(*) FROM mes_wm_material_stock s LEFT JOIN mes_wm_warehouse w ON w.id=s.warehouse_id AND w.tenant_id=2010 LEFT JOIN mes_wm_warehouse_area a ON a.id=s.area_id AND a.tenant_id=2010 LEFT JOIN mes_wm_warehouse_location l ON l.id=s.location_id AND l.tenant_id=2010 WHERE s.tenant_id=2010 AND (w.id IS NULL OR a.id IS NULL OR l.id IS NULL)) AS dangling"))
print('receipt detail line refs ok:')
print(q("SELECT (SELECT COUNT(*) FROM mes_wm_item_receipt_line l LEFT JOIN mes_wm_item_receipt h ON h.id=l.receipt_id AND h.tenant_id=2010 WHERE l.tenant_id=2010 AND h.id IS NULL) AS dangling_line_header"))
print('qc iqc_line -> iqc header dangling:')
print(q("SELECT (SELECT COUNT(*) FROM mes_qc_iqc_line l LEFT JOIN mes_qc_iqc h ON h.id=l.iqc_id AND h.tenant_id=2010 WHERE l.tenant_id=2010 AND h.id IS NULL) AS dangling"))
print('work order bom -> work order:')
print(q("SELECT (SELECT COUNT(*) FROM mes_pro_work_order_bom l LEFT JOIN mes_pro_work_order h ON h.id=l.work_order_id AND h.tenant_id=2010 WHERE l.tenant_id=2010 AND h.id IS NULL) AS dangling"))
print('cal team member team:')
print(q("SELECT (SELECT COUNT(*) FROM mes_cal_team_member m LEFT JOIN mes_cal_team t ON t.id=m.team_id AND t.tenant_id=2010 WHERE m.tenant_id=2010 AND t.id IS NULL) AS dangling"))
print('\n== sample workshop (global unique codes now suffixed) ==')
print(q("SELECT code,name FROM mes_md_workshop WHERE tenant_id=2010"))
print('== sample material stock count & item names ==')
print(q("SELECT i.name, s.warehouse_id, s.area_id, s.location_id, s.quantity FROM mes_wm_material_stock s LEFT JOIN mes_md_item i ON i.id=s.item_id AND i.tenant_id=2010 WHERE s.tenant_id=2010 LIMIT 6"))
