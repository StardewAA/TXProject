/* 投标流程跟踪 → 关联公告（本站+外站合并）验收
 * 验证点：
 *  A. 行的项目编号（招标项目编号）能查到**本站公告**（此前只查外站 → 搜不到）
 *  B. 行标题/「门户详情」点击 → 本站走 /portal/bidding/internal-detail/:id
 *  C. 无公告行 → 诚实空态文案（本站+外站均已查询）
 * 用法：cd txgy-ui-admin-vben && node e2e/cert/noticelink.spec.cjs
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

  await page.goto(BASE + '/auth/login', { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForSelector('input[name="username"]');
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin123');
  let clicked = false;
  for (const c of await page.locator('button:visible').all()) {
    if (((await c.textContent()) || '').trim() === '登录') { await c.click(); clicked = true; break; }
  }
  if (!clicked) throw new Error('未找到登录按钮');
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), { timeout: 90000 });
  await page.waitForTimeout(1500);

  await page.goto(BASE + '/bidding/processTrack', { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForSelector('.ant-layout, .ant-table', { timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(2000);

  // ===== A. 搜索「最严格闭环-59900」→ 打开关联公告 → 本站公告可见 =====
  const kw = page.getByPlaceholder('搜索公司 / 项目名称 / 项目编号 / 招标人 / 负责人');
  await kw.fill('最严格闭环-59900');
  await kw.press('Enter');
  await page.waitForTimeout(2500);
  const row = page.locator('.ant-table-tbody tr', { hasText: '最严格闭环-59900' }).first();
  rec('A0 搜索命中流程行', (await row.count()) > 0, 'keyword=最严格闭环-59900');
  if ((await row.count()) > 0) {
    await row.locator('a.cd-link', { hasText: '关联公告' }).first().click();
    const modal = page.locator('.ant-modal:visible').first();
    await modal.waitFor({ timeout: 20000 });
    await page.waitForFunction((t) => (document.body.innerText || '').includes(t), '最严格闭环公告-59900', { timeout: 20000 }).catch(() => {});
    const body = await page.locator('body').innerText();
    rec('A1 合并列表命中本站公告', body.includes('最严格闭环公告-59900'), '期望行标题可见');
    rec('A2 本站来源标记', body.includes('本站'), '来源列出现「本站」');
    rec('A3 查询维度为项目编号 + 编号展示', body.includes('查询维度：项目编号') && body.includes('I610100202600000k001'), 'tip 行含维度与编号');
    rec('A4 提示行注明两源合并', body.includes('本站 + 外站合并展示'), 'nl-tip 弱文案');
    const totalTxt = await modal.locator('.ant-pagination-total-text, .ant-table-pagination').allTextContents().catch(() => []);
    rec('A5 分页总数', totalTxt.some((t) => /共 1 条/.test(t)), JSON.stringify(totalTxt));
    await page.screenshot({ path: MY + '/shots/nl-merged-modal.png' });

    // ===== B. 点标题 → 新窗口 → 本站详情路由 =====
    const popupP = page.waitForEvent('popup', { timeout: 20000 }).catch(() => null);
    await modal.locator('a.nl-link', { hasText: '最严格闭环公告-59900' }).first().click();
    const popup = await popupP;
    let popupUrl = '';
    if (popup) { await popup.waitForLoadState('domcontentloaded').catch(() => {}); popupUrl = popup.url(); }
    rec('B1 标题点击打开新窗口', !!popup, 'popup=' + (popupUrl || '无'));
    rec('B2 本站公告走 internal-detail 路由', popupUrl.includes('/portal/bidding/internal-detail/2101082428225581057'), 'url=' + popupUrl);
  }

  // ===== C. 无公告行（0866-26D2SXQY0628，仅标段编号）→ 诚实空态 =====
  await page.goto(BASE + '/bidding/processTrack', { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForTimeout(1500);
  await kw.fill('0866-26D2SXQY0628');
  await kw.press('Enter');
  await page.waitForTimeout(2500);
  const row2 = page.locator('.ant-table-tbody tr', { hasText: '0866-26D2SXQY0628' }).first();
  if ((await row2.count()) > 0) {
    await row2.locator('a.cd-link', { hasText: '关联公告' }).first().click();
    const modal2 = page.locator('.ant-modal:visible').first();
    await modal2.waitFor({ timeout: 20000 });
    await page.waitForTimeout(2000);
    const body2 = await page.locator('body').innerText();
    rec('C1 无公告行给出诚实空态', body2.includes('暂无已发布关联公告（本站 + 外站均已查询）'), '空态文案');
    rec('C2 空态非「加载失败」', !body2.includes('加载失败'), '无错误提示');
    await page.screenshot({ path: MY + '/shots/nl-empty-modal.png' });
  } else {
    rec('C1 无公告行给出诚实空态', false, '搜索未命中行 0866-26D2SXQY0628');
  }

  await browser.close();
  const pass = results.filter((r) => r.ok).length;
  const fail = results.filter((r) => !r.ok).length;
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL');
  process.exit(fail ? 1 : 0);
})();
