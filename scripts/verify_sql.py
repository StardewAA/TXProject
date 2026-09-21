# -*- coding: utf-8 -*-
import json, re
s = open(r'D:\txgy-new\sql\2026-09-21-training-docx-content.sql', encoding='utf-8').read()
start = s.index('INSERT INTO `training_question`')
end = s.index(';', start)
seg = s[start:end]
# 每行以 '  (N,' 开头且以 '),' 结尾
lines = [l for l in seg.split('\n') if l.startswith('  (')]
print('题目行数:', len(lines))
bad = 0
for l in lines:
    # options_json 是第三个字段：匹配 (qtype, 'stem', OPTIONS, 'answer', ...
    m = re.match(r"^  \((\d+), '(?:[^']|'')*', (\[.*?\]|''), ", l, re.S)
    if not m:
        continue
    raw = m.group(2)
    if raw == "''":
        continue
    js = raw.replace("''", "'")
    try:
        json.loads(js)
    except Exception as e:
        bad += 1
        if bad <= 3:
            print('BAD:', e, js[:120])
print('非法 JSON 数量:', bad)
