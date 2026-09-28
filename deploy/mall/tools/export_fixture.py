"""Maintainer tool: read schema/public demo catalogue; emit a clean local fixture.

Not needed by people deploying the mall. Never exports existing users, tokens,
orders, chats, addresses, payment/file credentials or uploaded file contents.
All login accounts, contacts, WMS quantities and payment channels are synthetic.
Usage: python deploy/mall/tools/export_fixture.py --container <mysql-container>
Requires Python 3 and bcrypt. Reads MySQL through docker exec, without exposing
MYSQL_ROOT_PASSWORD in host arguments or logs.
"""
import argparse
import collections
import hashlib
import json
import mimetypes
import re
import subprocess
from pathlib import Path

import bcrypt
from fixture_units import resolve_unit

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parents[1] / 'sql'
parser = argparse.ArgumentParser()
parser.add_argument('--container', required=True)
parser.add_argument('--image-origin', default='https://java-ai-comency.oss-cn-beijing.aliyuncs.com')
args = parser.parse_args()
if not args.image_origin.startswith('https://') or '?' in args.image_origin or '#' in args.image_origin:
    raise ValueError('Public image origin must be a stable HTTPS origin without query or fragment')


def query(sql):
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N --raw --default-character-set=utf8mb4 ruoyi-vue-pro -e "$1"'
    return subprocess.run(['docker', 'exec', args.container, 'sh', '-c', command, 'sh', sql],
                          capture_output=True, check=True).stdout.decode('utf-8').splitlines()


metadata = collections.defaultdict(dict)
for line in query("SELECT table_name,column_name,data_type FROM information_schema.columns "
                  "WHERE table_schema='ruoyi-vue-pro' ORDER BY table_name,ordinal_position"):
    table, column, typ = line.split('\t')
    metadata[table][column] = typ


def rows(table, where='1=1'):
    # JSON avoids mysql tab/escape ambiguity, especially for Chinese text and BIT.
    pairs = []
    for column, typ in metadata[table].items():
        val = f'CAST(`{column}` AS UNSIGNED)' if typ == 'bit' else f'`{column}`'
        pairs.extend([f"'{column}'", val])
    return [json.loads(line) for line in query(
        f"SELECT JSON_OBJECT({','.join(pairs)}) FROM `{table}` WHERE {where} ORDER BY id")]


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return '1' if value else '0'
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, (list, dict)):
        value = json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''").replace('\x00', '\\0') + "'"


data = collections.defaultdict(list)


def add(table, row):
    row = {k: v for k, v in row.items() if k in metadata[table]}
    for field in ('creator', 'updater'):
        if field in metadata[table]:
            row[field] = 'mall-fixture'
    for field in ('create_time', 'update_time'):
        if field in metadata[table]:
            row[field] = '2026-09-24 00:00:00'
    data[table].append(row)


# Structural baseline only. No existing data or stored procedures are exported.
OUT.mkdir(parents=True, exist_ok=True)
tables = [t for t in metadata if t.startswith(('system_', 'infra_', 'member_', 'product_',
                                             'trade_', 'promotion_', 'pay_', 'wms_', 'mall_', 'QRTZ_', 'qrtz_'))
          and t != 'mall_release' and not any(word in t for word in ('backup', '_bak', 'trade_erp_mock_'))]
dump = subprocess.run(['docker', 'exec', args.container, 'sh', '-c',
                      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-data '
                      '--skip-add-drop-table --skip-triggers --no-tablespaces --set-gtid-purged=OFF '
                      '--default-character-set=utf8mb4 ruoyi-vue-pro "$@"', 'sh', *tables],
                     capture_output=True, check=True).stdout.decode('utf-8')
dump = re.sub(r' AUTO_INCREMENT=\d+', '', dump)
(OUT / '001-schema.sql').write_text(
    '-- Clean installation only. Existing tables cause an error; do not import over a used database.\n'
    'USE `ruoyi-vue-pro`;\n' + dump, encoding='utf-8', newline='\n')

