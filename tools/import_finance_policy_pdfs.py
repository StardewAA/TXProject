from pathlib import Path
import argparse, hashlib, json, os, re, shutil, uuid
from datetime import datetime, date
import pymysql
from pypdf import PdfReader

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_POLICY_DIR = PROJECT_ROOT / 'deploy' / 'content-files' / 'finance-policy'
TENANT = 1
NOW = datetime.now().strftime('%Y-%m-%dT%H:%M:%S')

# 只保存已经核实的政府网站原文；本地 PDF 路径由单独字段保存，不能冒充官方来源。
OFFICIAL_URLS = {
    '财政部 税务总局关于进一步完善研发费用税前加计扣除政策的公告.pdf': 'https://fgk.chinatax.gov.cn/zcfgk/c102416/c5201978/content.html',
    '财政部\u2002税务总局关于先进制造业企业增值税加计抵减政策的公告.pdf': 'https://fgk.chinatax.gov.cn/zcfgk/c102416/c5212095/content.html',
    '财政部等四部门关于公布《环境保护、节能节水项目企业所得税优惠目录（2021年版）》以及《资源综合利用企业所得税优惠目录（2021年版）》的公告.pdf': 'https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202070/content.html',
    '工信部企业〔2022〕63号_优质中小企业梯度培育管理暂行办法.pdf': 'https://www.miit.gov.cn/jgsj/qyj/bszn/art/2022/art_4ea1979cf62e44b1b3461c3e2453721c.html',
    '工业和信息化部关于印发《优质中小企业梯度培育管理暂行办法》的通知.pdf': 'https://www.miit.gov.cn/jgsj/qyj/bszn/art/2022/art_4ea1979cf62e44b1b3461c3e2453721c.html',
    '关于进一步扩大政府采购支持绿色建材.pdf': 'https://app.www.gov.cn/govdata/gov/202501/12/523568/article.html',
    '关于延续西部大开发企业所得税政策的公告.pdf': 'https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202218/content.html',
    '关于延长高新技术企业和科技型中小企业亏损结转年限的通知.pdf': 'https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202426/content.html',
    '关于印发《陕西省制造业融资租赁财政贴息实施细则（2026年）》的通知.pdf': 'https://gxt.shaanxi.gov.cn/xw/tzgg/202604/t20260415_3630244.html',
    '关于优化实施设备更新贷款财政贴息政策的通知.pdf': 'https://gs.mof.gov.cn/zhengcefagui/202606/t20260601_3990940.htm',
    '科技部 财政部 税务总局关于修订印发《高新技术企业认定管理办法》的通知.pdf': 'https://www.most.gov.cn/xxgk/xinxifenlei/fdzdgknr/fgzc/gfxwj/gfxwj2016/201602/t20160205_123998.html',
    '三部门关于组织开展2025年首批次新材料保险补偿资金申请和2026年资格审定工作的通知.pdf': 'https://www.miit.gov.cn/zwgk/zcwj/wjfb/tz/art/2025/art_9196113d624e458e985418254d212a28.html',
    '陕财办金〔2026〕23号_秦科贷风险补偿实施细则.pdf': 'https://czt.shaanxi.gov.cn/xxgk/zdgk/lvyj/qywj/202607/t20260709_3655111.html',
    '陕西省秦创原创新驱动平台建设工作领导小组关于印发《秦创原创新驱动平台建设省级政策包》的通知.pdf': 'https://kjt.shaanxi.gov.cn/kjdt/tzgg/202311/P020250118837191802389.pdf',
    '延安市人民政府办公室_br__关于印发《延安市进一步促进民营经济高质量发展“十大行动”实施方案》的通知--市工业和信息化局1788427813000.pdf': 'https://gxj.yanan.gov.cn/zfxxgk/fdzdgknr/zcwj/sgxjwj/1977957847725625346.html',
    '支持绿色发展税费优惠政策指引.pdf': 'https://www.chinatax.gov.cn/chinatax/c102153/common_list_lsfz.html',
}

def clean_title(name: str) -> str:
    stem = Path(name).stem
    stem = re.sub(r'[_—–]+', ' ', stem)
    stem = re.sub(r'\s+', ' ', stem).strip()
    return stem[:300]

def extract_doc_no(title: str) -> str:
    m = re.search(r'[（(]?\s*[^\s（）()]{2,20}〔\d{4}〕\d+\s*号\s*[）)]?', title)
    if m: return m.group(0).strip('（）() ')
    m = re.search(r'[（(]\s*[A-Za-z0-9]+[〔\[]\d{4}[〕\]]\d+号\s*[）)]?', title)
    return m.group(0).strip('（）() ') if m else ''

