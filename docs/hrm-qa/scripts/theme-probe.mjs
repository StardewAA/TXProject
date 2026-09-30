/**
 * 主题渲染排查：登录 HR 账号后，对比「系统外壳」与「HRM/门户页面」的主题表现，
 * 输出 html class、偏好设置里的主题模式，并截图留证。
 *
 * 运行：node docs/hrm-qa/scripts/theme-probe.mjs
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
const OUT = path.join(process.cwd(), 'docs/hrm-qa/evidence');
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
// 模拟系统深色（用户系统是暗色）
const page = await browser.newPage({
  colorScheme: 'dark',
  viewport: { width: 1680, height: 1000 },
});

async function login(user) {
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
  await page.locator('input[placeholder*="用户名"]').first().fill(user);
  await page.locator('input[type=password]').first().fill('admin123');
  await page.locator('button').filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(4000);
}

async function probe(name, hash) {
  await page.goto(`${BASE}/#${hash}`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const info = await page.evaluate(() => {
    const root = document.documentElement;
    const body = document.body;
    const app = document.querySelector('#app');
    const prefs = globalThis.localStorage?.getItem('vben-preferences');
    return {
      cls: root.className,
      bodyBg: getComputedStyle(body).backgroundColor,
      bodyColor: getComputedStyle(body).color,
      appBg: app ? getComputedStyle(app).backgroundColor : null,
      themeMode: prefs ? String(prefs).match(/"mode":"(\w+)"/)?.[1] ?? null : null,
    };
  });
  const file = path.join(OUT, `theme-${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log(
    `${name.padEnd(18)} html="${info.cls}" bodyBg=${info.bodyBg} bodyColor=${info.bodyColor} prefMode=${info.themeMode} shot=${path.basename(file)}`,
  );
  return info;
}

try {
  await login(process.env.HRM_WEB_USER || 'qinchuan_hr');
  await probe('hr-home', '/hrm/home');
  await probe('hr-employee-list', '/hrm/employee/list');
  await probe('hr-recruit-chat', '/hrm/recruit/chat');
  await probe('hr-recruit-post', '/hrm/recruit/post');
  await probe('hr-attendance-leave', '/hrm/attendance/leave');
  await probe('portal-recruit', '/portal/recruit');
} finally {
  await browser.close();
}
