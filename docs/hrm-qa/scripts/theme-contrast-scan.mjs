/**
 * 主题渲染扫描：在「暗色」主题下遍历页面可见元素，找出
 *   ① 浅色孤岛（背景很亮，但与系统暗色主题冲突）
 *   ② 低对比文字（文字色与其所在背景亮度接近，浅色/暗色下都可能看不清）
 * 输出按元素数聚合的「类名 → 次数」清单，便于定位需要改的样式。
 *
 * 运行：node docs/hrm-qa/scripts/theme-contrast-scan.mjs [--light]
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

const PAGES = [
  ['hr-home', '/hrm/home'],
  ['hr-employee-list', '/hrm/employee/list'],
  ['hr-recruit-post', '/hrm/recruit/post'],
  ['hr-recruit-chat', '/hrm/recruit/chat'],
  ['hr-recruit-funnel', '/hrm/recruit/funnel'],
  ['hr-attendance-leave', '/hrm/attendance/leave'],
  ['hr-salary-month', '/hrm/salary/month-record'],
  ['hr-performance-plan', '/hrm/performance/plan'],
  ['hr-policy', '/hrm/policy'],
  ['portal-recruit', '/portal/recruit'],
  ['portal-recruit-jobs', '/portal/recruit?section=jobs'],
  ['portal-recruit-companies', '/portal/recruit?section=companies'],
  ['portal-recruit-messages', '/portal/recruit?section=messages'],
  ['portal-recruit-resume', '/portal/recruit?section=resume'],
  ['portal-recruit-user', '/portal/recruit?section=user'],
];

/** 目标主题模式：dark / light / auto（auto 跟随系统） */
const MODE = (process.argv.find((arg) => arg.startsWith('--mode=')) || '--mode=dark').split('=')[1];

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({
  colorScheme: 'dark',
  viewport: { width: 1680, height: 1000 },
});

async function login() {
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
  // 强制指定主题模式（写入偏好缓存后重载，模拟用户在该模式下的真实渲染）
  await page.evaluate((mode) => {
    const keys = Object.keys(globalThis.localStorage);
    // 注意：要精确匹配到 -preferences 本体，不能命中 -preferences-locale / -preferences-custom
    const key = keys.find((k) => /-preferences$/.test(k));
    if (!key) return;
    try {
      const value = JSON.parse(globalThis.localStorage.getItem(key) || '{}');
      value.theme = { ...(value.theme || {}), mode };
      globalThis.localStorage.setItem(key, JSON.stringify(value));
      const themeKey = keys.find((k) => /-preferences-theme$/.test(k));
      if (themeKey) {
        globalThis.localStorage.setItem(themeKey, mode);
      }
    } catch {
      /* ignore */
    }
  }, MODE);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
}

const scanScript = () => {
  const lum = (rgb) => {
    const m = String(rgb).match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (!m) return null;
    if (m[4] !== undefined && Number(m[4]) === 0) return null; // 完全透明
    const [r, g, b] = [Number(m[1]), Number(m[2]), Number(m[3])];
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  };
  const describe = (el) => {
    const cls = (el.className && typeof el.className === 'string' ? el.className : '')
      .split(/\s+/)
      .filter((c) => c && !c.startsWith('data-v-') && !c.startsWith('v-'))
      .slice(0, 4)
      .join('.');
    return `${el.tagName.toLowerCase()}${cls ? '.' + cls : ''}`;
  };
  const visible = (el) => {
    const rect = el.getBoundingClientRect();
    if (rect.width < 8 || rect.height < 8) return false;
    const style = getComputedStyle(el);
    return style.visibility !== 'hidden' && style.display !== 'none' && Number(style.opacity) > 0.2;
  };
  const lightIslands = new Map();
  const lowContrast = new Map();
  const nodes = Array.from(document.querySelectorAll('body *')).slice(0, 4000);
  for (const el of nodes) {
    if (!visible(el)) continue;
    const style = getComputedStyle(el);
    const bg = lum(style.backgroundColor);
    const fg = lum(style.color);
    if (bg !== null && bg > 0.72) {
      const key = describe(el);
      lightIslands.set(key, (lightIslands.get(key) || 0) + 1);
    }
    // 低对比：文字亮度与自身背景（或最近的亮背景）差太小
    const text = (el.textContent || '').trim();
    if (text && el.children.length === 0 && fg !== null) {
      if (bg !== null && Math.abs(fg - bg) < 0.18) {
        const key = describe(el);
        lowContrast.set(key, (lowContrast.get(key) || 0) + 1);
      }
    }
  }
  const top = (map) =>
    [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12);
  return {
    dark: document.documentElement.classList.contains('dark'),
    lightIslands: top(lightIslands),
    lowContrast: top(lowContrast),
  };
};

try {
  await login();
  for (const [name, hash] of PAGES) {
    await page.goto(`${BASE}/#${hash}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3500);
    // 主题是 class 驱动（.dark 覆盖 CSS 变量）：按目标模式校正 class 后再扫描，
    // 这样 dark/light 两个方向的渲染问题都能测到。
    await page.evaluate((mode) => {
      const root = document.documentElement;
      const themeKey = Object.keys(globalThis.localStorage).find((k) =>
        /-preferences-theme$/.test(k),
      );
      if (themeKey) {
        globalThis.localStorage.setItem(themeKey, mode);
      }
      if (mode === 'light') {
        root.classList.remove('dark');
        root.classList.add('light');
      } else {
        root.classList.remove('light');
        root.classList.add('dark');
      }
    }, MODE);
    await page.waitForTimeout(800);
    const result = await page.evaluate(scanScript);
    console.log(`\n=== ${name} (dark=${result.dark}) ===`);
    console.log(
      '浅色孤岛:',
      result.lightIslands.map(([k, v]) => `${k}×${v}`).join('  ') || '（无）',
    );
    console.log(
      '低对比文字:',
      result.lowContrast.map(([k, v]) => `${k}×${v}`).join('  ') || '（无）',
    );
  }
} finally {
  await browser.close();
}
