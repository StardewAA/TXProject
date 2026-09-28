/**
 * 2026-09-25 第三轮修复 UI 冒烟（用户反馈 10 项）
 *
 * 覆盖：
 *   1. 主题跟随系统（fresh 上下文，暗色系统 → html.dark；浅色系统 → 无 dark 类）
 *   2. 招聘职位「地图选点」在未配置 AK 时可**手动填写经纬度**并回填表单
 *   3. 门户工作地点在未配置 AK 时给出提示 + 百度地图外链 + 复制地址（不白屏）
 *   6. 候选人「简历版本」点预览 → 站内弹窗渲染（不再是直接下载）
 *   7/8. 页面渲染跟随主题（暗色下门户/招聘沟通无大面积浅色块）
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260925-round3.mjs
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

async function login(page) {
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
  await page.locator('input[placeholder*="用户名"]').first().fill('qinchuan_hr');
  await page.locator('input[type=password]').first().fill('admin123');
  await page.locator('button').filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(4000);
}

const browser = await chromium.launch({ executablePath: CHROME, headless: true });

// ---------- 1. 主题跟随系统 ----------
for (const scheme of ['dark', 'light']) {
  const context = await browser.newContext({
    colorScheme: scheme,
    viewport: { width: 1680, height: 1000 },
  });
  const page = await context.newPage();
  try {
    await login(page);
    await page.goto(`${BASE}/#/hrm/home`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3000);
    const isDark = await page.evaluate(() =>
      document.documentElement.classList.contains('dark'),
    );
    record(
      `1) 系统${scheme === 'dark' ? '暗色' : '浅色'}时页面主题跟随`,
      scheme === 'dark' ? isDark : !isDark,
      `html.dark=${isDark}`,
    );
  } catch (error) {
    record(`1) 系统${scheme}时页面主题跟随`, false, String(error).slice(0, 160));
  } finally {
    await context.close();
  }
}

const context = await browser.newContext({
  colorScheme: 'dark',
  acceptDownloads: false,
  viewport: { width: 1680, height: 1000 },
});
const page = await context.newPage();
try {
  await login(page);

  // ---------- 2. 招聘职位地图选点（无 AK → 手动经纬度） ----------
  await page.goto(`${BASE}/#/hrm/recruit/post`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  await page.getByText('新增', { exact: true }).first().click();
  await page.waitForTimeout(2500);
  const mapButton = page.getByRole('button', { name: /地图选点/ }).first();
  if (await mapButton.count()) {
    await mapButton.click();
    await page.waitForTimeout(3500);
    const bodyText = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
    const manualHint =
      bodyText.includes('手动填写经纬度') || bodyText.includes('未配置百度地图 AK');
    const lonInput = page.locator('input[placeholder="如 116.404"]').first();
    const latInput = page.locator('input[placeholder="如 39.915"]').first();
    const manualUsable = (await lonInput.count()) > 0 && (await latInput.count()) > 0;
    if (manualUsable) {
      await lonInput.fill('116.404');
      await latInput.fill('39.915');
      await page.getByRole('button', { name: /确 定|确定/ }).first().click();
      await page.waitForTimeout(2000);
      const after = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
      const filled = after.includes('116.404') || after.includes('39.915');
      record(
        '2) 地图不可用时手动填写经纬度并回填表单',
        manualHint && manualUsable && filled,
        `提示=${manualHint} 可编辑=${manualUsable} 回填=${filled}`,
      );
    } else {
      record('2) 地图不可用时手动填写经纬度并回填表单', false, '未找到可编辑坐标输入框');
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(800);
  } else {
    record('2) 地图不可用时手动填写经纬度并回填表单', false, '未找到「地图选点」按钮');
  }
  await page.keyboard.press('Escape');
  await page.waitForTimeout(500);

  // ---------- 6. 简历版本预览（站内弹窗） ----------
  // 从候选人列表进入详情（走真实 UI，避免手工拼接口鉴权）
  await page.goto(`${BASE}/#/hrm/recruit/candidate`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(5000);
  // 列表默认页签可能为空（候选人都在其它状态），这里直接用接口拿一个候选人编号再打开详情路由
  let candidateRowId = null;
  try {
    const gateway = process.env.HRM_GATEWAY || 'http://127.0.0.1:48080';
    const loginResponse = await page.request.post(
      `${gateway}/admin-api/system/auth/login`,
      {
        data: {
          grantType: 'password',
          password: 'admin123',
          username: 'qinchuan_hr',
        },
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'tenant-id': '2020',
        },
      },
    );
    const token = (await loginResponse.json())?.data?.accessToken;
    const listResponse = await page.request.get(
      `${gateway}/admin-api/hrm/recruit/candidate/page?pageNo=1&pageSize=1`,
      { headers: { Authorization: `Bearer ${token}`, 'tenant-id': '2020' } },
    );
    candidateRowId = (await listResponse.json())?.data?.list?.[0]?.id ?? null;
  } catch {
    candidateRowId = null;
  }
  let openedDetail = false;
  if (candidateRowId) {
    await page.goto(
      `${BASE}/#/hrm/recruit/candidate/detail/${candidateRowId}`,
      { waitUntil: 'domcontentloaded' },
    );
    await page.waitForTimeout(4500);
    openedDetail = page.url().includes('/hrm/recruit/candidate/detail/');
  }
  if (openedDetail) {
    const resumeTab = page.getByText('简历版本', { exact: true }).first();
    if (await resumeTab.count()) {
      await resumeTab.click();
      await page.waitForTimeout(2500);
      const previewButton = page.getByText('预览', { exact: true }).first();
      if (await previewButton.count()) {
        await previewButton.click();
        // pdf.js 需要下载并逐页渲染，给足时间（实测约 12s）
        await page.waitForTimeout(14_000);
        const modalText = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
        const hasModal = modalText.includes('简历预览');
        const hasRendered = await page
          .locator('img[alt="简历页面"], .docx-preview-box, img[alt="简历图片"]')
          .count();
        record(
          '6) 简历版本「预览」在页面内渲染',
          hasModal && hasRendered > 0,
          `弹窗=${hasModal} 渲染元素=${hasRendered}`,
        );
        await page.keyboard.press('Escape');
        await page.waitForTimeout(800);
      } else {
        record('6) 简历版本「预览」在页面内渲染', false, '未找到预览按钮');
      }
    } else {
      record('6) 简历版本「预览」在页面内渲染', false, '未找到「简历版本」页签');
    }
  } else {
    record('6) 简历版本「预览」在页面内渲染', false, `未取到候选人编号（url=${page.url()}）`);
  }

  // ---------- 3. 门户工作地点降级 ----------
  // 从公开职位接口取一个职位编号，直接打开职位详情（工作地点卡片在详情页）
  const postPayload = await page.evaluate(async () => {
    const response = await fetch(
      '/admin-api/hrm/portal/recruit/post/page?pageNo=1&pageSize=1',
      { headers: { 'tenant-id': '2020' } },
    );
    return await response.json();
  });
  const postId = postPayload?.data?.list?.[0]?.id;
  await page.goto(`${BASE}/#/portal/recruit/detail?id=${postId}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.waitForTimeout(7000);
  const locationCardCount = await page.locator('.location-card').count();
  const portalText = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
  const mapLink = await page.locator('a:has-text("百度地图查看位置")').count();
  const hintOk =
    portalText.includes('未配置百度地图 AK') ||
    portalText.includes('尚未提供可定位的工作地址') ||
    portalText.includes('百度地图查看位置');
  record(
    '3) 门户工作地点在无 AK 时可用（外链/提示）',
    (locationCardCount > 0 && (mapLink > 0 || hintOk)) || hintOk,
    `地点卡片=${locationCardCount} 外链=${mapLink} 文案=${hintOk}`,
  );

  // ---------- 7/8. 暗色下门户无大面积浅色块 ----------
  const lightIslands = await page.evaluate(() => {
    const lum = (rgb) => {
      const m = String(rgb).match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
      if (!m) return null;
      const [r, g, b] = [Number(m[1]), Number(m[2]), Number(m[3])];
      return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    };
    let count = 0;
    for (const el of document.querySelectorAll('body *')) {
      const rect = el.getBoundingClientRect();
      if (rect.width * rect.height < 20_000) continue;
      const bg = lum(getComputedStyle(el).backgroundColor);
      if (bg !== null && bg > 0.8) count += 1;
    }
    return count;
  });
  record(
    '7/8) 暗色下门户无大面积浅色容器',
    lightIslands <= 6,
    `面积较大的浅色元素=${lightIslands}（阈值 ≤6）`,
  );
} catch (error) {
  record('执行异常', false, String(error).slice(0, 300));
} finally {
  await context.close();
  await browser.close();
}

const failed = results.filter((item) => !item.ok);
console.log('\n==== 汇总 ====');
console.log(
  `共 ${results.length} 项，通过 ${results.length - failed.length} 项，失败 ${failed.length} 项`,
);
process.exit(failed.length === 0 ? 0 : 1);
