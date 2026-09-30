/**
 * Phase 2 E2E + targeted regression probes
 */
import fs from "fs";
import path from "path";
import { createRequire } from "module";
import { fileURLToPath } from "url";

const require = createRequire(path.join(process.cwd(), "txgy-ui-admin-vben/package.json"));
const { chromium } = require("playwright");
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../../..");
const OUT = path.join(ROOT, "docs/hrm-qa/evidence");
fs.mkdirSync(OUT, { recursive: true });
const BASE = "http://localhost:5173";
const RESULTS = [];
const CHROME =
  "C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe";

function rec(row) {
  const r = { id: `E-${String(RESULTS.length + 1).padStart(3, "0")}`, ts: new Date().toISOString(), ...row };
  RESULTS.push(r);
  console.log(`[${r.id}] ${r.phase} ${r.step} · ${r.status}`);
  fs.writeFileSync(path.join(OUT, "phase2-e2e.json"), JSON.stringify({ count: RESULTS.length, RESULTS }, null, 2));
  return r;
}

async function bodyText(page) {
  return (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");
}

async function login(page, user = "qinchuan") {
  await page.goto(`${BASE}/#/auth/login`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForTimeout(1200);
  const cb = page.locator("[role=combobox]").first();
  await cb.click();
  await page.waitForTimeout(250);
  await page.keyboard.type("秦川");
  await page.waitForTimeout(400);
  await page.locator("[role=option]").filter({ hasText: "秦川化工产业园发展有限公司" }).first().click();
  await page.waitForTimeout(300);
  await page.locator('input[placeholder*="用户名"], input[placeholder*="工号"]').first().fill(user);
  await page.locator("input[type=password]").first().fill("admin123");
  await page.locator("button").filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(3000);
  return !/auth\/login/.test(page.url());
}

async function go(page, p) {
  await page.goto(`${BASE}/#${p}`, { waitUntil: "domcontentloaded", timeout: 20000 });
  await page.waitForTimeout(800);
  return bodyText(page);
}

async function clickText(page, re, timeout = 3000) {
  const loc = page.locator("button, a, [role=button], .vxe-button").filter({ hasText: re }).first();
  if (await loc.count()) {
    await loc.click({ timeout });
    return true;
  }
  return false;
}

const browser = await chromium.launch({ headless: true, executablePath: CHROME });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: "zh-CN" });
const page = await ctx.newPage();

const ok = await login(page, "qinchuan");
rec({ phase: "环境", step: "超管登录", status: ok ? "通过" : "失败", detail: page.url() });
if (!ok) {
  await browser.close();
  process.exit(1);
}

// ===== Path regression (from Q last cycle + this cycle) =====
const paths = [
  ["主线B", "考勤计薪-菜单config路径", "/hrm/salary/config/attendance-rule"],
  ["主线B", "考勤计薪-旧正确路径", "/hrm/salary/attendance-rule"],
  ["主线B", "比例上限-menu显示路径", "/hrm/salary/config/rate-limit"],
  ["主线B", "比例上限-可能真实路径", "/hrm/salary/rate-limit"],
  ["主线A", "招聘门户admin路径", "/hrm/portal/recruit"],
  ["主线A", "招聘门户公开路径", "/portal/recruit"],
  ["主线B", "请假管理", "/hrm/attendance/leave"],
  ["主线A", "招聘职位", "/hrm/recruit/post"],
  ["主线B", "KPI考核", "/hrm/performance/plan"],
  ["主线B", "考勤组", "/hrm/attendance/config/group"],
  ["主线B", "月度工资表", "/hrm/salary/month-record"],
];
for (const [phase, step, p] of paths) {
  const t = await go(page, p);
  const bad = /404|Not Found|页面不存在/.test(t.slice(0, 120)) || t.length < 30;
  rec({
    phase,
    step,
    path: p,
    status: bad ? "失败" : "通过",
    detail: bad ? "404/空白" : t.slice(0, 140),
    severity: bad ? "致命" : null,
  });
}

