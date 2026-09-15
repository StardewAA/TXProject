"""Extract embedded PDFs without executing OLE objects; audit their text layer."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from zipfile import ZipFile
from pypdf import PdfReader

p = argparse.ArgumentParser()
p.add_argument('docx', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
report = []
with ZipFile(a.docx) as archive:
    for name in archive.namelist():
        if not name.startswith('word/embeddings/') or name.endswith('/'):
            continue
        blob = archive.read(name)
        start, end = blob.find(b'%PDF-'), blob.rfind(b'%%EOF')
        if start < 0 or end < start:
            raise ValueError(f'No complete embedded PDF in {name}')
        data = blob[start:end + 5] + b'\n'
        digest = hashlib.sha256(data).hexdigest()
        target = a.output / (Path(name).stem + '.pdf')
        target.write_bytes(data)
        reader = PdfReader(io.BytesIO(data))
        pages = [page.extract_text() or '' for page in reader.pages]
        target.with_suffix('.txt').write_text('\n\n'.join(f'--- PDF PAGE {i} ---\n{text}' for i, text in enumerate(pages, 1)), encoding='utf-8')
        report.append(dict(file=target.name, sha256=digest, pages=len(pages), chars_per_page=[len(t.strip()) for t in pages], preview='\n'.join(pages[:2])[:2500]))
(a.output / 'inspection.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
