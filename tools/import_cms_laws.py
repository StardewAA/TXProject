"""Import a validated source batch into the existing local ES law index.

Default is dry-run. --apply uses create-only writes, verifies existing records,
and never replaces unrelated or changed documents. No MySQL or vector writes.
"""
import argparse
import hashlib
import json
import re
import unicodedata
from pathlib import Path
from urllib.request import Request, urlopen

def stable_id(value):
    # Stay within JavaScript's exact integer range as ids are consumed by the portal.
    return int(hashlib.sha256(value.encode()).hexdigest()[:13], 16)

def normalize(value):
    return re.sub(r'[\s·•—–－_\-]+','',unicodedata.normalize('NFKC',value).upper())

def request(base, path, body=None, ndjson=False):
    data = body.encode('utf-8') if ndjson else (json.dumps(body,ensure_ascii=False).encode('utf-8') if body is not None else None)
    req = Request(base + path, data=data, headers={'Content-Type':'application/x-ndjson' if ndjson else 'application/json'})
    with urlopen(req, timeout=45) as response:
        return json.load(response)

def build_records(directory, tenant):
    specs = json.loads(Path(__file__).with_name('cms_policy_sources.json').read_text(encoding='utf-8'))
    records, manifest = {}, []
    for spec in specs:
        doc = json.loads((directory/(spec['key']+'.json')).read_text(encoding='utf-8'))
        if doc['url'] != spec['url'] or not doc['chunks']:
            raise ValueError(f'Stale or empty preparation: {spec["key"]}')
        if 'articles' in spec and [c['number'] for c in doc['chunks']] != list(range(1,spec['articles']+1)):
            raise ValueError('Non-sequential articles')
        law_id = stable_id(f'cms-import:{tenant}:{spec["key"]}')
        for chunk in doc['chunks']:
            source_id = f'cms-import:{tenant}:{spec["key"]}:{chunk["number"]}'
            record = dict(tenant_id=str(tenant), law_id=law_id, article_id=stable_id(source_id),
                          source_id=source_id, version=doc['version'], title=doc['title'],
                          document_no=doc.get('document_no',''), document_no_normalized=normalize(doc.get('document_no','')),
                          article_no=chunk['label'], article_no_normalized=normalize(chunk['label']),
                          content=chunk['text'], issuing_authority=doc.get('authority','全国人民代表大会' if spec['key']=='ecological-code-2026' else '全国人民代表大会常务委员会'),
                          law_type='INDUSTRY_REGULATION', status=doc['status'], source_url=doc['url'],
                          published=True, revision=1)
            for field in ('effective_date','expiry_date'):
                if field in doc:
                    record[field] = doc[field]
            if source_id in records:
                raise ValueError('Duplicate document id')
            records[source_id] = record
        manifest.append(dict(key=spec['key'],title=doc['title'],chunks=len(doc['chunks']),law_id=law_id,
                             status=doc['status'],source_url=doc['url'],sha256=doc['sha256']))
    ids = [r['article_id'] for r in records.values()]
    if len(ids) != len(set(ids)):
        raise ValueError('Numeric id collision')
    return records, manifest

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--directory',type=Path,required=True)
    p.add_argument('--tenant',type=int,required=True)
    p.add_argument('--es',default='http://127.0.0.1:9200',choices=['http://127.0.0.1:9200','http://localhost:9200'])
    p.add_argument('--apply',action='store_true')
    args=p.parse_args()
    if args.tenant <= 0:
        raise ValueError('Explicit positive tenant required')
    records,manifest=build_records(args.directory,args.tenant)
    report=dict(tenant_id=args.tenant,documents=len(manifest),chunks=len(records),sources=manifest,applied=False)
    plan=args.directory/'import-plan.json'
    plan.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    if not args.apply:
        print(json.dumps(dict(documents=len(manifest),chunks=len(records),plan=str(plan)),ensure_ascii=False))
        return
    aliases=request(args.es,'/_alias/txgy-cms-law-write')
    if len(aliases)!=1 or not next(iter(aliases.values()))['aliases']['txgy-cms-law-write'].get('is_write_index'):
        raise ValueError('Expected one explicit write index')
    items=list(records.items())
    pending=[]
    for offset in range(0,len(items),200):
        batch=items[offset:offset+200]
        existing=request(args.es,'/txgy-cms-law-write/_mget',{'ids':[key for key,_ in batch]})
        for (key,record),hit in zip(batch,existing['docs'],strict=True):
            if 'error' in hit:
                raise ValueError(hit['error'])
            if hit.get('found'):
                if hit['_source']!=record:
                    raise ValueError(f'Existing document differs; refusing overwrite: {key}')
            else:
                pending.append((key,record))
    # Keep a recoverable deterministic record of exactly what this batch will create.
    journal=args.directory/'import-pending-ids.json'
    journal.write_text(json.dumps([key for key,_ in pending],indent=2),encoding='utf-8')
    for offset in range(0,len(pending),200):
        lines=[]
        for key,record in pending[offset:offset+200]:
            lines.extend([json.dumps({'create':{'_index':'txgy-cms-law-write','_id':key}}),json.dumps(record,ensure_ascii=False)])
        response=request(args.es,'/_bulk?refresh=wait_for&require_alias=true','\n'.join(lines)+'\n',True)
        if response.get('errors'):
            raise ValueError([item for item in response['items'] if item['create']['status']>=300])
    for offset in range(0,len(items),200):
        batch=items[offset:offset+200]
        verified=request(args.es,'/txgy-cms-law-read/_mget',{'ids':[key for key,_ in batch]})
        for (key,record),hit in zip(batch,verified['docs'],strict=True):
            if not hit.get('found') or hit['_source']!=record:
                raise ValueError(f'Read-back verification failed: {key}')
    report.update(applied=True,created=len(pending),existing=len(records)-len(pending),verified=len(records))
    (args.directory/'import-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='sources'},ensure_ascii=False))

if __name__=='__main__':
    main()
