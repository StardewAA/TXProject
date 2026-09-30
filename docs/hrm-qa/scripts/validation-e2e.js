async (page) => {
  const out = { validation: [], permission: {}, e2eA: [], e2eB: [], notes: [] };
  const text = async () => (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");

  // 1) Policy empty-submit validation
  await page.goto("http://localhost:5173/#/hrm/policy", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  await page.locator("button", { hasText: /新增/ }).first().click();
  await page.waitForTimeout(400);
  // force submit empty
  const confirmBtns = page.locator(".ant-modal button, [class*=modal] button, [role=dialog] button");
  const n = await confirmBtns.count();
  for (let i = 0; i < n; i++) {
    const t = (await confirmBtns.nth(i).innerText().catch(() => "")).trim();
    if (/确\s*定|保\s*存|提\s*交/.test(t)) {
      await confirmBtns.nth(i).click().catch(() => {});
      break;
    }
  }
  await page.waitForTimeout(500);
  out.validation.push({ step: "policy-empty-submit", body: (await text()).slice(-400) });
  await page.keyboard.press("Escape");

  // 2) Performance plan count vs list
  await page.goto("http://localhost:5173/#/hrm/performance/plan", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(900);
  const perfBody = await text();
  out.validation.push({ step: "perf-plan-counts", body: perfBody.slice(200, 700) });

  // 3) Attendance-rule menu path mismatch confirmation
  out.validation.push({ step: "paths", correct_salary_rule: "/hrm/salary/attendance-rule OK", wrong: "/hrm/salary/config/attendance-rule 404", portal_recruit_admin: "/hrm/portal/recruit 404", portal_recruit_ok: "/portal/recruit OK" });

  // 4) Employee query empty result
  await page.goto("http://localhost:5173/#/hrm/employee/list", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  const nameInput = page.locator("input[placeholder*=姓名], input[placeholder*=员工]").first();
  if (await nameInput.count()) {
    await nameInput.fill("不存在的员工XYZQQ");
    const search = page.locator("button", { hasText: /搜\s*索/ }).first();
    if (await search.count()) await search.click();
    await page.waitForTimeout(800);
    out.validation.push({ step: "employee-empty-query", body: (await text()).slice(-350) });
  }

  // 5) Recruit post status tabs
  await page.goto("http://localhost:5173/#/hrm/recruit/post", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.validation.push({ step: "recruit-post-tabs", body: (await text()).slice(350, 750) });

  // 6) Candidate funnel
  await page.goto("http://localhost:5173/#/hrm/recruit/candidate", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.validation.push({ step: "candidate-funnel", body: (await text()).slice(350, 750) });

  // 7) E2E A smoke: demand -> post linked view
  await page.goto("http://localhost:5173/#/hrm/recruit/demand", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.e2eA.push({ step: "demand-list", body: (await text()).slice(350, 800) });
  // open first demand if any
  const viewBtn = page.locator("button, a", { hasText: /查看|详情|编辑|修改/ }).first();
  if (await viewBtn.count()) {
    await viewBtn.click().catch(() => {});
    await page.waitForTimeout(700);
    out.e2eA.push({ step: "demand-detail", body: (await text()).slice(300, 900) });
    await page.keyboard.press("Escape");
  }

  // 8) E2E A: post publish states and create dialog
  await page.goto("http://localhost:5173/#/hrm/recruit/post", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const newPost = page.locator("button", { hasText: /新增|新建|发布/ }).first();
  if (await newPost.count()) {
    await newPost.click().catch(() => {});
    await page.waitForTimeout(600);
    out.e2eA.push({ step: "post-create-dialog", body: (await text()).slice(-600) });
    await page.keyboard.press("Escape");
  }

  // 9) Offer-related UI on candidate
  await page.goto("http://localhost:5173/#/hrm/recruit/candidate", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const candBtns = await page.locator("button").allInnerTexts();
  out.e2eA.push({ step: "candidate-actions", buttons: candBtns.map(s => s.trim()).filter(Boolean).slice(0, 40) });

  // 10) E2E B: attendance config
  await page.goto("http://localhost:5173/#/hrm/attendance/config/group", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  out.e2eB.push({ step: "attendance-group", body: (await text()).slice(350, 750) });
  const ag = page.locator("button", { hasText: /新增|新建/ }).first();
  if (await ag.count()) {
    await ag.click().catch(() => {});
    await page.waitForTimeout(500);
    out.e2eB.push({ step: "attendance-group-create", body: (await text()).slice(-500) });
    await page.keyboard.press("Escape");
  }

  // 11) E2E B: employee create dialog fields
  await page.goto("http://localhost:5173/#/hrm/employee/list", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  const empNew = page.locator("button", { hasText: /新增/ }).first();
  if (await empNew.count()) {
    await empNew.click().catch(() => {});
    await page.waitForTimeout(600);
    out.e2eB.push({ step: "employee-create-dialog", body: (await text()).slice(-700) });
    await page.keyboard.press("Escape");
  }

  // 12) Leave approval empty + export
  await page.goto("http://localhost:5173/#/hrm/attendance/leave", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  const leaveBtns = await page.locator("button").allInnerTexts();
  out.e2eB.push({ step: "leave-buttons", buttons: leaveBtns.map(s => s.trim()).filter(Boolean).slice(0, 30), empty: (await text()).includes("暂无数据") });

  // 13) Contract sign-record
  await page.goto("http://localhost:5173/#/hrm/contract/sign-record", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.e2eB.push({ step: "contract-sign", body: (await text()).slice(350, 750) });

  // 14) Portal home as admin
  await page.goto("http://localhost:5173/#/hrm/portal/home", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.notes.push({ step: "portal-home-admin", body: (await text()).slice(350, 750) });

  // 15) Dept page
  await page.goto("http://localhost:5173/#/hrm/dept", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(700);
  out.e2eB.push({ step: "dept", body: (await text()).slice(350, 800) });

  return out;
}
