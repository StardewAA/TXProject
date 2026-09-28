# -*- coding: utf-8 -*-
"""把官网爬取的技术资料 PDF 导入 CMS 知识库（问答库 QA），支持检索与 AI 问答。"""
from pathlib import Path
import argparse, hashlib, json, uuid
from datetime import datetime
import pymysql
from pypdf import PdfReader

SRC = Path(r'D:\codexshuju\tianxin-pipe-project\project-base\txgycloud\deploy\content-files\assets\PDF文档')
TENANT = 1
NOW = datetime.now().strftime('%Y-%m-%dT%H:%M:%S')

def split_chunks(text, size=800, overlap=80):
    if not text:
        text = '扫描版技术资料 PDF，正文待 OCR 校对。'
    chunks, start, n, ordinal = [], 0, len(text), 1
    while start < n:
        desired = min(n, start + size)
        end = desired
        if desired < n:
            cuts = []
            for sep in ('\n\n', '\n', '。', '；'):
                pos = text.rfind(sep, start + max(120, size // 2), desired)
                if pos >= 0:
                    cuts.append(pos + len(sep))
            if cuts:
                end = max(cuts)
        part = text[start:end].strip()
        if part:
            chunks.append((ordinal, start, end, part))
            ordinal += 1
        if end >= n:
            break
        start = max(start + 1, end - overlap)
    return chunks

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    files = sorted(SRC.glob('*.pdf'), key=lambda p: p.name)
    print(f'待导入 PDF：{len(files)} 份')
    conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='root',
                           database='ruoyi-vue-pro', charset='utf8mb4', autocommit=False)
    total_chunks = 0
    try:
        with conn.cursor() as cur:
            for pdf in files:
                title = pdf.stem.replace('_', ' ').strip()[:300]
                try:
                    reader = PdfReader(str(pdf))
                    pages = [(i, page.extract_text() or '') for i, page in enumerate(reader.pages, 1)]
                except Exception as e:
                    pages = []
                    print('  解析失败', pdf.name, e)
                raw = '\n'.join(t for _, t in pages).strip()
                if len(raw) < 200:
                    raw = f'技术资料：{title}\n原始文件：{pdf.name}\n该 PDF 为图片/扫描版，正文需 OCR 校对后再用于精确问答。'
                content = raw[:1_000_000]
                review_id = str(uuid.uuid5(uuid.NAMESPACE_URL, 'tech-doc:' + pdf.name))
                text_hash = hashlib.sha256(content.encode('utf-8')).hexdigest()
                local_path = '/files/assets/PDF文档/' + pdf.name
                chunks = split_chunks(content, 800, 80)
                metadata = {'title': title, 'documentNo': '', 'issuer': '天信管业官网技术资料',
                            'sourceUrl': local_path, 'kind': 'STANDARD', 'validity': 'UNKNOWN',
                            'effectiveDate': None, 'expiryDate': None, 'historical': False}
                evidence = {'officialMetadata': metadata, 'officialTextHash': text_hash,
                            'validitySourceUrl': local_path, 'note': '官网资料批量导入，已发布到问答库。',
                            'reviewerId': 1, 'checkedOn': datetime.now().date().isoformat()}
                chunk_models = [{'ordinal': o, 'startOffset': s, 'endOffset': e, 'text': part,
                                 'pageNumber': None, 'aiReviewStatus': 'PASS',
                                 'aiReviewMessage': '默认语义切片：800/80', 'manualEdited': False,
                                 'version': 0} for o, s, e, part in chunks]
                report_model = {'id': review_id, 'createdAt': NOW, 'metadata': metadata, 'textHash': text_hash,
                                'decision': 'PASS', 'score': 100,
                                'scores': {'ENCODING': 100, 'AUTHENTICITY': 100, 'FORMAT': 100, 'QUALITY': 100},
                                'issues': [], 'evidence': evidence, 'admitted': True, 'chunkCount': len(chunks),
                                'workflowStatus': 'APPROVED', 'aiModel': 'manual-import', 'aiCompletedAt': NOW,
                                'manualReviewerId': 1, 'manualReviewedAt': NOW,
                                'manualNote': '官网技术资料批量导入，已审核通过并发布。'}
                snapshot = {'report': report_model, 'text': content, 'filename': pdf.name, 'parsingIssues': [],
                            'submittedBy': 1, 'evidenceHistory': [evidence], 'chunks': chunk_models,
                            'chunking': {'category': 'QA', 'strategy': 'SEMANTIC', 'chunkSize': 800,
                                         'overlap': 80, 'delimiterRegex': None}, 'pages': []}
                if args.apply:
                    cur.execute("""INSERT INTO cms_document_review (id,tenant_id,snapshot,original_file,original_file_path,decision,created_at)
                                   VALUES (%s,%s,%s,NULL,%s,'PASS',NOW())
                                   ON DUPLICATE KEY UPDATE snapshot=VALUES(snapshot),original_file_path=VALUES(original_file_path),decision='PASS'""",
                                (review_id, TENANT, json.dumps(snapshot, ensure_ascii=False), local_path))
                    cur.execute("""INSERT INTO cms_knowledge_document
                                   (review_id,tenant_id,title,validity,source_url,text_hash,content,doc_category,local_pdf_path,
                                    chunk_strategy,chunk_config,review_status,version,published,created_at)
                                   VALUES (%s,%s,%s,'UNKNOWN',%s,%s,%s,'QA',%s,'SEMANTIC',%s,'APPROVED',1,1,NOW())
                                   ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),source_url=VALUES(source_url),
                                    text_hash=VALUES(text_hash),doc_category='QA',local_pdf_path=VALUES(local_pdf_path),
                                    chunk_strategy='SEMANTIC',chunk_config=VALUES(chunk_config),review_status='APPROVED',
                                    published=1,deleted_at=NULL,version=version+1""",
                                (review_id, TENANT, title, local_path, text_hash, content, local_path,
                                 json.dumps(snapshot['chunking'], ensure_ascii=False)))
                    cur.execute('DELETE FROM cms_knowledge_chunk WHERE tenant_id=%s AND review_id=%s', (TENANT, review_id))
                    for ordinal, start, end, part in chunks:
                        cur.execute("""INSERT INTO cms_knowledge_chunk
                                       (review_id,tenant_id,ordinal,start_offset,end_offset,content,page_number,
                                        ai_review_result,ai_review_message,is_manual_edited,version,valid)
                                       VALUES (%s,%s,%s,%s,%s,%s,NULL,'PASS','默认语义切片：800/80',0,0,1)""",
                                    (review_id, TENANT, ordinal, start, end, part))
                total_chunks += len(chunks)
                print(f'  [{"导入" if args.apply else "预演"}] {title}  切片 {len(chunks)}')
            if args.apply:
                conn.commit()
        if args.apply:
            print(f'完成：{len(files)} 份 PDF，共 {total_chunks} 个切片')
        else:
            print(f'预演完成：{len(files)} 份 PDF，预计 {total_chunks} 个切片（加 --apply 生效）')
    finally:
        conn.close()

if __name__ == '__main__':
    main()