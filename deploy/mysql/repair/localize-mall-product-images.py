"""Reuse the bundled ERP product images in the shared file service.
Dry run by default. --apply backs up image fields and applies an atomic SQL batch.
Any stale image field rolls back the entire batch; retry with a new output folder.
Only tenant 1, products 910001..910030, and existing third-party image URLs qualify.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request
import uuid

HOSTS = {'img.alicdn.com', 'image.made-in-china.com', 'img10.360buyimg.com',
         'www.graco.com', 'iororwxhqopojr5p-static.micyjz.com',
         'ko.onekapaint.com', '14060438.s21i.faiusr.com', 'img11.360buyimg.com',
         'r2.shopwis.cn', 'img13.360buyimg.com', 'thai.baodupanel.com',
         'down-tw.img.susercontent.com'}

IMAGE_FIELDS = {
    'product_spu': {'pic_url', 'slider_pic_urls', 'description'},
    'product_sku': {'pic_url'},
    'trade_order_item': {'pic_url'},
    'trade_after_sale': {'pic_url'},
}
COMMITTED = 'MALL_IMAGES_COMMITTED'
ROLLED_BACK = 'MALL_IMAGES_ROLLED_BACK'

def sql_value(value):
    if value is None:
        return 'NULL'
    return 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4) COLLATE utf8mb4_bin'

def mysql(query):
    result = subprocess.run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', args.container,
        'mysql', '-uroot', '--default-character-set=utf8mb4', '-N', '-B', '--raw',
        '--skip-force', '--skip-reconnect', args.database],
        input=query.encode('utf-8'), capture_output=True,
        env=dict(os.environ, MYSQL_PWD=os.environ.get('MYSQL_PWD', 'root')))
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8').strip()

def rows(table, fields, where):
    pairs = ','.join("'" + field + "'," + field for field in ['id'] + fields)
    output = mysql('SELECT JSON_OBJECT(' + pairs + ') FROM ' + table + ' WHERE ' + where)
    return [json.loads(line) for line in output.splitlines()] if output else []


def guarded_sql(changes, restore=False):
    """One connection/transaction, including validation and the commit decision.

    UPDATE locks are retained until every expected field has matched. A stale,
    missing, deleted or foreign-tenant row rolls back even earlier successful
    updates. Byte comparisons also detect case/trailing-space changes. The SQL
    can be saved/replayed without a stored procedure or Python DB dependency.
    """
    changes = sorted(changes, key=lambda r: (r['table'], r['id'], r['field']))
    seen = set()
    for r in changes:
        if r['table'] not in IMAGE_FIELDS or r['field'] not in IMAGE_FIELDS[r['table']]:
            raise ValueError('Unsupported image field')
        if type(r['id']) is not int or r['id'] <= 0:
            raise ValueError('Invalid row id')
        key = (r['table'], r['id'], r['field'])
        if key in seen or r['before'] == r['after']:
            raise ValueError('Duplicate or unchanged image field')
        seen.add(key)
    tables = sorted({r['table'] for r in changes})
    sql = ['-- Run as one batch with mysql --skip-force --skip-reconnect.',
           'SET NAMES utf8mb4;', 'START TRANSACTION;', 'SET @mall_images_ok = 1;']
    if tables:
        # Do not promise rollback for MyISAM or silently skip a missing table.
        table_list = ','.join(sql_value(t) for t in tables)
        sql.append('SET @mall_images_ok = (SELECT COUNT(*) FROM information_schema.tables '
                   'WHERE table_schema=DATABASE() AND engine=\'InnoDB\' AND table_name IN ('
                   + table_list + f')) = {len(tables)};')
    for r in changes:
        before, after = (r['after'], r['before']) if restore else (r['before'], r['after'])
        sql.append(f"UPDATE {r['table']} SET {r['field']}={sql_value(after)} "
                   f"WHERE @mall_images_ok=1 AND tenant_id=1 AND deleted=0 AND id={r['id']} "
                   f"AND CAST({r['field']} AS BINARY) <=> CAST({sql_value(before)} AS BINARY);")
        # Read immediately after UPDATE; intervening statements reset ROW_COUNT.
        sql.append('SET @mall_images_ok = @mall_images_ok AND (ROW_COUNT() = 1);')
    for r in changes:
        expected = r['before'] if restore else r['after']
        # Verify before COMMIT in the same transaction, including non-strict SQL
        # truncation/conversion. Locks prevent another editor changing these rows.
        sql.append(f"SET @mall_images_ok = @mall_images_ok AND (SELECT COUNT(*) FROM {r['table']} "
                   f"WHERE tenant_id=1 AND deleted=0 AND id={r['id']} "
                   f"AND CAST({r['field']} AS BINARY) <=> CAST({sql_value(expected)} AS BINARY)) = 1;")
    sql.extend([
        "SET @mall_images_finish = IF(@mall_images_ok=1, 'COMMIT', 'DO 0');",
        'PREPARE mall_images_finish FROM @mall_images_finish;',
        'EXECUTE mall_images_finish;',
        'DEALLOCATE PREPARE mall_images_finish;',
        # COMMIT is preparable in MySQL 8.4; ROLLBACK need not be. After a
        # successful COMMIT this is harmless; otherwise it undoes the full batch.
        'ROLLBACK;',
        f"SELECT IF(@mall_images_ok=1, '{COMMITTED}', '{ROLLED_BACK}');",
    ])
    return '\n'.join(sql) + '\n'


def apply_guarded_sql(statements):
    result = mysql(statements)
    if result == ROLLED_BACK:
        raise RuntimeError('Image batch rolled back: a row changed, was removed, or a table '
                           'is not InnoDB. No image fields from this batch were committed. '
                           'Review the backup and retry with a new output folder.')
    if result != COMMITTED:
        # A lost response after COMMIT is ambiguous, not proof of a rollback.
        raise RuntimeError('Unexpected transaction result; inspect the saved backup and '
                           'current image fields before retrying.')

def upload(path):
    content = path.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    if content.startswith(bytes.fromhex('ffd8')):
        mime, suffix = 'image/jpeg', '.jpg'
    elif content.startswith(bytes.fromhex('89504e47')):
        mime, suffix = 'image/png', '.png'
    elif content[:4] == b'RIFF' and content[8:12] == b'WEBP':
        mime, suffix = 'image/webp', '.webp'
    else:
        raise ValueError('Unsupported image: ' + str(path))
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="directory"\r\n\r\n'
            f'mall/erp-local\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; '
            f'filename="{path.stem}-{digest[:12]}{suffix}"\r\nContent-Type: {mime}\r\n\r\n').encode()
    body += content + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(args.gateway + '/app-api/infra/file/upload', data=body,
        headers={'Content-Type': 'multipart/form-data; boundary=' + boundary, 'tenant-id': '1'})
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.load(response)
    if result.get('code') != 0 or not isinstance(result.get('data'), str):
        raise RuntimeError('Image upload failed: ' + str(result))
    url = result['data']
    if urllib.parse.urlparse(url).scheme not in {'http', 'https'}:
        raise RuntimeError('Expected HTTP file URL')
    with urllib.request.urlopen(url, timeout=30) as response:
        if hashlib.sha256(response.read()).hexdigest() != digest:
            raise RuntimeError('Uploaded image checksum mismatch')
    return url

def main():
    products = rows('product_spu', ['pic_url', 'slider_pic_urls', 'description'],
                    'tenant_id=1 AND deleted=0 AND id BETWEEN 910001 AND 910030')
    products = [p for p in products if urllib.parse.urlparse(p['pic_url'] or '').hostname in HOSTS]
    for p in products:
        if not (args.source / (str(p['id']) + '.jpg')).is_file():
            raise FileNotFoundError(str(p['id']) + '.jpg')
    print(f'Eligible products: {len(products)}', flush=True)
    if not args.apply or not products:
        return
    args.output.mkdir(parents=True, exist_ok=False)
    backup = []
    for product in products:
        old = product['pic_url']
        new = upload(args.source / (str(product['id']) + '.jpg'))
        targets = [('product_spu', product)]
        for table in ['product_sku', 'trade_order_item', 'trade_after_sale']:
            columns = mysql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + sql_value(table)).splitlines()
            if 'pic_url' in columns and 'spu_id' in columns:
                targets += [(table, row) for row in rows(table, ['pic_url'],
                    f"tenant_id=1 AND deleted=0 AND spu_id={product['id']} AND pic_url=" + sql_value(old))]
        for table, row in targets:
            for field in ['pic_url', 'slider_pic_urls', 'description']:
                before = row.get(field)
                if isinstance(before, str) and old in before:
                    after = before.replace(old, new)
                    backup.append(dict(table=table, id=row['id'], field=field, before=before, after=after))
        print('Prepared ' + str(product['id']), flush=True)
    (args.output / 'image-fields-before.json').write_text(json.dumps(backup, ensure_ascii=False, indent=2), encoding='utf-8')
    statements = guarded_sql(backup)
    (args.output / 'apply.sql').write_text(statements, encoding='utf-8')
    (args.output / 'restore.sql').write_text(guarded_sql(backup, restore=True), encoding='utf-8')
    apply_guarded_sql(statements)
    print(f'Updated and verified {len(backup)} image fields.', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[3] / 'txgy-mall-web/public/product-images')
    parser.add_argument('--gateway', default='http://localhost:48080')
    parser.add_argument('--container', default='deploy-mysql-1')
    parser.add_argument('--database', default='ruoyi-vue-pro')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    main()
