"""Download official originals and validate complete, sequential law articles."""
import argparse
import gzip
import hashlib
import json
import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen
from lxml import html as html_parser

ARTICLE = re.compile(r'^第([一二三四五六七八九十百千万零〇]+)\s*条')

def number(s):
    digits = dict(zip('零〇一二三四五六七八九', [0,0,1,2,3,4,5,6,7,8,9]))
    total = current = 0
    for c in s:
        if c in digits:
            current = digits[c]
        else:
            total += (current or 1) * {'十':10,'百':100,'千':1000,'万':10000}[c]
            current = 0
    return total + current

def extract(html, title):
    soup = html_parser.fromstring(html)
    for tag in soup.xpath('//script | //style | //noscript'):
        tag.drop_tree()
    candidates = soup.xpath('//*[contains(@class,"TRS_Editor") or contains(@class,"trs_editor") or @id="zoom" or @id="zoomcon" or @class="article-content" or @class="content" or @class="content_body_box" or @id="j-show-body"]')
    valid = [c for c in candidates if len(c.text_content()) > 1000]
    # Use the smallest content container with a full legal body, not navigation.
    valid = [c for c in valid if '第一条' in c.text_content() or title in c.text_content()]
    root = min(valid, key=lambda c: len(c.text_content())) if valid else soup
    # Preserve inline spans while retaining paragraph boundaries.
    for br in root.xpath('.//br'):
        br.tail = '\n' + (br.tail or '')
    lines = []
    for node in root.xpath('.//p[not(ancestor::table)] | .//table[not(ancestor::table)]'):
        if node.tag == 'table':
            lines.append('\n'.join(' | '.join(cell.text_content().strip() for cell in row.xpath('./th | ./td')) for row in node.xpath('.//tr')))
        else:
            lines.append(node.text_content().strip())
    if len(''.join(lines)) < 1000:
        lines = root.text_content().splitlines()
    return '\n'.join(x for x in lines if x).split('版权所有：')[0].strip()

def split_articles(text, expected):
    result = []
    for line in text.splitlines():
        line = line.strip()
        match = ARTICLE.match(line)
        if match:
            result.append({'number':number(match[1]),'label':f'第{match[1]}条','text':line})
        elif result:
            # Chapter headings belong in the original, not the preceding article.
            if not re.match(r'^第[一二三四五六七八九十百]+[章节编]', line):
                result[-1]['text'] += '\n' + line
    actual = [r['number'] for r in result]
    if actual != list(range(1, expected + 1)):
        raise ValueError(f'Article sequence mismatch: expected 1..{expected}, got {actual[:12]} ... {actual[-12:]} (count {len(actual)})')
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    sources = json.loads(Path(__file__).with_name('cms_policy_sources.json').read_text(encoding='utf-8'))
    def prepare(s):
        try:
            path = args.output / (s['key'] + '.html')
            if not path.exists():
                with urlopen(s['url'], timeout=45) as response:
                    path.write_bytes(response.read())
            raw = path.read_bytes()
            if raw.startswith(b'\x1f\x8b'):
                raw = gzip.decompress(raw)
            # Official sites declare UTF-8; reject decoding errors rather than corrupt law text.
            html = raw.decode('utf-8-sig')
            text = extract(html, s['title'])
            # This official page joins article 68 onto the final paragraph of 67.
            # Restore only its paragraph boundary; preserve all original wording.
            if s['key'] == 'construction-2019':
                text = text.replace('连带赔偿责任。第六十八条', '连带赔偿责任。\n第六十八条')
            if len(text) < 1000:
                raise ValueError('Full text unavailable')
            if 'articles' in s:
                chunks = split_articles(text, s['articles'])
            else:
                blocks, current = [], ''
                for line in text.splitlines():
                    if current and len(current) + len(line) > 1400:
                        blocks.append(current)
                        current = ''
                    current += ('\n' if current else '') + line
                if current:
                    blocks.append(current)
                chunks = [{'number':i,'label':f'原文片段{i}','text':t} for i,t in enumerate(blocks,1)]
            doc = dict(s, sha256=hashlib.sha256(raw).hexdigest(), chunks=chunks)
            path.with_suffix('.txt').write_text(text, encoding='utf-8')
            path.with_suffix('.json').write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
            return {'key':s['key'],'chunks':len(chunks),'characters':len(text),'ok':True}
        except Exception as exc:
            return {'key':s['key'],'ok':False,'error':str(exc)}
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(prepare,sources))
    (args.output/'preparation-report.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(results,ensure_ascii=False,indent=2))
    if any(not r['ok'] for r in results):
        raise SystemExit(1)

if __name__ == '__main__':
    main()
