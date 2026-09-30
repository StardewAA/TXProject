#!/usr/bin/env node
/**
 * smoke-cert-api.js —— 电子资质域「五页改造缺接口」（Q1~Q23）接口冒烟脚本。
 *
 * 规范出处：docs/电子证照前前端契约/后端变更请求-资质证书五页改造缺接口-2026-09-18.md（Q1~Q23）；
 *           交付口径见同目录《后端变更通知-给前端.md》条目 16~28。
 * 为什么单独放在 tools/：本仓 sql/tools/*.js 是**静态**自检（不连服务、可离线跑，会被 check-all.js 汇总）；
 *           本脚本是**运行期**验收（需要活着的服务 + 登录令牌），两类不能混，故独立成工具，不并入 check-all。
 * 业务目标：把"服务起来后到底通没通"从手工点页面变成一条命令；缺字典/字段改名/404 这类集成问题当场暴露。
 *
 * 用法：
 *   node tools/api-smoke/smoke-cert-api.js --token <JWT> [--base http://127.0.0.1:48110] [--tenant 1]
 *                                  [--ent 1] [--risk-id <id>] [--lifecycle-id <id>] [--write] [--json]
 *   SMOKE_TOKEN=<JWT> node tools/api-smoke/smoke-cert-api.js        # 令牌也可走环境变量
 *
 * 取令牌（本地联调）：POST {base}/admin-api/system/auth/login
 *   headers: tenant-id: 1, Content-Type: application/json
 *   body: {"username":"admin","password":"admin123"}
 *
 * 退出码：0=全部通过；1=有失败；2=用法/令牌缺失。
 */

const argv = process.argv.slice(2);
function arg(name, def) {
  const i = argv.indexOf("--" + name);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[i + 1] : def;
}
const flag = (name) => argv.includes("--" + name);

const BASE = (arg("base", process.env.SMOKE_BASE || "http://127.0.0.1:48110")).replace(/\/$/, "");
const TOKEN = arg("token", process.env.SMOKE_TOKEN || "");
const TENANT = arg("tenant", "1");
const ENT_ID = arg("ent", "1");
const RISK_ID = arg("risk-id", "");
const LIFECYCLE_ID = arg("lifecycle-id", "");
const DO_WRITE = flag("write");
const AS_JSON = flag("json");

if (!TOKEN) {
  console.error("[用法] 缺少令牌：--token <JWT> 或环境变量 SMOKE_TOKEN。取令牌方式见本文件头注释。");
  process.exit(2);
}

const STATUS6 = ["ACTIVE", "EXPIRING_SOON", "EXPIRED", "SUSPENDED", "REVOKED", "CANCELLED"];
const RISK_STATUS5 = ["PENDING", "ASSIGNED", "HANDLING", "HANDLED", "IGNORED"];
const DICT4 = ["risk_item_status", "alert_level", "alert_type", "risk_rule_cond_type"];

const results = [];

/** 统一请求：返回 { http, ct, buf, json }；不抛异常，失败也进结果表。 */
async function call(method, path, opts = {}) {
  const url = BASE + "/admin-api" + path;
  const headers = Object.assign({ "tenant-id": TENANT, Authorization: "Bearer " + TOKEN }, opts.headers || {});
  const init = { method, headers };
  if (opts.body !== undefined) {
    init.body = typeof opts.body === "string" ? opts.body : JSON.stringify(opts.body);
    if (!headers["Content-Type"]) headers["Content-Type"] = "application/json";
  }
  try {
    const resp = await fetch(url, init);
    const ct = resp.headers.get("content-type") || "";
    const buf = Buffer.from(await resp.arrayBuffer());
    let json = null;
    if (ct.includes("json") && buf.length) { try { json = JSON.parse(buf.toString("utf8")); } catch (_) {} }
    return { http: resp.status, ct, buf, json };
  } catch (e) {
    return { http: 0, ct: "", buf: Buffer.alloc(0), json: null, error: String(e && e.message || e) };
  }
}

function record(name, ok, detail) {
  results.push({ name, ok, detail });
}