// ===== Targeted: Leave create entry =====
{
  const t = await go(page, "/hrm/attendance/leave");
  const buttons = await page.locator("button").allInnerTexts();
  const hasCreate = buttons.some((b) => /新建|新增|添加|发起|申请/.test(b));
  rec({
    phase: "主线B",
    step: "请假-管理端新增入口",
    status: hasCreate ? "通过" : "失败",
    detail: `buttons=${buttons.map((x) => x.trim()).filter(Boolean).slice(0, 15).join("/")}`,
    severity: hasCreate ? null : "严重",
    code: "frontend attendance/leave/index.vue",
  });
}

// ===== Targeted: Post draft state =====
{
  const t = await go(page, "/hrm/recruit/post");
  await clickText(page, /新建|新增|发布职位|创建/);
  await page.waitForTimeout(700);
  const dtext = await bodyText(page);
  const hasDraft = /草稿/.test(dtext);
  const saysPublish = /保存后直接发布|直接发布|发布到招聘/.test(dtext);
  rec({
    phase: "主线A",
    step: "职位-草稿态",
    status: hasDraft ? "通过" : "失败",
    detail: hasDraft ? "存在草稿语义" : `无草稿；弹窗提示=${saysPublish ? "保存即发布" : "未见发布说明"} snippet=${dtext.slice(dtext.search(/新建|发布|保存/), dtext.search(/新建|发布|保存/) + 120)}`,
    severity: hasDraft ? null : "严重",
    code: "HrmRecruitPostServiceImpl.canChangeStatus / views/hrm/recruit/post/modules/form.vue",
  });
  await page.keyboard.press("Escape");
  await page.waitForTimeout(300);
}

// ===== Targeted: KPI badge vs list =====
{
  const t = await go(page, "/hrm/performance/plan");
  // capture tab/badge counts
  const badges = await page.locator(".el-badge__content, .vxe-badge, [class*=badge], [class*=count]").allInnerTexts().catch(() => []);
  const body = await bodyText(page);
  rec({
    phase: "主线B",
    step: "KPI-角标与列表一致性线索",
    status: "部分",
    detail: `badges=${JSON.stringify(badges.slice(0, 12))}; bodyHead=${body.slice(0, 200)}`,
    code: "views/hrm/performance/plan/index.vue",
  });
}

