/* 前台门户跳转菜单 + 菜单修正 验收（真实数据）
 * P1 招投标目录下出现「前台门户」菜单
 * P2 侧边栏不再有「主体信息与授权管理」
 * P3 点击前台门户 → 同 SPA 跳到 /portal/bidding 公告大厅
 * P4 token 保留：门户顶栏显示 admin（鉴权用户态未丢）
 * P5 外部公告审核管理菜单仍在（6736 权限码修正为 query 后对 admin 仍可见）
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
  let loggedIn = false;
  for (let attempt = 1; attempt <= 3 && !loggedIn; attempt += 1) {
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
  await page.waitForTimeout(6000);
  // 展开招投标目录
  await page.locator('.vben-menu').getByText('招投标', { exact: true }).first().click({ timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1000);
  const menuHas = async (t) => (await page.locator('.vben-menu').getByText(t, { exact: true }).count()) > 0;
  rec('P1 前台门户菜单出现', await menuHas('前台门户'), '菜单含前台门户');
  rec('P2 主体信息与授权管理已删', !(await menuHas('主体信息与授权管理')), '菜单不含已删项');
  rec('P5 外部公告审核管理仍在', await menuHas('外部公告审核管理'), '菜单含外部公告审核管理');
  await page.screenshot({ path: MY + '/shots/portal-menu.png' });
  // 点击前台门户 → 跳转
  await page.locator('.vben-menu').getByText('前台门户', { exact: true }).first().click({ timeout: 30000 });
  await page.waitForURL((u) => u.pathname === '/portal/bidding', { timeout: 60000 });
  await page.waitForTimeout(4000);
  const url = page.url();
  const body = await page.locator('body').innerText().catch(() => '');
  rec('P3 跳转到门户公告大厅', url === BASE + '/portal/bidding' || url === 'http://localhost:5666/portal/bidding', 'url=' + url);
  rec('P3b 门户内容渲染', body.includes('招标投标') || body.includes('公告'), '门户页有内容');
  // 门户顶栏出现「退出登录」= 用户态在（鉴权 token 保留）；匿名访客不会渲染该按钮
  const navUser = await page.locator('[class*=user]').first().innerText().catch(() => '');
  rec('P4 token 保留(顶栏用户态)', navUser.includes('退出登录'), 'navUser=' + navUser.slice(0, 30));
  await page.screenshot({ path: MY + '/shots/portal-jump.png' });
  const pass = results.filter((x) => x.ok).length;
  const fail = results.filter((x) => !x.ok).length;
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL');
  await browser.close();
  process.exit(fail ? 1 : 0);
})();
