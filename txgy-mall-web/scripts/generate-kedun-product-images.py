from __future__ import annotations

from html import escape
from pathlib import Path
import base64
import json


PRODUCTS = [
    (940201, "环氧富锌底漆", "20kg/组", "coating", "#9b341b"),
    (940202, "脂肪族聚氨酯面漆", "20kg/组", "coating", "#146c94"),
    (940203, "环氧云铁中间漆", "20kg/组", "coating", "#4b5563"),
    (940204, "薄型钢结构防火涂料", "25kg/桶", "coating", "#b45309"),
    (940205, "憎水岩棉板", "100mm", "board", "#a16207"),
    (940206, "橡塑保温管壳", "B1级", "shell", "#374151"),
    (940207, "预制直埋保温管", "DN100", "pipe", "#c2410c"),
    (940208, "3PE防腐钢管", "DN150", "pipe", "#111827"),
    (940209, "硅酸铝纤维毯", "50mm", "blanket", "#9ca3af"),
    (940210, "耐火纤维模块", "300mm", "module", "#78716c"),
    (940211, "环氧磷酸锌底漆", "20kg/组", "coating", "#a33a2b"),
    (940212, "无机富锌底漆", "25kg/桶", "coating", "#475569"),
    (940213, "环氧玻璃鳞片涂料", "20kg/组", "coating", "#0f766e"),
    (940214, "环氧煤沥青防腐漆", "20kg/组", "coating", "#171717"),
    (940216, "丙烯酸聚氨酯面漆", "20kg/组", "coating", "#1d4ed8"),
    (940217, "水性工业防腐底漆", "20kg/桶", "coating", "#0369a1"),
    (940218, "厚型钢结构防火涂料", "25kg/袋", "bag", "#b45309"),
    (940219, "超薄型钢结构防火涂料", "20kg/桶", "coating", "#ea580c"),
    (940220, "有机硅耐高温漆", "20kg/组", "coating", "#be123c"),
    (940221, "岩棉保温管壳", "DN100", "shell", "#ca8a04"),
    (940222, "离心玻璃棉卷毡", "50mm", "blanket", "#eab308"),
    (940223, "硅酸铝针刺毯", "50mm", "blanket", "#a8a29e"),
    (940224, "B1级橡塑保温板", "30mm", "board", "#1f2937"),
    (940225, "硬泡聚氨酯保温板", "50mm", "board", "#d97706"),
    (940226, "PIR低温保冷板", "50mm", "board", "#2563eb"),
    (940227, "保温外护铝皮", "0.5mm", "coil", "#94a3b8"),
    (940228, "保温外护镀锌铁皮", "0.5mm", "coil", "#64748b"),
    (940229, "防潮密封玛蹄脂", "20kg/桶", "mastic", "#7c3aed"),
    (940230, "无碱玻璃纤维布", "200g/m²", "mesh", "#0891b2"),
    (940231, "聚氨酯直埋保温管", "DN200", "pipe", "#c2410c"),
    (940232, "钢套钢蒸汽保温管", "DN150", "pipe", "#475569"),
    (940233, "3PE防腐弯头", "DN100", "elbow", "#111827"),
    (940234, "低温保冷管托", "DN150", "support", "#2563eb"),
    (940235, "高温纤维粘结剂", "20kg/桶", "mastic", "#a16207"),
]

