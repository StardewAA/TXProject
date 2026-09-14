# -*- coding: utf-8 -*-
import io, os, re, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(HERE, 'init-all.sql')
txt = io.open(p, encoding='utf-8').read()
pat = re.compile(r'(?<![\w`\'"\d])(103|104|107)(?![\w`\'"])')
for m in pat.finditer(txt):
    s = max(0, m.start()-70); e = min(len(txt), m.end()+50)
    print('...%s...' % txt[s:e].replace('\n', '|'))
