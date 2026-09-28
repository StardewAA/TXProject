import { createRequire } from "module";
import path from "path";
import fs from "fs";
const require = createRequire(path.join(process.cwd(), "txgy-ui-admin-vben/package.json"));
const { chromium } = require("playwright");

const CHROME =
  process.env.PLAYWRIGHT_CHROMIUM ||
  "C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe";

const browser = await chromium.launch({ headless: true, executablePath: CHROME });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on("console", (m) => console.log("CONSOLE", m.type(), m.text().slice(0, 200)));
page.on("response", (r) => {
  if (r.url().includes("auth") || r.url().includes("login") || r.status() >= 400) {
    console.log("RESP", r.status(), r.url().slice(0, 160));
  }
});
await page.goto("http://localhost:5173/#/auth/login", { waitUntil: "domcontentloaded", timeout: 30000 });
await page.waitForTimeout(2000);
console.log("URL", page.url());
console.log("TITLE", await page.title());
const body = (await page.locator("body").innerText()).replace(/\s+/g, " ");
console.log("BODY", body.slice(0, 500));
console.log("combobox", await page.locator("[role=combobox]").count());
console.log("inputs", await page.locator("input").count());
console.log("placeholders", await page.locator("input").evaluateAll((els) => els.map((e) => e.placeholder + "|" + e.type)));
console.log("buttons", await page.locator("button").allInnerTexts());
await page.screenshot({ path: "docs/hrm-qa/evidence/login-debug.png", fullPage: true });
await browser.close();
