/**
 * HRM full menu walk + expanded check matrix (Phase 1)
 * Usage: node docs/hrm-qa/scripts/run-phase1-full.mjs
 */
import fs from "fs";
import path from "path";
import { fileURLToPath, pathToFileURL } from "url";
import { createRequire } from "module";

const require = createRequire(path.join(process.cwd(), "txgy-ui-admin-vben/package.json"));
const { chromium } = require("playwright");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../../..");
const OUT = path.join(ROOT, "docs/hrm-qa/evidence");
fs.mkdirSync(OUT, { recursive: true });

const BASE = "http://localhost:5173";
const RESULTS = [];

const MENUS = [
  { group: "工作台", name: "HR 工作台", path: "/hrm/home" },
  { group: "工作台", name: "团队工作台", path: "/hrm/team-home" },
  { group: "组织", name: "组织管理", path: "/hrm/dept" },
  { group: "组织", name: "部门编制", path: "/hrm/headcount" },
  { group: "组织", name: "用工主体信息", path: "/hrm/company" },
  { group: "招聘管理", name: "招聘需求", path: "/hrm/recruit/demand" },
  { group: "招聘管理", name: "招聘沟通", path: "/hrm/recruit/chat" },
  { group: "招聘管理", name: "职位类型", path: "/hrm/recruit/post-type" },
  { group: "招聘管理", name: "候选人", path: "/hrm/recruit/candidate" },
  { group: "招聘管理", name: "招聘职位", path: "/hrm/recruit/post" },
  { group: "招聘设置", name: "招聘渠道", path: "/hrm/recruit/settings/channel" },
  { group: "招聘设置", name: "淘汰原因", path: "/hrm/recruit/settings/eliminate-reason" },
  { group: "招聘设置", name: "招聘企业", path: "/hrm/recruit/settings/company" },
  { group: "员工管理", name: "员工档案", path: "/hrm/employee/list" },
  { group: "员工管理", name: "员工设置", path: "/hrm/employee/config" },
  { group: "员工管理", name: "用工类型", path: "/hrm/employee/employment-type" },
  { group: "员工管理", name: "黑名单", path: "/hrm/employee/blacklist" },
  { group: "员工管理", name: "入转调离审批", path: "/hrm/employee/approval" },
  { group: "员工管理", name: "工号规则", path: "/hrm/employee/no-rule" },
  { group: "合同管理", name: "合同模板", path: "/hrm/contract/template" },
  { group: "合同管理", name: "签订记录", path: "/hrm/contract/sign-record" },
  { group: "合同管理", name: "到期预警", path: "/hrm/contract/expire" },
  { group: "资格证件", name: "证件台账", path: "/hrm/certificate/ledger" },
  { group: "资格证件", name: "证件类型", path: "/hrm/certificate/type" },
  { group: "资格证件", name: "到期预警", path: "/hrm/certificate/warning" },
  { group: "安全与政策", name: "安全教育与体检", path: "/hrm/safety" },
  { group: "安全与政策", name: "政策制度", path: "/hrm/policy" },
  { group: "考勤管理", name: "打卡记录", path: "/hrm/attendance/clock" },
  { group: "考勤管理", name: "请假", path: "/hrm/attendance/leave" },
  { group: "考勤管理", name: "月度考勤", path: "/hrm/attendance/month" },
  { group: "考勤设置", name: "考勤组", path: "/hrm/attendance/config/group" },
  { group: "考勤设置", name: "节假日", path: "/hrm/attendance/config/holiday" },
  { group: "社保管理", name: "社保缴纳", path: "/hrm/insurance/month-record" },
  { group: "社保管理", name: "社保方案", path: "/hrm/insurance/config/scheme" },
  { group: "薪资管理", name: "考勤计薪规则", path: "/hrm/salary/config/attendance-rule" },
  { group: "薪资管理", name: "工资表", path: "/hrm/salary/month-record" },
  { group: "薪资管理", name: "计件工资", path: "/hrm/salary/piece-record" },
  { group: "薪资管理", name: "历史工资", path: "/hrm/salary/history" },
  { group: "薪资管理", name: "薪资档案", path: "/hrm/salary/employee-info" },
  { group: "薪资管理", name: "工资条/发放记录", path: "/hrm/salary/slip" },
  { group: "薪资设置", name: "薪资组", path: "/hrm/salary/config/group" },
  { group: "薪资设置", name: "计税规则", path: "/hrm/salary/config/tax-rule" },
  { group: "薪资设置", name: "计薪设置", path: "/hrm/salary/config/config" },
  { group: "薪资设置", name: "工资表设置", path: "/hrm/salary/config/option" },
  { group: "薪资设置", name: "调薪模板", path: "/hrm/salary/config/change-template" },
  { group: "薪资设置", name: "比例上限(若有)", path: "/hrm/salary/config/rate-limit" },
  { group: "绩效管理", name: "KPI 考核", path: "/hrm/performance/plan" },
  { group: "绩效管理", name: "绩效档案", path: "/hrm/performance/assessment" },
  { group: "绩效管理", name: "考核指标模板", path: "/hrm/performance/config/assessment-template" },
  { group: "绩效管理", name: "考核结果设置", path: "/hrm/performance/config/result-template" },
  { group: "员工端", name: "个人工作台", path: "/hrm/portal/home" },
  { group: "员工端", name: "我的档案", path: "/hrm/portal/employee" },
  { group: "员工端", name: "我的社保", path: "/hrm/portal/insurance" },
  { group: "员工端", name: "我的工资条", path: "/hrm/portal/salary/slip" },
  { group: "员工端", name: "我的考勤", path: "/hrm/portal/attendance/report" },
  { group: "员工端", name: "绩效任务", path: "/hrm/portal/performance/assessment" },
  { group: "员工端", name: "绩效历史", path: "/hrm/portal/performance/history" },
  { group: "员工端", name: "招聘应聘(admin路径)", path: "/hrm/portal/recruit" },
  { group: "门户", name: "招聘门户", path: "/portal/recruit" },
  { group: "旧路径探测", name: "考勤计薪规则config", path: "/hrm/salary/attendance-rule" },
];

