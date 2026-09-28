/**
 * 门户/页面按钮对比度扫描：按 WCAG 相对亮度计算「文字色 vs 有效背景」的对比度，
 * 报告低于 4.5:1 的交互元素（按钮/链接/输入框），用于定位"浅色下看不清"的元素。
 *
 * 运行：node docs/hrm-qa/scripts/portal-contrast-scan.mjs [--mode=dark|light]
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
const MODE = (process.argv.find((arg) => arg.startsWith('--mode=')) || '--mode=dark').split('=')[1];

const PAGES = [
  ['recruit-home', '/portal/recruit'],
  ['recruit-jobs', '/portal/recruit?section=jobs'],
  ['recruit-companies', '/portal/recruit?section=companies'],
  ['recruit-detail', '/portal/recruit?section=jobs'],
  ['hrm-chat', '/hrm/recruit/chat'],
  ['hrm-home', '/hrm/home'],
];

const scan = () => {
  const parse = (rgb) => {
    const m = String(rgb).match(/rgba?\((\d+(?:\.\d+)?),\s*(\d+(?:\.\d+)?),\s*(\d+(?:\.\d+)?)(?:,\s*([\d.]+))?\)/);
    if (!m) return null;
    const alpha = m[4] === undefined ? 1 : Number(m[4]);
    return { a: alpha, b: Number(m[2]), g: Number(m[1]), r: Number(m[0]) };
  };
  const rel = ({ b, g, r }) => {
    const f = (v) => {
      const s = v / 255;
      return s <= 0.039_28 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  };
  const contrast = (fg, bg) => {
    const l1 = rel(fg);
    const l2 = rel(bg);
    const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1];
    return (hi + 0.05) / (lo + 0.05);
  };
  const effectiveBg = (el) => {
    let node = el;
    while (node && node !== document.documentElement) {
      const c = parse(getComputedStyle(node).backgroundColor);
      if (c && c.a > 0.5) return c;
      node = node.parentElement;
    }
    const rootBg = parse(getComputedStyle(document.body).backgroundColor);
    return rootBg && rootBg.a > 0.5 ? rootBg : { a: 1, b: 255, g: 255, r: 255 };
  };
  const describe = (el) => {
    const cls = (typeof el.className === 'string' ? el.className : '')
      .split(/\s+/)
      .filter((c) => c && !c.startsWith('css-') && !c.startsWith('data-v-'))
      .slice(0, 3)
      .join('.');
    return `${el.tagName.toLowerCase()}${cls ? '.' + cls : ''}`;
  };
  const bad = new Map();
  for (const el of document.querySelectorAll('button, a, input, select, .ant-btn, [role=button]')) {
    const rect = el.getBoundingClientRect();
    if (rect.width < 12 || rect.height < 12) continue;
    const text = (el.textContent || '').trim();
    if (!text) continue;
    const fg = parse(getComputedStyle(el).color);
    if (!fg) continue;
    const ratio = contrast(fg, effectiveBg(el));
    if (ratio < 4.5) {
      const key = `${describe(el)} [${text.slice(0, 14)}] ${ratio.toFixed(2)}:1`;
      bad.set(key, (bad.get(key) || 0) + 1);
    }
  }
  return [...bad.entries()].sort((a, b) => a[1] - b[1]).slice(0, 15);
};

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({
  colorScheme: MODE === 'dark' ? 'dark' : 'light',
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
}

try {
  await login();
  for (const [name, hash] of PAGES) {
    await page.goto(`${BASE}/#${hash}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(3500);
    await page.evaluate((mode) => {
      const root = document.documentElement;
      if (mode === 'light') {
        root.classList.remove('dark');
        root.classList.add('light');
      } else {
        root.classList.remove('light');
        root.classList.add('dark');
      }
    }, MODE);
    await page.waitForTimeout(700);
    const rows = await page.evaluate(scan);
    console.log(`\n=== ${name} (mode=${MODE}) 低对比交互元素 ${rows.length} 类 ===`);
    for (const [key, count] of rows) {
      console.log(`  ×${count}  ${key}`);
    }
  }
} finally {
  await browser.close();
}
