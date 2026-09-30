# -*- coding: utf-8 -*-
"""把三个 write 别名标记为显式写索引（is_write_index=true）。"""
import json, urllib.request
ES = 'http://127.0.0.1:9200'
PAIRS = [
    ('txgy-cms-law-v2-000001', 'txgy-cms-law-write'),
    ('txgy-cms-qa-v2-000001', 'txgy-cms-qa-write'),
    ('txgy-cms-finance-policy-v2-000001', 'txgy-cms-finance-policy-write'),
]
def req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(ES + path, data=data, method=method, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(r, timeout=60) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}
actions = [{'add': {'index': idx, 'alias': alias, 'is_write_index': True}} for idx, alias in PAIRS]
print(json.dumps(req('POST', '/_aliases', {'actions': actions}), ensure_ascii=False))
print(json.dumps(req('GET', '/_cat/aliases?format=json&h=alias,index,is_write_index'), ensure_ascii=False)[:400])