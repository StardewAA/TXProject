async (page) => {
  const out = {};
  const text = async () => (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");

  // restore admin first via login page carefully
  await page.goto("http://localhost:5173/#/auth/login", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1000);

  // snapshot-like discovery
  out.loginForm = {
    combobox: await page.locator("[role=combobox]").count(),
    user: await page.locator("input").count(),
  };

  // Select tenant by typing in combobox
  const cb = page.locator("[role=combobox]").first();
  await cb.click();
  await page.waitForTimeout(400);
  // type to filter
  await page.keyboard.type("秦川");
  await page.waitForTimeout(400);
  const opt = page.locator("[role=option]", { hasText: "秦川化工产业园发展有限公司" }).first();
  out.optCount = await page.locator("[role=option]").count();
  if (await opt.count()) {
    await opt.click();
  } else {
    // click by text
    await page.locator("text=秦川化工产业园发展有限公司").first().click().catch(() => {});
  }
  await page.waitForTimeout(300);

  // fill by placeholder
  const userInput = page.getByPlaceholder(/用户名|工号/).first();
  const pwdInput = page.locator("input[type=password]").first();
  await userInput.fill("qinchuan_hr");
  await pwdInput.fill("admin123");
  await page.waitForTimeout(200);
  await page.getByRole("button", { name: /登录/ }).first().click();
  await page.waitForTimeout(2000);
  out.afterHrLogin = await page.title();
  out.afterHrUrl = page.url();
  out.afterHrBody = (await text()).slice(0, 250);

  // If still login, try admin restore for remaining tests
  if (out.afterHrLogin.includes("登录")) {
    // try again with admin
    await userInput.fill("qinchuan").catch(() => {});
    await pwdInput.fill("admin123").catch(() => {});
    await page.getByRole("button", { name: /登录/ }).first().click();
    await page.waitForTimeout(1500);
  }
  out.restored = await page.title();

  // If logged in as someone, check menus
  await page.goto("http://localhost:5173/#/hrm/employee/list", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.menuSnippet = (await text()).slice(200, 550);
  out.userBadge = (await text()).match(/孙莉|HR1|HR2|胡杰|徐超/)?.[0];

  return out;
}