CATALOG = {
    940201: (940002, 940101, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940202: (940003, 940101, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940203: (940002, 940102, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940204: (940007, 940102, (944001, "包装规格", 944103, "25kg/桶"), (944007, "计量单位", 944702, "桶")),
    940205: (940008, 940106, (944002, "厚度", 944203, "100mm"), (944007, "计量单位", 944703, "m²")),
    940206: (940009, 940107, (944005, "阻燃等级", 944501, "B1级"), (944007, "计量单位", 944704, "m")),
    940207: (940010, 940104, (944003, "公称直径", 944301, "DN100"), (944007, "计量单位", 944704, "m")),
    940208: (940010, 940104, (944003, "公称直径", 944302, "DN150"), (944007, "计量单位", 944704, "m")),
    940209: (940006, 940105, (944002, "厚度", 944201, "50mm"), (944007, "计量单位", 944705, "卷")),
    940210: (940006, 940105, (944006, "模块尺寸", 944601, "300mm"), (944007, "计量单位", 944706, "块")),
    940211: (940002, 940101, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940212: (940002, 940101, (944001, "包装规格", 944103, "25kg/桶"), (944007, "计量单位", 944702, "桶")),
    940213: (940003, 940101, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940214: (940003, 940101, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940215: (940003, 940101, (944001, "包装规格", 944104, "20kg/桶"), (944007, "计量单位", 944702, "桶")),
    940216: (940003, 940102, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940217: (940002, 940102, (944001, "包装规格", 944104, "20kg/桶"), (944007, "计量单位", 944702, "桶")),
    940218: (940007, 940102, (944001, "包装规格", 944105, "25kg/袋"), (944007, "计量单位", 944707, "袋")),
    940219: (940007, 940102, (944001, "包装规格", 944104, "20kg/桶"), (944007, "计量单位", 944702, "桶")),
    940220: (940003, 940102, (944001, "包装规格", 944102, "20kg/组"), (944007, "计量单位", 944701, "组")),
    940221: (940008, 940106, (944003, "公称直径", 944301, "DN100"), (944007, "计量单位", 944704, "m")),
    940222: (940008, 940106, (944002, "厚度", 944201, "50mm"), (944007, "计量单位", 944705, "卷")),
    940223: (940006, 940105, (944002, "厚度", 944201, "50mm"), (944007, "计量单位", 944705, "卷")),
    940224: (940009, 940107, (944002, "厚度", 944204, "30mm"), (944007, "计量单位", 944703, "m²")),
    940225: (940009, 940103, (944002, "厚度", 944201, "50mm"), (944007, "计量单位", 944703, "m²")),
    940226: (940009, 940103, (944002, "厚度", 944201, "50mm"), (944007, "计量单位", 944703, "m²")),
    940227: (940012, 940104, (944002, "厚度", 944205, "0.5mm"), (944007, "计量单位", 944703, "m²")),
    940228: (940012, 940104, (944002, "厚度", 944205, "0.5mm"), (944007, "计量单位", 944703, "m²")),
    940229: (940012, 940107, (944001, "包装规格", 944104, "20kg/桶"), (944007, "计量单位", 944702, "桶")),
    940230: (940012, 940108, (944004, "克重", 944401, "200g/m²"), (944007, "计量单位", 944703, "m²")),
    940231: (940010, 940104, (944003, "公称直径", 944303, "DN200"), (944007, "计量单位", 944704, "m")),
    940232: (940010, 940104, (944003, "公称直径", 944302, "DN150"), (944007, "计量单位", 944704, "m")),
    940233: (940011, 940104, (944003, "公称直径", 944301, "DN100"), (944007, "计量单位", 944708, "个")),
    940234: (940011, 940104, (944003, "公称直径", 944302, "DN150"), (944007, "计量单位", 944709, "套")),
    940235: (940006, 940105, (944001, "包装规格", 944104, "20kg/桶"), (944007, "计量单位", 944702, "桶")),
}


def illustration(kind: str, accent: str) -> str:
    common = f'stroke="{accent}" stroke-width="18" stroke-linecap="round" stroke-linejoin="round"'
    if kind == "coating":
        return f'''<ellipse cx="500" cy="330" rx="205" ry="54" fill="#e8edf0" {common}/><path d="M295 330v300c0 72 410 72 410 0V330" fill="#f8fafc" {common}/><path d="M340 440h320v125H340z" fill="{accent}" opacity=".9"/><path d="M390 315c0-115 220-115 220 0" fill="none" {common}/>'''
    if kind == "bag":
        return f'''<path d="M330 245h340l45 430H285z" fill="#f8fafc" {common}/><path d="M365 245l28-70h214l28 70" fill="none" {common}/><path d="M315 435h370v150H315z" fill="{accent}" opacity=".9"/>'''
    if kind == "board":
        return ''.join(f'<path d="M260 {300+i*105}l400-65 105 55-405 70z" fill="{accent}" opacity="{0.35+i*0.18}" stroke="#26343a" stroke-width="12"/>' for i in range(4))
    if kind == "pipe":
        return f'''<path d="M220 590L655 250l145 150-440 345z" fill="{accent}" opacity=".9" stroke="#172126" stroke-width="18"/><ellipse cx="725" cy="325" rx="110" ry="88" transform="rotate(43 725 325)" fill="#111827" stroke="#d8e0e4" stroke-width="28"/><ellipse cx="725" cy="325" rx="56" ry="42" transform="rotate(43 725 325)" fill="#edf2f4"/>'''
    if kind == "shell":
        return f'''<path d="M215 610L650 265l150 150-440 345z" fill="{accent}" opacity=".72" stroke="#172126" stroke-width="18"/><ellipse cx="728" cy="338" rx="112" ry="88" transform="rotate(43 728 338)" fill="#d7c083" stroke="#7c6429" stroke-width="20"/><ellipse cx="728" cy="338" rx="48" ry="36" transform="rotate(43 728 338)" fill="#f8fafc"/><path d="M650 265l150 150" stroke="#f8fafc" stroke-width="12"/>'''
    if kind == "blanket":
        return f'''<path d="M265 290h370v380H265z" fill="#f4e7b2" stroke="#7c6f4b" stroke-width="16"/><path d="M635 290c150 0 150 380 0 380-112 0-112-380 0-380z" fill="{accent}" opacity=".65" stroke="#615d55" stroke-width="16"/><ellipse cx="635" cy="480" rx="55" ry="150" fill="#faf7ed" stroke="#615d55" stroke-width="14"/><path d="M310 350l270 260M310 470l150 140M430 320l150 145" stroke="#fff" stroke-width="10" opacity=".75"/>'''
    if kind == "module":
        return ''.join(f'<path d="M{250+(i%2)*235} {315+(i//2)*190}h205v160H{250+(i%2)*235}z" fill="{accent}" opacity="{0.4+i*0.12}" stroke="#57534e" stroke-width="14"/><path d="M{270+(i%2)*235} {345+(i//2)*190}h165" stroke="#fff" stroke-width="12" opacity=".7"/>' for i in range(4))
    if kind == "coil":
        return f'''<path d="M270 300h360v360H270z" fill="url(#metal)" stroke="#475569" stroke-width="18"/><ellipse cx="640" cy="480" rx="145" ry="180" fill="{accent}" stroke="#334155" stroke-width="18"/><ellipse cx="640" cy="480" rx="62" ry="82" fill="#f8fafc" stroke="#64748b" stroke-width="16"/><path d="M270 300h370" stroke="#fff" stroke-width="15" opacity=".7"/>'''
    if kind == "mesh":
        return f'''<path d="M240 320h440v350H240z" fill="url(#grid)" stroke="{accent}" stroke-width="18"/><ellipse cx="680" cy="495" rx="105" ry="175" fill="#ecfeff" stroke="{accent}" stroke-width="18"/><ellipse cx="680" cy="495" rx="38" ry="92" fill="#fff" stroke="{accent}" stroke-width="12"/>'''
    if kind == "elbow":
        return f'''<path d="M280 685V475c0-145 115-260 260-260h195v170H550c-55 0-100 45-100 100v200z" fill="{accent}" stroke="#0f172a" stroke-width="22"/><ellipse cx="365" cy="685" rx="86" ry="35" fill="#1f2937"/><ellipse cx="735" cy="300" rx="35" ry="85" fill="#1f2937"/>'''
    if kind == "support":
        return f'''<path d="M265 405c45-220 425-220 470 0l-115 35c-35-105-205-105-240 0z" fill="{accent}" stroke="#1e3a5f" stroke-width="18"/><path d="M320 435v230M680 435v230M270 665h460" fill="none" {common}/><path d="M400 410h200v115H400z" fill="#dbeafe" stroke="#1e3a5f" stroke-width="15"/>'''
    return f'''<ellipse cx="500" cy="330" rx="205" ry="54" fill="#e8edf0" {common}/><path d="M295 330v300c0 72 410 72 410 0V330" fill="#f8fafc" {common}/><path d="M340 440h320v125H340z" fill="{accent}" opacity=".9"/>'''


def make_svg(product_id: int, name: str, spec: str, kind: str, accent: str) -> str:
    title = escape(name)
    subtitle = escape(spec)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1000" viewBox="0 0 1000 1000">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f7faf9"/><stop offset="1" stop-color="#dfe8e5"/></linearGradient>
  <linearGradient id="metal" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#94a3b8"/><stop offset=".45" stop-color="#f8fafc"/><stop offset="1" stop-color="#64748b"/></linearGradient>
  <pattern id="grid" width="34" height="34" patternUnits="userSpaceOnUse"><path d="M0 0h34v34H0z" fill="#f8fafc"/><path d="M0 0l34 34M34 0L0 34" stroke="{accent}" stroke-width="3"/></pattern>
  <filter id="shadow"><feDropShadow dx="0" dy="18" stdDeviation="18" flood-opacity=".18"/></filter>
</defs>
<rect width="1000" height="1000" rx="54" fill="url(#bg)"/>
<path d="M0 0h1000v24H0z" fill="{accent}"/>
<g filter="url(#shadow)">{illustration(kind, accent)}</g>
<rect x="70" y="770" width="860" height="165" rx="24" fill="#fff" opacity=".96"/>
<text x="105" y="838" fill="#172126" font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" font-size="44" font-weight="700">{title}</text>
<text x="105" y="895" fill="{accent}" font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" font-size="30" font-weight="600">规格：{subtitle}</text>
<text x="895" y="895" text-anchor="end" fill="#64748b" font-family="Arial, sans-serif" font-size="22">SKU {product_id}</text>
</svg>'''


def main() -> None:
    output = Path(__file__).resolve().parents[1] / "public" / "product-images" / "kedun"
    output.mkdir(parents=True, exist_ok=True)
    for item in PRODUCTS:
        product_id, name, *_ = item
        (output / f"{product_id}.svg").write_text(make_svg(*item), encoding="utf-8")
    write_sql(output)
    print(f"generated {len(PRODUCTS)} images in {output}")


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def write_sql(output: Path) -> None:
    repo = Path(__file__).resolve().parents[2]
    migration = repo / "deploy" / "mysql" / "repair" / "2026-09-17-kedun-product-catalog-corrections.sql"
    names = {product_id: name for product_id, name, *_ in PRODUCTS}
    image_rows = []
    file_rows = []
    content_rows = []
    for product_id, name, *_ in PRODUCTS:
        path = f"mall/catalog/20260917/kedun/{product_id}.svg"
        url = f"http://127.0.0.1:48080/admin-api/infra/file/4/get/{path}"
        data = (output / f"{product_id}.svg").read_bytes()
        encoded = base64.b64encode(data).decode("ascii")
        image_rows.append((product_id, url))
        content_rows.append(f"(4,{sql_string(path)},FROM_BASE64('{encoded}'),'mall-kedun-catalog','mall-kedun-catalog',b'0')")
        file_rows.append(f"(4,{sql_string(product_id.__str__()+'.svg')},{sql_string(path)},{sql_string(url)},'image/svg+xml',{len(data)},'mall-kedun-catalog','mall-kedun-catalog',b'0')")

    # Preserve the generated photorealistic image already used by 940215.
    image_rows.append((940215, "http://127.0.0.1:48080/admin-api/infra/file/4/get/mall/catalog/20260917/940215-chlorinated-rubber-topcoat.png"))
    image_rows.sort()
    paths = [f"mall/catalog/20260917/kedun/{product_id}.svg" for product_id, *_ in PRODUCTS]
    path_list = ",".join(sql_string(path) for path in paths)
    catalog_rows = []
    for product_id, url in image_rows:
        category_id, brand_id, spec, unit = CATALOG[product_id]
        props = [
            {"propertyId": spec[0], "propertyName": spec[1], "valueId": spec[2], "valueName": spec[3]},
            {"propertyId": unit[0], "propertyName": unit[1], "valueId": unit[2], "valueName": unit[3]},
        ]
        catalog_rows.append(f"({product_id},{sql_string(url)},{category_id},{brand_id},{sql_string(json.dumps(props, ensure_ascii=False, separators=(',', ':')))})")

    sql = f"""-- 科盾商城商品图片、品牌、分类及规格属性校正（可重复执行）
START TRANSACTION;

DELETE FROM infra_file_content WHERE config_id=4 AND path IN ({path_list});
DELETE FROM infra_file WHERE config_id=4 AND path IN ({path_list});
INSERT INTO infra_file_content(config_id,path,content,creator,updater,deleted) VALUES
{',\n'.join(content_rows)};
INSERT INTO infra_file(config_id,name,path,url,type,size,creator,updater,deleted) VALUES
{',\n'.join(file_rows)};

INSERT INTO product_brand(id,name,pic_url,sort,description,status,creator,updater,deleted,tenant_id) VALUES
(940101,'科盾防护','',10,'工业防腐涂料与耐高温涂层品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940102,'固邦防腐','',20,'防腐、防火及工业保护涂层品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940103,'华瀚节能','',30,'聚氨酯与PIR保温保冷材料品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940104,'锐捷管道','',40,'预制保温管、防腐管及管件品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940105,'卓远耐火','',50,'硅酸铝及高温耐火保温材料品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940106,'西北恒泰','',60,'岩棉、玻璃棉工业保温材料品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940107,'隆达橡塑','',70,'橡塑保温及防潮密封材料品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940108,'汇泰玻纤','',80,'玻璃纤维布与防腐增强材料品牌',0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009)
ON DUPLICATE KEY UPDATE name=VALUES(name),sort=VALUES(sort),description=VALUES(description),status=0,updater=VALUES(updater),deleted=b'0';

INSERT INTO product_category(id,parent_id,name,pic_url,sort,status,creator,updater,deleted,tenant_id) VALUES
(940007,940001,'防火涂料','',30,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940008,940004,'岩棉与玻璃棉','',10,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940009,940004,'橡塑与硬泡保温','',20,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940010,940005,'保温管与防腐管','',10,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940011,940005,'管件与管托','',20,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009),
(940012,0,'施工辅材','',60,0,'mall-kedun-catalog','mall-kedun-catalog',b'0',2009)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),sort=VALUES(sort),status=0,updater=VALUES(updater),deleted=b'0';

UPDATE product_category SET name='防腐面漆',updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=940003;

INSERT INTO product_property(id,name,status,remark,creator,updater,tenant_id,deleted) VALUES
(944004,'克重',0,'玻纤及卷材规格','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944005,'阻燃等级',0,'保温材料阻燃等级','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944006,'模块尺寸',0,'耐火模块规格','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944007,'计量单位',0,'商品销售计量单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),status=0,remark=VALUES(remark),updater=VALUES(updater),deleted=b'0';

INSERT INTO product_property_value(id,property_id,name,remark,creator,updater,tenant_id,deleted) VALUES
(944103,944001,'25kg/桶','涂料包装','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944104,944001,'20kg/桶','涂料包装','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944105,944001,'25kg/袋','粉料包装','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944204,944002,'30mm','板材厚度','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944205,944002,'0.5mm','金属外护厚度','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944401,944004,'200g/m²','玻纤布克重','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944501,944005,'B1级','阻燃等级','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944601,944006,'300mm','模块尺寸','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944701,944007,'组','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944702,944007,'桶','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944703,944007,'m²','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944704,944007,'m','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944705,944007,'卷','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944706,944007,'块','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944707,944007,'袋','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944708,944007,'个','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0'),
(944709,944007,'套','销售单位','mall-kedun-catalog','mall-kedun-catalog',2009,b'0')
ON DUPLICATE KEY UPDATE property_id=VALUES(property_id),name=VALUES(name),remark=VALUES(remark),updater=VALUES(updater),deleted=b'0';

DROP TEMPORARY TABLE IF EXISTS tmp_kedun_catalog_fix;
CREATE TEMPORARY TABLE tmp_kedun_catalog_fix(spu_id BIGINT PRIMARY KEY,pic_url VARCHAR(1024),category_id BIGINT,brand_id BIGINT,properties_json JSON);
INSERT INTO tmp_kedun_catalog_fix VALUES
{',\n'.join(catalog_rows)};

UPDATE product_spu spu JOIN tmp_kedun_catalog_fix fix ON fix.spu_id=spu.id
SET spu.pic_url=fix.pic_url,spu.slider_pic_urls=JSON_ARRAY(fix.pic_url),spu.category_id=fix.category_id,spu.brand_id=fix.brand_id,
    spu.keyword=CONCAT(spu.name,' 防腐保温 工业采购'),
    spu.introduction=CONCAT(spu.name,'，规格与计量单位已按产品档案校正。'),
    spu.description=CONCAT('<section style="line-height:1.8;color:#24322f"><h2>',spu.name,'</h2><p>',spu.introduction,'。下单前请核对规格、数量、施工条件和配套体系。</p><img src="',fix.pic_url,'" alt="',spu.name,'" style="display:block;width:100%;height:auto;margin:16px 0"/><h3>采购与验收提示</h3><p>到货后请核对包装标识、规格、批次、外观及随货资料。</p></section>'),
    spu.updater='mall-kedun-catalog',spu.update_time=NOW()
WHERE spu.tenant_id=2009 AND spu.deleted=b'0';

UPDATE product_sku sku JOIN tmp_kedun_catalog_fix fix ON fix.spu_id=sku.spu_id
SET sku.pic_url=fix.pic_url,sku.updater='mall-kedun-catalog',sku.update_time=NOW()
WHERE sku.tenant_id=2009 AND sku.deleted=b'0';
UPDATE product_sku sku JOIN tmp_kedun_catalog_fix fix ON fix.spu_id=sku.spu_id
SET sku.properties=fix.properties_json,sku.updater='mall-kedun-catalog',sku.update_time=NOW()
WHERE sku.tenant_id=2009 AND sku.deleted=b'0' AND sku.id BETWEEN 940301 AND 940335;

UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944001,'propertyName','包装规格','valueId',944101,'valueName','10kg/组'),JSON_OBJECT('propertyId',944007,'propertyName','计量单位','valueId',944701,'valueName','组')),updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=945001;
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944002,'propertyName','厚度','valueId',944201,'valueName','50mm'),JSON_OBJECT('propertyId',944007,'propertyName','计量单位','valueId',944703,'valueName','m²')),updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=945002;
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944002,'propertyName','厚度','valueId',944202,'valueName','75mm'),JSON_OBJECT('propertyId',944007,'propertyName','计量单位','valueId',944703,'valueName','m²')),updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=945003;
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944003,'propertyName','公称直径','valueId',944301,'valueName','DN100'),JSON_OBJECT('propertyId',944007,'propertyName','计量单位','valueId',944704,'valueName','m')),updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=945004;
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944003,'propertyName','公称直径','valueId',944302,'valueName','DN150'),JSON_OBJECT('propertyId',944007,'propertyName','计量单位','valueId',944704,'valueName','m')),updater='mall-kedun-catalog' WHERE tenant_id=2009 AND id=945005;

UPDATE product_brand brand JOIN product_spu spu ON spu.tenant_id=2009 AND spu.brand_id=brand.id AND spu.deleted=b'0'
SET brand.pic_url=spu.pic_url,brand.updater='mall-kedun-catalog' WHERE brand.tenant_id=2009 AND brand.id BETWEEN 940101 AND 940108;
UPDATE product_category category JOIN product_spu spu ON spu.tenant_id=2009 AND spu.category_id=category.id AND spu.deleted=b'0'
SET category.pic_url=spu.pic_url,category.updater='mall-kedun-catalog' WHERE category.tenant_id=2009 AND category.id BETWEEN 940001 AND 940012;
UPDATE trade_order_item item JOIN product_spu spu ON spu.tenant_id=item.tenant_id AND spu.id=item.spu_id AND spu.deleted=b'0'
SET item.pic_url=spu.pic_url,item.updater='mall-kedun-catalog' WHERE item.tenant_id=2009;
UPDATE product_comment comment JOIN product_spu spu ON spu.tenant_id=comment.tenant_id AND spu.id=comment.spu_id AND spu.deleted=b'0'
SET comment.sku_pic_url=spu.pic_url,comment.pic_urls=JSON_ARRAY(spu.pic_url),comment.updater='mall-kedun-catalog' WHERE comment.tenant_id=2009;

COMMIT;
"""
    migration.write_text(sql, encoding="utf-8")


if __name__ == "__main__":
    main()
