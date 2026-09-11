# -*- coding: utf-8 -*-
"""Clone tenant-1 MES rows -> tenant 2010 (docker ruoyi-vue-pro @13306).
- copies all mes_ tables that have tenant_id + t1 rows, keeping original ids
  so cross-table references stay intact
- remaps user references (bigint *_user_id) t1 users -> 2010 users (1:1 by id order)
- remaps creator/updater (varchar: username or id string) -> 2010 usernames
- remaps mes_md_workstation_worker.post_id -> 2010 posts
- keeps tenant 2010's own default rows of mes_md_auto_code_* untouched
"""
import io, os, re, subprocess, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'clone_t1_to_2010.sql')
MYSQL = ['mysql', '-h127.0.0.1', '-P13306', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']
SRC, DST = 1, 2010

def q(sql):
    p = subprocess.run(MYSQL + ['-N', '-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('ERR:', p.stderr.decode('utf-8', 'replace')[:600]); sys.exit(1)
    return p.stdout.decode('utf-8', 'replace')

# ---- target users of 2010 (asc) and source users of t1 (asc) ----
def users(T):
    return [(int(i), u) for i, u in
            (x.split('\t') for x in q(
                "SELECT id, username FROM system_users WHERE tenant_id=%d AND deleted=0 AND status=0 ORDER BY id" % T).splitlines() if x)]

src_users = users(SRC)
dst_users = users(DST)
uid_map = {old_id: new_id for (old_id, _), (new_id, _) in zip(src_users, dst_users)}
uname_map = {old_name: new_name for (_, old_name), (_, new_name) in zip(src_users, dst_users)}
print('src_users=%d dst_users=%d' % (len(src_users), len(dst_users)))
print('uid_map:', uid_map)

# ---- tables to clone ----
rows = q("SELECT table_name FROM information_schema.tables WHERE table_schema='ruoyi-vue-pro' AND table_name LIKE 'mes_%' ORDER BY table_name").split()
skip = {'mes_md_auto_code_part', 'mes_md_auto_code_rule', 'mes_md_auto_code_record'}
tables = []
for tb in rows:
    if tb in skip:
        continue
    c1 = int(q("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%d" % (tb, SRC)).strip())
    if c1 == 0:
        continue
    tables.append((tb, c1))
print('clone tables=%d' % len(tables))

post_map = {1: 70190, 2: 70193}  # workstation_worker post remap (1->production mgr post, 2->QC post)

def case_expr(col, token_map, is_int):
    """build CASE col WHEN token THEN mapped ... ELSE col END"""
    parts = ['CASE `%s`' % col]
    for k, v in token_map.items():
        if is_int:
            parts.append('WHEN %d THEN %d' % (k, v))
        else:
            parts.append("WHEN '%s' THEN '%s'" % (k, v))
    parts.append('ELSE `%s` END' % col)
    return ' '.join(parts)

def col_expr(tb, col):
    if col == 'tenant_id':
        return str(DST)
    if col in ('creator', 'updater'):
        tok = {}
        for (oid, oname), (nid, nname) in zip(src_users, dst_users):
            tok[str(oid)] = nname
            tok[oname] = nname
        return case_expr(col, tok, False)
    if re.search(r'(^|_)user_id$', col):
        return case_expr(col, uid_map, True)
    if tb == 'mes_md_workstation_worker' and col == 'post_id':
        return case_expr(col, post_map, True)
    return '`%s`' % col

sql_lines = ['SET FOREIGN_KEY_CHECKS=0;', 'SET NAMES utf8mb4;']
for tb, n in tables:
    cols = [c for c in q("SELECT column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' AND table_name='%s' ORDER BY ordinal_position" % tb).split() if c]
    ins = ', '.join('`%s`' % c for c in cols)
    sel = ', '.join(col_expr(tb, c) for c in cols)
    sql_lines.append("INSERT INTO `%s` (%s)\nSELECT %s FROM `%s` WHERE tenant_id=%d;" % (tb, ins, sel, tb, SRC))
sql_lines.append('-- done')
with io.open(OUT, 'w', encoding='utf-8') as f:
    f.write('\n'.join(sql_lines))
print('sql written=%s lines=%d' % (OUT, len(sql_lines)))

# ---- run ----
p = subprocess.run(MYSQL, input=('\n'.join(sql_lines)).encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if p.returncode != 0:
    print('RUN ERROR rc=%d:\n%s' % (p.returncode, p.stderr.decode('utf-8', 'replace')[:8000]))
    sys.exit(2)
print('run ok, verify below')
