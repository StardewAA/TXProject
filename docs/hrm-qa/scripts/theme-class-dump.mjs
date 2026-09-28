/**
 * 暗色主题问题类清单导出：在暗色模式下，导出「背景偏亮（与暗色主题冲突）」的元素类名
 * 以及它们的文字/边框色，用于生成针对性的暗色覆盖样式。
 *
 * 运行：node docs/hrm-qa/scripts/theme-class-dump.mjs
 */
import { createRequire } from 'node:module';
import path from 'node:path';
import fs from 'node:fs';

const require = createRequire(
  path.join(process.cwd(), 'txgy-ui-admin-vben/package.json'),
);
const { chromium } = require('playwright');

const CHROME =
  process.env.PLAYWRIGHT_CHROMIUM ||
  'C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe';
const BASE = process.env.HRM_WEB_BASE || 'http://127.0.0.1:5173';
const OUT = path.join(process.cwd(), 'docs/hrm-qa/evidence/theme-dark-offenders.json');

const PAGES = [
  ['portal-recruit', '/portal/recruit'],
  ['portal-jobs', '/portal/recruit?section=jobs'],
  ['portal-companies', '/portal/recruit?section=companies'],
  ['portal-messages', '/portal/recruit?section=messages'],
  ['portal-resume', '/portal/recruit?section=resume'],
  ['portal-user', '/portal/recruit?section=user'],
];

const scan = () => {
  const lum = (rgb) => {
    const m = String(rgb).match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
    if (!m) return null;
    if (m[4] !== undefined && Number(m[4]) === 0) return null;
    const [r, g, b] = [Number(m[1]), Number(m[2]), Number(m[3])];
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  };
  const result = {};
  for (const el of document.querySelectorAll('body *')) {
    const rect = el.getBoundingClientRect();
    if (rect.width < 8 || rect.height < 8) continue;
    const style = getComputedStyle(el);
    if (style.visibility === 'hidden' || style.display === 'none') continue;
    const bg = lum(style.backgroundColor);
    if (bg === null || bg <= 0.72) continue;
    const classes = (typeof el.className === 'string' ? el.className : '')
      .split(/\s+/)
      .filter((c) => c && !c.startsWith('css-') && !c.startsWith('data-v-'));
    const key = `${el.tagName.toLowerCase()}|${classes.slice(0, 3).join('.')}`;
    result[key] ??= {
      background: style.backgroundColor,
      border: style.borderColor,
      color: style.color,
      count: 0,
      tag: el.tagName.toLowerCase(),
      classes,
    };
    result[key].count += 1;
  }
  return result;
};

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({
  colorScheme: 'dark',
  viewport: { width: 1680, height: 1200 },
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
  const dump = {};
  for (const [name, hash] of PAGES) {
    await page.goto(`${BASE}/#${hash}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(4000);
    dump[name] = await page.evaluate(scan);
    console.log(`${name}: ${Object.keys(dump[name]).length} 类浅色元素`);
  }
  fs.writeFileSync(OUT, JSON.stringify(dump, null, 2));
  console.log(`written: ${OUT}`);
} finally {
  await browser.close();
}