# Public, non-credential configuration. Rows containing user data are not read.
for table in ('system_menu', 'system_dict_type', 'system_dict_data', 'product_category',
              'product_brand', 'product_property', 'product_property_value', 'member_config',
              'member_level', 'member_group', 'member_sign_in_config', 'trade_config',
              'trade_delivery_express', 'trade_delivery_express_template',
              'trade_delivery_express_template_charge', 'trade_delivery_express_template_free'):
    for row in rows(table, 'deleted=0'):
        for field in ('pic_url', 'logo', 'logo_url', 'icon', 'icon_url'):
            if field in row and isinstance(row[field], str) and row[field].startswith('http'):
                row[field] = ''
        if 'brokerage_enabled' in row:
            row['brokerage_enabled'] = 0
        add(table, row)

tenants = rows('system_tenant', 'deleted=0 AND (id=1 OR id BETWEEN 2001 AND 2024 OR id=3000)')
tenant_ids = {t['id'] for t in tenants}
# Keep only the modules shipped in this Compose stack, plus their parent menus.
all_menus = {r['id']: r for r in data['system_menu']}
selected = {r['id'] for r in all_menus.values() if
            (r.get('permission') or '').startswith(('system:', 'infra:', 'member:', 'product:',
                                                     'trade:', 'promotion:', 'pay:', 'wms:'))
            or (r.get('component') or '').startswith(('mall/', 'pay/', 'member/', 'wms/'))}
for mid in list(selected):
    parent = all_menus[mid].get('parent_id')
    while parent and parent in all_menus and parent not in selected:
        selected.add(parent)
        parent = all_menus[parent].get('parent_id')
data['system_menu'] = [r for r in all_menus.values() if r['id'] in selected]
menus = [r['id'] for r in data['system_menu']]
add('system_tenant_package', dict(id=119, name='商城演示套餐', status=0, menu_ids=menus, deleted=0))
for row in tenants:
    row.update(contact_name='演示联系人', contact_mobile='13800000000',
               contact_user_id=1 if row['id'] == 1 else (None if row['id'] == 3000 else 700000000 + row['id']),
               websites=[], expire_time='2099-12-31 23:59:59', account_count=10000,
               package_id=0 if row['id'] == 1 else 119)
    add('system_tenant', row)
for row in rows('mall_portal_tenant', 'deleted=0'):
    if row['tenant_id'] in tenant_ids:
        for field in ('logo', 'logo_url', 'tenant_logo'):
            if field in row:
                row[field] = ''
        add('mall_portal_tenant', row)

enterprises = rows('product_enterprise', 'deleted=0 AND audit_status=1')
enterprises = {r['id']: r for r in enterprises if r['tenant_id'] in tenant_ids
               and r['id'] not in (300000115, 300000116)}
shops = rows('product_shop', 'deleted=0 AND status=0')
shops = {r['id']: r for r in shops if r['enterprise_id'] in enterprises}
for row in enterprises.values():
    row.update(contact_name='演示联系人', contact_mobile='13800000000', address='演示园区',
               license_url='', credit_code=f"DEMO{row['id']:014d}", audit_remark='独立演示环境预置资料')
    add('product_enterprise', row)
for row in shops.values():
    row.update(contact_name='演示客服', contact_mobile='13800000000', manager_user_id=None,
               logo_url='', introduction=f"{row['name']}，独立演示目录")
    add('product_shop', row)


def picture(product):
    if 910001 <= product['id'] <= 910030:
        return f"/product-images/{product['id']}.jpg"
    # Prefer the checked-in matching picture already used by the public catalogue.
    match = re.search(r'/product-images/[^"\s<>?]+', product.get('pic_url') or '')
    if match and (ROOT / 'txgy-mall-web/public' / match[0].lstrip('/')).is_file():
        return match[0]
    name = product['name']
    kinds = [('粘', 'mastic-adhesive'), ('胶', 'mastic-adhesive'), ('管托', 'pipe-support'),
             ('3PE', '3pe-pipe'), ('预制', 'preinsulated-pipe'), ('纤维', 'ceramic-fiber'),
             ('防火', 'fireproof-coating'), ('岩棉', 'mineral-wool'), ('玻璃棉', 'mineral-wool'),
             ('橡塑', 'elastomeric-rubber'), ('玻纤', 'fiberglass-cloth'), ('铝', 'metal-cladding'),
             ('铁皮', 'metal-cladding'), ('板', 'rigid-foam-board')]
    asset = next((asset for word, asset in kinds if word in name), 'coating')
    return f'/product-images/kedun-real/{asset}.png'


