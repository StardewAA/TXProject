# -*- coding: utf-8 -*-
import io, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
MYSQL = ['mysql', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:500]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
tbs = [t for t in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%'").split() if t]
diff = 0
for tb in tbs:
    try:
        r = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb).strip()
        r2 = q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=121" % tb).strip()
    except Exception:
        continue
    a, b = int(r), int(r2)
    if a == 0:
        continue
    if a != b:
        diff += 1
        print('DIFF %-34s t1=%-4s t121=%s' % (tb, a, b))
print('tables with data@t1=%d, mismatched=%d' % (len([1 for tb in tbs if True]), diff))
