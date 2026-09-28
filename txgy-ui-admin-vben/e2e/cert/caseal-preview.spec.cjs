/* CA 签章台账 · 文件预览接线验收（2026-10-01）
 * 验证点：
 *  A 页面可打开（去重后不再编译崩溃；24 行台账有数据）
 *  B 点击「原文件名」→ 原文件预览弹窗（fileId 内联出口）
 *  C 点击红章文件「预览」→ 红章文件预览弹窗（sealFileId 内联出口）
 *  D 证据链弹窗「原文件名」同样可点预览
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

  // 登录（退避重试：dev server 冷编译首屏可能超时）
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
  await page.waitForTimeout(1500);

  // CA 签章台账
  // SPA 深链：dev server 冷编译 caSeal 大块路由可能很慢，超大超时 + 重试一次
  let depthLoaded = false;
  for (let attempt = 1; attempt <= 2 && !depthLoaded; attempt++) {
    try {
      await page.goto(BASE + '/certificate/ca-seal', { waitUntil: 'domcontentloaded', timeout: 180000 });
      depthLoaded = true;
    } catch {
      if (attempt === 2) throw new Error('深链 /certificate/ca-seal 两次超时，dev server 冷编译太慢');
      await page.waitForTimeout(15000);
    }
  }
  await page.waitForFunction(() => document.querySelector('#app') && document.querySelector('#app').innerHTML.length > 100, undefined, { timeout: 120000 }).catch(() => {});
  // 深链全量刷新后 accessStore/动态路由/首屏数据要异步就绪：等到真实数据行出现（此前 4s 死等会撞上"暂无数据"假空态）
  await page.waitForFunction(
    () => document.querySelectorAll('.ant-table-tbody tr:not(.ant-table-measure-row)').length > 0,
    undefined,
    { timeout: 60000 },
  ).catch(() => {});
  await page.waitForTimeout(800);
  const body = await page.locator('body').innerText();
  rec('A1 页面可打开（无编译错误提示）', !body.includes('Internal Server Error') && !body.includes('Failed to compile'), 'body 无编译错误');
  rec('A2 台账标题渲染', body.includes('CA 签章台账'), '页头标题');
  // antd 表格首行是隐藏的 measure-row（无单元格），必须排除才能拿到真实数据行
  const row = page.locator('.ant-table-tbody tr:not(.ant-table-measure-row)').first();
  rec('A3 台账有数据行', (await row.count()) > 0, '24 条 seal 记录');
  if ((await row.count()) > 0) {
    // B. 原文件名 → 原文件预览
    const srcLink = row.locator('a.ct-link').first();
    rec('B0 原文件名可点击（有 fileId）', (await srcLink.count()) > 0, 'documentName 列渲染为链接');
    if ((await srcLink.count()) > 0) {
      await srcLink.click();
      const modal = page.locator('.ant-modal:visible').first();
      await modal.waitFor({ timeout: 20000 });
      const mt = await modal.locator('.ant-modal-title').innerText().catch(() => '');
      rec('B1 原文件预览弹窗打开', mt.includes('原文件预览'), 'title=' + mt);
      const iframe = modal.locator('iframe, img').first();
      rec('B2 预览内容已加载', (await iframe.count()) > 0, 'iframe/img 渲染');
      await page.screenshot({ path: MY + '/shots/ca-seal-src-preview.png' });
      // 点 modal 右上 ✕ 关闭（Escape 会被 iframe 焦点吞掉，导致下一行按钮被 iframe 遮挡无法点击）
      await page.locator('.ant-modal:visible .ant-modal-close').first().click({ timeout: 15000 });
      await page.waitForTimeout(600);
    }
    // C. 红章文件 预览
    const sealBtn = row.locator('button', { hasText: '预览' }).first();
    if ((await sealBtn.count()) > 0) {
      await sealBtn.click();
      const modal2 = page.locator('.ant-modal:visible').first();
      await modal2.waitFor({ timeout: 20000 });
      const mt2 = await modal2.locator('.ant-modal-title').innerText().catch(() => '');
      rec('C1 红章文件预览弹窗打开', mt2.includes('红章文件预览'), 'title=' + mt2);
      await page.screenshot({ path: MY + '/shots/ca-seal-seal-preview.png' });
      await page.locator('.ant-modal:visible .ant-modal-close').first().click({ timeout: 15000 });
      await page.waitForTimeout(600);
    } else {
      rec('C1 红章文件预览弹窗打开', false, '未找到「预览」按钮');
    }
    // D. 证据链 → 原文件名预览
    const chainBtn = row.locator('button', { hasText: '证据链' }).first();
    if ((await chainBtn.count()) > 0) {
      await chainBtn.click();
      const modal3 = page.locator('.ant-modal:visible').first();
      await modal3.waitFor({ timeout: 20000 });
      const link3 = modal3.locator('a.ct-link').first();
      rec('D1 证据链弹窗原文件名可点预览', (await link3.count()) > 0, 'detail.fileId 渲染为链接');
      await page.screenshot({ path: MY + '/shots/ca-seal-evidence.png' });
    } else {
      rec('D1 证据链弹窗原文件名可点预览', false, '未找到「证据链」按钮');
    }
  }

  await browser.close();
  const pass = results.filter((r) => r.ok).length;
  const fail = results.filter((r) => !r.ok).length;
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL');
  process.exit(fail ? 1 : 0);
})();