// ===== Targeted: Attendance group shift form =====
{
  await go(page, "/hrm/attendance/config/group");
  if (await clickText(page, /新建|新增/)) {
    await page.waitForTimeout(600);
    // try open shift config
    await clickText(page, /班次|打卡时段|添加班次/);
    await page.waitForTimeout(500);
    const dtext = await bodyText(page);
    rec({
      phase: "主线B",
      step: "考勤组-班次/打卡时段展示",
      status: /异常|undefined|NaN|\[object/.test(dtext) ? "失败" : "部分",
      detail: dtext.slice(0, 250),
      severity: /undefined|NaN|\[object/.test(dtext) ? "严重" : "一般",
      code: "views/hrm/attendance/config/group/modules/shift-form.vue",
    });
    await page.keyboard.press("Escape");
    await page.keyboard.press("Escape");
  }
}

// ===== Targeted: Salary readiness (未入组/无档案) =====
{
  const t = await go(page, "/hrm/salary/month-record");
  const hit = t.match(/未入[^\s]{0,8}|无[^\s]{0,6}档案|未配置|就绪|readiness[^\s]*/gi) || [];
  rec({
    phase: "主线B",
    step: "工资表-核算就绪提示",
    status: hit.length ? "通过" : "部分",
    detail: `hints=${JSON.stringify(hit.slice(0, 8))}; body=${t.slice(0, 220)}`,
  });
}

// ===== Targeted: Employee list actions (转正/调岗/离职) =====
{
  const t = await go(page, "/hrm/employee/list");
  // open first row more menu if any
  const more = page.locator("button, a, [role=button]").filter({ hasText: /更多|操作/ }).first();
  if (await more.count()) {
    await more.click().catch(() => {});
    await page.waitForTimeout(400);
  }
  const t2 = await bodyText(page);
  const has = {
    regular: /转正/.test(t2),
    transfer: /调岗|调整部门|晋升|降级/.test(t2),
    quit: /离职/.test(t2),
  };
  rec({
    phase: "主线B",
    step: "员工-转正/调岗/离职入口",
    status: has.regular && has.transfer && has.quit ? "通过" : "部分",
    detail: JSON.stringify(has) + " " + t2.slice(0, 180),
  });
  await page.keyboard.press("Escape");
}

// ===== Mainline A: demand list ops =====
{
  const t = await go(page, "/hrm/recruit/demand");
  const buttons = (await page.locator("button").allInnerTexts()).map((x) => x.trim()).filter(Boolean);
  rec({
    phase: "主线A",
    step: "需求-操作完备性",
    status: /修改|取消|删除/.test(buttons.join("/")) ? "通过" : "部分",
    detail: `buttons=${buttons.slice(0, 20).join("/")}`,
  });
}

// ===== Mainline A: candidate funnel / offer =====
{
  const t = await go(page, "/hrm/recruit/candidate");
  const hasFunnel = /漏斗|新|初选|面试|Offer|入职|淘汰/.test(t);
  const buttons = (await page.locator("button").allInnerTexts()).map((x) => x.trim()).filter(Boolean);
  rec({
    phase: "主线A",
    step: "候选人-漏斗与Offer操作",
    status: hasFunnel ? "通过" : "失败",
    detail: `funnel=${hasFunnel}; buttons=${buttons.slice(0, 18).join("/")}`,
    severity: hasFunnel ? null : "严重",
  });
}

// ===== Mainline B: contract / onboarding linkage =====
{
  await go(page, "/hrm/contract/sign-record");
  const t1 = await bodyText(page);
  await go(page, "/hrm/employee/list");
  const t2 = await bodyText(page);
  rec({
    phase: "衔接",
    step: "入职后合同签订记录/员工档案衔接线索",
    status: "部分",
    detail: `signRecord=${t1.slice(0, 120)} | employee=${t2.slice(0, 120)}`,
  });
}

// ===== Exception: empty submit on policy =====
{
  await go(page, "/hrm/policy");
  await clickText(page, /新建|新增/);
  await page.waitForTimeout(500);
  await clickText(page, /确\s*定|保\s*存|提\s*交/);
  await page.waitForTimeout(400);
  const t = await bodyText(page);
  rec({
    phase: "异常路径",
    step: "政策-空表单提交校验",
    status: /请输入|请选择|必填|不能为空/.test(t) ? "通过" : "失败",
    detail: t.slice(0, 180),
    severity: /请输入|请选择|必填|不能为空/.test(t) ? null : "一般",
  });
  await page.keyboard.press("Escape");
}

// ===== Exception: illegal navigation (detail without id) =====
for (const p of ["/hrm/employee/detail", "/hrm/recruit/candidate/detail", "/hrm/salary/month-record/detail"]) {
  const t = await go(page, p);
  const bad = /404|异常|错误|Error/.test(t.slice(0, 150));
  rec({
    phase: "异常路径",
    step: `非法详情路由 ${p}`,
    status: bad ? "失败" : "通过",
    detail: t.slice(0, 140),
    severity: bad ? "一般" : null,
  });
}

// ===== Permission: HR vs admin menu for salary compute =====
{
  const page2 = await ctx.newPage();
  // new context-like: clear storage by re-login in new page same context may share token; use new context
}
const ctx2 = await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: "zh-CN" });
const pageHr = await ctx2.newPage();
const okHr = await login(pageHr, "qinchuan_hr");
rec({ phase: "权限", step: "HR登录", status: okHr ? "通过" : "失败", detail: pageHr.url() });
if (okHr) {
  for (const p of [
    "/hrm/home",
    "/hrm/employee/list",
    "/hrm/salary/month-record",
    "/hrm/recruit/candidate",
    "/hrm/portal/home",
    "/hrm/performance/plan",
  ]) {
    const t = await go(pageHr, p);
    const denied = /无权限|暂无权限|403|404/.test(t.slice(0, 150));
    rec({
      phase: "权限",
      step: `HR访问 ${p}`,
      status: denied ? "失败" : "通过",
      detail: denied ? t.slice(0, 120) : "可见",
      severity: denied ? "严重" : null,
    });
  }
}

fs.writeFileSync(path.join(OUT, "phase2-e2e.json"), JSON.stringify({ count: RESULTS.length, RESULTS }, null, 2));
console.log("done", RESULTS.length);
await browser.close();
