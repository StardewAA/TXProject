# -*- coding: utf-8 -*-
"""
将 CRM 测试数据(16 个 CSV)导入固邦租户(2012)。
- 负责人统一挂 gubang(80446),保证默认视图可见
- 部门/用户按 CSV 建立并映射到固邦租户
- 字典按 CSV 写入 system_dict_data(dict_type 小写化)
- 客户/线索引用的 customer_id 缺失时按 CSV 信息自动补建
"""
import csv
import os
import re
import subprocess
import sys
from decimal import Decimal

import pymysql

CSV_DIR = os.environ.get(
    "CSV_DIR", r"C:\Users\zmj15\Desktop\实习\CRM测试数据(1)\测试数据"
)
TENANT = int(os.environ.get("TARGET_TENANT", "2012"))
OWNER = int(os.environ.get("TARGET_OWNER", "80446"))

pw = os.environ.get("MYSQL_PASSWORD", "")
if not pw:
    try:
        pw = subprocess.check_output(
            ["docker", "exec", "deploy-mysql-1", "printenv", "MYSQL_ROOT_PASSWORD"]
        ).decode().strip()
    except Exception:
        pw = "root"

conn = pymysql.connect(host="127.0.0.1", port=int(os.environ.get("MYSQL_PORT", "13306")),
                       user="root", password=pw,
                       database="ruoyi-vue-pro", charset="utf8mb4")
cur = conn.cursor()

BIZ = {"线索": 1, "客户": 2, "联系人": 3, "商机": 4, "合同": 5,
       "产品": 6, "回款": 7, "回款计划": 8}
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?$")
CN_NUM = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
          "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}