const FLUSH = path.join(OUT, "phase1-full.json");
function rec(partial) {
  const row = {
    id: `T-${String(RESULTS.length + 1).padStart(3, "0")}`,
    phase: "第一阶段",
    ...partial,
    ts: new Date().toISOString(),
  };
  RESULTS.push(row);
  console.log(`[${row.id}] ${row.menu || row.path} · ${row.feature || ""} · ${row.status}`);
  if (RESULTS.length % 5 === 0) {
    fs.writeFileSync(FLUSH, JSON.stringify({ count: RESULTS.length, RESULTS }, null, 2));
  }
  return row;
}

async function bodyText(page) {
  return (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");
}

async function login(page, user = "qinchuan") {
  await page.goto(`${BASE}/#/auth/login`, { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.waitForTimeout(1200);
  const ent = page.locator("button").filter({ hasText: /^企业登录$/ }).first();
  if (await ent.count()) await ent.click().catch(() => {});
  await page.waitForTimeout(300);

  const cb = page.locator("[role=combobox]").first();
  await cb.click();
  await page.waitForTimeout(250);
  await page.keyboard.type("秦川");
  await page.waitForTimeout(450);
  const opt = page.locator("[role=option]").filter({ hasText: "秦川化工产业园发展有限公司" }).first();
  if (!(await opt.count())) {
    await page.screenshot({ path: path.join(OUT, "login-no-option.png"), fullPage: true });
    throw new Error("tenant option not found");
  }
  await opt.click();
  await page.waitForTimeout(350);

  await page.locator('input[placeholder*="用户名"], input[placeholder*="工号"]').first().fill(user);
  await page.locator("input[type=password]").first().fill("admin123");
  const loginBtn = page.locator("button").filter({ hasText: /^登录$/ }).first();
  if (!(await loginBtn.count())) {
    await page.screenshot({ path: path.join(OUT, "login-no-btn.png"), fullPage: true });
    throw new Error("login button not found");
  }
  await loginBtn.click();
  await page.waitForTimeout(3200);
  const body = await bodyText(page);
  const url = page.url();
  const t = await page.title();
  const ok = !/auth\/login/.test(url);
  return { url, title: t, ok, hint: body.slice(0, 180) };
}

async function probePage(page, menu) {
  const issues = [];
  const features = [];
  try {
    await page.goto(`${BASE}/#${menu.path}`, { waitUntil: "domcontentloaded", timeout: 20000 });
    await page.waitForTimeout(900);
    const text = await bodyText(page);
    const url = page.url();
    const title = await page.title();
    const notFound = /404|Not Found|页面不存在|无法找到/i.test(text) || text.length < 20;
    const blank = text.trim().length < 5;
    const btnTexts = await page.locator("button").allInnerTexts().catch(() => []);
    const buttons = btnTexts.map((s) => s.trim()).filter(Boolean);
    const hasTable = (await page.locator(".vxe-table, table, .el-table").count()) > 0;
    const hasForm = (await page.locator("form, .el-form, .vxe-form").count()) > 0;
    const inputs = await page.locator("input:visible, textarea:visible").count();
    const errorMsg = text.match(/(失败|错误|异常|Error|Exception|500|404)[^\n]{0,40}/)?.[0] || null;

    // matrix signals
    const matrix = {
      create: buttons.some((b) => /新建|新增|添加|创建|提报|发起/.test(b)),
      edit: buttons.some((b) => /编辑|修改/.test(b)) || text.includes("编辑"),
      delete: buttons.some((b) => /删除/.test(b)) || text.includes("删除"),
      query: inputs > 0 || buttons.some((b) => /查询|搜索|筛选/.test(b)),
      reset: buttons.some((b) => /重置|清空/.test(b)),
      import: buttons.some((b) => /导入|上传/.test(b)),
      export: buttons.some((b) => /导出|下载/.test(b)),
      approve: buttons.some((b) => /审批|通过|驳回/.test(b)) || /审批|通过|驳回/.test(text.slice(0, 800)),
      batch: buttons.some((b) => /批量/.test(b)),
      enable: buttons.some((b) => /启用|停用|开启|关闭/.test(b)),
    };

    // open create dialog if present (non-destructive: just open+validate+close)
    let createDialog = null;
    if (matrix.create) {
      const createBtn = page.locator("button", { hasText: /新建|新增|添加|创建|提报|发起/ }).first();
      try {
        await createBtn.click({ timeout: 3000 });
        await page.waitForTimeout(600);
        const dtext = await bodyText(page);
        const required = (await page.locator(".is-required, .el-form-item--required").count()) > 0;
        const submit = page.locator("button", { hasText: /确\s*定|保\s*存|提\s*交|确\s*认/ }).last();
        let emptySubmit = null;
        if (await submit.count()) {
          await submit.click().catch(() => {});
          await page.waitForTimeout(400);
          const after = await bodyText(page);
          emptySubmit = {
            stillOpen: (await page.locator(".el-dialog, .vxe-modal, [role=dialog]").count()) > 0,
            hint: after.match(/请输入|请选择|必填|不能为空|校验/)?.[0] || null,
            afterSnippet: after.slice(0, 200),
          };
        }
        createDialog = { opened: true, required, emptySubmit, snippet: dtext.slice(0, 180) };
        await page.keyboard.press("Escape");
        await page.waitForTimeout(300);
        await page.keyboard.press("Escape");
      } catch (e) {
        createDialog = { opened: false, error: String(e).slice(0, 120) };
      }
    }

    // empty query / reset probe (type nonsense into first visible text input if query)
    let queryProbe = null;
    if (matrix.query) {
      try {
        const firstText = page.locator("input[type=text]:visible, input:not([type]):visible").first();
        if (await firstText.count()) {
          await firstText.fill("不存在的数据XYZ9527");
          const qbtn = page.locator("button", { hasText: /查询|搜索|筛选/ }).first();
          if (await qbtn.count()) await qbtn.click();
          await page.waitForTimeout(700);
          const qtext = await bodyText(page);
          queryProbe = {
            emptyHint: /暂无数据|无数据|没有找到|空/.test(qtext),
            snippet: qtext.slice(0, 160),
          };
          const rbtn = page.locator("button", { hasText: /重置|清空/ }).first();
          if (await rbtn.count()) await rbtn.click();
          await page.waitForTimeout(400);
        }
      } catch (e) {
        queryProbe = { error: String(e).slice(0, 100) };
      }
    }

    if (blank) {
      issues.push({ severity: "致命", kind: "白屏", detail: `${menu.path} body 为空` });
      rec({ menu: `${menu.group}/${menu.name}`, path: menu.path, feature: "页面加载", status: "异常", detail: "白屏", issues });
      return;
    }
    if (notFound || /404/.test(title + text.slice(0, 80))) {
      issues.push({ severity: "致命", kind: "路由404", detail: `${menu.path} → ${title}` });
      rec({ menu: `${menu.group}/${menu.name}`, path: menu.path, feature: "页面加载", status: "失败", detail: `404/未找到 title=${title}`, issues, matrix, buttons: buttons.slice(0, 20) });
      return;
    }
    if (errorMsg && /500|Exception|Error/.test(errorMsg)) {
      issues.push({ severity: "严重", kind: "接口/渲染错误", detail: errorMsg });
    }

    features.push({ feature: "页面加载", status: "通过", detail: `title=${title}; interactive=${buttons.length + inputs}` });
    features.push({ feature: "检查矩阵", status: matrix.create || hasTable || hasForm ? "通过" : "异常", detail: JSON.stringify(matrix), matrix });
    if (createDialog) features.push({ feature: "新增弹窗/必填", status: createDialog.opened ? (createDialog.emptySubmit?.hint || createDialog.emptySubmit?.stillOpen ? "通过" : "一般") : "异常", detail: JSON.stringify(createDialog).slice(0, 300) });
    if (queryProbe) features.push({ feature: "空结果查询", status: queryProbe.emptyHint === false ? "一般" : "通过", detail: JSON.stringify(queryProbe).slice(0, 240) });
    if (matrix.export) features.push({ feature: "导出入口", status: "通过", detail: "存在导出按钮" });
    else features.push({ feature: "导出入口", status: "未覆盖", detail: "未发现导出按钮" });
    if (matrix.import) features.push({ feature: "导入入口", status: "通过", detail: "存在导入/上传" });
    if (matrix.approve) features.push({ feature: "审批相关", status: "通过", detail: "存在审批/通过/驳回语义" });

    rec({
      menu: `${menu.group}/${menu.name}`,
      path: menu.path,
      feature: "综合巡检",
      status: issues.some((i) => i.severity === "致命") ? "异常" : issues.length ? "失败" : "通过",
      detail: `${title} | buttons=${buttons.slice(0, 12).join("/")}`,
      matrix,
      buttons: buttons.slice(0, 25),
      bodyHint: text.slice(0, 280),
      issues,
      features,
    });

    // also record per-feature rows for checklist granularity
    for (const f of features) {
      rec({ menu: `${menu.group}/${menu.name}`, path: menu.path, feature: f.feature, status: f.status, detail: f.detail, phase: "第一阶段" });
    }
  } catch (e) {
    rec({ menu: `${menu.group}/${menu.name}`, path: menu.path, feature: "页面加载", status: "异常", detail: String(e).slice(0, 200), issues: [{ severity: "严重", kind: "导航异常", detail: String(e) }] });
  }
}

const CHROME =
  process.env.PLAYWRIGHT_CHROMIUM ||
  "C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe";
const browser = await chromium.launch({ headless: true, executablePath: CHROME });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: "zh-CN" });
const page = await context.newPage();

console.log("login as qinchuan ...");
const auth = await login(page, "qinchuan");
rec({ menu: "登录", path: "/auth/login", feature: "超管登录", status: auth.ok ? "通过" : "失败", detail: JSON.stringify(auth) });
if (!auth.ok) {
  fs.writeFileSync(path.join(OUT, "phase1-full.json"), JSON.stringify({ auth, RESULTS }, null, 2));
  await browser.close();
  process.exit(1);
}

for (const menu of MENUS) {
  await probePage(page, menu);
}

// permission smoke: logout and login as HR
console.log("switch to HR ...");
await page.goto(`${BASE}/#/auth/login`, { waitUntil: "domcontentloaded" }).catch(() => {});
// force re-login
await context.clearCookies();
const page2 = await context.newPage();
const authHr = await login(page2, "qinchuan_hr");
rec({ menu: "登录", path: "/auth/login", feature: "HR登录", status: authHr.ok ? "通过" : "失败", detail: JSON.stringify(authHr) });
if (authHr.ok) {
  for (const p of ["/hrm/home", "/hrm/employee/list", "/hrm/salary/month-record", "/hrm/portal/home", "/hrm/performance/plan"]) {
    await page2.goto(`${BASE}/#${p}`, { waitUntil: "domcontentloaded", timeout: 15000 });
    await page2.waitForTimeout(700);
    const t = await bodyText(page2);
    rec({
      menu: "权限-HR",
      path: p,
      feature: "HR可见性",
      status: /404|无权限|暂无权限|Not Found/i.test(t) ? "失败" : "通过",
      detail: t.slice(0, 160),
    });
  }
}

fs.writeFileSync(FLUSH, JSON.stringify({ auth, authHr, count: RESULTS.length, RESULTS }, null, 2));
console.log(`done, rows=${RESULTS.length}`);
await browser.close();
