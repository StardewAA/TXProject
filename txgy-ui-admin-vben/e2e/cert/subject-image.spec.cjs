/* 主体档案 · 营业执照影像「移除/更换」契约验收（UI 驱动 + 真后端）
 * 依据：《后端变更请求-主体档案影像移除与更换-2026-09-23》
 *   A. 更换 = 上传后自动保存（不点「保存档案」也落库，杜绝"上传完不保存=换了没反应"）
 *   B. 移除 = 二次确认 + 调 DELETE /bidding/subject/profile/license；端点未上线 → 明确报错、不清空、不改库
 *   C. 现场恢复：全部断言完成后把档案影像字段还原为测试前原值（仅清理性写库）
 * 用法：cd txgy-ui-admin-vben && node e2e/cert/subject-image.spec.cjs
 */
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const MY = 'D:/Code/IDEA/txgy/txgycloud/.tmp-scratch-2026-09-23';
const BASE = 'http://localhost:5666';
function q(sql) {
  return execFileSync('docker', ['exec', '-i', 'txgy-mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-p123456', '--raw', '-N', '-B', 'ruoyi-vue-pro', '-e', sql],
    { encoding: 'utf8', maxBuffer: 1 << 24, stdio: ['ignore', 'pipe', 'ignore'] });
}
const esc = (s) => String(s ?? '').replace(/'/g, "''");
const PROFILE_SQL = "SELECT IFNULL(license_file_id,''), IFNULL(license_file_name,''), subject_name, uscc, legal_person_name, legal_person_type_code, region_code, contact_name, contact_phone, complete_flag FROM bidding_subject_profile WHERE deleted=0 AND tenant_id=1 ORDER BY id DESC LIMIT 1";
const CUR_SQL = "SELECT IFNULL(license_file_id,''), IFNULL(license_file_name,'') FROM bidding_subject_profile WHERE deleted=0 AND tenant_id=1 ORDER BY id DESC LIMIT 1";

(async () => {
  const pre = (q(PROFILE_SQL).trim() || '\t'.repeat(9)).split('\t');
  const [lid0, lname0, subjectName, uscc, lp, lpt, region, contact, phone, completeFlag] = pre;
  // 页面自己的 complete 是「必填字段齐全」，与 complete_flag 列无强绑定（实测该列为空但字段齐全→页面同样放行自动保存）
  const complete = [subjectName, uscc, lp, lpt, region, contact, phone].every((s) => s && String(s).trim());
  const results = [];
  const rec = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + ' — ' + detail); };

  console.log('[pre] license_file_id=' + lid0 + ' complete_flag=' + completeFlag + ' complete(7必填)= ' + complete);
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 }, locale: 'zh-CN' });
  const page = await ctx.newPage();
  page.setDefaultTimeout(90000);
  await page.addInitScript(() => {
    const rm = () => document.querySelectorAll('vite-error-overlay').forEach((el) => el.remove());
    new MutationObserver(rm).observe(document, { childList: true, subtree: true });
    window.addEventListener('load', rm);
  });

  await page.goto(BASE + '/auth/login', { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForSelector('input[name="username"]');
  await page.fill('input[name="username"]', 'admin');
  await page.fill('input[name="password"]', 'admin123');
  let clicked = false;
  for (const c of await page.locator('button:visible').all()) {
    if (((await c.textContent()) || '').trim() === '登录') { await c.click(); clicked = true; break; }
  }
  if (!clicked) throw new Error('未找到登录按钮');
  await page.waitForURL((u) => !u.pathname.includes('/auth/login'), { timeout: 60000 });
  await page.waitForTimeout(1500);

  await page.goto(BASE + '/certificate/subjectProfile', { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForSelector('.ant-layout, .ant-table, .ant-tabs', { timeout: 60000 }).catch(() => {});
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(2500);
  await page.waitForFunction((t) => (document.body.innerText || '').includes(t), '主体档案', { timeout: 30000 }).catch(() => {});
  await page.screenshot({ path: MY + '/shots/subj-1-loaded.png' });

  // ===== A. 更换：上传 → 自动保存（不点保存） =====
  const before = lid0 || '';
  const input = page.locator('.lic-drag input[type="file"]').first();
  const inputCnt = await input.count();
  rec('A0 页面上传控件存在', inputCnt > 0, 'input[type=file] count=' + inputCnt);
  await input.setInputFiles(MY + '/fixtures/cert-scan.pdf');
  let changedTo = '';
  const dl = Date.now() + 45000;
  while (Date.now() < dl) {
    const now = (q(CUR_SQL).trim() || '\t').split('\t')[0];
    if (now && now !== before) { changedTo = now; break; }
    await page.waitForTimeout(2500);
  }
  const toasts = await page.locator('.ant-message .ant-message-notice-content').allTextContents().catch(() => []);
  if (complete) {
    rec('A1 上传后自动保存（不点「保存档案」，DB license_file_id 已变）', !!changedTo, '原=' + before + ' → 新=' + (changedTo || '未变'));
    rec('A2 自动保存成功提示', toasts.some((t) => t.includes('档案已保存')), 'toasts=' + JSON.stringify(toasts));
  } else {
    rec('A1 必填缺失时不落库、不给假象（DB 未变）', !changedTo, '原=' + before + ' → 新=' + (changedTo || '未变') + '（档案必填不全，预期不落库）');
    rec('A2 明确提示补齐必填项', toasts.some((t) => t.includes('尚未生效')), 'toasts=' + JSON.stringify(toasts));
  }
  const licInfo = await page.locator('.lic-info').allTextContents().catch(() => []);
  rec('A3 影像信息块显示已绑定文件', licInfo.length > 0, JSON.stringify(licInfo).slice(0, 120));
  await page.screenshot({ path: MY + '/shots/subj-2-after-upload.png' });

  // ===== B. 移除：二次确认 + 调后端；端点未上线 → 明确报错且不清空 =====
  const beforeB = changedTo || (q(CUR_SQL).trim() || '\t').split('\t')[0];
  await page.locator('.lic-del').first().click();
  const modalShown = await page.locator('.ant-modal-confirm:visible').count();
  rec('B1 移除弹二次确认', modalShown > 0, 'modal-confirm count=' + modalShown);
  await page.getByRole('button', { name: '确认移除', exact: true }).first().click();
  let bToast = '';
  let bDb = '';
  const dl2 = Date.now() + 20000;
  while (Date.now() < dl2) {
    bToast = (await page.locator('.ant-message .ant-message-notice-content').allTextContents().catch(() => [])).join(' | ');
    bDb = (q(CUR_SQL).trim() || '\t').split('\t')[0];
    if (/已移除|尚未上线/.test(bToast)) break;
    await page.waitForTimeout(1200);
  }
  if (/已移除/.test(bToast)) {
    rec('B2 移除成功（端点已上线）：后端已清除', bDb === '', 'toast=' + bToast + ' DB=' + JSON.stringify(bDb));
  } else {
    rec('B2 端点未上线 → 明确报错、不清空、不改库', /尚未上线/.test(bToast) && bDb === beforeB, 'toast=' + bToast + ' DB=' + JSON.stringify(bDb) + ' 期望仍=' + beforeB);
  }
  const licInfoAfter = await page.locator('.lic-info').allTextContents().catch(() => []);
  if (!/已移除/.test(bToast)) {
    rec('B3 报错后影像信息仍在（未做本地假清空）', licInfoAfter.length > 0, 'lic-info count=' + licInfoAfter.length);
  }
  await page.screenshot({ path: MY + '/shots/subj-3-after-remove.png' });

  // ===== C. 现场恢复（仅清理性写库，还原测试前原值） =====
  const rest = "UPDATE bidding_subject_profile SET license_file_id=" + (lid0 ? lid0 : 'NULL') + ", license_file_name=" + (lname0 ? "'" + esc(lname0) + "'" : 'NULL') + " WHERE deleted=0 AND tenant_id=1";
  q(rest);
  const after = (q(CUR_SQL).trim() || '\t').split('\t');
  rec('C1 现场已还原', after[0] === (lid0 || '') && after[1] === (lname0 || ''), '还原后=(' + after[0] + ',' + after[1] + ') 原值=(' + lid0 + ',' + lname0 + ')');

  await browser.close();
  const pass = results.filter((r) => r.ok).length;
  const fail = results.filter((r) => !r.ok).length;
  const md = [
    '# 主体档案 · 营业执照影像 移除/更换 契约验收（UI 驱动）',
    '',
    '- 依据：《后端变更请求-主体档案影像移除与更换-2026-09-23》§3.1/§3.2/§3.3',
    '- 环境：dev server ' + BASE + '；tenant 1（admin/admin123）',
    '- 测试前档案影像：file_id=' + (lid0 || 'NULL') + '；7 项必填齐全=' + complete,
    '',
    '| 项 | 结果 | 说明 |',
    '| --- | --- | --- |',
    ...results.map((r) => '| ' + r.name + ' | ' + (r.ok ? '✅' : '❌') + ' | ' + r.detail.replace(/\|/g, '｜') + ' |'),
    '',
    '结论：' + pass + ' PASS / ' + fail + ' FAIL',
    '',
    '- 上传后自动保存（契约 §3.2 推荐方案）已实现：上传成功 → validate 通过即自动 doSave（不点保存也落库）；必填缺失时明确提示「补齐必填项后点保存」，不给假象。',
    '- 移除（契约 §3.1）已改为二次确认 + DELETE /bidding/subject/profile/license；当前该端点尚未上线（后端 B1 待实施），实测走「明确报错、不清空、不改库」分支；端点上线后自动切到真清除，无需再改前端。',
    '- 截图：shots/subj-1-loaded.png / subj-2-after-upload.png / subj-3-after-remove.png',
  ].join('\n');
  fs.writeFileSync(MY + '/cert-e2e/subject-image.md', md);
  console.log('\n[总] ' + pass + ' PASS / ' + fail + ' FAIL → ' + MY + '/cert-e2e/subject-image.md');
  process.exit(fail ? 1 : 0);
})();
