import fs from "fs";
import path from "path";
import { createRequire } from "module";
const require = createRequire(path.join(process.cwd(), "txgy-ui-admin-vben/package.json"));
const { chromium } = require("playwright");
const CHROME =
  "C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe";
const BASE = "http://localhost:5173";
const OUT = path.join(process.cwd(), "docs/hrm-qa/evidence");

const browser = await chromium.launch({ headless: true, executablePath: CHROME });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, locale: "zh-CN" });

async function login(user = "qinchuan") {
  await page.goto(`${BASE}/#/auth/login`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1200);
  await page.locator("[role=combobox]").first().click();
  await page.waitForTimeout(250);
  await page.keyboard.type("秦川");
  await page.waitForTimeout(400);
  await page.locator("[role=option]").filter({ hasText: "秦川化工产业园发展有限公司" }).first().click();
  await page.locator('input[placeholder*="用户名"]').first().fill(user);
  await page.locator("input[type=password]").first().fill("admin123");
  await page.locator("button").filter({ hasText: /^登录$/ }).first().click();
  await page.waitForTimeout(3000);
}

await login();
const results = [];

// open HRM menu and click target items by sidebar text
await page.goto(`${BASE}/#/hrm/home`, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(1000);

const targets = ["考勤计薪规则", "比例上限", "请假", "招聘职位", "用工主体信息", "入转调离审批", "员工异动审批"];
for (const name of targets) {
  const link = page.locator("a, [role=menuitem], .vben-menu a, li").filter({ hasText: name }).first();
  if (!(await link.count())) {
    results.push({ name, found: false, url: page.url() });
    continue;
  }
  await link.click().catch(() => {});
  await page.waitForTimeout(900);
  const url = page.url();
  const body = (await page.locator("body").innerText()).replace(/\s+/g, " ");
  const is404 = /404|Not Found|页面不存在/.test(body.slice(0, 80));
  // buttons sample
  const buttons = (await page.locator("button").allInnerTexts()).map((x) => x.trim()).filter(Boolean).slice(0, 12);
  results.push({ name, found: true, url, is404, buttons, body: body.slice(0, 160) });
  console.log(name, "→", url, is404 ? "404" : "ok", buttons.join("/"));
}

// also collect all HRM sidebar hrefs
const hrefs = await page.locator("a[href*='#/hrm']").evaluateAll((els) =>
  [...new Set(els.map((e) => e.getAttribute("href") || e.hash || e.innerText))]
);
console.log("hrefs", hrefs);
results.push({ sidebarHrefs: hrefs });

fs.writeFileSync(path.join(OUT, "sidebar-routes.json"), JSON.stringify(results, null, 2));
await browser.close();
