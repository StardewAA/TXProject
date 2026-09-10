"""Import the two enterprise-provided samples without changing existing products.

Python 3, standard library only. Example:
  python import-tianxin-product-samples.py --source "C:/Users/ltr33/Desktop/电商图设计" --apply

The source contains no prices, selling units or inventory. Products are therefore
created OFF SALE, with zero price/stock, and must be completed in product admin.
Images use the existing infra upload API (database file storage in local deploy).
Re-running preserves edits made in admin and never restores stock or prices.
"""

import argparse
import html
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile


PRODUCTS = [
    dict(id=910031, folder="3PE", document="商品信息.docx", name="3PE加强级防腐钢管",
         keyword="3PE 三层聚乙烯 防腐管 电阻焊钢管 TXG001",
         intro="天信管 · Q235B · 三层聚乙烯防腐；适用于燃气、水利工程。",
         detail="详情页/1.png", count=5,
         specs=["外径45mm / 壁厚3mm / 内径32mm", "外径57mm / 壁厚3.5mm / 内径50mm",
                "外径89mm / 壁厚4mm / 内径80mm", "外径133mm / 壁厚5mm / 内径125mm",
                "外径219mm / 壁厚6mm / 内径200mm", "外径325mm / 壁厚7mm / 内径300mm",
                "外径377mm / 壁厚8mm / 内径350mm"]),
    dict(id=910032, folder="环氧树脂涂层钢管", document="商品参数.docx", name="环氧树脂涂层钢管（内外涂塑钢管）",
         keyword="环氧树脂 涂层钢管 内外涂塑 大口径 焊接钢管",
         intro="天信管 · Q235B · 内外涂塑钢管；用于走水、供排水，可配送到厂。",
         detail="详情页/详情页.png", count=3,
         specs=["219×6mm", "720×9mm", "820×12mm", "920×12mm", "325×10mm"]),
]
MARKER = "tianxin-samples"


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    if value == '':
        return "''"
    # Hex avoids dependence on SQL escaping mode and console encoding.
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


def insert(table, values):
    return f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join(map(literal, values.values()))});"


def owned(values):
    return dict(values, creator=MARKER, updater=MARKER, tenant_id=1)


def mysql(args, query):
    command = ["docker", "exec", "-i", "-e", "MYSQL_PWD", args.mysql_container,
               "mysql", "--default-character-set=utf8mb4", "-uroot", "-N", "-B", "--raw", args.database]
    env = dict(os.environ, MYSQL_PWD=os.environ.get("MYSQL_PWD", "root"))
    result = subprocess.run(command, input=query.encode("utf-8"), capture_output=True, env=env)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8")


def document_html(path):
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    with zipfile.ZipFile(path) as archive:
        root = ET.fromstring(archive.read("word/document.xml"))
    text = lambda node: "".join(node.itertext()) if node.tag.endswith('}t') else "".join(t.text or "" for t in node.findall(".//w:t", ns))
    parts = []
    for block in root.find("w:body", ns):
        if block.tag.endswith("}p") and text(block).strip():
            parts.append("<p>" + html.escape(text(block)) + "</p>")
        elif block.tag.endswith("}tbl"):
            parts.append('<table><tbody>')
            for row in block.findall("w:tr", ns):
                parts.append('<tr>' + ''.join('<td>' + html.escape(text(cell)) + '</td>' for cell in row.findall('w:tc', ns)) + '</tr>')
            parts.append('</tbody></table>')
    return ''.join(parts)