products = rows('product_spu', 'deleted=0 AND status=1')
products = {r['id']: r for r in products if r['shop_id'] in shops
            and ((910001 <= r['id'] <= 910030) or (940201 <= r['id'] <= 940235)
                 or (200110001 <= r['id'] <= 300010035))}
assets = {}
for row in products.values():
    relative = picture(row)
    source = ROOT / 'txgy-mall-web/public' / relative.lstrip('/')
    content = source.read_bytes()  # Missing source is a release error, not a silent placeholder.
    key = 'mall-demo/v1' + relative
    assets.setdefault(key, dict(objectKey=key, source=source.relative_to(ROOT).as_posix(),
                                sha256=hashlib.sha256(content).hexdigest(), size=len(content),
                                contentType=mimetypes.guess_type(source.name)[0], spuIds=[]))['spuIds'].append(row['id'])
    row.update(pic_url=args.image_origin.rstrip('/') + '/' + key, review_status=20, content_version=1,
               approved_version=1, submission_id=None, review_remark='演示商品',
               description=f"<p>{row['name']}</p><p>{row.get('introduction') or ''}</p>",
               sales_count=0, virtual_sales_count=0, browse_count=0, give_integral=0)
    row['slider_pic_urls'] = [row['pic_url']]
skus = [r for r in rows('product_sku', 'deleted=0') if r['spu_id'] in products]
for row in skus:
    product = products[row['spu_id']]
    enterprise = shops[product['shop_id']]['enterprise_id']
    row.update(pic_url=product['pic_url'], stock=100, sales_count=0,
               wms_sku_id=row['id'], wms_warehouse_id=enterprise)
    add('product_sku', row)
    # WMS is the stock source. Seed matching finished goods and quantities as well.
    properties = json.loads(row['properties']) if isinstance(row['properties'], str) else row['properties']
    unit_name = next((p.get('valueName', '个') for p in (properties or [])
                      if p.get('propertyName') in ('计量单位', '单位')), '个')
    unit_code = resolve_unit(unit_name)
    add('wms_item', dict(id=row['id'], code=f"MALL-SKU-{row['id']}", name=product['name'],
                         category_id=0, unit=unit_code, material_type='FINISHED_GOODS', status=0, quality_required=0,
                         source_version=1, tenant_id=row['tenant_id'], deleted=0))
    add('wms_item_sku', dict(id=row['id'], item_id=row['id'], code=f"MALL-SKU-{row['id']}",
                             name=product['name'], selling_price=row['price'],
                             cost_price=row['cost_price'], tenant_id=row['tenant_id'], deleted=0))
    add('wms_inventory', dict(id=row['id'], sku_id=row['id'], warehouse_id=enterprise,
                              quantity=100, tenant_id=row['tenant_id'], deleted=0,
                              remark='独立演示库存'))
for product in products.values():
    product['stock'] = sum(s['stock'] for s in skus if s['spu_id'] == product['id'])
    add('product_spu', product)
for eid in sorted({s['wms_warehouse_id'] for s in skus}):
    ent = enterprises[eid]
    add('wms_warehouse', dict(id=eid, code=f'MALL-FG-{eid}', name=f"{ent['name']}演示成品仓",
                             warehouse_type='FINISHED_GOODS', status=0, data_version=1,
                             tenant_id=ent['tenant_id'], deleted=0))

