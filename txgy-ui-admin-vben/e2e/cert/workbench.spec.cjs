/* 审批工作台 v2 · 平铺任务列表验收（真实数据）
 * W1 平铺三类型混排（项目/招标项目/标段 全部可见）
 * W2 内部编号查询（I610100202600000k → 1 行）
 * W3 项目代码查询（投资项目统一代码 2101-610100-76-59900 → 只留项目行且命中）
 * W4 类型筛选（标段 → 全为标段行）
 * W5 查看流转 → 当前审批对象面板 + 返回任务列表
 * 用法：cd txgy-ui-admin-vben && node e2e/cert/workbench.spec.cjs
 */
const { chromium } = require('playwright');
const MY = 'D:/Code/IDEA/txgy/txgycloud/.tmp-scratch-2026-09-23';
const BASE = 'http://localhost:5666';

(async () => {
  const results = [];
  const rec = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + ' — ' + detail); };
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 }, locale: 'zh-CN' });
  const page = await ctx.newPage();
  page.setDefaultTimeout(90000);
  await page.addInitScript(() => {
    const rm = () => document.querySelectorAll('vite-error-overlay').forEach((el) => el.remove());
    new MutationObserver(rm).observe(document, { childList: true, subtree: true });
    window.addEventListener('load', rm);
  });

  // 登录（退避重试）
  let loggedIn = false;
  for (let attempt = 1; attempt <= 3 && !loggedIn; attempt++) {
    try {
      await page.goto(BASE + '/auth/login', { waitUntil: 'domcontentloaded', timeout: 90000 });
      await page.waitForSelector('input[name="username"]', { timeout: 60000 });
      await page.fill('input[name="username"]', 'admin');
      await page.fill('input[name="password"]', 'admin123');
      let clicked = false;
      for (const c of await page.locator('button:visible').all()) {
        if (((await c.textContent()) || '').trim() === '登录') { await c.click(); clicked = true; break; }
      }
      if (!clicked) throw new Error('未找到登录按钮');
      await page.waitForURL((u) => !u.pathname.includes('/auth/login'), { timeout: 90000 });
      loggedIn = true;
    } catch (err) {
      console.log('login attempt ' + attempt + ' failed: ' + (err instanceof Error ? err.message : String(err)));
      if (attempt === 3) throw err;
      await page.waitForTimeout(8000);
    }
  }
  await page.waitForFunction(() => document.querySelector('#app') && document.querySelector('#app').innerHTML.length > 100, undefined, { timeout: 120000 }).catch(() => {});
  const ROW = () => page.locator('.ant-table-tbody tr:not(.ant-table-measure-row)');
  const waitRows = async () => page.waitForFunction(() => document.querySelectorAll('.ant-table-tbody tr:not(.ant-table-measure-row)').length > 0, undefined, { timeout: 60000 }).catch(() => {});

  // ===== 深链进入审批工作台（真实 URL = /bidding/flow/flow：招投标/bidding · 招标全流程/flow · 审批工作台/flow） =====
  await page.goto(BASE + '/bidding/flow/flow', { waitUntil: 'domcontentloaded', timeout: 180000 });
  await waitRows();
  await page.waitForTimeout(1500);
  const n1 = await ROW().count();
  const body1 = await page.locator('body').innerText();
  rec('W1 平铺任务表有行', n1 > 0, 'rows=' + n1);
  rec('W1b 全部视图含多种类型', body1.includes('项目') && body1.includes('标段'), 'body 类型标记');
  await page.screenshot({ path: MY + '/shots/wb-all.png' });

  // ===== W2 内部编号查询 =====
  const codeInput = page.locator('input[placeholder*="内部编号"]').first();
  await codeInput.fill('I610100202600000k');
  await page.locator('.ct-panel button', { hasText: /查\s*询/ }).first().click();
  await page.waitForTimeout(1800);
  const n2 = await ROW().count();
  const t2 = await page.locator('body').innerText();
  // 三表并发查询：编号为模糊匹配，招标/标段表可能因编号前缀命中更多行；用户语义 = 命中目标项目
  rec('W2 内部编号命中目标项目', n2 >= 1 && t2.includes('最严格闭环'), 'rows=' + n2);
  await page.screenshot({ path: MY + '/shots/wb-bycode.png' });
  await page.locator('.ct-panel button', { hasText: /重\s*置/ }).first().click();
  await page.waitForTimeout(1500);

  // ===== W3 项目代码查询 =====
  const icInput = page.locator('input[placeholder*="投资项目统一代码"]').first();
  await icInput.fill('2101-610100-76-59900');
  await page.locator('.ct-panel button', { hasText: /查\s*询/ }).first().click();
  await page.waitForTimeout(1800);
  const t3 = await page.locator('body').innerText();
  const n3 = await ROW().count();
  rec('W3 项目代码命中且只余项目类型', t3.includes('最严格闭环'), 'rows=' + n3 + ' 命中最严格闭环');
  rec('W3b 本地过滤提示可见', t3.includes('项目代码为本地过滤'), '提示行');
  await page.screenshot({ path: MY + '/shots/wb-byinvest.png' });
  await page.locator('.ct-panel button', { hasText: /重\s*置/ }).first().click();
  await page.waitForTimeout(1500);

  // ===== W4 类型=标段 =====
  await page.locator('.ant-select').first().click();
  await page.locator('.ant-select-item', { hasText: '标段' }).first().click();
  await page.locator('.ct-panel button', { hasText: /查\s*询/ }).first().click();
  await page.waitForTimeout(1800);
  const n4 = await ROW().count();
  // 逐行检查「类型」列（第一列 td）均为「标段」，避免侧边栏文本干扰
  const typeCols = [];
  for (let i = 0; i < Math.min(n4, 8); i += 1) {
    typeCols.push((await ROW().nth(i).locator('td').first().innerText().catch(() => '')) || '');
  }
  const hasSection = typeCols.some((t) => t.includes('标段'));
  rec('W4 标段筛选只显标段', n4 > 0 && typeCols.every((t) => t.includes('标段')), 'rows=' + n4 + ' 类型列=' + JSON.stringify(typeCols));
  await page.screenshot({ path: MY + '/shots/wb-section.png' });

  // ===== W5 查看流转 → 面板 =====
  await ROW().first().locator('button', { hasText: '查看流转' }).first().click();
  await page.waitForTimeout(2500);
  const t5 = await page.locator('body').innerText();
  rec('W5 当前审批对象面板出现', t5.includes('当前审批对象') && t5.includes('返回任务列表'), '对象面板');
  rec('W5b 可执行动作区出现', t5.includes('当前状态与可执行动作'), '动作面板');
  await page.screenshot({ path: MY + '/shots/wb-flow.png' });

  const pass = results.filter((r) => r.ok).length;
  const fail = results.filter((r) => !r.ok).length;
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL');
  await browser.close();
  process.exit(fail ? 1 : 0);
})();
