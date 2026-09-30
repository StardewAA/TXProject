# -*- coding: utf-8 -*-
"""为 CMS 三个索引接入 smartcn 中文分词：新建 v2 索引（带 smartcn 分析器）并切换别名。"""
import json, urllib.request

ES = 'http://127.0.0.1:9200'
TEXT_FIELDS = ['title', 'content', 'document_no', 'article_no', 'issuing_authority']
PLAN = [
    ('txgy-cms-law-v1-000001', 'txgy-cms-law-v2-000001', 'txgy-cms-law'),
    ('txgy-cms-qa-v1-000001', 'txgy-cms-qa-v2-000001', 'txgy-cms-qa'),
    ('txgy-cms-finance-policy-v1-000001', 'txgy-cms-finance-policy-v2-000001', 'txgy-cms-finance-policy'),
]

def req(method, path, body=None):
    data = json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
    r = urllib.request.Request(ES + path, data=data, method=method,
                               headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(r, timeout=60) as resp:
        raw = resp.read().decode('utf-8')
        return json.loads(raw) if raw else {}

def main():
    for old_index, new_index, prefix in PLAN:
        mapping = req('GET', f'/{old_index}/_mapping')[old_index]['mappings']
        for field in TEXT_FIELDS:
            prop = mapping.get('properties', {}).get(field)
            if prop and prop.get('type') == 'text':
                prop['analyzer'] = 'smartcn'
                prop['search_analyzer'] = 'smartcn'
        req('DELETE', f'/{new_index}') if False else None
        try:
            req('PUT', f'/{new_index}', {'mappings': mapping, 'settings': {'number_of_shards': 1, 'number_of_replicas': 0}})
            print(f'  新建 {new_index}（smartcn）')
        except Exception as e:
            print(f'  {new_index} 创建结果: {e}')
        actions = []
        for suffix in ('read', 'write'):
            alias = f'{prefix}-{suffix}'
            actions.append({'remove': {'index': old_index, 'alias': alias}})
            actions.append({'add': {'index': new_index, 'alias': alias}})
        req('POST', '/_aliases', {'actions': actions})
        print(f'  别名切换 {prefix}-read / -write -> {new_index}')

if __name__ == '__main__':
    main()