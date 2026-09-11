# -*- coding: utf-8 -*-
# ASCII-only source. Tenantize mes mock seed (init-all.sql tenant_id=1) to arbitrary tenant.
# Steps: 1) remap platform user-id tokens (1/103/104/107) to real users of target tenant
#        2) fix tables whose user_id is GLOBALLY unique (mes_pro_work_record) -> shrink rows
#        3) tenantize rows/WHERE/bare sub-queries to tenant_id=T
# Usage: python tenantize.py <TenantId> [--run]
import io, os, re, subprocess, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'init-all.sql')
MYSQL = ['mysql', '-uroot', '-p123456', '--default-character-set=utf8mb4', 'ruoyi-vue-pro']

USER_TOKENS = (103, 104, 107)          # platform tenant user ids hard-coded in seed
UNIQUE_USER_TABLES = ['mes_pro_work_record']   # user_id globally unique -> shrink
LOOP_USER_TABLES = ['mes_pro_work_record_log'] # just remap, duplicates allowed


def q(sql):
    p = subprocess.run(MYSQL + ['-N', '-e', sql], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('MYSQL QUERY ERROR: %s' % p.stderr.decode('utf-8', 'replace')[:800])
        sys.exit(3)
    return p.stdout.decode('utf-8', 'replace').split()


def fetch_users(T):
    rows = q('SELECT id FROM system_users WHERE tenant_id=%d AND deleted=0 AND status=0 ORDER BY id' % T)
    return [int(x) for x in rows]


def remap_user_tokens(text, uids):
    """map 103/104/107 -> target users (cyclic by token order)."""
    if not uids:
        return text
    n = len(uids)
    mapv = {USER_TOKENS[i]: uids[i % n] for i in range(len(USER_TOKENS))}
    pat = re.compile(r'(?<![\w`\'"\d])(%s)(?![\w`\'"])' % '|'.join(str(t) for t in USER_TOKENS))
    def rep(m):
        return str(mapv[int(m.group(1))])
    text2 = pat.sub(rep, text)
    diff = sum(1 for _ in pat.finditer(text))
    print('remap user tokens: %d replaced' % diff)
    return text2


def fix_insert_users(text, table, uids, dedup):
    """rewrite user_id of a single-table INSERT block: row i -> uids[i % n];
    for dedup tables, drop rows when more rows than users."""
    if not uids:
        return text
    m = re.search(r'(INSERT INTO `%s`\s*\([^)]*user_id[^)]*\)\s*VALUES\s*\n)' % table, text, re.I)
    if not m:
        return text
    head = m.group(1)
    start = m.end()
    endm = re.search(r';\s*\n', text[start:])
    if not endm:
        return text
    end = start + endm.start()
    body = text[start:end]
    rows = body.split('\n')
    out = []
    i = 0
    for r in rows:
        if not r.strip():
            continue
        mm = re.match(r'^(\s*)\(\s*\d+,', r)
        if not mm:
            continue
        uid = uids[i % len(uids)]
        if dedup and i >= len(uids):
            i += 1  # skip: no unique user left for this row
            continue
        out.append(mm.group(1) + '(' + str(uid) + ',' + r[mm.end():])
        i += 1
    newbody = '\n'.join(out)
    if out:
        # make sure body ends with ; (original may carry trailing , on prior rows)
        newbody = newbody.rstrip()
        if not newbody.endswith(';'):
            # last row of VALUES list may end with `,` -> replace closing
            if newbody.endswith(','):
                newbody = newbody[:-1] + ';'
            else:
                newbody = newbody + ';'
    return text[:start] + newbody + text[end:]


def tenantize(text, T):
    cnt1 = len(re.findall(r',\s*1\)\s*[,;]', text))
    text = re.sub(r',(\s*)1\)(\s*[,;])', lambda m: ',' + m.group(1) + '%d)' % T + m.group(2), text)
    text = re.sub(r'WHERE\s+tenant_id\s*=\s*1\b', 'WHERE tenant_id=%d' % T, text)
    cnt2 = len(re.findall(r'\bWHERE\s+(?!tenant_id\s*=)', text))
    text = re.sub(r'\bWHERE\s+(?!tenant_id\s*=)', 'WHERE tenant_id=%d AND ' % T, text)
    cnt3 = 0
    def bare(m):
        nonlocal cnt3
        cnt3 += 1
        return m.group(1) + ' WHERE tenant_id=%d ' % T + m.group(2)
    text = re.sub(r'(FROM\s+(?:`mes_[a-z0-9_]+`|mes_[a-z0-9_]+))\s+(ORDER\s+BY|LIMIT)', bare, text, flags=re.I)
    return text, cnt1, cnt2, cnt3


def run_sql_file(path):
    with io.open(path, encoding='utf-8') as f:
        sql = f.read()
    p = subprocess.run(MYSQL, input=sql.encode('utf-8'), stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('MYSQL ERROR rc=%d:\n%s' % (p.returncode, p.stderr.decode('utf-8', 'replace')[:5000]))
        sys.exit(2)
    print('ok: %s' % os.path.basename(path))


def main():
    args = [a for a in sys.argv[1:] if a != '--run']
    run = '--run' in sys.argv[1:]
    if not args:
        print('usage: python tenantize.py <TenantId> [--run]')
        sys.exit(1)
    T = int(args[0])
    uids = fetch_users(T)
    print('tenant=%d active users=%s' % (T, uids))
    with io.open(SRC, encoding='utf-8') as f:
        text = f.read()
    text = remap_user_tokens(text, uids)
    for tb in LOOP_USER_TABLES:
        text = fix_insert_users(text, tb, uids, dedup=False)
    for tb in UNIQUE_USER_TABLES:
        text = fix_insert_users(text, tb, uids, dedup=True)
    out, c1, c2, c3 = tenantize(text, T)
    dst = os.path.join(HERE, 'init-all-t%d.sql' % T)
    with io.open(dst, 'w', encoding='utf-8') as f:
        f.write(out)
    print('tenant=%d generated=%s replaced: rowtail=%d where=%d bare=%d' % (T, dst, c1, c2, c3))
    if not run:
        return
    rb = os.path.join(HERE, 'rollback-t%d.sql' % T)
    with io.open(SRC.replace('init-all', 'rollback'), encoding='utf-8') as f:
        rb_text = f.read()
    rb_text = rb_text.replace('tenant_id = 1', 'tenant_id = %d' % T)
    with io.open(rb, 'w', encoding='utf-8') as f:
        f.write(rb_text)
    print('rolling back tenant=%d ...' % T)
    run_sql_file(rb)
    print('seeding tenant=%d ...' % T)
    run_sql_file(dst)


if __name__ == '__main__':
    main()
