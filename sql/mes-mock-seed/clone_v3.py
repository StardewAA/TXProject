# -*- coding: utf-8 -*-
"""Clone tenant-1 MES rows -> tenant 2010 (docker DB 13306). v3: id-shift + ref resolver.
Resolve every numeric *_id column's referenced source table by:
  1) column-name suffix matches exactly one clone table concept (rank 3)
  2) col tokens subset of a table name -> value-overlap scoring (rank 1)
  3) self references (parent_id / primary_id) (rank 2)
Unresolved columns stay unchanged and are printed for review.
Usage:
  python clone_v3.py dry   # decisions report + SQL (no execute)
  python clone_v3.py run   # execute SQL too
"""
import io, os, re, subprocess, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'clone_v3.sql')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
SRC, DST, SHIFT = 1, 2010, 2000000000
RUN = len(sys.argv) > 1 and sys.argv[1] == 'run'

def q(sql):
    p = subprocess.run(MYSQL + ['-N', '-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR:', p.stderr.decode('utf-8', 'replace')[:700]); sys.exit(1)
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

def tables_ncols():
    allt = []
    for tb in q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split():
        if tb in SKIP:
            continue
        n = int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).strip())
        if not n:
            continue
        cols = [c.split('\t')[0] for c in q(
            "SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' ORDER BY ordinal_position" % tb).splitlines()]
        allt.append((tb, n, cols))
    return allt

clone = tables_ncols()
print('# clone tables=%d' % len(clone))
idsets = {tb: set(q("SELECT id FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).split()) for tb, _, _ in clone}
conc_map = {}                       # concept (without domain) -> tb ; ambiguity resolved by overlap
concept_no_dom = {tb: '_'.join(tb.split('_')[2:]) for tb, _, _ in clone}
prefix_parents = {}
for tb, _, _ in clone:
    pp = tb.split('_')[2:]
    for i in range(1, len(pp)):
        pre = 'mes_' + tb.split('_')[1] + '_' + '_'.join(pp[:-i]) if i < len(pp) else None
    pre = []
    for cut in range(1, len(pp)):
        cand = 'mes_' + '_'.join(['mes'] if False else []) if False else None
    # simpler: all proper prefixes (multi word granularity not guaranteed) -> use word prefixes
    parts = tb.split('_')  # [mes, dom, ...]
    for cut in range(1, len(parts) - 2 + 1):
        cand = 'mes_' + '_'.join(parts[1:len(parts) - cut])
        if cand in idsets:
            prefix_parents.setdefault(tb, []).append(cand)
            break  # shortest? we take closest (cut=1) then stop -> nearest parent
    # also register exact concept col convenience
    if tb not in conc_map:
        conc_map[concept_no_dom[tb]] = tb

overrides = {
    ('mes_cal_team_shift', 'shift_id'): 'mes_cal_team',
    ('mes_md_workstation_worker', 'post_id'): 'system_post_none',
}
report = []   # decisions that are NOT obvious exact suffix
refmap = {}   # (tb,col) -> target
kept = []     # unresolved

# precompute distinct values for numeric *_id cols (excluding known non-refs)
known = ('tenant_id',)
userpat = re.compile(r'(^|_)user_id$')

def distinct_vals(tb, col):
    return set(v for v in q("SELECT DISTINCT `%s` FROM `%s` WHERE tenant_id=%d AND `%s` IS NOT NULL" % (col, tb, SRC, col)).splitlines() if v.isdigit())

valcache = {}
for tb, n, cols in clone:
    for col in cols:
        if col in known or userpat.search(col) or col in ('creator', 'updater', 'id'):
            continue
        if not re.search(r'_id$', col):
            continue
        if col == 'post_id' and tb == 'mes_md_workstation_worker':
            continue
        vals = valcache.setdefault((tb, col), distinct_vals(tb, col))
        if not vals:
            continue
        ctokens = set(col[:-3].split('_'))
        # exact suffix
        exact = None
        for ct, ttb in conc_map.items():
            if ct == col[:-3] and ttb != tb:
                exact = ttb
                break
        if exact and (vals & idsets[exact]):
            refmap[(tb, col)] = exact
            continue
        # self reference
        if col in ('parent_id', 'primary_id') and (vals & idsets[tb]):
            refmap[(tb, col)] = tb
            continue
        # header/sibling prefix parents + value overlap
        cands = []
        for pt in prefix_parents.get(tb, []):
            if pt != tb and (vals & idsets[pt]):
                cands.append(pt)
        if len(cands) == 1:
            refmap[(tb, col)] = cands[0]
            report.append('%-32s %-22s -> %s  [prefix]' % (tb, col, cands[0]))
            continue
        # generic: candidates with name containing all col tokens
        others = []
        for t2, _, _ in clone:
            if t2 == tb:
                continue
            if ctokens <= set(t2.split('_')):
                ov = vals & idsets[t2]
                if ov:
                    others.append((len(ov), t2))
        if others:
            others.sort(key=lambda x: -x[0])
            if len(others) == 1 or others[0][0] > others[1][0]:
                refmap[(tb, col)] = others[0][1]
                report.append('%-32s %-22s -> %s  [overlap %d/%d]' % (tb, col, others[0][1], others[0][0], len(vals)))
                continue
            # tie: prefer explicit overrides
            if (tb, col) in overrides:
                refmap[(tb, col)] = overrides[(tb, col)]
                report.append('%-32s %-22s -> %s  [override-tie]' % (tb, col, overrides[(tb, col)]))
                continue
            report.append('%-32s %-22s -> AMBIG %s  KEEP' % (tb, col, ','.join(t for _, t in others[:4])))
            kept.append((tb, col))
            continue
        kept.append((tb, col))
        report.append('%-32s %-22s -> UNKNOWN KEEP' % (tb, col))

print('# resolved ref cols=%d  unresolved=%d' % (len(refmap), len(kept)))
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
    if userpat.search(col):
        return 'CASE `%s` %s ELSE `%s` END' % (col, ' '.join('WHEN %d THEN %d' % (a, b) for a, b in uid_map.items()), col)
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
    print('dry done. re-run with "run" to execute.')
    sys.exit(0)
p = subprocess.run(MYSQL, input='\n'.join(lines).encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if p.returncode != 0:
    print('RUN ERROR rc=%d:\n%s' % (p.returncode, p.stderr.decode('utf-8', 'replace')[:8000]))
    sys.exit(2)
print('# executed ok')
