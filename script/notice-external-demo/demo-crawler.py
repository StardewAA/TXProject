# -*- coding: utf-8 -*-
"""
示例数据采集器（模拟真实的外部采集服务回传，全走 HTTP 接口，非 SQL 直插）。

链路：登录 system（取 admin token）
    → POST /bidding/notice-external/ingest（采集回传入库 → NEW+UNREVIEWED）
    → POST /bidding/notice-external/approve（审核通过 → ACTIVE）
    → 服务端自动触发：关注扇出（EXTERNAL_ACTIVE 事件）+ ES 索引（bidding-notice-external）
数据源：同目录 samples.json（4 条真实公告样例，曾 SQL 直插，本脚本改为链路重灌）。

用法：py demo-crawler.py [api_base] [sys_base]
  api_base 默认 http://127.0.0.1:48110/admin-api（bidding-server 直连）
  sys_base 默认 http://127.0.0.1:48081/admin-api（system-server，签发 token；需本地/容器已起 system）
说明：bidding 管理端接口需真实 token（本地 bidding 权限校验走 system-server OAuth2/权限 RPC）。
"""
import json
import sys
import urllib.request
import urllib.error

API_BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:48110/admin-api"
SYS_BASE = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:48081/admin-api"
TENANT_ID = "1"
ADMIN_USER = "admin"
ADMIN_PASS = "admin123"
SAMPLES = "samples.json"


def call(method, base, path, body=None, token=None, timeout=30):
    req = urllib.request.Request(base + path, method=method)
    req.add_header("Content-Type", "application/json;charset=UTF-8")
    req.add_header("tenant-id", TENANT_ID)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return json.loads(raw)
        except Exception:
            return {"code": e.code, "message": raw[:200]}


def login_token():
    resp = call("POST", SYS_BASE, "/system/auth/login",
                {"username": ADMIN_USER, "password": ADMIN_PASS})
    if resp.get("code") == 0:
        tok = resp["data"]["accessToken"]
        print(f"登录成功（{ADMIN_USER} @ {SYS_BASE}）")
        return tok
    print(f"!! 登录失败：code={resp.get('code')} msg={resp.get('message')}（需本地起 system-server 48081）")
    return None


def main():
    with open(SAMPLES, encoding="utf-8") as f:
        samples = json.load(f)
    print(f"加载样本 {len(samples)} 条 from {SAMPLES}")
    token = login_token()
    if not token:
        sys.exit(1)
    ok = 0
    for s in samples:
        title = s["title"]
        print(f"[ingest] {title[:42]}...")
        dto = {k: v for k, v in s.items() if k != "id"}  # 服务端自产新 id
        resp = call("POST", API_BASE, "/bidding/notice-external/ingest", dto, token)
        if resp.get("code") != 0:
            print(f"  !! ingest 失败: code={resp.get('code')} msg={resp.get('message')}")
            continue
        nid = resp["data"]
        print(f"  -> 入库 id={nid}（NEW，待审核）")
        ar = call("POST", API_BASE, f"/bidding/notice-external/approve?id={nid}", {}, token)
        if ar.get("code") != 0:
            print(f"  !! approve 失败: code={ar.get('code')} msg={ar.get('message')}")
            continue
        print(f"  -> 审核通过 id={nid}（ACTIVE，ES 索引+关注扇出已触发）")
        ok += 1
    print(f"完成：{ok}/{len(samples)} 条经采集接口重灌并审核通过")


if __name__ == "__main__":
    main()
