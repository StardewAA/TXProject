# -*- coding: utf-8 -*-
import io, os, subprocess, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '-N', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
def q(sql):
    p = subprocess.run(MYSQL + ['-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')
SKIP = {'mes_md_auto_code_part', 'mes_md_auto_code_rule', 'mes_md_auto_code_record'}
clone = []
for tb in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split():
    if tb not in SKIP and int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=1" % tb).strip()) > 0:
        clone.append(tb)
# suffix set: strip domain first token, e.g mes_md_item_type -> item_type
suffix = {}
for t in clone:
    parts = t.split('_')          # ['mes','md','item','type']
    sfx = '_'.join(parts[2:])     # remove 'mes' and domain token
    suffix.setdefault(sfx, t)
    # also register md_item -> 'item' etc.? handled via suffix
# exact concept suffix like 'item_type'
lines = io.open(os.path.join(HERE, 'refs_candidates.txt'), encoding='utf-8').read().splitlines()
out = []
auto = 0
for ln in lines:
    tb, col = ln.split()[0], ln.split()[1]
    if col.endswith('_id'):
        cname = col[:-3]
    else:
        out.append(ln + '   [no _id suffix?]')
        continue
    if cname in suffix and suffix[cname] != tb:
        auto += 1
        continue
    # suffix that appears in MULTIPLE tables (ambiguous) -> list it
    same = [t for t in clone if cname == '_'.join(t.split('_')[2:])]
    if len(same) == 1 and same[0] != tb:
        auto += 1
        continue
    out.append(ln)
with io.open(os.path.join(HERE, 'refs_unclear.txt'), 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))
print('auto-matched=%d unclear=%d -> refs_unclear.txt' % (auto, len(out)))