def upload(args, path, product_id, filename):
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="directory"\r\n\r\n'
            f'mall/tianxin/{product_id}\r\n--{boundary}\r\n'
            f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + path.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(args.gateway.rstrip('/') + '/app-api/infra/file/upload', data=body,
                                     headers={'Content-Type': f'multipart/form-data; boundary={boundary}', 'tenant-id': '1'})
    with urllib.request.urlopen(request, timeout=120) as response:
        result = json.load(response)
    if result.get('code') != 0 or not isinstance(result.get('data'), str):
        raise RuntimeError(f'Upload failed: {result}')
    print(f'Uploaded {product_id}/{filename}', flush=True)
    return result['data']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--gateway', default='http://localhost:48080')
    parser.add_argument('--mysql-container', default='deploy-mysql-1')
    parser.add_argument('--database', default='ruoyi-vue-pro')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    for p in PRODUCTS:
        for relative in [p['document'], p['detail']] + [f'主图/{n}.png' for n in range(1, p['count'] + 1)]:
            if not (args.source / p['folder'] / relative).is_file():
                raise FileNotFoundError(relative)
    if not args.apply:
        print('Source files verified. --apply uploads images and creates 2 off-sale products with 12 SKUs (tenant 1, shop 1).')
        return
    if mysql(args, "SELECT COUNT(*) FROM product_shop WHERE id=1 AND tenant_id=1 AND deleted=0;").strip() != '1':
        raise RuntimeError('Expected shop 1 in tenant 1 was not found')
    # Refuse ID collisions before uploading. Already imported records are preserved.
    checks = [('product_spu', [910031, 910032]), ('product_sku', list(range(920031, 920043))),
              ('product_category', [900016, 900017]), ('product_brand', [930022]),
              ('product_property_value', list(range(943031, 943043)))]
    for table, ids in checks:
        conflict = mysql(args, f"SELECT id FROM {table} WHERE id IN ({','.join(map(str, ids))}) AND (COALESCE(creator,'')<>'{MARKER}' OR tenant_id<>1);")
        if conflict.strip():
            raise RuntimeError(f'ID collision in {table}: {conflict}')
    if mysql(args, "SELECT COUNT(*) FROM product_property WHERE id=940001 AND HEX(name)='E8A784E6A0BC' AND tenant_id=1 AND deleted=0;").strip() != '1':
        raise RuntimeError('Expected product specification property 940001 was not found')
    statements = ['-- Enterprise design samples. Original prices/stock/units were not supplied.',
                  '-- Products start OFF SALE (status 0). Complete commercial data in admin before enabling.',
                  'SET NAMES utf8mb4;', 'START TRANSACTION;']
    shared = [
        ('product_category', owned(dict(id=900016, parent_id=0, name='管材管件', pic_url='', sort=11, status=0))),
        ('product_category', owned(dict(id=900017, parent_id=900016, name='防腐钢管', pic_url='', sort=1, status=0))),
        ('product_brand', owned(dict(id=930022, name='天信管', pic_url='', description='品牌来源：企业提供的两份商品参数文档。', sort=22, status=0))),
    ]
    for table, values in shared:
        if not mysql(args, f"SELECT id FROM {table} WHERE id={values['id']};").strip():
            statements.append(insert(table, values))
    sku_id = 920031
    imported = 0
    for p in PRODUCTS:
        if mysql(args, f"SELECT id FROM product_spu WHERE id={p['id']};").strip():
            sku_id += len(p['specs'])
            print(f"Preserving existing product {p['id']}", flush=True)
            continue
        base = args.source / p['folder']
        images = [upload(args, base / f'主图/{n}.png', p['id'], f'main-{n}.png') for n in range(1, p['count'] + 1)]
        detail = upload(args, base / p['detail'], p['id'], 'detail.png')
        description = (f'<p><img src="{html.escape(detail, quote=True)}" alt="{html.escape(p["name"])} 图文介绍" /></p>'
                       '<h2>商品参数</h2>' + document_html(base / p['document']))
        statements.append(insert('product_spu', owned(dict(
            id=p['id'], shop_id=1, name=p['name'], keyword=p['keyword'], introduction=p['intro'], description=description,
            category_id=900017, brand_id=930022, pic_url=images[0], slider_pic_urls=json.dumps(images),
            sort=100 if p['id']==910031 else 99, status=0, spec_type=1, price=0, market_price=0, cost_price=0,
            stock=0, delivery_types='1', give_integral=0, sub_commission_type=0, sales_count=0, virtual_sales_count=0, browse_count=0))))
        for spec in p['specs']:
            value_id = sku_id + 23000
            statements.append(insert('product_property_value', owned(dict(id=value_id, property_id=940001, name=spec, remark='企业设计样例规格'))))
            properties = json.dumps([dict(propertyId=940001, propertyName='规格', valueId=value_id, valueName=spec)], ensure_ascii=False)
            statements.append(insert('product_sku', owned(dict(id=sku_id, spu_id=p['id'], properties=properties,
                price=0, market_price=0, cost_price=0, bar_code=f'TX-SAMPLE-{sku_id}', pic_url=images[0], stock=0,
                weight=0, volume=0, first_brokerage_price=0, second_brokerage_price=0, sales_count=0))))
            sku_id += 1
        imported += 1
    statements.append('COMMIT;')
    if imported:
        sql = '\n'.join(statements) + '\n'
        output = Path(__file__).with_name('2026-09-10-tianxin-product-samples.sql')
        output.write_text(sql, encoding='utf-8')
        mysql(args, sql)
    print(f'Created {imported} off-sale products. Existing records left unchanged.', flush=True)


if __name__ == '__main__':
    main()
