# -*- coding: utf-8 -*-
import io, os, collections, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
c = collections.Counter()
pairs = {}
for ln in io.open(os.path.join(HERE, 'refs2.txt'), encoding='utf-8').read().splitlines():
    tb, col, rest = ln.split('|')[0], ln.split('|')[1], ln.split('|', 2)[2]
    c[col] += 1
    pairs.setdefault(col, []).append(tb + ' (' + rest + ')')
for col, n in c.most_common():
    print('%-24s x%-3d | %s' % (col, n, ' ; '.join(pairs[col][:8])))
