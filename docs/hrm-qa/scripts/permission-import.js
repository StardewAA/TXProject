async (page) => {
  const out = { hr: {}, adminMore: {}, issues: [] };
  const text = async () => (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");

  // Admin: leave page buttons only
  await page.goto("http://localhost:5173/#/hrm/attendance/leave", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const leaveBtns = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
  out.adminMore.leaveButtons = leaveBtns.filter(b => /新增|导入|导出|审批|删除|编辑|修改|查询|搜索/.test(b));
  out.adminMore.leaveHasNew = leaveBtns.some(b => /新增/.test(b));

  // Import/export scan all pages
  const pages = [
    ["/hrm/attendance/clock", "clock"],
    ["/hrm/attendance/leave", "leave"],
    ["/hrm/attendance/month", "month"],
    ["/hrm/salary/month-record", "salary"],
    ["/hrm/salary/employee-info", "salary-emp"],
    ["/hrm/salary/piece-record", "piece"],
    ["/hrm/insurance/month-record", "insurance"],
    ["/hrm/employee/list", "employee"],
    ["/hrm/recruit/candidate", "candidate"],
    ["/hrm/certificate/ledger", "cert"],
  ];
  out.adminMore.importExport = {};
  for (const [p, n] of pages) {
    await page.goto("http://localhost:5173/#" + p, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(600);
    const b = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
    out.adminMore.importExport[n] = b.filter(x => /导入|导出|模板|下载/.test(x));
  }

  // Recruit post: confirm no draft
  await page.goto("http://localhost:5173/#/hrm/recruit/post", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  const postBody = await text();
  out.adminMore.postStatusHint = postBody.includes("直接发布到招聘门户");
  out.adminMore.postTabs = /已发布（\d+）\s*已暂停（\d+）\s*已关闭（\d+）/.exec(postBody)?.[0] || "tabs not matched";

  // Performance filter by status
  await page.goto("http://localhost:5173/#/hrm/performance/plan", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  // click 进行中 tab
  const inProg = page.locator("text=进行中").first();
  if (await inProg.count()) {
    await inProg.click().catch(() => {});
    await page.waitForTimeout(600);
  }
  out.adminMore.perfAfterFilter = (await text()).slice(-300);

  // Logout and login as HR
  // Clear storage and re-login via UI
  await page.context().clearCookies();
  await page.evaluate(() => localStorage.clear()).catch(() => {});
  await page.evaluate(() => sessionStorage.clear()).catch(() => {});
  await page.goto("http://localhost:5173/#/auth/login", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);

  // select tenant
  const combo = page.locator("combobox").first();
  if (await combo.count()) {
    await combo.click();
    await page.waitForTimeout(300);
    const opt = page.locator("option", { hasText: "秦川化工产业园发展有限公司" }).first();
    if (await opt.count()) await opt.click();
    await page.waitForTimeout(300);
  }
  await page.locator("input[placeholder*=用户名], input[placeholder*=工号]").first().fill("qinchuan_hr");
  await page.locator("input[type=password]").first().fill("admin123");
  await page.locator("button", { hasText: /登录|login/ }).first().click();
  await page.waitForTimeout(1500);
  out.hr.afterLogin = await page.title();

  // HR menu visibility
  await page.goto("http://localhost:5173/#/hrm/employee/list", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.hr.employeeTitle = await page.title();
  out.hr.employeeBody = (await text()).slice(350, 700);

  // HR should maybe not see some system menus
  await page.goto("http://localhost:5173/#/hrm/salary/month-record", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.hr.salaryTitle = await page.title();
  out.hr.salaryBody = (await text()).slice(-250);

  // HR try system user page (should 403/404)
  await page.goto("http://localhost:5173/#/system/user", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.hr.systemUser = await page.title();

  // HR policy
  await page.goto("http://localhost:5173/#/hrm/policy", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.hr.policyTitle = await page.title();
  const hrBtns = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
  out.hr.policyButtons = hrBtns.filter(b => /新增|删除|修改|导出|导入/.test(b));

  // HR portal recruit
  await page.goto("http://localhost:5173/#/portal/recruit", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.hr.portalRecruit = await page.title();

  return out;
}