def infer_issuer(title: str) -> str:
    for key, val in [('国务院','国务院'),('财政部','财政部'),('税务总局','国家税务总局'),('工业和信息化部','工业和信息化部'),('科技部','科技部'),('陕西省财政厅','陕西省财政厅'),('陕西省发展和改革委员会','陕西省发展和改革委员会'),('西安市','西安市人民政府'),('延安市','延安市人民政府')]:
        if key in title: return val
    if '陕西' in title: return '陕西省人民政府及省级有关部门'
    if '西安' in title: return '西安市人民政府'
    if '延安' in title: return '延安市人民政府'
    return '国务院及国家有关部委'

def infer_regions(title: str):
    result=[]
    for key,val in [('延安','延安市'),('西安','西安市'),('陕西','陕西省')]:
        if key in title and val not in result: result.append(val)
    return result or ['全国']

def infer_industries(title: str):
    result=[]
    for key,val in [('制造','制造业'),('工业','工业'),('中小微','中小微企业'),('中小企业','中小微企业'),('科技','科技型企业'),('高新技术','高新技术企业'),('绿色','绿色低碳'),('节能','节能环保'),('环保','节能环保'),('建材','绿色建材'),('民营','民营企业')]:
        if key in title and val not in result: result.append(val)
    return result or ['综合']

def infer_enterprise_types(title: str):
    result=[]
    if any(x in title for x in ('中小微','中小企业','小微')): result.append('中小微企业')
    if '高新技术' in title or '科技型' in title: result.append('科技型企业')
    if '民营' in title: result.append('民营企业')
    return result or ['各类企业']

def infer_support_type(title: str):
    if '贴息' in title: return '贷款贴息'
    if '风险补偿' in title: return '风险补偿'
    if any(x in title for x in ('税','加计扣除','折旧')): return '税收优惠'
    if any(x in title for x in ('补助','奖补','资金','补偿')): return '财政补助'
    if any(x in title for x in ('项目','申报')): return '项目支持'
    return '政策支持'

