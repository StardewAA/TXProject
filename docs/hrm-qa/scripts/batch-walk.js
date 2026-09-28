async (page) => {
  const paths = [
    "/hrm/home","/hrm/team-home","/hrm/dept","/hrm/headcount","/hrm/company","/hrm/safety","/hrm/policy",
    "/hrm/recruit/demand","/hrm/recruit/chat","/hrm/recruit/post-type","/hrm/recruit/candidate","/hrm/recruit/post",
    "/hrm/recruit/settings/channel","/hrm/recruit/settings/eliminate-reason","/hrm/recruit/settings/company",
    "/hrm/employee/list","/hrm/employee/config","/hrm/employee/employment-type","/hrm/employee/blacklist","/hrm/employee/approval","/hrm/employee/no-rule",
    "/hrm/contract/template","/hrm/contract/sign-record","/hrm/contract/expire",
    "/hrm/certificate/ledger","/hrm/certificate/type","/hrm/certificate/warning",
    "/hrm/attendance/clock","/hrm/attendance/leave","/hrm/attendance/month","/hrm/attendance/config/group","/hrm/attendance/config/holiday",
    "/hrm/insurance/month-record","/hrm/insurance/config/scheme",
    "/hrm/salary/config/attendance-rule","/hrm/salary/month-record","/hrm/salary/piece-record","/hrm/salary/history","/hrm/salary/employee-info","/hrm/salary/slip",
    "/hrm/salary/config/group","/hrm/salary/config/tax-rule","/hrm/salary/config/config","/hrm/salary/config/option","/hrm/salary/config/change-template",
    "/hrm/performance/plan","/hrm/performance/assessment","/hrm/performance/config/assessment-template","/hrm/performance/config/result-template",
    "/hrm/portal/home","/hrm/portal/employee","/hrm/portal/insurance","/hrm/portal/salary/slip","/hrm/portal/attendance/report","/hrm/portal/performance/assessment","/hrm/portal/performance/history","/hrm/portal/recruit"
  ];
  const results = [];
  for (const p of paths) {
    try {
      await page.goto("http://localhost:5173/#" + p, { waitUntil: "domcontentloaded", timeout: 15000 });
      await page.waitForTimeout(700);
      const title = await page.title();
      const bodyText = (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 100);
      const interactive = await page.locator("button, input, .vxe-table, table").count();
      results.push({ path: p, title, interactive, bodyHint: bodyText });
    } catch (e) {
      results.push({ path: p, error: String(e).slice(0, 100) });
    }
  }
  return results;
}