# New synthetic accounts. No production password hash is read or copied.
password = bcrypt.hashpw(b'admin123', bcrypt.gensalt(rounds=10)).decode()
account_list = []
for tenant in tenants:
    tid = tenant['id']
    if tid == 3000:
        continue  # Quarantined legacy tenant must never become a login target.
    aid = 1 if tid == 1 else 700000000 + tid
    role = aid
    login = 'admin' if tid == 1 else f'tenant{tid}'
    add('system_users', dict(id=aid, username=login, password=password, nickname='演示管理员',
                             status=0, tenant_id=tid, deleted=0))
    add('system_role', dict(id=role, name='演示管理员', code='super_admin' if tid == 1 else 'tenant_admin',
                            sort=0, status=0, type=1, data_scope=1, data_scope_dept_ids=[],
                            tenant_id=tid, deleted=0))
    add('system_user_role', dict(user_id=aid, role_id=role, tenant_id=tid, deleted=0))
    for mid in menus:
        add('system_role_menu', dict(role_id=role, menu_id=mid, tenant_id=tid, deleted=0))
    tenant['contact_user_id'] = aid
    account_list.append(dict(tenant=tid, entry='admin', username=login))
    uid = 710000000 + tid
    buyer = 'buyer01' if tid == 1 else f'buyer{tid}'
    mobile = f'139{tid:08d}'
    add('member_user', dict(id=uid, login_name=buyer, password=password, mobile=mobile,
                           contact_mobile=mobile, nickname='演示采购用户', status=0,
                           point=0, experience=0, member_identity='BUYER', register_terminal=10, register_ip='127.0.0.1',
                           tenant_id=tid, deleted=0))
    add('member_platform_account', dict(id=uid, platform_no=f'DEMO{uid}', credential_type='LOGIN_NAME',
                                        credential_identifier=buyer, login_name=buyer, mobile=mobile,
                                        canonical_member_id=uid, status=0, deleted=0))
    add('member_platform_account_link', dict(id=uid, platform_account_id=uid, legacy_member_id=uid,
                                             source_tenant_id=tid, link_status='READY',
                                             link_origin='SELF_REGISTRATION', identity_verified_by='mall-fixture',
                                             identity_verified_at='2026-09-24 00:00:00', deleted=0))
    account_list.append(dict(tenant=tid, entry='PC/H5', username=buyer))
    # Internal Docker callbacks, independent of the browser host/port.
    for offset, key, notify in [(0, 'mall', 'trade/order'), (1, 'wallet', 'pay/wallet-recharge')]:
        appid = tid * 10 + offset
        order_url = ('http://gateway:48080/app-api/' if key == 'mall' else
                     'http://gateway:48080/admin-api/') + notify + '/update-paid'
        refund_url = 'http://gateway:48080/admin-api/' + ('trade/after-sale' if key == 'mall' else notify) + '/update-refunded'
        add('pay_app', dict(id=appid, app_key=key, name=f'演示{key}', status=0,
                            order_notify_url=order_url, refund_notify_url=refund_url,
                            tenant_id=tid, deleted=0))
        add('pay_channel', dict(id=appid * 10, app_id=appid, code='mock', status=0,
                                fee_rate=0, config={'@class':'cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.NonePayClientConfig'},
                                tenant_id=tid, deleted=0))
        if key == 'mall':
            add('pay_channel', dict(id=appid * 10 + 1, app_id=appid, code='wallet', status=0,
                                    fee_rate=0, config={'@class':'cn.iocoder.txgy.module.pay.framework.pay.core.client.impl.NonePayClientConfig'},
                                    tenant_id=tid, deleted=0))

shop_menu_ids = [r['id'] for r in data['system_menu'] if (r.get('permission') or '').startswith(
    ('product:', 'trade:', 'promotion:kefu', 'system:user:query', 'system:tenant:query', 'infra:file:'))]
# Include ancestor menus so the navigation tree is reachable.
menu_map = {m['id']: m for m in data['system_menu']}
for mid in list(shop_menu_ids):
    parent = menu_map[mid].get('parent_id')
    while parent and parent in menu_map and parent not in shop_menu_ids:
        shop_menu_ids.append(parent)
        parent = menu_map[parent].get('parent_id')