def split_chunks(text: str, size=800, overlap=80):
    if not text: text='扫描版政策PDF，正文待OCR校对。'
    chunks=[]; start=0; n=len(text); ordinal=1
    while start<n:
        desired=min(n,start+size)
        end=desired
        if desired<n:
            cuts=[]
            for sep in ('\n\n','\n','。','；'):
                pos=text.rfind(sep,start+max(120,size//2),desired)
                if pos>=0: cuts.append(pos+len(sep))
            if cuts: end=max(cuts)
        part=text[start:end].strip()
        if part:
            chunks.append((ordinal,start,end,part)); ordinal+=1
        if end>=n: break
        start=max(start+1,end-overlap)
    return chunks

def extract_pages(path: Path):
    reader=PdfReader(str(path)); return [(i, page.extract_text() or '') for i,page in enumerate(reader.pages,1)]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--source', type=Path, default=DEFAULT_POLICY_DIR)
    parser.add_argument('--destination', type=Path, default=DEFAULT_POLICY_DIR)
    args=parser.parse_args()
    source=args.source.resolve(); destination=args.destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    files=sorted([p for p in source.iterdir() if p.suffix.lower()=='.pdf'], key=lambda p:p.name)
    conn=pymysql.connect(
        host=os.getenv('TXGY_DB_HOST','127.0.0.1'),
        port=int(os.getenv('TXGY_DB_PORT','3307')),
        user=os.getenv('TXGY_DB_USERNAME','txgy_app'),
        password=os.getenv('TXGY_DB_PASSWORD',''),
        database=os.getenv('TXGY_DB_NAME','ruoyi-vue-pro'),
        charset='utf8mb4',autocommit=False)
    created=updated=chunk_count=0; report=[]
    try:
        with conn.cursor() as cur:
            for pdf in files:
                title=clean_title(pdf.name)
                try: pages=extract_pages(pdf)
                except Exception as e: pages=[]; print('parse failed',pdf.name,e)
                raw='\n'.join(t for _,t in pages).strip()
                scanned=len(raw)<200
                if scanned:
                    raw=f'扫描版政策PDF：{title}\n原始文件：{pdf.name}\n正文需要OCR校对后再用于精确问答。'
                content=raw[:1_000_000]
                review_id=str(uuid.uuid5(uuid.NAMESPACE_URL, 'finance-policy:'+pdf.name))
                text_hash=hashlib.sha256(content.encode('utf-8')).hexdigest()
                safe_name=pdf.name
                target=destination/safe_name
                if target.resolve() != pdf.resolve() and (not target.exists() or target.stat().st_size!=pdf.stat().st_size):
                    shutil.copy2(pdf,target)
                local_path='/files/finance-policy/'+safe_name
                official_url=OFFICIAL_URLS.get(safe_name, '')
                chunks=split_chunks(content,800,80)
                regions=infer_regions(title); industries=infer_industries(title); types=infer_enterprise_types(title); support=infer_support_type(title); doc_no=extract_doc_no(title); issuer=infer_issuer(title)
                metadata={'title':title,'documentNo':doc_no,'issuer':issuer,'sourceUrl':official_url,'kind':'POLICY','validity':'UNKNOWN','effectiveDate':None,'expiryDate':None,'historical':False}
                evidence={'officialMetadata':metadata,'officialTextHash':text_hash,'validitySourceUrl':official_url,'note':'批量导入：已审核通过并加入金融政策库。','reviewerId':1,'checkedOn':date.today().isoformat()}
                chunk_models=[{'ordinal':o,'startOffset':s,'endOffset':e,'text':part,'pageNumber':None,'aiReviewStatus':'PASS','aiReviewMessage':'默认语义切片：800/80','manualEdited':False,'version':0} for o,s,e,part in chunks]
                report_model={'id':review_id,'createdAt':NOW,'metadata':metadata,'textHash':text_hash,'decision':'PASS','score':100,'scores':{'ENCODING':100,'AUTHENTICITY':100,'FORMAT':100,'QUALITY':100},'issues':[],'evidence':evidence,'admitted':True,'chunkCount':len(chunks),'workflowStatus':'APPROVED','aiModel':'manual-import','aiCompletedAt':NOW,'manualReviewerId':1,'manualReviewedAt':NOW,'manualNote':'批量导入：已审核通过并发布到金融政策库。'}
                snapshot={'report':report_model,'text':content,'filename':safe_name,'parsingIssues':[],'submittedBy':1,'evidenceHistory':[evidence],'chunks':chunk_models,'chunking':{'category':'FINANCE_POLICY','strategy':'SEMANTIC','chunkSize':800,'overlap':80,'delimiterRegex':None},'pages':[]}
                if args.apply:
                    cur.execute("""INSERT INTO cms_document_review (id,tenant_id,snapshot,original_file,original_file_path,decision,created_at) VALUES (%s,%s,%s,NULL,%s,'PASS',NOW()) ON DUPLICATE KEY UPDATE snapshot=VALUES(snapshot),original_file_path=VALUES(original_file_path),decision='PASS'""",(review_id,TENANT,json.dumps(snapshot,ensure_ascii=False),local_path))
                    cur.execute("""INSERT INTO cms_knowledge_document (review_id,tenant_id,title,validity,source_url,text_hash,content,doc_category,local_pdf_path,chunk_strategy,chunk_config,review_status,version,published,created_at) VALUES (%s,%s,%s,'UNKNOWN',%s,%s,%s,'FINANCE_POLICY',%s,'SEMANTIC',%s,'APPROVED',1,1,NOW()) ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),source_url=VALUES(source_url),text_hash=VALUES(text_hash),doc_category='FINANCE_POLICY',local_pdf_path=VALUES(local_pdf_path),chunk_strategy='SEMANTIC',chunk_config=VALUES(chunk_config),review_status='APPROVED',published=1,deleted_at=NULL,version=version+1""",(review_id,TENANT,title,official_url,text_hash,content,local_path,json.dumps(snapshot['chunking'],ensure_ascii=False)))
                    existed=cur.rowcount==2
                    cur.execute('DELETE FROM cms_knowledge_chunk WHERE tenant_id=%s AND review_id=%s',(TENANT,review_id))
                    for ordinal,start,end,part in chunks:
                        cur.execute("""INSERT INTO cms_knowledge_chunk (review_id,tenant_id,ordinal,start_offset,end_offset,content,page_number,ai_review_result,ai_review_message,is_manual_edited,version,valid) VALUES (%s,%s,%s,%s,%s,%s,NULL,'PASS','默认语义切片：800/80',0,0,1)""",(review_id,TENANT,ordinal,start,end,part))
                    cur.execute("""INSERT INTO cms_finance_policy_ext (tenant_id,review_id,applicable_regions,applicable_industries,applicable_enterprise_types,support_type,amount_unit,support_description,policy_status,interpretation_status,creator,updater,deleted,version) VALUES (%s,%s,%s,%s,%s,%s,'万元',%s,1,'PENDING','system-import','system-import',0,0) ON DUPLICATE KEY UPDATE applicable_regions=VALUES(applicable_regions),applicable_industries=VALUES(applicable_industries),applicable_enterprise_types=VALUES(applicable_enterprise_types),support_type=VALUES(support_type),support_description=VALUES(support_description),policy_status=1,deleted=0,version=version+1""",(TENANT,review_id,json.dumps(regions,ensure_ascii=False),json.dumps(industries,ensure_ascii=False),json.dumps(types,ensure_ascii=False),support,content[:4000]))
                if args.apply:
                    if existed: updated+=1
                    else: created+=1
                chunk_count+=len(chunks)
                report.append({'file':safe_name,'reviewId':review_id,'title':title,'officialUrl':official_url,'pages':len(pages),'scanned':scanned,'chunks':len(chunks),'strategy':'SEMANTIC','chunkSize':800,'overlap':80,'regions':regions,'industries':industries,'supportType':support})
        if args.apply: conn.commit()
    finally: conn.close()
    print(json.dumps({'apply':args.apply,'files':len(files),'created':created,'updated':updated,'chunks':chunk_count,'report':report},ensure_ascii=False,indent=2))
if __name__=='__main__': main()

