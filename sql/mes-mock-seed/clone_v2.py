# -*- coding: utf-8 -*-
"""Clone tenant-1 MES rows -> tenant 2010 (docker DB 13306). v2: id-shift cloning.

Problem: copying a table into itself for another tenant collides on global PK.
Solution: every cloned row gets  new_id = old_id + SHIFT (SHIFT=2_000_000_000),
and every FK-ish column referencing a cloned table gets the same +SHIFT,
so all intra-MES relations stay intact. User references get remapped to 2010
users; creator/updater get remapped to 2010 usernames.

Detect ref columns heuristically (name token + value overlap) and print a
mapping report for review (dry mode) before executing.
Usage:
  python clone_v2.py dry   # build SQL + report only
  python clone_v2.py run   # execute too
"""
import io, os, re, subprocess, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'clone_v2.sql')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
SRC, DST, SHIFT = 1, 2010, 2000000000
RUN = len(sys.argv) > 1 and sys.argv[1] == 'run'

def q(sql):
    p = subprocess.run(MYSQL + ['-N', '-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR:', p.stderr.decode('utf-8', 'replace')[:600]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')

def users(T):
    out = []
    for line in q("SELECT id, username FROM system_users WHERE tenant_id=%d AND deleted=0 AND status=0 ORDER BY id" % T).splitlines():
        if not line:
            continue
        a, b = line.split('\t', 1)
        out.append((int(a), b))
    return out

src_users = users(SRC); dst_users = users(DST)
uid_map = {a: b for (a, _), (b, _) in zip(src_users, dst_users)}
uname_map = {a: b for (_, a), (_, b) in zip(src_users, dst_users)}
print('# src_users=%d dst_users=%d' % (len(src_users), len(dst_users)))

SKIP = {'mes_md_auto_code_part', 'mes_md_auto_code_rule', 'mes_md_auto_code_record'}
allmes = [t for t in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split() if t]

def cols_of(tb):
    return q("SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' ORDER BY ordinal_position" % tb).split()

def rows_of(tb, cols):
    return [r.split('\t') for r in q(
        "SELECT %s FROM `%s` WHERE tenant_id=%d" % (','.join('`%s`' % c for c in cols), tb, SRC)).splitlines()]

# tables to clone with t1 counts and column lists
clone = []
for tb in allmes:
    if tb in SKIP:
        continue
    n = int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).strip())
    if n:
        clone.append((tb, n, cols_of(tb)))
print('# clone tables=%d' % len(clone))

# source id sets of every cloned table
idsets = {tb: set(q("SELECT id FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).split()) for tb, _, _ in clone}
# concept map: normalized table label for name matching
def concept(tb):
    return tb[4:]  # strip 'mes_'
# map concept token -> table. e.g. md_item_type, md_workshop
CONC = {}
for tb, _, _ in clone:
    CONC.setdefault(concept(tb), tb)

def find_ref_sources(tb, col, values):
    """return list of candidate source tables referenced by this column."""
    vals = [v for v in values if v and v != 'NULL' and v.isdigit()]
    if not vals or len(vals) > 3000:  # skip huge/odd
        return []
    vset = set(vals)
    # candidate by column-name token matching
    cname = col[:-3] if col.endswith('_id') else col
    tokens = cname.split('_')
    cand = set()
    for i in range(len(tokens)):
        sub = '_'.join(tokens[i:])
        if sub in CONC:
            cand.add(CONC[sub])
    # self-reference
    if tb in cand:
        cand.discard(tb)  # handle separately below
    # score by value coverage among candidates
    best = []
    for c in sorted(cand):
        ids = idsets[c]
        hit = vset & ids
        if hit:
            best.append((len(hit), c, hit))
    best.sort(key=lambda x: -x[0])
    # exact full-token match priority
    if cname in CONC:
        exact = CONC[cname]
        if any(v in idsets[exact] for v in vset):
            return [exact]
    return [c for _, c, _ in best] or None

# column classification map: (tb, col) -> 'user' | 'creator' | 'tenant' | ('ref', sourcetb) | None
report = []
refmap = {}
for tb, n, cols in clone:
    rowdata = rows_of(tb, cols)
    for ci, col in enumerate(cols):
        if col == 'tenant_id':
            continue
        if col == 'id':
            continue
        if col in ('creator', 'updater'):
            continue
        if re.search(r'(^|_)user_id$', col):
            continue
        if tb == 'mes_md_workstation_worker' and col == 'post_id':
            continue
        if not re.search(r'_id$', col):
            continue
        values = [r[ci] for r in rowdata]
        # self parent reference
        srcs = find_ref_sources(tb, col, values)
        if srcs:
            refmap[(tb, col)] = srcs[0]
            report.append('%-34s %-28s -> %s (n=%d)' % (tb, col, srcs[0], len(set(v for v in values if v not in ('NULL', '')))))
        else:
            # possible self ref (parent_id)
            self_hit = values and idsets[tb] & set(v for v in values if v and v.isdigit())
            if self_hit:
                refmap[(tb, col)] = tb
                report.append('%-34s %-28s -> %s (self)' % (tb, col, tb))
print('# detected intra-MES ref columns: %d' % len(report))
for r in report:
    print('   ', r)

# ---- build SQL ----
def expr(tb, col):
    if col == 'tenant_id':
        return str(DST)
    if col == 'id':
        return '`id` + %d' % SHIFT
    if col in ('creator', 'updater'):
        tok = {}
        for (oid, oname), (nid, nname) in zip(src_users, dst_users):
            tok[str(oid)] = nname
            tok[oname] = nname
        return 'CASE `%s` %s ELSE `%s` END' % (col, ' '.join("WHEN '%s' THEN '%s'" % (k, v) for k, v in tok.items()), col)
    if re.search(r'(^|_)user_id$', col):
        return 'CASE `%s` %s ELSE `%s` END' % (col, ' '.join('WHEN %d THEN %d' % (a, b) for a, b in uid_map.items()), col)
    if tb == 'mes_md_workstation_worker' and col == 'post_id':
        return 'CASE `%s` WHEN 1 THEN 70190 WHEN 2 THEN 70193 ELSE `%s` END' % (col, col)
    if (tb, col) in refmap:
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
if not RUN:
    print('dry done (no execute). re-run with "run".')
    sys.exit(0)

p = subprocess.run(MYSQL, input='\n'.join(lines).encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if p.returncode != 0:
    print('RUN ERROR rc=%d:\n%s' % (p.returncode, p.stderr.decode('utf-8', 'replace')[:8000]))
    sys.exit(2)
print('# executed ok')
