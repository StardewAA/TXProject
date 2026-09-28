async (page) => {
  const report = [];
  const snap = async (label) => {
    const t = await page.title();
    const body = (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ");
    report.push({ label, title: t, body: body.slice(0, 500) });
    return body;
  };

  // Fix path checks
  for (const p of ["/hrm/salary/attendance-rule", "/hrm/salary/config/attendance-rule", "/hrm/portal/recruit", "/portal/recruit"]) {
    await page.goto("http://localhost:5173/#" + p, { waitUntil: "domcontentloaded", timeout: 12000 });
    await page.waitForTimeout(600);
    await snap("path:" + p);
  }

  // --- Policy CRUD (simple page) ---
  await page.goto("http://localhost:5173/#/hrm/policy", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("policy:load");

  // Try open create dialog
  const createBtn = page.locator("button", { hasText: /新建|新增|添加|创建/ }).first();
  if (await createBtn.count()) {
    await createBtn.click().catch(() => {});
    await page.waitForTimeout(500);
    await snap("policy:create-dialog");
    // Required validation: submit empty
    const submit = page.locator("button", { hasText: /确\s*定|保\s*存|提\s*交|提\s*交/ }).last();
    if (await submit.count()) {
      await submit.click().catch(() => {});
      await page.waitForTimeout(400);
      await snap("policy:empty-submit");
    }
    // close
    await page.keyboard.press("Escape");
    await page.waitForTimeout(300);
  }

  // Query empty filter
  await snap("policy:query-default");

  // --- Employment type CRUD ---
  await page.goto("http://localhost:5173/#/hrm/employee/employment-type", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("employment-type:load");
  const cbtn = page.locator("button", { hasText: /新建|新增|添加|创建/ }).first();
  if (await cbtn.count()) {
    await cbtn.click().catch(() => {});
    await page.waitForTimeout(500);
    await snap("employment-type:create-dialog");
    await page.keyboard.press("Escape");
    await page.waitForTimeout(200);
  }

  // --- Recruit post-type ---
  await page.goto("http://localhost:5173/#/hrm/recruit/post-type", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("post-type:load");
  const pb = page.locator("button", { hasText: /新建|新增|添加|创建/ }).first();
  if (await pb.count()) {
    await pb.click().catch(() => {});
    await page.waitForTimeout(500);
    await snap("post-type:create-dialog");
    await page.keyboard.press("Escape");
  }

  // --- Recruit demand ---
  await page.goto("http://localhost:5173/#/hrm/recruit/demand", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("demand:load");
  const db = page.locator("button", { hasText: /新建|新增|添加|创建|提报/ }).first();
  if (await db.count()) {
    await db.click().catch(() => {});
    await page.waitForTimeout(500);
    await snap("demand:create-dialog");
    await page.keyboard.press("Escape");
  }

  // --- Employee list query ---
  await page.goto("http://localhost:5173/#/hrm/employee/list", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("employee:load");

  // --- Contract template ---
  await page.goto("http://localhost:5173/#/hrm/contract/template", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("contract-template:load");

  // --- Attendance leave ---
  await page.goto("http://localhost:5173/#/hrm/attendance/leave", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("leave:load");

  // --- Salary month-record ---
  await page.goto("http://localhost:5173/#/hrm/salary/month-record", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("salary-month:load");

  // --- Performance plan ---
  await page.goto("http://localhost:5173/#/hrm/performance/plan", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("perf-plan:load");

  // --- Insurance scheme ---
  await page.goto("http://localhost:5173/#/hrm/insurance/config/scheme", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("insurance-scheme:load");

  // --- Certificate type ---
  await page.goto("http://localhost:5173/#/hrm/certificate/type", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("cert-type:load");

  // --- Company profile ---
  await page.goto("http://localhost:5173/#/hrm/company", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("company:load");

  // --- Safety ---
  await page.goto("http://localhost:5173/#/hrm/safety", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("safety:load");

  // --- Recruit post ---
  await page.goto("http://localhost:5173/#/hrm/recruit/post", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("recruit-post:load");

  // --- Recruit candidate ---
  await page.goto("http://localhost:5173/#/hrm/recruit/candidate", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await snap("candidate:load");

  // --- Import/export button presence scan on key pages ---
  const importExport = {};
  for (const [name, p] of [["employee","/hrm/employee/list"],["attendance","/hrm/attendance/clock"],["salary","/hrm/salary/month-record"],["insurance","/hrm/insurance/month-record"]]) {
    await page.goto("http://localhost:5173/#" + p, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(700);
    const btns = await page.locator("button").allInnerTexts();
    importExport[name] = btns.filter(b => /导入|导出|模板|下载|上传/.test(b)).map(s => s.trim()).slice(0, 10);
  }
  report.push({ label: "import-export-buttons", importExport });

  return report;
}
