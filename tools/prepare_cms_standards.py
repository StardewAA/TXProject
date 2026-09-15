"""Archive page-level standard text as unpublished search drafts for review."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from pypdf import PdfReader
from import_cms_laws import stable_id, normalize, request

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--directory',type=Path,required=True)
    p.add_argument('--tenant',type=int,required=True)
    p.add_argument('--apply',action='store_true')
    args=p.parse_args()
    if args.tenant<=0:
        raise ValueError('Explicit positive tenant required')
    specs=json.loads(Path(__file__).with_name('cms_standard_sources.json').read_text(encoding='utf-8'))
    records,report={},[]
    for s in specs:
        pdf=args.directory/s['file']
        reader=PdfReader(pdf)
        if len(reader.pages)!=s['pages']:
            raise ValueError('PDF page count mismatch')
        if s['extraction']=='windows-ocr':
            pages=json.loads(pdf.with_suffix('.ocr.json').read_text(encoding='utf-8-sig'))
        else:
            pages=[{'page':i,'text':page.extract_text() or ''} for i,page in enumerate(reader.pages,1)]
        if [x['page'] for x in pages]!=list(range(1,s['pages']+1)):
            raise ValueError('Page sequence mismatch')
        retained=[]
        for page in pages:
            # Drop malformed watermark lines; preserve raw extraction alongside the PDF.
            lines=[line for line in page['text'].splitlines() if not re.search(r'[\x00-\x08\x0b\x0c\x0e-\x1f]',line)]
            text='\n'.join(lines).strip()
            text=re.sub(r'(?<=[\u4e00-\u9fff]) +(?=[\u4e00-\u9fff])','',text)
            if not text:
                continue
            label=f'PDF第{page["page"]}页'
            key=f'cms-import:{args.tenant}:{s["key"]}:page-{page["page"]}'
            records[key]=dict(tenant_id=str(args.tenant),law_id=stable_id(f'cms-import:{args.tenant}:{s["key"]}'),article_id=stable_id(key),
                source_id=key,version=s['document_no'],title=s['title'],document_no=s['document_no'],document_no_normalized=normalize(s['document_no']),
                article_no=label,article_no_normalized=normalize(label),content=text,law_type='STANDARD',status='UNKNOWN',published=False,revision=1)
            retained.append(page['page'])
        report.append(dict(s,sha256=hashlib.sha256(pdf.read_bytes()).hexdigest(),published=False,indexed_pages=retained,
                           empty_pages=[i for i in range(1,s['pages']+1) if i not in retained]))
    (args.directory/'standard-review-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    created=0
    if args.apply:
        for key,record in records.items():
            result=request('http://127.0.0.1:9200','/txgy-cms-law-write/_mget',{'ids':[key]})['docs'][0]
            if 'error' in result:
                raise ValueError(result['error'])
            if result.get('found'):
                if result['_source']!=record:
                    raise ValueError(f'Refusing overwrite: {key}')
                continue
            payload=json.dumps({'create':{'_index':'txgy-cms-law-write','_id':key}})+'\n'+json.dumps(record,ensure_ascii=False)+'\n'
            response=request('http://127.0.0.1:9200','/_bulk?require_alias=true',payload,True)
            if response.get('errors'):
                raise ValueError(response)
            created+=1
        hits=request('http://127.0.0.1:9200','/txgy-cms-law-read/_mget',{'ids':list(records)})['docs']
        if any(not h.get('found') or h['_source']!=records[h['_id']] for h in hits):
            raise ValueError('Draft read-back verification failed')
    summary=dict(documents=len(specs),pages=sum(s['pages'] for s in specs),nonempty_pages=len(records),created=created,applied=args.apply,published=False)
    (args.directory/'standard-import-result.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))

if __name__=='__main__':
    main()
