# -*- coding: utf-8 -*-
"""
修正固邦租户(2012)测试数据的负责人：
按 CSV 每行的 owner_user_id 还原真实负责人（刘志强/赵磊/陈晓东/周文斌/王海涛/孙丽娟），
而不是统一挂到 gubang(80446)。同时同步 crm_permission 的 user_id。
"""
import csv
import os
import subprocess

import pymysql

CSV_DIR = os.environ.get(
    "CSV_DIR", r"C:\Users\zmj15\Desktop\实习\CRM测试数据(1)\测试数据"
)
TENANT = int(os.environ.get("TARGET_TENANT", "2012"))

pw = os.environ.get("MYSQL_PASSWORD", "root")

conn = pymysql.connect(host="127.0.0.1", port=int(os.environ.get("MYSQL_PORT", "13306")),
                       user="root", password=pw,
                       database="ruoyi-vue-pro", charset="utf8mb4")
cur = conn.cursor()


def read(fn):
    with open(os.path.join(CSV_DIR, fn), encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def s(row, k, default=None):
    v = (row.get(k) or "").strip()
    return v if v != "" else default


def i(row, k, default=None):
    v = (row.get(k) or "").strip()
    if v == "":
        return default
    try:
        return int(float(v))
    except ValueError:
        return default


# CSV user_id -> 固邦实际用户 id
# 说明：历史版本脚本按 id=910111+序号 生成用户；当前 import_to_gubang_2012.py 按「用户名=手机号」创建，
#      id 由自增生成。因此这里优先按 username=mobile 精确匹配（并取最新一条），再按 mobile，最后兜底旧方案。
user_map = {}
for idx, r in enumerate(read("02_组织_用户.csv")):
    uid = i(r, "user_id")
    mobile = s(r, "mobile")
    got = None
    if mobile:
        cur.execute("SELECT id FROM system_users WHERE tenant_id=%s AND username=%s AND deleted=0 "
                    "ORDER BY id DESC LIMIT 1", (TENANT, mobile))
        got = cur.fetchone()
    if not got and mobile:
        cur.execute("SELECT id FROM system_users WHERE tenant_id=%s AND mobile=%s AND deleted=0 "
                    "ORDER BY id DESC LIMIT 1", (TENANT, mobile))
        got = cur.fetchone()
    if not got:
        cur.execute("SELECT id FROM system_users WHERE tenant_id=%s AND id=%s AND deleted=0",
                    (TENANT, 910111 + idx))
        got = cur.fetchone()
    if got:
        user_map[uid] = got[0]
print("用户映射:", user_map)

# 兜底负责人：gubang
cur.execute("SELECT id FROM system_users WHERE tenant_id=%s AND username='gubang' AND deleted=0", (TENANT,))
row = cur.fetchone()
FALLBACK = row[0] if row else 80446

BIZ = {"线索": 1, "客户": 2, "联系人": 3, "商机": 4, "合同": 5,
       "产品": 6, "回款": 7, "回款计划": 8}

updated = {}


def set_owner(table, pk_col, csv_id, csv_owner):
    """按 CSV 行更新负责人"""
    oid = user_map.get(csv_owner) or FALLBACK
    cur.execute(
        "UPDATE `%s` SET owner_user_id=%%s WHERE `%s`=%%s AND tenant_id=%%s" % (table, pk_col),
        (oid, csv_id, TENANT))
    updated[table] = updated.get(table, 0) + cur.rowcount


# ---- 产品(04)：owner_user_id ----
for r in read("04_产品_产品列表.csv"):
    set_owner("crm_product", "id", i(r, "product_id"), i(r, "owner_user_id"))

# ---- 客户(06)：owner_user_id ----
for r in read("06_客户_客户档案.csv"):
    set_owner("crm_customer", "id", i(r, "customer_id"), i(r, "owner_user_id"))

# ---- 线索(05) ----
for r in read("05_线索_线索档案.csv"):
    set_owner("crm_clue", "id", i(r, "lead_id"), i(r, "owner_user_id"))

# ---- 联系人(07) ----
for r in read("07_联系人_联系人档案.csv"):
    set_owner("crm_contact", "id", i(r, "contact_id"), i(r, "owner_user_id"))

# ---- 商机(08) ----
for r in read("08_商机_商机档案.csv"):
    set_owner("crm_business", "id", i(r, "business_id"), i(r, "owner_user_id"))

# ---- 合同(10)：owner_user_id + sign_user_id ----
for r in read("10_合同_合同档案.csv"):
    oid = user_map.get(i(r, "owner_user_id")) or FALLBACK
    cur.execute("UPDATE crm_contract SET owner_user_id=%s, sign_user_id=%s "
                "WHERE id=%s AND tenant_id=%s",
                (oid, oid, i(r, "contract_id"), TENANT))
    updated["crm_contract"] = updated.get("crm_contract", 0) + cur.rowcount

# ---- 回款计划(12) ----
for r in read("12_回款_回款计划.csv"):
    set_owner("crm_receivable_plan", "id", i(r, "plan_id"), i(r, "owner_user_id"))

# ---- 回款记录(13) ----
for r in read("13_回款_回款记录.csv"):
    set_owner("crm_receivable", "id", i(r, "receivable_id"), i(r, "owner_user_id"))

# ---- 跟进记录(14)：creator 按 owner 还原 ----
for r in read("14_跟进_跟进记录.csv"):
    oid = user_map.get(i(r, "owner_user_id")) or FALLBACK
    cur.execute("UPDATE crm_follow_up_record SET creator=%s, updater=%s "
                "WHERE id=%s AND tenant_id=%s",
                (str(oid), str(oid), i(r, "follow_id"), TENANT))
    updated["crm_follow_up_record"] = updated.get("crm_follow_up_record", 0) + cur.rowcount

# ---- 团队成员(15)：按 CSV 的 user_id 还原权限成员 ----
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
    uid = user_map.get(i(r, "user_id")) or FALLBACK
    cur.execute(
        "INSERT INTO crm_permission(biz_type,biz_id,user_id,level,creator,create_time,"
        "updater,update_time,deleted,tenant_id) VALUES(%s,%s,%s,%s,'1',NOW(),'',NOW(),0,%s)",
        (BIZ.get(s(r, "biz_type"), 2), i(r, "biz_id"), uid, lvl, TENANT))
    updated["crm_permission"] = updated.get("crm_permission", 0) + 1

conn.commit()

print("\n=== 更新行数 ===")
for k, v in updated.items():
    print("  %-26s %s" % (k, v))

print("\n=== 固邦(2012) 客户负责人分布 ===")
cur.execute("""SELECT u.nickname, COUNT(*) FROM crm_customer c
               LEFT JOIN system_users u ON u.id=c.owner_user_id
               WHERE c.tenant_id=%s AND c.deleted=0 GROUP BY c.owner_user_id, u.nickname""", (TENANT,))
for r in cur.fetchall():
    print("  %-12s %s" % (r[0] or "(无)", r[1]))

print("\n=== 固邦(2012) 线索负责人分布 ===")
cur.execute("""SELECT u.nickname, COUNT(*) FROM crm_clue c
               LEFT JOIN system_users u ON u.id=c.owner_user_id
               WHERE c.tenant_id=%s AND c.deleted=0 GROUP BY c.owner_user_id, u.nickname""", (TENANT,))
for r in cur.fetchall():
    print("  %-12s %s" % (r[0] or "(无)", r[1]))

conn.close()
print("\n修正完成")
