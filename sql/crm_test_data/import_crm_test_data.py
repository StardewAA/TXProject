# -*- coding: utf-8 -*-
"""将测试数据从租户1迁移到固邦租户(2012)：清理租户1导入数据，在2012下重建。"""
import csv, os, re, subprocess, pymysql
from decimal import Decimal

BASE = os.path.dirname(os.path.abspath(__file__))
CSV_DIR = BASE  # CSV 与脚本同目录
TENANT = int(os.environ.get("TARGET_TENANT", "2012"))  # 目标租户，默认固邦(2012)
OWNER = int(os.environ.get("TARGET_OWNER", "80446"))  # 数据负责人，默认 gubang，保证默认视图可见

# 数据库密码：环境变量 MYSQL_PASSWORD > 同目录 pw.txt > docker 容器变量 > 交互输入
pw = os.environ.get("MYSQL_PASSWORD", "")
if not pw:
    _pwf = os.path.join(BASE, "pw.txt")
    if os.path.exists(_pwf):
        pw = open(_pwf).read().strip()
if not pw:
    try:
        pw = subprocess.check_output(
            ["docker", "exec", "txgy-mysql", "printenv", "MYSQL_ROOT_PASSWORD"]).decode().strip()
    except Exception:
        pw = ""
if not pw:
    pw = input("MySQL root 密码: ")
conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password=pw,
                       database="ruoyi-vue-pro", charset="utf8mb4")
cur = conn.cursor()

