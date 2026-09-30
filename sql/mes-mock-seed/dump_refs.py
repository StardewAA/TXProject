# -*- coding: utf-8 -*-
import io, os, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR', p.stderr.decode('utf-8', 'replace')[:600]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
SKIP = {'mes_md_auto_code_part', 'mes_md_auto_code_rule', 'mes_md_auto_code_record'}
out = []
clone = []
for tb in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split():
    if tb in SKIP:
        continue
    n = int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb).strip())
    if n:
        clone.append((tb, n))
for tb, n in clone:
    cinfo = [(c.split('\t')[0], c.split('\t')[1]) for c in q(
        "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' ORDER BY ordinal_position" % tb).splitlines()]
    names = [c for c, _ in cinfo]
    rows = [r.split('\t') for r in q("SELECT %s FROM `%s` WHERE tenant_id=1" % (','.join('`%s`' % c for c in names), tb)).splitlines()]
    for ci, (col, dt) in enumerate(cinfo):
        if not col.endswith('_id') or col in ('tenant_id',) or dt not in ('bigint', 'int', 'smallint'):
            continue
        vals = []
        for r in rows:
            if len(r) != len(names):
                break
            v = r[ci]
            if v not in ('NULL', '', '\\0', '0') and v not in vals:
                vals.append(v)
        if not vals:
            continue
        out.append('%s|%s|n=%d|sample=%s' % (tb, col, n, ','.join(vals[:12])))
with io.open(os.path.join(HERE, 'refs2.txt'), 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))
print('real ref cols=%d -> refs2.txt' % len(out))
