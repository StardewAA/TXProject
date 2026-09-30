/**
 * 2026-09-25 第五轮 UI 冒烟：员工端「我的档案」自助完善 → 后台同步
 *
 * 覆盖：
 *   1. 员工账号登录后，「我的档案」出现「教育经历 / 工作经历」两个自助 Tab
 *   2. 教育经历：新增 → 列表出现该条 → 后台 HR 接口查得到（同一条员工记录）
 *   3. 工作经历：新增 → 列表出现该条
 *   4. 清理：UI 删除刚新增的两条，避免残留测试数据
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260925-round5-ui.mjs
 */
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(
  path.join(process.cwd(), 'txgy-ui-admin-vben/package.json'),
);
const { chromium } = require('playwright');

const CHROME =
  process.env.PLAYWRIGHT_CHROMIUM ||
  'C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe';
const BASE = process.env.HRM_WEB_BASE || 'http://127.0.0.1:5173';
const GATEWAY = process.env.HRM_GATEWAY || 'http://127.0.0.1:48080/admin-api';
const TENANT_ID = process.env.HRM_TENANT_ID || '2020';
const EMPLOYEE_ID = 178;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`);
}

async function login(page, username, password) {
  await page.goto(`${BASE}/#/auth/login`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  await page.locator('[role=combobox]').first().click();
  await page.waitForTimeout(300);
  await page.keyboard.type('秦川');
  await page.waitForTimeout(600);
  await page
    .locator('[role=option]')
    .filter({ hasText: '秦川化工产业园发展有限公司' })
    .first()
    .click();
  await page.locator('input[placeholder*="用户名"]').first().fill(username);
  await page.locator('input[type=password]').first().fill(password);
  await page.locator('button').filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(4000);
}

