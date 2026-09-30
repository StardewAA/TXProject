import { createRequire } from "module";
import path from "path";
const require = createRequire(path.join(process.cwd(), "txgy-ui-admin-vben/package.json"));
const { chromium } = require("playwright");
const CHROME =
  "C:\\Users\\Lenovo\\AppData\\Local\\ms-playwright\\chromium-1246\\chrome-win64\\chrome.exe";

const browser = await chromium.launch({ headless: true, executablePath: CHROME });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on("console", (m) => console.log("C", m.type(), m.text().slice(0, 180)));
page.on("response", async (r) => {
  if (r.url().includes("login") || r.url().includes("auth") || r.status() >= 400) {
    let body = "";
    try {
      body = (await r.text()).slice(0, 200);
    } catch {}
    console.log("R", r.status(), r.request().method(), r.url().slice(0, 140), body);
  }
});

await page.goto("http://localhost:5173/#/auth/login", { waitUntil: "domcontentloaded" });
await page.waitForTimeout(1500);

// dump tenant control structure
const cb = page.locator("[role=combobox]").first();
console.log("combobox html", await cb.evaluate((e) => e.outerHTML.slice(0, 400)).catch(() => "none"));
await cb.click().catch(() => {});
await page.waitForTimeout(300);
await page.keyboard.type("秦川");
await page.waitForTimeout(500);
console.log("after type body", (await page.locator("body").innerText()).replace(/\s+/g, " ").slice(0, 400));
const opts = await page.locator("[role=option]").allInnerTexts();
console.log("options", opts);
if (opts.length) {
  await page.locator("[role=option]").filter({ hasText: "秦川" }).first().click();
} else {
  // click list item
  await page.locator("text=秦川化工产业园发展有限公司").first().click().catch((e) => console.log("click name err", e.message));
}
await page.waitForTimeout(400);
console.log("selected?", await cb.innerText().catch(() => ""));

await page.locator('input[placeholder*="用户名"]').first().fill("qinchuan");
await page.locator("input[type=password]").first().fill("admin123");
const btns = await page.locator("button").evaluateAll((els) =>
  els.map((e, i) => ({ i, text: (e.innerText || "").trim(), type: e.type, disabled: e.disabled }))
);
console.log("buttons", JSON.stringify(btns));
const loginBtn = page.locator("button").filter({ hasText: /^登录$/ }).first();
console.log("login count", await loginBtn.count());
await loginBtn.click();
await page.waitForTimeout(3500);
console.log("final url", page.url());
console.log("final body", (await page.locator("body").innerText()).replace(/\s+/g, " ").slice(0, 350));
await page.screenshot({ path: "docs/hrm-qa/evidence/login-after.png", fullPage: true });
await browser.close();