for shop in shops.values():
    tid, sid, eid = shop['tenant_id'], shop['id'], shop['enterprise_id']
    if tid == 3000:
        continue
    uid = 800000000 + sid
    login = f'shop{sid}'
    add('system_users', dict(id=uid, username=login, password=password, nickname='演示店铺经营账号',
                             status=0, tenant_id=tid, deleted=0))
    add('system_role', dict(id=uid, name='演示店铺经营', code=f'demo_shop_{sid}', sort=1, status=0,
                            type=2, data_scope=1, data_scope_dept_ids=[], tenant_id=tid, deleted=0))
    add('system_user_role', dict(user_id=uid, role_id=uid, tenant_id=tid, deleted=0))
    for mid in sorted(set(shop_menu_ids)):
        add('system_role_menu', dict(role_id=uid, menu_id=mid, tenant_id=tid, deleted=0))
    add('product_enterprise_admin_member', dict(admin_user_id=uid, enterprise_id=eid,
                                                scope_type=2, status=0, tenant_id=tid, deleted=0))
    add('product_shop_admin_authorization', dict(admin_user_id=uid, enterprise_id=eid, shop_id=sid,
                                                status=0, tenant_id=tid, deleted=0))
    account_list.append(dict(tenant=tid, entry='admin', username=login, shop=sid))

add('system_oauth2_client', dict(id=1, client_id='default', secret='demo-local-client', name='商城演示', logo='',
                                 status=0, access_token_validity_seconds=7200, refresh_token_validity_seconds=2592000,
                                 authorized_grant_types=['password', 'refresh_token'], scopes=['user_info'],
                                 auto_approve_scopes=[], redirect_uris=[], deleted=0))
add('infra_file_config', dict(id=4, name='数据库上传（OSS 上传验收前保留）', storage=1, master=1,
                              config={'@class':'cn.iocoder.txgy.module.infra.framework.file.core.client.db.DBFileClientConfig',
                                      'domain':'http://localhost:48080'}, deleted=0))
add('infra_file_config', dict(id=36, name='商城公开 OSS 图片（上传待验收）', storage=20, master=0,
                              config={'@class':'cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClientConfig',
                                      'endpoint':'oss-cn-beijing.aliyuncs.com', 'bucket':'java-ai-comency',
                                      'domain':args.image_origin.rstrip('/'), 'region':'cn-beijing',
                                      'accessKey':'env:OSS_ACCESS_KEY', 'accessSecret':'env:OSS_ACCESS_SECRET',
                                      'enablePathStyleAccess':False, 'enablePublicAccess':True}, deleted=0))
# Stable object keys accompany the rendered URLs. Only public fixture pictures
# are listed here; never include licences, addresses, chat or order attachments.
for index, (key, asset) in enumerate(sorted(assets.items()), start=960000001):
    add('infra_file', dict(id=index, config_id=36, name=Path(key).name, path=key,
                          url=args.image_origin.rstrip('/') + '/' + key,
                          type=asset['contentType'], size=asset['size'], deleted=0))

required = collections.defaultdict(list)
for line in query("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='ruoyi-vue-pro' "
                  "AND is_nullable='NO' AND column_default IS NULL AND extra NOT LIKE '%auto_increment%'"):
    table, column = line.split('\t')
    required[table].append(column)
missing = {table: sorted({c for r in records for c in required[table] if r.get(c) is None})
           for table, records in data.items()}
missing = {t: cs for t, cs in missing.items() if cs}
if missing:
    raise RuntimeError(f'Required fixture fields are missing: {missing}')

parts = ['-- Synthetic local/demo data; password for all documented demo accounts: admin123.',
         '-- No existing user, order, address, token or third-party secret is included.',
         'USE `ruoyi-vue-pro`;', 'SET NAMES utf8mb4;', 'SET FOREIGN_KEY_CHECKS=0;', 'START TRANSACTION;']
for table, records in data.items():
    parts.append(f'-- {table}: {len(records)} rows')
    for row in records:
        parts.append(f"INSERT INTO `{table}` ({','.join('`'+k+'`' for k in row)}) VALUES "
                     f"({','.join(literal(v) for v in row.values())});")
parts.extend(['COMMIT;', 'SET FOREIGN_KEY_CHECKS=1;'])
(OUT / '002-demo.sql').write_text('\n'.join(parts) + '\n', encoding='utf-8', newline='\n')
(OUT.parent / 'demo-accounts.json').write_text(json.dumps(account_list, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
(OUT.parent / 'public-assets.json').write_text(json.dumps(dict(
    version=1, origin=args.image_origin.rstrip('/'), assets=list(assets.values())),
    ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'schemaTables':len(tables), 'tenants':len(tenants), 'shops':len(shops),
                  'products':len(products), 'skus':len(skus), 'demoAccounts':len(account_list)}))