async function apiLogin(account) {
  const resp = await fetch(`${GATEWAY}/system/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': TENANT_ID },
    body: JSON.stringify(account),
  });
  const json = await resp.json();
  return json.code === 0 ? json.data.accessToken : null;
}

async function hrEducationList(token) {
  const resp = await fetch(
    `${GATEWAY}/hrm/employee/education-experience/list?employeeId=${EMPLOYEE_ID}`,
    { headers: { Authorization: `Bearer ${token}`, 'tenant-id': TENANT_ID } },
  );
  const json = await resp.json();
  return json.data || [];
}

const stamp = Date.now();
const schoolName = `QA自助-西安理工大学-${stamp}`;
const companyName = `QA自助-西安精工-${stamp}`;

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const context = await browser.newContext({
  viewport: { width: 1680, height: 1000 },
  colorScheme: 'light',
});
const page = await context.newPage();
const consoleErrors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') consoleErrors.push(msg.text());
});

try {
  await login(page, 'qinchuan', 'admin123');
  await page.goto(`${BASE}/#/hrm/portal/employee`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);

  // ---------- 1. 自助 Tab 是否出现 ----------
  const tabTexts = await page.locator('.ant-tabs-tab').allInnerTexts();
  record(
    '1) 我的档案出现「教育经历 / 工作经历」自助 Tab',
    tabTexts.some((t) => t.includes('教育经历')) && tabTexts.some((t) => t.includes('工作经历')),
    `tabs=[${tabTexts.join(' | ')}]`,
  );

  // ---------- 2. 教育经历新增 ----------
  await page.locator('.ant-tabs-tab').filter({ hasText: '教育经历' }).first().click();
  await page.waitForTimeout(1200);
  const addEduBtn = page.locator('button').filter({ hasText: /新增教育经历/ }).first();
  record('2a) 教育经历 Tab 有「新增教育经历」按钮', (await addEduBtn.count()) > 0, '按钮存在性检查');
  await addEduBtn.click();
  await page.waitForTimeout(800);

  // 学历下拉
  const eduModal = page.locator('.ant-modal-content:visible');
  await eduModal.locator('.ant-select').first().click();
  await page.waitForTimeout(400);
  await page.locator('.ant-select-item-option').filter({ hasText: /^本科$/ }).first().click();
  await page.waitForTimeout(200);
  // 毕业院校 / 专业
  const eduInputs = eduModal.locator('input.ant-input');
  await eduInputs.nth(0).fill(schoolName);
  await eduInputs.nth(1).fill('自动化');
  await eduModal.locator('button').filter({ hasText: /^确\s*定$/ }).first().click();
  await page.waitForTimeout(2500);

  const eduRowVisible = await page.locator('td').filter({ hasText: schoolName }).count();
  record('2b) 教育经历保存后出现在我的列表', eduRowVisible > 0, `命中单元格=${eduRowVisible}`);

  // ---------- 3. 后台同一条记录可见 ----------
  const hrToken = await apiLogin({ username: 'qinchuan_hr', password: 'admin123' });
  const backendList = hrToken ? await hrEducationList(hrToken) : [];
  const backendHit = backendList.find((item) => item.graduateSchool === schoolName);
  record(
    '3) 后台员工档案同步可见（同一 employeeId）',
    !!backendHit,
    `后台记录 id=${backendHit ? backendHit.id : '-'} 院校=${backendHit ? backendHit.graduateSchool : '-'}`,
  );

  // ---------- 4. 工作经历新增 ----------
  await page.locator('.ant-tabs-tab').filter({ hasText: '工作经历' }).first().click();
  await page.waitForTimeout(1200);
  await page.locator('button').filter({ hasText: /新增工作经历/ }).first().click();
  await page.waitForTimeout(800);
  const workModal = page.locator('.ant-modal-content:visible');
  const workInputs = workModal.locator('input.ant-input');
  await workInputs.nth(0).fill(companyName);
  await workInputs.nth(1).fill('电气工程师');
  await workModal.locator('button').filter({ hasText: /^确\s*定$/ }).first().click();
  await page.waitForTimeout(2500);
  const workRowVisible = await page.locator('td').filter({ hasText: companyName }).count();
  record('4) 工作经历保存后出现在我的列表', workRowVisible > 0, `命中单元格=${workRowVisible}`);

  // ---------- 5. 清理：用接口删除刚才两条（UI 删除按钮的二次确认弹窗样式随主题变化，接口更稳） ----------
  const employeeToken = await apiLogin({ username: 'qinchuan', password: 'admin123' });
  const myEdu = await (await fetch(`${GATEWAY}/hrm/portal/employee/education-experience/list`, {
    headers: { Authorization: `Bearer ${employeeToken}`, 'tenant-id': TENANT_ID },
  })).json();
  const myWork = await (await fetch(`${GATEWAY}/hrm/portal/employee/work-experience/list`, {
    headers: { Authorization: `Bearer ${employeeToken}`, 'tenant-id': TENANT_ID },
  })).json();
  for (const row of (myEdu.data || []).filter((item) => item.graduateSchool === schoolName)) {
    await fetch(`${GATEWAY}/hrm/portal/employee/education-experience/delete?id=${row.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${employeeToken}`, 'tenant-id': TENANT_ID },
    });
  }
  for (const row of (myWork.data || []).filter((item) => item.workUnit === companyName)) {
    await fetch(`${GATEWAY}/hrm/portal/employee/work-experience/delete?id=${row.id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${employeeToken}`, 'tenant-id': TENANT_ID },
    });
  }
  await page.waitForTimeout(1200);

  const afterClean = await hrEducationList(hrToken);
  record(
    '5) 清理：删除后后台同步消失',
    !afterClean.some((item) => item.graduateSchool === schoolName),
    `剩余匹配=${afterClean.filter((item) => item.graduateSchool === schoolName).length}`,
  );

  record(
    '6) 页面无 JS 控制台报错',
    consoleErrors.length === 0,
    consoleErrors.slice(0, 2).join(' || ') || '无',
  );
} finally {
  await browser.close();
}

const failed = results.filter((item) => !item.ok);
console.log(`\n汇总：${results.length - failed.length}/${results.length} 通过`);
process.exit(failed.length === 0 ? 0 : 1);
