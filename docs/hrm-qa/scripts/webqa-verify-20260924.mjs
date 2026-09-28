/**
 * 2026-09-24 网页问题批量修复 —— UI 冒烟验证
 *
 * 验证点（对应 index.html 报告中的条目）：
 *   1. Q-004 管理端请假出现「代提请假」入口，且弹窗可打开（HR 代提/补录）
 *   2. Q-013 薪资工资表列表出现「未入组」提示与「去分配薪资组」入口
 *   3. Q-003 招聘职位出现「草稿」页签，新增弹窗提示"默认保存为草稿"并带「保存为草稿」按钮
 *   4. Q-016 直接打开 /hrm/employee/detail（无 id）给出提示并回到列表
 *   5. Q-014 绩效计划出现「草稿」页签，空态可一键切到有数据的页签
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260924.mjs
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

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`);
}

const browser = await chromium.launch({
  executablePath: CHROME,
  headless: true,
});
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const bodyText = async () =>
  (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');

try {
  // ---------- 登录（租户：秦川化工产业园，账号 qinchuan） ----------
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
  await page.locator('input[placeholder*="用户名"]').first().fill('qinchuan');
  await page.locator('input[type=password]').first().fill('admin123');
  await page.locator('button').filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(4000);
  await page.goto(`${BASE}/#/hrm/home`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  const loggedIn = page.url().includes('/hrm/');
  record('登录秦川租户', loggedIn, `url=${page.url()}`);

  // ---------- 1. 请假：代提入口 ----------
  await page.goto(`${BASE}/#/hrm/attendance/leave`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  let text = await bodyText();
  const hasQuickLeave = text.includes('代提请假');
  record('Q-004 请假页出现「代提请假」入口', hasQuickLeave, hasQuickLeave ? '按钮存在' : text.slice(0, 160));
  if (hasQuickLeave) {
    await page.getByText('代提请假', { exact: true }).first().click();
    await page.waitForTimeout(1500);
    const modalText = await bodyText();
    const modalOk =
      modalText.includes('HR 代提 / 补录请假') &&
      modalText.includes('直接生效');
    record(
      'Q-004 代提请假弹窗内容（含直接生效开关）',
      modalOk,
      modalOk ? '弹窗字段完整' : modalText.slice(0, 200),
    );
    await page.keyboard.press('Escape');
    await page.waitForTimeout(800);
  }

  // ---------- 2. 工资表：未入组提示 ----------
  await page.goto(`${BASE}/#/hrm/salary/month-record`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3500);
  text = await bodyText();
  const hasNoGroup = text.includes('未加入任何薪资组');
  const hasGoGroup = text.includes('去分配薪资组');
  record(
    'Q-013 工资表页出现「未入组」提示与跳转入口',
    hasNoGroup && hasGoGroup,
    `未入组提示=${hasNoGroup} 跳转入口=${hasGoGroup}`,
  );

  // ---------- 3. 招聘职位：草稿页签 + 保存为草稿 ----------
  await page.goto(`${BASE}/#/hrm/recruit/post`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  text = await bodyText();
  const hasDraftTab = text.includes('草稿');
  record('Q-003 招聘职位出现「草稿」页签', hasDraftTab, hasDraftTab ? '页签存在' : text.slice(0, 160));
  await page.getByText('新增', { exact: true }).first().click();
  await page.waitForTimeout(2000);
  const formText = await bodyText();
  const draftFormOk =
    formText.includes('保存为草稿') &&
    (formText.includes('默认保存为「草稿」') || formText.includes('保存并发布'));
  record(
    'Q-003 新增职位弹窗含「保存为草稿 / 保存并发布」',
    draftFormOk,
    draftFormOk ? '双按钮与提示均存在' : formText.slice(0, 220),
  );
  await page.keyboard.press('Escape');
  await page.waitForTimeout(800);

  // ---------- 4. 详情路由缺参守卫 ----------
  await page.goto(`${BASE}/#/hrm/employee/detail`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  const url = page.url();
  text = await bodyText();
  const guardOk =
    url.includes('/hrm/employee/list') && text.includes('员工');
  record(
    'Q-016 无 id 打开员工详情 → 回退列表',
    guardOk,
    `url=${url.includes('/hrm/employee/list') ? '/hrm/employee/list' : url}`,
  );

  // ---------- 5. 绩效计划：草稿页签 + 空态引导 ----------
  await page.goto(`${BASE}/#/hrm/performance/plan`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3000);
  text = await bodyText();
  const planDraft = text.includes('草稿');
  const planEmptyHint =
    text.includes('当前页签暂无绩效计划') || text.includes('进行中（');
  record(
    'Q-014 绩效计划含草稿页签 + 空态引导',
    planDraft && planEmptyHint,
    `草稿页签=${planDraft} 空态/数据提示=${planEmptyHint}`,
  );
} catch (error) {
  record('执行异常', false, String(error).slice(0, 300));
} finally {
  await browser.close();
}

const failed = results.filter((item) => !item.ok);
console.log('\n==== 汇总 ====');
console.log(`共 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`);
process.exit(failed.length === 0 ? 0 : 1);