const bizOk = (r) => r.json && r.json.code === 0;
const bizMsg = (r) => (r.json ? "code=" + r.json.code + " " + (r.json.msg || "") : "HTTP " + r.http + (r.error ? " " + r.error : ""));
const isBinary = (r) => r.http === 200 && r.buf.length > 0 && !r.ct.includes("json");

async function main() {
  // ① Q3 证照看板统计：按**前端已发布类型**断言字段名（api/bidding/certificate/index.ts 的 CertificateStats）
  //    为什么断言前端字段名而不是后端自定义字段：前端类型是已发布契约，字段名对不上=卡片显示 undefined，接口"通"也没用。
  {
    const FE_FIELDS = ["total", "active", "expiring", "abnormal", "cancelled", "unverified", "byStatus"];
    const r = await call("GET", "/bidding/certificate/stats");
    const d = r.json && r.json.data;
    const missing = d ? FE_FIELDS.filter((k) => !(k in d)) : FE_FIELDS;
    const sixMiss = d && d.statusCounts ? STATUS6.filter((k) => !(k in d.statusCounts)) : [];
    record("Q3 GET /certificate/stats（前端字段 active/expiring/abnormal/cancelled/unverified/byStatus）",
      bizOk(r) && missing.length === 0,
      bizOk(r) ? ("缺字段=" + (missing.length ? missing.join(",") : "无")
        + "；total=" + d.total + (sixMiss.length ? "；statusCounts 缺六态键=" + sixMiss.join(",") : "")) : bizMsg(r));
  }

  // ② Q1 证照导入模板：二进制 xls
  {
    const r = await call("GET", "/bidding/certificate/import-template");
    record("Q1 GET /bidding/certificate/import-template（模板下载）", isBinary(r), "HTTP " + r.http + " ct=" + r.ct + " bytes=" + r.buf.length);
  }

  // ③ Q5 资质看板统计：必须含 unlinkedStaff
  {
    const FE = ["total", "valid", "expiring", "expired", "unlinkedStaff", "byStatus"];
    const r = await call("GET", "/bidding/qualification/stats?entId=" + encodeURIComponent(ENT_ID));
    const d = r.json && r.json.data;
    const missing = d ? FE.filter((k) => !(k in d)) : FE;
    record("Q5 GET /qualification/stats（前端字段含 unlinkedStaff/byStatus）", bizOk(r) && missing.length === 0,
      bizOk(r) ? ("缺字段=" + (missing.length ? missing.join(",") : "无") + "；unlinkedStaff=" + d.unlinkedStaff) : bizMsg(r));
  }

  // ④ Q8 资质台账关联人员数：行上必须有 staffCount 字段（空表时提示无法断言）
  {
    const r = await call("GET", "/bidding/qualification/page?pageNo=1&pageSize=1&entId=" + encodeURIComponent(ENT_ID));
    const d = r.json && r.json.data;
    const rows = (d && d.list) || [];
    const has = rows.length > 0 ? ("staffCount" in rows[0]) : null;
    record("Q8 GET /bidding/qualification/page（出参含 staffCount）", bizOk(r) && (has === true || has === null),
      bizOk(r) ? (has === null ? "空表：无法断言字段存在（造一条数据后重跑）" : "staffCount=" + rows[0].staffCount) : bizMsg(r));
  }

  // ⑤ Q6 资质导出：二进制
  {
    const r = await call("GET", "/bidding/qualification/export?entId=" + encodeURIComponent(ENT_ID));
    record("Q6 GET /bidding/qualification/export（Excel 导出）", isBinary(r), "HTTP " + r.http + " bytes=" + r.buf.length);
  }

  // ⑥ Q10 员工看板统计
  {
    const FE = ["holderCount", "certTotal", "expiring", "expired", "unverified"];
    const r = await call("GET", "/bidding/personnel-certificate/stats");
    const d = r.json && r.json.data;
    const missing = d ? FE.filter((k) => !(k in d)) : FE;
    record("Q10 GET /personnel-certificate/stats（前端五字段）", bizOk(r) && missing.length === 0,
      bizOk(r) ? ("缺字段=" + (missing.length ? missing.join(",") : "无") + "；" + JSON.stringify(d).slice(0, 100)) : bizMsg(r));
  }

  // ⑦ Q9 员工维度分页
  {
    const r = await call("GET", "/bidding/personnel-certificate/page-by-person?pageNo=1&pageSize=1");
    record("Q9 GET /bidding/personnel-certificate/page-by-person", bizOk(r), bizOk(r) ? "OK" : bizMsg(r));
  }

  // ⑧ Q18 风险条目分页：状态必须落在五态值域内（对齐前端 RiskStatus）
  {
    const r = await call("GET", "/bidding/safety-permit/risk/page?pageNo=1&pageSize=5");
    const d = r.json && r.json.data;
    const rows = (d && d.list) || [];
    const bad = rows.filter((x) => x.status && !RISK_STATUS5.includes(x.status)).map((x) => x.status);
    record("Q18 GET /bidding/safety-permit/risk/page（状态∈五态）", bizOk(r) && bad.length === 0,
      bizOk(r) ? ("total=" + d.total + " 越界状态=" + (bad.length ? bad.join(",") : "无")) : bizMsg(r));
    // 注：rows 用 (d && d.list) || [] 兜底，服务不可用时也不能抛异常（否则整脚本中断、看不到失败表）
  }

  // ⑨ Q19 风险大盘
  {
    const r = await call("GET", "/bidding/safety-permit/risk/stats");
    const d = r.json && r.json.data;
    const keys = ["high", "medium", "low", "handled", "total"];
    const missing = d ? keys.filter((k) => !(k in d)) : keys;
    record("Q19 GET /bidding/safety-permit/risk/stats（五项）", bizOk(r) && missing.length === 0,
      bizOk(r) ? (missing.length ? "缺=" + missing.join(",") : JSON.stringify(d)) : bizMsg(r));
  }

  // ⑩ Q21 风险导出
  {
    const r = await call("GET", "/bidding/safety-permit/risk/export");
    record("Q21 GET /bidding/safety-permit/risk/export（Excel）", isBinary(r), "HTTP " + r.http + " bytes=" + r.buf.length);
  }

  // ⑪ 关键字典可下发（前端不再回落硬编码英文/中文）
  {
    const r = await call("GET", "/bidding/meta/dicts?types=" + DICT4.join(","));
    const d = (r.json && r.json.data) || {};
    const empty = DICT4.filter((t) => !Array.isArray(d[t]) || d[t].length === 0);
    record("字典 GET /bidding/meta/dicts（risk_item_status/alert_level/alert_type/risk_rule_cond_type）",
      bizOk(r) && empty.length === 0, bizOk(r) ? (empty.length ? "未下发=" + empty.join(",") : "4 类均非空") : bizMsg(r));
  }

  // ⑫ Q16 生命周期详情含 files[]（需 --lifecycle-id）
  if (LIFECYCLE_ID) {
    const r = await call("GET", "/bidding/cert-lifecycle/get?id=" + encodeURIComponent(LIFECYCLE_ID));
    const d = r.json && r.json.data;
    record("Q16 GET /bidding/cert-lifecycle/get（含 files[]）", bizOk(r) && d && Array.isArray(d.files),
      bizOk(r) ? ("files=" + (Array.isArray(d.files) ? d.files.length : "字段缺失")) : bizMsg(r));
  }

  // ⑬ Q18 handle 入参兼容 targetStatus（--write；同值提交=幂等 no-op，不改业务状态）
  if (DO_WRITE && RISK_ID) {
    const cur = await call("GET", "/bidding/safety-permit/risk/get?id=" + encodeURIComponent(RISK_ID));
    const row = cur.json && cur.json.data;
    if (!bizOk(cur) || !row) {
      record("Q18 POST /risk/handle（targetStatus 别名，同值 no-op）", false, "取详情失败：" + bizMsg(cur));
    } else {
      const r = await call("POST", "/bidding/safety-permit/risk/handle", {
        body: { id: RISK_ID, targetStatus: row.status, version: row.version, remark: "smoke: 同值幂等校验" },
      });
      record("Q18 POST /risk/handle（targetStatus 别名，同值 no-op）", bizOk(r),
        bizOk(r) ? ("已接受 targetStatus=" + row.status + "（未改状态）") : bizMsg(r) + "（若报 400/参数校验=别名未生效）");
    }
  }

  // ⑭ Q2 批量验真出参容器/元素（bogus id：只验形状，不碰真实数据）
  {
    const r = await call("POST", "/bidding/certificate-verify/verify-batch", { body: { certIds: [999999999] } });
    const d = r.json && r.json.data;
    const item = d && Array.isArray(d.items) && d.items.length > 0 ? d.items[0] : null;
    const FE = ["certId", "certName", "accepted", "verifyResult", "verifyChannel", "message"];
    const missing = item ? FE.filter((k) => !(k in item)) : FE;
    record("Q2 POST /certificate-verify/verify-batch（出参 items[] + 元素字段）",
      bizOk(r) && item && missing.length === 0,
      bizOk(r) ? (item ? ("items[0] 缺字段=" + (missing.length ? missing.join(",") : "无")) : "无 items 容器") : bizMsg(r));
  }

  // ⑮ Q11 批量续期回执包装 + version 可省（bogus id：行级失败即证明形状与放宽生效）
  {
    const r = await call("POST", "/bidding/personnel-certificate/batch-renew", {
      body: { items: [{ id: 999999999, newValidTo: "2030-01-01" }] },   // 故意不带 version
    });
    const d = r.json && r.json.data;
    const FE = ["total", "successCount", "failCount", "failures"];
    const missing = d ? FE.filter((k) => !(k in d)) : FE;
    record("Q11 POST /personnel-certificate/batch-renew（回执对象 + version 可省）",
      bizOk(r) && missing.length === 0,
      bizOk(r) ? ("缺字段=" + (missing.length ? missing.join(",") : "无") + "；failures=" + (d.failures || []).length)
        : bizMsg(r) + "（若报 400 版本不能为空=version 仍未放宽）");
  }

  // ⑯ Q13 证书级验真入参：前端发 JSON body（bogus id 应返回"不存在"业务码，而不是 400 参数校验）
  {
    const r = await call("POST", "/bidding/personnel-certificate/verify", { body: { certId: 999999999 } });
    const code = r.json && r.json.code;
    record("Q13 POST /personnel-certificate/verify（JSON body 入参）",
      code !== undefined && code !== 400,   // 业务错误码（如"证书不存在"）也算通过：证明 body 已被绑定
      code !== undefined && code !== 400 ? ("业务码 " + code + " " + (r.json.msg || "")) : bizMsg(r) + "（400=body 绑定未生效）");
  }

  // ⑰ Q6 导出路径别名 /export-excel（前端已发布路径）
  {
    const r = await call("GET", "/bidding/qualification/export-excel?entId=" + encodeURIComponent(ENT_ID));
    record("Q6 GET /qualification/export-excel（路径别名）", isBinary(r), "HTTP " + r.http + " bytes=" + r.buf.length);
  }

  // 输出
  const pass = results.filter((x) => x.ok).length;
  if (AS_JSON) {
    console.log(JSON.stringify({ base: BASE, total: results.length, pass, results }, null, 2));
  } else {
    console.log("冒烟目标：" + BASE + "（tenant-id=" + TENANT + "）");
    console.log("");
    for (const x of results) console.log((x.ok ? "  [PASS] " : "  [FAIL] ") + x.name + "  —— " + x.detail);
    console.log("");
    console.log("结果：" + pass + "/" + results.length + " 通过" + (pass === results.length ? "（全部通过）" : "（有失败项，见上）"));
    if (!DO_WRITE) console.log("提示：写接口校验默认关闭；需要时加 --write --risk-id <id>（同值 handle，不改业务状态）。");
  }
  process.exit(pass === results.length ? 0 : 1);
}

main();
