# -*- coding: utf-8 -*-
# ASCII only source: report structure of seed SQL files (no path literals with CJK).
import io, os, re, sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
FILES = ['md.sql','pro.sql','wm.sql','dv.sql','tm.sql','qc.sql','cal.sql','set.sql',
         'init-all.sql','rollback.sql','verify.sql']

INS_RE  = re.compile(r'INSERT\s+INTO\s+`?(\w+)`?', re.I)
TBL_RE  = re.compile(r'FROM\s+`?(\w+)`?', re.I)

for f in FILES:
    p = os.path.join(HERE, f)
    if not os.path.exists(p):
        continue
    txt = io.open(p, encoding='utf-8', errors='replace').read()
    lines = txt.count('\n') + 1
    ins = INS_RE.findall(txt)
    ins_tables = []
    for t in ins:
        if t not in ins_tables:
            ins_tables.append(t)
    # count trailing tenant row patterns
    row_tail_1 = len(re.findall(r',1\)\s*,', txt)) + len(re.findall(r',1\)\s*;', txt))
    row_tail_T = len(re.findall(r',(?:1|%d)\)\s*[,;]' % 0, txt))
    # sub-select without WHERE but with ORDER/LIMIT (offset-style references)
    bare_sel = re.findall(r'SELECT\s+([\w*]+)\s+FROM\s+`?(\w+)`?\s+(ORDER\s+BY|LIMIT)', txt, re.I)
    sel_where = re.findall(r'SELECT\s+[\w*]+\s+FROM\s+`?(\w+)`?\s+WHERE', txt, re.I)
    has_cal = ('mes_cal_' in txt)
    has_set = ('mes_set_' in txt or 'mes_pollution' in txt)
    print('== %s == lines=%d ins_tables=%d bare_select=%d sel_where=%d tail1=%d cal=%s set=%s' %
          (f, lines, len(ins_tables), len(bare_sel), len(sel_where), row_tail_1, has_cal, has_set))
    print('   insert tables:', ', '.join(ins_tables[:60]))
    if bare_sel:
        seen = {}
        for col, tb, kw in bare_sel:
            seen.setdefault((tb, kw), 0)
            seen[(tb, kw)] += 1
        print('   bare-select (no WHERE):', ', '.join('%s[%s x%d]' % (tb, kw.strip(), n) for (tb, kw), n in seen.items()))
