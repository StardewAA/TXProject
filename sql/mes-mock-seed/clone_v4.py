# -*- coding: utf-8 -*-
"""Final clone: tenant-1 MES rows -> tenant 2010 (docker DB @13306). id-shift clone.

- every cloned row id += SHIFT (2000000000); every numeric *_id FK whose value is
  found inside any cloned source table's id space also gets +SHIFT (safe because
  ALL cloned tables share the same SHIFT -> any intra-MES reference stays valid).
- *_user_id columns: values present in uid_map (t1 sys user -> 2010 user) are
  remapped; other numeric user ids are remapped deterministically to spare 2010
  users (stable per distinct value).
- creator/updater: t1 username/id tokens -> 2010 usernames.
- kept as-is: source_doc_id / source_line_id / biz_id / biz_line_id /
  related_transaction_id / handler_role_id / post_id (audit/cross-doc refs and
  system-role refs) — not part of the cloned MES doc space.
- auto_code tables and pollution/project (t1 empty) are not cloned.

Run: python clone_v4.py
"""
import io, os, re, subprocess, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'clone_v4.sql')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
SRC, DST, SHIFT = 1, 2010, 2000000000
KEEP = {'source_doc_id', 'source_line_id', 'biz_id', 'biz_line_id',
        'related_transaction_id', 'handler_role_id', 'post_id'}

def q(sql):
    p = subprocess.run(MYSQL + ['-N', '-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR:', p.stderr.decode('utf-8', 'replace')[:700]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')

def users(T):
    return [(int(a), b) for line in q(
        "SELECT id, username FROM system_users WHERE tenant_id=%d AND deleted=0 AND status=0 ORDER BY id" % T).splitlines()
        for a, b in [line.split('\t', 1)] if line]

src_users = users(SRC); dst_users = users(DST)
uid_map = {a: b for (a, _), (b, _) in zip(src_users, dst_users)}
print('# src_users=%d dst_users=%d' % (len(src_users), len(dst_users)))

SKIP = {'mes_md_auto_code_part', 'mes_md_auto_code_rule', 'mes_md_auto_code_record'}
clone = []
for tb in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split():
    if tb in SKIP:
        continue
    n = int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).strip())
    if not n:
        continue
    cols = q("SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' ORDER BY ordinal_position" % tb).split()
    clone.append((tb, n, cols))
print('# clone tables=%d' % len(clone))
ids_all = set().union(*[set(q("SELECT id FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).split()) for tb, _, _ in clone])
print('# source rows ids total=%d' % len(ids_all))

# -- detect globally unique string columns: unique index NOT containing tenant_id
glb = {}   # (tb, col) -> data_type
for tb, _, _ in clone:
    for line in q("SELECT index_name, GROUP_CONCAT(column_name) FROM information_schema.statistics "
                  "WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' AND non_unique=0 AND index_name<>'PRIMARY' "
                  "GROUP BY index_name" % tb).splitlines():
        ix, cnames = line.split('\t')
        cols = cnames.split(',')
        if 'tenant_id' in cols:
            continue
        for cn in cols:
            if cn in ('id', 'tenant_id', 'deleted'):
                continue
            dt = q("SELECT data_type FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' AND column_name='%s'" % (tb, cn)).strip()
            if dt in ('char', 'varchar', 'text'):
                glb[(tb, cn)] = dt
print('# global unique string cols=%d' % len(glb))

# -- clean target tenant leftovers (only our partial clone)
for tb, _, _ in clone:
    q("DELETE FROM `%s` WHERE tenant_id=%d" % (tb, DST))
print('# cleaned tenant 2010 rows in clone tables')

userpat = re.compile(r'(^|_)user_id$')
keep_pat = re.compile('|'.join('^%s$' % re.escape(c) for c in KEEP))

def col_values(tb, col):
    return q("SELECT DISTINCT `%s` FROM `%s` WHERE tenant_id=%d AND `%s` IS NOT NULL AND `%s`<>0" % (col, tb, SRC, col, col)).split()

# per-column user fallback map (distinct non-uid_map values -> spare dst users)
def user_expr(tb, col):
    vals = col_values(tb, col)
    miss = [v for v in vals if v not in uid_map]
    spare = [i for i, _ in dst_users]  # cycling spare
    cmap = {}
    for i, v in enumerate(miss):
        cmap[v] = spare[i % len(spare)]
    parts = ['CASE `%s`' % col]
    for k, v in uid_map.items():
        parts.append('WHEN %d THEN %d' % (int(k), v))
    for k, v in cmap.items():
        parts.append('WHEN %d THEN %d' % (int(k), v))
    parts.append('ELSE `%s` END' % col)
    return ' '.join(parts)

def creator_expr(col):
    tok = {}
    for (oid, oname), (_, nname) in zip(src_users, dst_users):
        tok[str(oid)] = nname
        tok[oname] = nname
    return 'CASE `%s` %s ELSE `%s` END' % (col, ' '.join("WHEN '%s' THEN '%s'" % (k, v) for k, v in tok.items()), col)

def is_ref(tb, col, vals):
    if keep_pat.match(col):
        return False
    if col in ('parent_id', 'primary_id'):
        return any(v in ids_all for v in vals)
    if not vals:
        return False
    hit = sum(1 for v in vals if v in ids_all)
    return hit / len(vals) >= 0.5 or (vals and any(v in ids_all for v in vals))

def expr(tb, col):
    if col == 'tenant_id':
        return str(DST)
    if col == 'id':
        return '`id` + %d' % SHIFT
    if col in ('creator', 'updater'):
        return creator_expr(col)
    if (tb, col) in glb:
        return 'CONCAT(`%s`, \'-%d\')' % (col, DST)
    if userpat.search(col):
        return user_expr(tb, col)
    if re.search(r'_id$', col):
        vals = col_values(tb, col)
        if is_ref(tb, col, vals):
            return 'CASE WHEN `%s` IS NULL THEN NULL WHEN `%s` = 0 THEN 0 ELSE `%s` + %d END' % (col, col, col, SHIFT)
    return '`%s`' % col

lines = ['SET FOREIGN_KEY_CHECKS=0;', 'SET NAMES utf8mb4;']
for tb, n, cols in clone:
    ins = ', '.join('`%s`' % c for c in cols)
    sel = ', '.join(expr(tb, c) for c in cols)
    lines.append('INSERT INTO `%s` (%s)\nSELECT %s FROM `%s` WHERE tenant_id=%d;' % (tb, ins, sel, tb, SRC))
with io.open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))
print('# sql written: %s' % OUT)

p = subprocess.run(MYSQL, input='\n'.join(lines).encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if p.returncode != 0:
    print('RUN ERROR rc=%d:\n%s' % (p.returncode, p.stderr.decode('utf-8', 'replace')[:8000]))
    sys.exit(2)
print('# executed ok')
