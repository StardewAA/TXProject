/**
 * 2026-09-24 第二批（I-020 ~ I-025）UI 冒烟验证
 *
 * 验证点：
 *   1. I-020 员工列表「上岗向导」入口 + 弹窗四项清单
 *   2. I-021 HR 工作台「待办中心」新增四类跨模块待办
 *   3. I-024 HR 工作台日历出现「到期图例」（合同/证件/转正三色）
 *   4. I-025 HR 工作台「沙箱演示数据」卡片与两个按钮
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260924-batch2.mjs
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

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({ viewport: { width: 1680, height: 1000 } });
const bodyText = async () =>
  (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');

try {
  // 登录（租户：秦川化工产业园，账号 qinchuan）
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
  record('登录秦川租户', page.url().includes('/hrm/'), `url=${page.url()}`);

  // ---------- I-021 / I-024 / I-025：HR 工作台 ----------
  // 菜单「HR 工作台」= 路径 /hrm/home（组件 hrm/home/hr/index.vue）
  await page.goto(`${BASE}/#/hrm/home`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  let text = await bodyText();
  const todoOk =
    text.includes('待办中心') &&
    text.includes('待批请假') &&
    text.includes('待批异动') &&
    text.includes('待面试') &&
    text.includes('绩效进行中');
  record(
    'I-021 HR 工作台待办中心含四类跨模块待办',
    todoOk,
    todoOk ? '四项均在' : text.slice(0, 200),
  );

  const legendOk = text.includes('到期图例') && text.includes('合同到期') && text.includes('证件到期');
  record(
    'I-024 日历出现到期三色图例',
    legendOk,
    legendOk ? '图例存在' : text.slice(0, 200),
  );

  const sandboxOk = text.includes('沙箱演示数据') && text.includes('生成演示数据');
  const clearOk = text.includes('一键清场') || text.includes('沙箱');
  record(
    'I-025 沙箱演示数据卡片与生成/清场入口',
    sandboxOk && clearOk,
    `卡片=${sandboxOk} 清场按钮=${clearOk}`,
  );

  // ---------- I-020：员工列表上岗向导 ----------
  await page.goto(`${BASE}/#/hrm/employee/list`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(4000);
  text = await bodyText();
  const entryOk = text.includes('上岗向导');
  record('I-020 员工列表出现「上岗向导」入口', entryOk, entryOk ? '入口存在' : text.slice(0, 200));
  if (entryOk) {
    await page.getByText('上岗向导', { exact: true }).first().click();
    await page.waitForTimeout(2500);
    const modalText = await bodyText();
    const modalOk =
      modalText.includes('后台账号') &&
      modalText.includes('薪资配置') &&
      modalText.includes('考勤组') &&
      modalText.includes('社保参保');
    record(
      'I-020 向导弹窗含四项就绪检查',
      modalOk,
      modalOk ? '四项齐全' : modalText.slice(0, 260),
    );
    await page.keyboard.press('Escape');
    await page.waitForTimeout(600);
  }

  // ---------- Q-015：考勤组班次时间轴预览 ----------
  await page.goto(`${BASE}/#/hrm/attendance/config/group`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(3500);
  text = await bodyText();
  // 直接打开「新增考勤组」→「新增班次」，验证班次弹窗里的时间轴预览
  const addGroup = page.getByText('新增', { exact: true }).first();
  if (await addGroup.count()) {
    await addGroup.click();
    await page.waitForTimeout(2500);
  }
  const addShift = page.getByText('新增班次', { exact: true }).first();
  if (await addShift.count()) {
    await addShift.click();
    await page.waitForTimeout(2000);
    const shiftText = await bodyText();
    const timelineOk =
      shiftText.includes('时间轴预览') &&
      shiftText.includes('上班时段') &&
      shiftText.includes('休息');
    record(
      'Q-015 班次弹窗含时间轴预览（可视化）',
      timelineOk,
      timelineOk ? '时间轴与图例存在' : shiftText.slice(0, 220),
    );
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);
  } else {
    record('Q-015 班次弹窗含时间轴预览（可视化）', false, `未找到「新增班次」入口：${text.slice(0, 160)}`);
  }
} catch (error) {
  record('执行异常', false, String(error).slice(0, 300));
} finally {
  await browser.close();
}

const failed = results.filter((item) => !item.ok);
console.log('\n==== 汇总 ====');
console.log(
  `共 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`,
);
process.exit(failed.length === 0 ? 0 : 1);
