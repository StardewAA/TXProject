async (page) => {
  const out = {};
  const text = async () => (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");

  // Demand reject / cancel branch UI
  await page.goto("http://localhost:5173/#/hrm/recruit/demand");
  await page.waitForTimeout(800);
  const dBtns = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
  out.demandActions = dBtns.filter(b => /取消|删除|修改|审批|驳回|提交/.test(b));
  out.demandBody = (await text()).slice(350, 700);

  // Post close/pause branch
  await page.goto("http://localhost:5173/#/hrm/recruit/post");
  await page.waitForTimeout(800);
  const pBtns = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
  out.postActions = [...new Set(pBtns.filter(b => /暂停|关闭|修改|发布|新建/.test(b)))];

  // Candidate offer branches
  await page.goto("http://localhost:5173/#/hrm/recruit/candidate");
  await page.waitForTimeout(800);
  const cBtns = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);
  out.candidateActions = [...new Set(cBtns.filter(b => /Offer|淘汰|面试|录用|入职|修改|更多|清理/.test(b)))];
  out.candidateBody = (await text()).slice(350, 750);

  // Employee quit / leave process
  await page.goto("http://localhost:5173/#/hrm/employee/list");
  await page.waitForTimeout(800);
  const eText = await text();
  out.employeeStatusTabs = (eText.match(/在职（\d+）|待离职（\d+）|已离职（\d+）|试用（\d+）/g) || []).join(" | ");
  // open first row more menu if possible
  const more = page.locator("button", { hasText: /更多|离职|异动|调岗/ }).first();
  if (await more.count()) {
    await more.click().catch(() => {});
    await page.waitForTimeout(400);
    out.employeeMoreMenu = (await text()).slice(-350);
    await page.keyboard.press("Escape");
  }

  // Attendance leave approval empty-state with create absence
  await page.goto("http://localhost:5173/#/hrm/attendance/leave");
  await page.waitForTimeout(700);
  out.leaveBody = (await text()).slice(350, 650);
  out.leaveButtons = (await page.locator("button").allInnerTexts()).map(s => s.trim()).filter(Boolean);

  // Salary attendance-rule actual path proof
  await page.goto("http://localhost:5173/#/hrm/salary/attendance-rule");
  await page.waitForTimeout(600);
  out.ruleOkTitle = await page.title();
  await page.goto("http://localhost:5173/#/hrm/salary/config/attendance-rule");
  await page.waitForTimeout(600);
  out.ruleBadTitle = await page.title();

  // Portal recruit path
  await page.goto("http://localhost:5173/#/hrm/portal/recruit");
  await page.waitForTimeout(600);
  out.portalRecruitAdminTitle = await page.title();
  await page.goto("http://localhost:5173/#/portal/recruit");
  await page.waitForTimeout(800);
  out.portalRecruitTitle = await page.title();

  // Performance plan default vs filter
  await page.goto("http://localhost:5173/#/hrm/performance/plan");
  await page.waitForTimeout(800);
  const p1 = await text();
  out.perfDefault = (p1.match(/未开始（\d+）\s*进行中（\d+）\s*已归档（\d+）\s*已终止（\d+）/) || [])[0];
  out.perfDefaultList = (p1.match(/共\s*\d+\s*条记录/) || [])[0];
  await page.locator("text=进行中").first().click().catch(() => {});
  await page.waitForTimeout(600);
  const p2 = await text();
  out.perfFiltered = (p2.match(/共\s*\d+\s*条记录/) || [])[0];

  return out;
}
