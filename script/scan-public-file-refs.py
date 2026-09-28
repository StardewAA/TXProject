#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
扫描业务表中引用的 infra 文件 URL，按「公开/私有」分类，核对：
  1) 是否存在存活 infra_file 记录；2) 是否为 PUBLIC；3) 内容是否还在(infra_file_content)。
用于历史公开图片的「核实后补入白名单」。
只读，不修改任何数据。
"""
import json
import subprocess
import sys
from collections import defaultdict

MYSQL = ["docker", "exec", "-i", "-e", "MYSQL_PWD=root", "deploy-mysql-1",
         "mysql", "-h127.0.0.1", "-uroot", "-N", "--default-character-set=utf8mb4",
         "ruoyi-vue-pro"]


def q(sql):
    p = subprocess.run(MYSQL + ["-e", sql], capture_output=True)
    if p.returncode != 0:
        sys.stderr.write("SQL FAIL: %s\n%s\n" % (sql[:200], p.stderr.decode('utf-8', 'ignore')[:500]))
        return []
    out = p.stdout.decode('utf-8', 'ignore')
    return [ln for ln in out.split("\n") if ln != ""]


# (表, 列, 形态, 期望分类) ; 形态: single / json / csv
COLS = [
    # ---- 公开（商城对外展示）----
    ("product_spu", "pic_url", "single", "public"),
    ("product_spu", "slider_pic_urls", "json", "public"),
    ("product_sku", "pic_url", "single", "public"),
    ("product_brand", "pic_url", "single", "public"),
    ("product_category", "pic_url", "single", "public"),
    ("product_shop", "logo_url", "single", "public"),
    ("product_comment", "pic_urls", "json", "public"),
    ("product_comment", "sku_pic_url", "single", "public"),
    ("promotion_banner", "pic_url", "single", "public"),
    ("promotion_article", "pic_url", "single", "public"),
    ("promotion_article_category", "pic_url", "single", "public"),
    ("promotion_seckill_config", "slider_pic_urls", "json", "public"),
    ("promotion_diy_page", "preview_pic_urls", "json", "public"),
    ("promotion_diy_template", "preview_pic_urls", "json", "public"),
    ("promotion_combination_record", "pic_url", "single", "public"),
    ("member_level", "background_url", "single", "public"),
    ("trade_delivery_express", "logo", "single", "public"),
    ("trade_delivery_pick_up_store", "logo", "single", "public"),
    ("trade_order_item", "pic_url", "single", "public"),
    ("trade_platform_checkout_plan_item", "pic_url", "single", "public"),
    ("iot_product", "pic_url", "single", "public"),
    ("iot_device", "pic_url", "single", "public"),
    ("service_type", "pic_url", "single", "public"),
    ("service_fault_type", "pic_url", "single", "public"),
    ("mes_md_vendor", "logo", "single", "public"),
    ("mes_md_client", "logo", "single", "public"),
    # ---- 私有（凭证/头像/单据附件等）----
    ("product_purchase_inquiry_item", "pic_url", "single", "private"),
    ("member_user", "avatar", "single", "private"),
    ("trade_after_sale", "pic_url", "single", "private"),
    ("trade_after_sale", "apply_pic_urls", "json", "private"),
    ("product_enterprise", "license_url", "single", "private"),
    ("system_users", "avatar", "single", "private"),
    ("system_social_user", "avatar", "single", "private"),
]


def expand(value, kind):
    value = value.strip()
    if not value:
        return []
    if kind == "single":
        return [value]
    if kind == "json":
        try:
            v = json.loads(value)
        except Exception:
            return [value]
        if isinstance(v, list):
            return [str(x) for x in v if x]
        if isinstance(v, str):
            return [v]
        return []
    if kind == "csv":
        return [x.strip() for x in value.split(",") if x.strip()]
    return [value]


def main():
    refs = defaultdict(set)   # url -> {期望分类}
    raw_cnt = defaultdict(int)

    for table, col, kind, cls in COLS:
        rows = q("SELECT %s FROM %s WHERE %s LIKE '%%/infra/file/%%'" % (col, table, col))
        for r in rows:
            for u in expand(r, kind):
                if "/infra/file/" in u:
                    refs[u].add(cls)
                    raw_cnt[(table, col)] += 1

    # 存活记录
    live = {}
    for ln in q("SELECT id, url, access_type, bind_status FROM infra_file WHERE deleted=0"):
        parts = ln.split("\t")
        if len(parts) >= 4:
            live[parts[1]] = (parts[0], int(parts[2]), int(parts[3]))
    # 存活内容
    content = set()
    for ln in q("SELECT path FROM infra_file_content WHERE deleted=0"):
        content.add(ln)

    def path_of(u):
        return u.split("/get/", 1)[1] if "/get/" in u else ""

    print("引用列统计（原始出现次数）:")
    for k in sorted(raw_cnt):
        print("   %-45s %d" % ("%s.%s" % k, raw_cnt[k]))
    print("")
    print("去重后 /infra/file/ URL 总数: %d" % len(refs))
    print("")

    only_public, only_private, both = [], [], []
    for u, cs in refs.items():
        if cs == {"public"}:
            only_public.append(u)
        elif cs == {"private"}:
            only_private.append(u)
        else:
            both.append(u)

    print("仅被公开面引用: %d ；仅被私有面引用: %d ；两面都引用: %d" % (len(only_public), len(only_private), len(both)))
    print("")

    def analyze(title, urls):
        print("=== %s (%d) ===" % (title, len(urls)))
        buckets = defaultdict(list)
        for u in urls:
            rec = live.get(u)
            has_content = path_of(u) in content
            if rec is None:
                key = "NO_RECORD" + (" +content_ok" if has_content else " +content_gone")
            else:
                rid, at, bs = rec
                key = "record_%d/%d" % (at, bs) + (" +content_ok" if has_content else " +content_gone")
            buckets[key].append(u)
        for k in sorted(buckets):
            print("   %-28s %d" % (k, len(buckets[k])))
        print("")
        return buckets

    pb = analyze("仅公开面引用的 URL", only_public)
    analyze("仅私有面引用的 URL", only_private)
    if both:
        analyze("两面都引用（需人工判断）", both)

    # 需要处置：公开面引用，但当前非 PUBLIC，且内容还在
    print("=== 待处置（公开面引用 + 内容存在 + 当前非 PUBLIC）===")
    todo = []
    for u in sorted(set(only_public) | set(both)):
        rec = live.get(u)
        if path_of(u) not in content:
            continue
        if rec is None:
            todo.append((u, "NO_RECORD"))
        elif rec[1] != 1:
            todo.append((u, "record_%d/%d" % (rec[1], rec[2])))
    if not todo:
        print("   （无）")
    for u, why in todo:
        print("   %-14s %s" % (why, u))
    print("")
    print("合计待处置: %d" % len(todo))

    # 仅公开面引用但无内容（历史即坏，非本次回归）
    gone = [u for u in set(only_public) | set(both) if path_of(u) not in content]
    print("仅公开面引用但内容已不存在(历史即坏): %d" % len(gone))


if __name__ == "__main__":
    main()