def read(fn):
    with open(os.path.join(CSV_DIR, fn), encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def s(row, k, default=None):
    v = (row.get(k) or "").strip()
    return v if v != "" else default


def dt(row, k, default=None):
    v = (row.get(k) or "").strip()
    return v if DATE_RE.match(v) else default


def i(row, k, default=None):
    v = (row.get(k) or "").strip()
    if v == "":
        return default
    try:
        return int(float(v))
    except ValueError:
        return default


def dec(row, k, default=None):
    v = (row.get(k) or "").strip()
    if v == "":
        return default
    try:
        return Decimal(v)
    except Exception:
        return default


def bit(row, k, default=0):
    v = (row.get(k) or "").strip().lower()
    return 1 if v in ("true", "1", "yes") else (0 if v in ("false", "0", "no") else default)


def upsert(table, data):
    cols, vals, ph = [], [], []
    for k, v in data.items():
        cols.append("`%s`" % k)
        if v == "__NOW__":
            ph.append("NOW()")
        else:
            ph.append("%s")
            vals.append(v)
    upd = ",".join(["`%s`=VALUES(`%s`)" % (c, c) for c in data
                    if c not in ("id", "create_time", "creator")])
    sql = "INSERT INTO `%s` (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s" % (
        table, ",".join(cols), ",".join(ph), upd)
    cur.execute(sql, vals)
    return cur.lastrowid


def audit():
    return {"creator": "1", "create_time": "__NOW__", "updater": "",
            "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}


# ============ 1. 字典(00) ============
DICT_TYPE_NAME = {}
for r in read("00_字典_数据字典.csv"):
    dtp = s(r, "dict_type", "").lower()
    if not dtp:
        continue
    DICT_TYPE_NAME[dtp] = s(r, "dict_type_name", dtp)

for dtp, name in DICT_TYPE_NAME.items():
    cur.execute("SELECT id FROM system_dict_type WHERE type=%s AND deleted=0", (dtp,))
    if not cur.fetchone():
        cur.execute(
            "INSERT INTO system_dict_type(name,type,status,remark,creator,create_time,"
            "updater,update_time,deleted) VALUES(%s,%s,0,%s,'admin',NOW(),'admin',NOW(),0)",
            (name, dtp, "由测试数据导入"))

# 字典数据：按 dict_type + value 判断存在则更新 label
for r in read("00_字典_数据字典.csv"):
    dtp = s(r, "dict_type", "").lower()
    val = s(r, "value", "")
    lab = s(r, "label", "")
    if not (dtp and val):
        continue
    sort = i(r, "sort", 0)
    status = i(r, "status", 0)
    remark = s(r, "remark", "")
    cur.execute("SELECT id FROM system_dict_data WHERE dict_type=%s AND value=%s AND deleted=0",
                (dtp, val))
    got = cur.fetchone()
    if got:
        cur.execute("UPDATE system_dict_data SET label=%s,sort=%s,status=%s,remark=%s,"
                    "update_time=NOW() WHERE id=%s", (lab, sort, status, remark, got[0]))
    else:
        cur.execute(
            "INSERT INTO system_dict_data(sort,label,value,dict_type,status,remark,"
            "creator,create_time,updater,update_time,deleted) "
            "VALUES(%s,%s,%s,%s,%s,%s,'admin',NOW(),'admin',NOW(),0)",
            (sort, lab, val, dtp, status, remark))
print("字典完成：%d 类" % len(DICT_TYPE_NAME))

# ============ 2. 部门(01) -> 固邦 ============
# 固邦已有部门按名称复用，否则新建
dept_map = {}
for r in read("01_组织_部门.csv"):
    name = s(r, "dept_name", "")
    cur.execute("SELECT id FROM system_dept WHERE tenant_id=%s AND name=%s AND deleted=0 LIMIT 1",
                (TENANT, name))
    got = cur.fetchone()
    if got:
        dept_map[i(r, "dept_id")] = got[0]
    else:
        parent = dept_map.get(i(r, "parent_id"), 0)
        nid = upsert("system_dept", {
            "name": name, "parent_id": parent, "sort": 0, "status": 0,
            "leader_user_id": None, "phone": "", "email": "",
            "creator": "1", "create_time": "__NOW__", "updater": "",
            "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT})
        dept_map[i(r, "dept_id")] = nid
print("部门映射:", dept_map)

# ============ 3. 用户(02) -> 固邦 ============
cur.execute("SELECT password FROM system_users WHERE id=1")
default_pwd = cur.fetchone()[0]
cur.execute("SELECT MAX(id) FROM system_users")
next_uid = (cur.fetchone()[0] or 0) + 1

user_map = {}
for r in read("02_组织_用户.csv"):
    csv_uid = i(r, "user_id")
    mobile = s(r, "mobile")
    cur.execute("SELECT id FROM system_users WHERE username=%s AND deleted=0", (mobile,))
    got = cur.fetchone()
    if got:
        user_map[csv_uid] = got[0]
        continue
    post_ids = "[]"
    post_name = s(r, "post")
    if post_name:
        cur.execute("SELECT id FROM system_post WHERE tenant_id=%s AND name=%s AND deleted=0 LIMIT 1",
                    (TENANT, post_name))
        pg = cur.fetchone()
        if pg:
            post_ids = "[%s]" % pg[0]
    upsert("system_users", {
        "id": next_uid, "username": mobile, "password": default_pwd,
        "nickname": s(r, "nickname", ""), "dept_id": dept_map.get(i(r, "dept_id")),
        "post_ids": post_ids, "mobile": mobile,
        "email": "user%s@txgy.local" % csv_uid, "sex": 0,
        "status": i(r, "status", 0), "remark": "测试数据导入",
        "creator": "1", "create_time": "__NOW__", "updater": "",
        "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT})
    user_map[csv_uid] = next_uid
    next_uid += 1
print("用户映射:", user_map)

# 负责人统一挂 gubang
def owner(_row):
    return OWNER


# ============ 4. 产品分类(03) / 产品(04) ============
for r in read("03_产品_产品分类.csv"):
    d = {"id": i(r, "category_id"), "name": s(r, "category_name", ""),
         "parent_id": i(r, "parent_id", 0)}
    d.update(audit())
    upsert("crm_product_category", d)

for r in read("04_产品_产品列表.csv"):
    d = {"id": i(r, "product_id"), "name": s(r, "name", ""), "no": s(r, "no", ""),
         "unit": i(r, "unit_value"), "price": dec(r, "price", Decimal("0")),
         "status": i(r, "status", 0), "category_id": i(r, "category_id"),
         "description": s(r, "description", ""), "owner_user_id": owner(r)}
    d.update(audit())
    upsert("crm_product", d)

# ============ 5. 客户(06) 先导，供线索/商机引用 ============
customer_rows = read("06_客户_客户档案.csv")
existing_cust = set()
for r in customer_rows:
    cid = i(r, "customer_id")
    addr = " ".join(x for x in [s(r, "region"), s(r, "detail_address")] if x)
    d = {"id": cid, "name": s(r, "customer_name", ""),
         "follow_up_status": 0, "mobile": s(r, "mobile"), "telephone": s(r, "phone"),
         "email": s(r, "email"), "level": i(r, "level"), "industry_id": i(r, "industry"),
         "source": i(r, "source"), "owner_user_id": owner(r),
         "owner_time": "__NOW__", "lock_status": bit(r, "lock_status"),
         "deal_status": bit(r, "deal_status"), "detail_address": addr or None,
         "contact_last_time": dt(r, "last_follow_time"),
         "contact_last_content": s(r, "last_follow_content"),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_customer", d)
    existing_cust.add(cid)

# ============ 6. 线索(05)：缺失客户自动补建 ============
for r in read("05_线索_线索档案.csv"):
    cid = i(r, "customer_id")
    if cid and cid not in existing_cust:
        # 从线索信息补建客户
        addr = " ".join(x for x in [s(r, "region"), s(r, "detail_address")] if x)
        d = {"id": cid, "name": s(r, "lead_name", "客户%s" % cid),
             "follow_up_status": 0, "mobile": s(r, "mobile"),
             "telephone": s(r, "phone"), "email": s(r, "email"),
             "level": i(r, "level"), "industry_id": i(r, "industry"),
             "source": i(r, "source"), "owner_user_id": owner(r),
             "owner_time": "__NOW__", "lock_status": 0, "deal_status": 0,
             "detail_address": addr or None, "remark": "由线索自动补建"}
        d.update(audit())
        upsert("crm_customer", d)
        existing_cust.add(cid)
        print("  自动补建客户 id=%s (%s)" % (cid, d["name"]))

    addr = " ".join(x for x in [s(r, "region"), s(r, "detail_address")] if x)
    d = {"id": i(r, "lead_id"), "name": s(r, "lead_name", ""),
         "follow_up_status": i(r, "follow_status", 0),
         "transform_status": i(r, "convert_status", 0), "customer_id": cid,
         "mobile": s(r, "mobile"), "telephone": s(r, "phone"), "qq": s(r, "qq"),
         "wechat": s(r, "wechat"), "email": s(r, "email"),
         "detail_address": addr or None, "industry_id": i(r, "industry"),
         "level": i(r, "level"), "source": i(r, "source"), "owner_user_id": owner(r),
         "contact_last_time": dt(r, "last_follow_time"),
         "contact_last_content": s(r, "last_follow_content"),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_clue", d)

# ============ 7. 联系人(07) ============
for r in read("07_联系人_联系人档案.csv"):
    cid = i(r, "customer_id")
    if cid and cid not in existing_cust:
        d = {"id": cid, "name": s(r, "customer_name", "客户%s" % cid),
             "follow_up_status": 0, "owner_user_id": owner(r),
             "owner_time": "__NOW__", "lock_status": 0, "deal_status": 0,
             "remark": "由联系人自动补建"}
        d.update(audit())
        upsert("crm_customer", d)
        existing_cust.add(cid)
    d = {"id": i(r, "contact_id"), "name": s(r, "name", ""),
         "customer_id": cid, "mobile": s(r, "mobile"),
         "telephone": s(r, "phone"), "email": s(r, "email"), "qq": s(r, "qq"),
         "wechat": s(r, "wechat"), "post": s(r, "post"), "master": bit(r, "is_main"),
         "owner_user_id": owner(r), "sex": None, "parent_id": None,
         "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_contact", d)

# ============ 8. 商机状态组/阶段 + 商机(08) ============
type_cache, status_cache = {}, {}
for r in read("08_商机_商机档案.csv"):
    gname = s(r, "status_group", "标准流程")
    if gname not in type_cache:
        cur.execute("SELECT id FROM crm_business_status_type WHERE tenant_id=%s AND name=%s LIMIT 1",
                    (TENANT, gname))
        got = cur.fetchone()
        if got:
            type_cache[gname] = got[0]
        else:
            nid = upsert("crm_business_status_type", {
                "name": gname, "dept_ids": "", "creator": "1", "create_time": "__NOW__",
                "updater": "", "update_time": "__NOW__", "tenant_id": TENANT, "deleted": 0})
            type_cache[gname] = nid
    stid = type_cache[gname]
    sname = s(r, "stage", "")
    if sname and (stid, sname) not in status_cache:
        cur.execute("SELECT id FROM crm_business_status WHERE type_id=%s AND name=%s LIMIT 1",
                    (stid, sname))
        got = cur.fetchone()
        if got:
            status_cache[(stid, sname)] = got[0]
        else:
            nid = upsert("crm_business_status", {
                "type_id": stid, "name": sname,
                "percent": dec(r, "win_rate", Decimal("0")), "sort": len(status_cache),
                "creator": "1", "create_time": "__NOW__", "updater": "",
                "update_time": "__NOW__", "tenant_id": TENANT, "deleted": 0})
            status_cache[(stid, sname)] = nid

for r in read("08_商机_商机档案.csv"):
    cid = i(r, "customer_id")
    if cid and cid not in existing_cust:
        d = {"id": cid, "name": s(r, "customer_name", "客户%s" % cid),
             "follow_up_status": 0, "owner_user_id": owner(r),
             "owner_time": "__NOW__", "lock_status": 0, "deal_status": 0,
             "remark": "由商机自动补建"}
        d.update(audit())
        upsert("crm_customer", d)
        existing_cust.add(cid)
    stid = type_cache[s(r, "status_group", "标准流程")]
    d = {"id": i(r, "business_id"), "name": s(r, "business_name", ""),
         "customer_id": cid, "follow_up_status": 0, "owner_user_id": owner(r),
         "status_type_id": stid, "status_id": status_cache.get((stid, s(r, "stage", ""))),
         "end_status": i(r, "end_status"), "deal_time": None,
         "total_product_price": dec(r, "total_product_price", Decimal("0")),
         "discount_percent": dec(r, "discount_percent", Decimal("0")),
         "total_price": dec(r, "total_price", Decimal("0")),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_business", d)

# 商机产品明细(09)
for r in read("09_商机_商机产品明细.csv"):
    d = {"business_id": i(r, "business_id"), "product_id": i(r, "product_id"),
         "product_price": dec(r, "standard_price", Decimal("0")),
         "business_price": dec(r, "business_price", Decimal("0")),
         "count": dec(r, "quantity", Decimal("0")),
         "total_price": dec(r, "subtotal", Decimal("0"))}
    d.update(audit())
    upsert("crm_business_product", d)

# ============ 9. 合同(10) + 明细(11) ============
for r in read("10_合同_合同档案.csv"):
    cid = i(r, "customer_id")
    if cid and cid not in existing_cust:
        d = {"id": cid, "name": s(r, "customer_name", "客户%s" % cid),
             "follow_up_status": 0, "owner_user_id": owner(r),
             "owner_time": "__NOW__", "lock_status": 0, "deal_status": 0,
             "remark": "由合同自动补建"}
        d.update(audit())
        upsert("crm_customer", d)
        existing_cust.add(cid)
    d = {"id": i(r, "contract_id"), "name": s(r, "contract_name", ""),
         "no": s(r, "contract_no", ""), "customer_id": cid,
         "business_id": i(r, "business_id"), "owner_user_id": owner(r),
         "sign_user_id": owner(r), "sign_contact_id": i(r, "sign_contact_id"),
         "process_instance_id": s(r, "process_instance_id"),
         "audit_status": i(r, "audit_status", 0),
         "order_date": dt(r, "order_date"), "start_time": dt(r, "start_time"),
         "end_time": dt(r, "end_time"),
         "total_product_price": dec(r, "total_product_price", Decimal("0")),
         "discount_percent": dec(r, "discount_percent", Decimal("0")),
         "total_price": dec(r, "total_price", Decimal("0")), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_contract", d)

for r in read("11_合同_合同产品明细.csv"):
    d = {"contract_id": i(r, "contract_id"), "product_id": i(r, "product_id"),
         "product_price": dec(r, "standard_price", Decimal("0")),
         "contract_price": dec(r, "contract_price", Decimal("0")),
         "count": dec(r, "quantity", Decimal("0")),
         "total_price": dec(r, "subtotal", Decimal("0"))}
    d.update(audit())
    upsert("crm_contract_product", d)

# ============ 10. 回款计划(12) / 回款记录(13) ============
for r in read("12_回款_回款计划.csv"):
    v = s(r, "period", "")
    m = re.search(r"第\s*([0-9一二三四五六七八九十]+)\s*期", v or "")
    if m:
        t = m.group(1)
        pno = int(t) if t.isdigit() else CN_NUM.get(t, 1)
    else:
        m2 = re.search(r"\d+", v or "")
        pno = int(m2.group()) if m2 else 1
    d = {"id": i(r, "plan_id"), "period": pno,
         "customer_id": i(r, "customer_id"), "contract_id": i(r, "contract_id"),
         "owner_user_id": owner(r), "receivable_id": i(r, "receivable_id"),
         "price": dec(r, "plan_amount", Decimal("0")), "return_time": dt(r, "plan_date"),
         "return_type": i(r, "return_type"), "remind_days": i(r, "remind_days"),
         "remind_time": dt(r, "remind_time"), "remark": v or ""}
    d.update(audit())
    upsert("crm_receivable_plan", d)

for r in read("13_回款_回款记录.csv"):
    rid = i(r, "receivable_id")
    d = {"id": rid, "no": "TXGY-SK-%s" % rid, "plan_id": i(r, "plan_id"),
         "customer_id": i(r, "customer_id"), "contract_id": i(r, "contract_id"),
         "owner_user_id": owner(r), "audit_status": i(r, "audit_status", 0),
         "process_instance_id": s(r, "process_instance_id"),
         "return_time": dt(r, "return_date"), "return_type": i(r, "return_type"),
         "price": dec(r, "price", Decimal("0")), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_receivable", d)

# ============ 11. 跟进记录(14) ============
for r in read("14_跟进_跟进记录.csv"):
    ow = str(owner(r))
    d = {"id": i(r, "follow_id"), "biz_type": BIZ.get(s(r, "biz_type"), 2),
         "biz_id": i(r, "biz_id"), "type": i(r, "follow_type"),
         "content": s(r, "content", ""), "next_time": dt(r, "next_contact_time"),
         "pic_urls": "", "file_urls": "", "business_ids": "", "contact_ids": "",
         "creator": ow, "create_time": (dt(r, "create_time") or "__NOW__"),
         "updater": ow, "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}
    upsert("crm_follow_up_record", d)

# ============ 12. 团队成员(15) ============
rows15 = read("15_团队_团队成员.csv")
pairs = {(BIZ.get(s(r, "biz_type"), 2), i(r, "biz_id")) for r in rows15}
if pairs:
    conds, args = [], []
    for bt, bid in pairs:
        conds.append("(biz_type=%s AND biz_id=%s)")
        args += [bt, bid]
    cur.execute("DELETE FROM crm_permission WHERE tenant_id=%s AND (" % TENANT
                + " OR ".join(conds) + ")", args)
for r in rows15:
    lvl = s(r, "permission_level", "1")
    lvl = int(lvl.split("-")[0]) if lvl and lvl[0].isdigit() else 1
    d = {"biz_type": BIZ.get(s(r, "biz_type"), 2), "biz_id": i(r, "biz_id"),
         "user_id": OWNER, "level": lvl,
         "creator": "1", "create_time": "__NOW__", "updater": "",
         "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}
    upsert("crm_permission", d)

conn.commit()

print("\n=== 固邦租户(%s) 导入后统计 ===" % TENANT)
for t in ["crm_customer", "crm_clue", "crm_contact", "crm_business", "crm_business_product",
          "crm_contract", "crm_contract_product", "crm_receivable", "crm_receivable_plan",
          "crm_follow_up_record", "crm_permission", "crm_product", "crm_product_category"]:
    cur.execute("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%s AND deleted=0" % (t, TENANT))
    print("  %-26s %s" % (t, cur.fetchone()[0]))

conn.close()
print("\n导入完成")