BIZ = {"线索": 1, "客户": 2, "联系人": 3, "商机": 4, "合同": 5, "产品": 6, "回款": 7, "回款计划": 8}
DATE_RE = re.compile(r'^\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?$')
CN_NUM = {"一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}

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

def ids(fn, col):
    out = []
    for r in read(fn):
        v = (r.get(col) or "").strip()
        if v:
            out.append(int(float(v)))
    return out

def audit(creator="1"):
    return {"creator": creator, "create_time": "__NOW__", "updater": "",
            "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}

def upsert(table, data, skip_check=None):
    if skip_check:
        cur.execute(skip_check[0], skip_check[1])
        if cur.fetchone():
            return 0
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
    cur.execute("INSERT INTO `%s` (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s"
                % (table, ",".join(cols), ",".join(ph), upd), vals)
    return 1

def delete_in(table, col, idl, tenant=True):
    if not idl:
        return
    ph = ",".join(["%s"] * len(idl))
    sql = "DELETE FROM `%s` WHERE `%s` IN (%s)" % (table, col, ph)
    args = list(idl)
    if tenant:
        sql += " AND tenant_id=%s"
        args.append(TENANT)
    cur.execute(sql, args)

# ================= 1. 清理租户1 中本次导入的数据 =================
clean = [
    ("crm_business_product", "business_id", ids("09_商机_商机产品明细.csv", "business_id"), False),
    ("crm_contract_product", "contract_id", ids("11_合同_合同产品明细.csv", "contract_id"), False),
    ("crm_customer", "id", ids("06_客户_客户档案.csv", "customer_id"), False),
    ("crm_clue", "id", ids("05_线索_线索档案.csv", "lead_id"), False),
    ("crm_contact", "id", ids("07_联系人_联系人档案.csv", "contact_id"), False),
    ("crm_business", "id", ids("08_商机_商机档案.csv", "business_id"), False),
    ("crm_contract", "id", ids("10_合同_合同档案.csv", "contract_id"), False),
    ("crm_receivable_plan", "id", ids("12_回款_回款计划.csv", "plan_id"), False),
    ("crm_receivable", "id", ids("13_回款_回款记录.csv", "receivable_id"), False),
    ("crm_follow_up_record", "id", ids("14_跟进_跟进记录.csv", "follow_id"), False),
    ("crm_product", "id", ids("04_产品_产品列表.csv", "product_id"), False),
    ("crm_product_category", "id", ids("03_产品_产品分类.csv", "category_id"), False),
]
for table, col, idl, tflag in clean:
    # 清理租户1：按 id 精确删除（这些 id 仅本次导入使用）
    if idl:
        ph = ",".join(["%s"] * len(idl))
        cur.execute("DELETE FROM `%s` WHERE `%s` IN (%s) AND tenant_id=1" % (table, col, ph), idl)
# 商机状态（租户1 新建的）
stages = sorted({s(r, "stage", "") for r in read("08_商机_商机档案.csv") if s(r, "stage")})
groups = sorted({s(r, "status_group", "") for r in read("08_商机_商机档案.csv") if s(r, "status_group")})
if stages:
    cur.execute("DELETE FROM crm_business_status WHERE tenant_id=1 AND name IN (%s)"
                % ",".join(["%s"] * len(stages)), stages)
if groups:
    cur.execute("DELETE FROM crm_business_status_type WHERE tenant_id=1 AND name IN (%s)"
                % ",".join(["%s"] * len(groups)), groups)
# 团队成员（租户1）
rows15 = read("15_团队_团队成员.csv")
pairs = {(BIZ.get(s(r, "biz_type"), 2), i(r, "biz_id")) for r in rows15}
if pairs:
    conds, args = [], []
    for bt, bid in pairs:
        conds.append("(biz_type=%s AND biz_id=%s)")
        args += [bt, bid]
    cur.execute("DELETE FROM crm_permission WHERE tenant_id=1 AND (" + " OR ".join(conds) + ")", args)
# 部门（租户1 新建的 1-7）
cur.execute("DELETE FROM system_dept WHERE tenant_id=1 AND id BETWEEN 1 AND 9")
# 用户（租户1 新建的 101-106，107-110 为系统原有保留）
cur.execute("DELETE FROM system_users WHERE tenant_id=1 AND id BETWEEN 101 AND 106")
print("CLEANED tenant-1 imported rows")

# ================= 2. 在固邦(2012) 下重建：部门映射 =================
dept_map = {}
dept_rows = read("01_组织_部门.csv")
for r in dept_rows:  # 先建父后建子（CSV 按 parent 顺序）
    name = s(r, "dept_name", "")
    cur.execute("SELECT id FROM system_dept WHERE tenant_id=%s AND name=%s LIMIT 1", (TENANT, name))
    got = cur.fetchone()
    if got:
        dept_map[i(r, "dept_id")] = got[0]
    else:
        parent = dept_map.get(i(r, "parent_id"), 0)
        upsert("system_dept",
               {"name": name, "parent_id": parent, "sort": 0, "status": 0,
                "leader_user_id": None, "phone": "", "email": "",
                "creator": "1", "create_time": "__NOW__", "updater": "",
                "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT})
        dept_map[i(r, "dept_id")] = cur.lastrowid
print("DEPT MAP:", dept_map)

# ================= 3. 固邦下新建员工，建立用户映射 =================
cur.execute("SELECT password FROM system_users WHERE id=1")
default_pwd = cur.fetchone()[0]
cur.execute("SELECT MAX(id) FROM system_users")
next_uid = (cur.fetchone()[0] or 0) + 1
user_map = {}
for r in read("02_组织_用户.csv"):
    csv_uid = i(r, "user_id")
    username = s(r, "mobile") or ("gubang%s" % csv_uid)
    cur.execute("SELECT id FROM system_users WHERE username=%s", (username,))
    if cur.fetchone():
        username = "%s_gb" % username
    # post_ids 为 JSON 数组字符串，且岗位需属于目标租户，否则后端反序列化报错
    post_ids = "[]"
    post_name = s(r, "post")
    if post_name:
        cur.execute("SELECT id FROM system_post WHERE tenant_id=%s AND name=%s LIMIT 1",
                    (TENANT, post_name))
        got = cur.fetchone()
        if got:
            post_ids = "[%s]" % got[0]
    upsert("system_users",
           {"id": next_uid, "username": username, "password": default_pwd,
            "nickname": s(r, "nickname", ""), "dept_id": dept_map.get(i(r, "dept_id")),
            "post_ids": post_ids, "mobile": s(r, "mobile"),
            "email": "user%s@txgy.local" % csv_uid, "sex": 0,
            "status": i(r, "status", 0), "remark": s(r, "remark", ""),
            "creator": "1", "create_time": "__NOW__", "updater": "",
            "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT})
    user_map[csv_uid] = next_uid
    next_uid += 1
print("USER MAP:", user_map)

def owner(row, key="owner_user_id"):
    """数据负责人统一挂到 TARGET_OWNER（默认 gubang 80446），保证用户默认视图即可见"""
    return OWNER

# ================= 4. 业务数据（tenant=2012）=================
# 产品分类
for r in read("03_产品_产品分类.csv"):
    d = {"id": i(r, "category_id"), "name": s(r, "category_name", ""),
         "parent_id": i(r, "parent_id", 0)}
    d.update(audit())
    upsert("crm_product_category", d)

# 产品
for r in read("04_产品_产品列表.csv"):
    d = {"id": i(r, "product_id"), "name": s(r, "name", ""), "no": s(r, "no", ""),
         "unit": s(r, "unit_value"), "price": dec(r, "price", Decimal("0")),
         "status": i(r, "status", 0), "category_id": i(r, "category_id"),
         "description": s(r, "description", ""), "owner_user_id": owner(r)}
    d.update(audit())
    upsert("crm_product", d)

# 线索
for r in read("05_线索_线索档案.csv"):
    addr = " ".join(x for x in [s(r, "region"), s(r, "detail_address")] if x)
    d = {"id": i(r, "lead_id"), "name": s(r, "lead_name", ""),
         "follow_up_status": i(r, "follow_status", 0),
         "transform_status": i(r, "convert_status", 0), "customer_id": i(r, "customer_id"),
         "mobile": s(r, "mobile"), "telephone": s(r, "phone"), "qq": s(r, "qq"),
         "wechat": s(r, "wechat"), "email": s(r, "email"),
         "detail_address": addr or None, "industry_id": i(r, "industry"),
         "level": i(r, "level"), "source": i(r, "source"), "owner_user_id": owner(r),
         "contact_last_time": dt(r, "last_follow_time"),
         "contact_last_content": s(r, "last_follow_content"),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_clue", d)

# 客户
for r in read("06_客户_客户档案.csv"):
    addr = " ".join(x for x in [s(r, "region"), s(r, "detail_address")] if x)
    d = {"id": i(r, "customer_id"), "name": s(r, "customer_name", ""),
         "follow_up_status": 0, "mobile": s(r, "mobile"), "telephone": s(r, "phone"),
         "email": s(r, "email"), "level": i(r, "level"), "industry_id": i(r, "industry"),
         "source": i(r, "source"), "owner_user_id": owner(r),
         "owner_time": "__NOW__", "lock_status": 0, "deal_status": bit(r, "deal_status"),
         "detail_address": addr or None,
         "contact_last_time": dt(r, "last_follow_time"),
         "contact_last_content": s(r, "last_follow_content"),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_customer", d)

# 联系人
for r in read("07_联系人_联系人档案.csv"):
    d = {"id": i(r, "contact_id"), "name": s(r, "name", ""),
         "customer_id": i(r, "customer_id"), "mobile": s(r, "mobile"),
         "telephone": s(r, "phone"), "email": s(r, "email"), "qq": s(r, "qq"),
         "wechat": s(r, "wechat"), "post": s(r, "post"), "master": bit(r, "is_main"),
         "owner_user_id": owner(r), "sex": None, "parent_id": None,
         "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_contact", d)

# 商机状态组 / 阶段
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
            upsert("crm_business_status_type",
                   {"name": gname, "dept_ids": "", "creator": "1", "create_time": "__NOW__",
                    "updater": "", "update_time": "__NOW__", "tenant_id": TENANT, "deleted": 0})
            type_cache[gname] = cur.lastrowid
    stid = type_cache[gname]
    sname = s(r, "stage", "")
    if sname and (stid, sname) not in status_cache:
        cur.execute("SELECT id FROM crm_business_status WHERE type_id=%s AND name=%s LIMIT 1",
                    (stid, sname))
        got = cur.fetchone()
        if got:
            status_cache[(stid, sname)] = got[0]
        else:
            upsert("crm_business_status",
                   {"type_id": stid, "name": sname,
                    "percent": dec(r, "win_rate", Decimal("0")), "sort": len(status_cache),
                    "creator": "1", "create_time": "__NOW__", "updater": "",
                    "update_time": "__NOW__", "tenant_id": TENANT, "deleted": 0})
            status_cache[(stid, sname)] = cur.lastrowid

# 商机
for r in read("08_商机_商机档案.csv"):
    stid = type_cache[s(r, "status_group", "标准流程")]
    d = {"id": i(r, "business_id"), "name": s(r, "business_name", ""),
         "customer_id": i(r, "customer_id"), "follow_up_status": 0, "owner_user_id": owner(r),
         "status_type_id": stid, "status_id": status_cache.get((stid, s(r, "stage", ""))),
         "end_status": i(r, "end_status"), "deal_time": None,
         "total_product_price": dec(r, "total_product_price", Decimal("0")),
         "discount_percent": dec(r, "discount_percent", Decimal("0")),
         "total_price": dec(r, "total_price", Decimal("0")),
         "contact_next_time": dt(r, "next_contact_time"), "remark": s(r, "remark", "")}
    d.update(audit())
    upsert("crm_business", d)

# 商机产品明细
bids = sorted(set(ids("09_商机_商机产品明细.csv", "business_id")))
delete_in("crm_business_product", "business_id", bids)
for r in read("09_商机_商机产品明细.csv"):
    d = {"business_id": i(r, "business_id"), "product_id": i(r, "product_id"),
         "product_price": dec(r, "standard_price", Decimal("0")),
         "business_price": dec(r, "business_price", Decimal("0")),
         "count": dec(r, "quantity", Decimal("0")),
         "total_price": dec(r, "subtotal", Decimal("0"))}
    d.update(audit())
    upsert("crm_business_product", d)

# 合同
for r in read("10_合同_合同档案.csv"):
    d = {"id": i(r, "contract_id"), "name": s(r, "contract_name", ""),
         "no": s(r, "contract_no", ""), "customer_id": i(r, "customer_id"),
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

# 合同产品明细
cids = sorted(set(ids("11_合同_合同产品明细.csv", "contract_id")))
delete_in("crm_contract_product", "contract_id", cids)
for r in read("11_合同_合同产品明细.csv"):
    d = {"contract_id": i(r, "contract_id"), "product_id": i(r, "product_id"),
         "product_price": dec(r, "standard_price", Decimal("0")),
         "contract_price": dec(r, "contract_price", Decimal("0")),
         "count": dec(r, "quantity", Decimal("0")),
         "total_price": dec(r, "subtotal", Decimal("0"))}
    d.update(audit())
    upsert("crm_contract_product", d)

# 回款计划
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
         "remind_time": dt(r, "remind_time"),
         "remark": (s(r, "remark") or v or "")}
    d.update(audit())
    upsert("crm_receivable_plan", d)

# 回款记录
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

# 跟进记录
for r in read("14_跟进_跟进记录.csv"):
    ow = str(owner(r) or 1)
    d = {"id": i(r, "follow_id"), "biz_type": BIZ.get(s(r, "biz_type"), 2),
         "biz_id": i(r, "biz_id"), "type": i(r, "follow_type"),
         "content": s(r, "content", ""), "next_time": dt(r, "next_contact_time"),
         "pic_urls": "", "file_urls": "", "business_ids": "", "contact_ids": "",
         "creator": ow, "create_time": (dt(r, "create_time") or "__NOW__"),
         "updater": ow, "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}
    upsert("crm_follow_up_record", d)

# 团队成员
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
         "user_id": user_map.get(i(r, "user_id")), "level": lvl,
         "creator": "1", "create_time": "__NOW__", "updater": "",
         "update_time": "__NOW__", "deleted": 0, "tenant_id": TENANT}
    upsert("crm_permission", d)

conn.commit()

print("=== 固邦租户(%s) 导入后统计 ===" % TENANT)
for t in ["crm_customer", "crm_clue", "crm_contact", "crm_business", "crm_business_product",
          "crm_contract", "crm_contract_product", "crm_receivable", "crm_receivable_plan",
          "crm_follow_up_record", "crm_permission", "crm_product", "crm_product_category"]:
    cur.execute("SELECT COUNT(*) FROM `%s` WHERE tenant_id=%s AND deleted=0" % (t, TENANT))
    print("  %-26s %s" % (t, cur.fetchone()[0]))
cur.execute("SELECT id,name,owner_user_id,tenant_id FROM crm_customer WHERE tenant_id=%s ORDER BY id" % TENANT)
print("  客户清单:", cur.fetchall())
cur.execute("SELECT id,username,nickname,dept_id,tenant_id FROM system_users WHERE tenant_id=%s ORDER BY id DESC LIMIT 10" % TENANT)
print("  新建员工:", cur.fetchall())
conn.close()
