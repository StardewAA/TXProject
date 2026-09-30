# -*- coding: utf-8 -*-
"""把子长采油厂 3 份成交公告的 OCR 文字回填到知识库（替换扫描版占位内容）。"""
from pathlib import Path
import hashlib, json, re
from datetime import datetime
import pymysql

OCR = Path(r'D:\夸克浏览器\子长采油厂成交公告_OCR识别文字.txt')
TENANT = 1
SIZE, OVERLAP = 800, 80

def split_chunks(text, size=SIZE, overlap=OVERLAP):
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

def parse_ocr():
    sections, current, buf, stop = {}, None, [], False
    for line in OCR.read_text(encoding='utf-8').splitlines():
        if line.startswith('文件：'):
            if current:
                sections[current] = '\n'.join(buf).strip()
            current, buf, stop = line[3:].strip(), [], False
            continue
        if line.startswith('====='):
            if current:
                sections[current] = '\n'.join(buf).strip()
                current, stop = None, True
            continue
        if line.startswith('-----') or line.startswith('【'):
            continue
        if current and not stop:
            buf.append(line)
    if current:
        sections[current] = '\n'.join(buf).strip()
    return {k: v for k, v in sections.items() if v}

def main():
    sections = parse_ocr()
    print(f'解析出 {len(sections)} 段 OCR 文字：')
    for name, text in sections.items():
        print(f'  {name}  ({len(text)} 字)')
    conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='root',
                           database='ruoyi-vue-pro', charset='utf8mb4', autocommit=False)
    try:
        with conn.cursor() as cur:
            for filename, content in sections.items():
                cur.execute("""SELECT review_id, title, local_pdf_path FROM cms_knowledge_document
                               WHERE tenant_id=%s AND doc_category='QA' AND deleted_at IS NULL""", (TENANT,))
                row = None
                key = re.sub(r'\s+', '', filename)
                for candidate in cur.fetchall():
                    if re.sub(r'\s+', '', candidate[2].split('/')[-1]) == key:
                        row = (candidate[0], candidate[1])
                        break
                if not row:
                    print(f'  MISS {filename}')
                    continue
                review_id, title = row
                chunks = split_chunks(content)
                text_hash = hashlib.sha256(content.encode('utf-8')).hexdigest()
                cur.execute("""UPDATE cms_knowledge_document
                               SET content=%s, text_hash=%s, version=version+1
                               WHERE tenant_id=%s AND review_id=%s""",
                            (content, text_hash, TENANT, review_id))
                cur.execute('DELETE FROM cms_knowledge_chunk WHERE tenant_id=%s AND review_id=%s', (TENANT, review_id))
                for ordinal, start, end, part in chunks:
                    cur.execute("""INSERT INTO cms_knowledge_chunk
                                   (review_id,tenant_id,ordinal,start_offset,end_offset,content,page_number,
                                    ai_review_result,ai_review_message,is_manual_edited,version,valid)
                                   VALUES (%s,%s,%s,%s,%s,%s,NULL,'PASS','OCR 文字回填',0,0,1)""",
                                (review_id, TENANT, ordinal, start, end, part))
                # 同步审查快照
                cur.execute("SELECT snapshot FROM cms_document_review WHERE tenant_id=%s AND id=%s", (TENANT, review_id))
                snap_row = cur.fetchone()
                if snap_row and snap_row[0]:
                    snap = json.loads(snap_row[0])
                    snap['text'] = content
                    snap['report']['textHash'] = text_hash
                    snap['report']['chunkCount'] = len(chunks)
                    snap['report']['manualNote'] = '扫描件 OCR 文字回填，已审核通过并发布。'
                    snap['chunks'] = [{'ordinal': o, 'startOffset': s, 'endOffset': e, 'text': part,
                                       'pageNumber': None, 'aiReviewStatus': 'PASS',
                                       'aiReviewMessage': 'OCR 文字回填', 'manualEdited': False, 'version': 0}
                                      for o, s, e, part in chunks]
                    cur.execute("UPDATE cms_document_review SET snapshot=%s WHERE tenant_id=%s AND id=%s",
                                (json.dumps(snap, ensure_ascii=False), TENANT, review_id))
                print(f'  OK {title}  chunks={len(chunks)}')
            conn.commit()
        print('回填完成')
    finally:
        conn.close()

if __name__ == '__main__':
    main()