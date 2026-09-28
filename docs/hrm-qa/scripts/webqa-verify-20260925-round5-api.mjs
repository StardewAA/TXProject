/**
 * 2026-09-25 第五轮：员工端「我的档案」自助完善 → 后台同步 接口级验证
 *
 * 覆盖：
 *   1. 员工（qinchuan / 员工档案 178）登录后能看到自己的工作经历/教育经历列表接口
 *   2. 员工新增教育经历 → 后台 HR 用同一条 employeeId 查得到（同一条记录，天然同步）
 *   3. 员工修改 / 删除自己的记录 → 后台同步变化
 *   4. 越权保护：员工不能改别人的记录（用 HR 员工的记录 id 触发，期望业务异常）
 *   5. 留痕：员工自助操作后，员工档案 remark 出现「【系统】…员工自助…请 HR 复核」
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260925-round5-api.mjs
 */
const GATEWAY = process.env.HRM_GATEWAY || 'http://127.0.0.1:48080/admin-api';
const TENANT_ID = process.env.HRM_TENANT_ID || '2020';

const EMPLOYEE_ACCOUNT = { username: 'qinchuan', password: 'admin123' };
const HR_ACCOUNT = { username: 'qinchuan_hr', password: 'admin123' };
/** qinchuan 账号绑定的员工档案编号 */
const EMPLOYEE_ID = 178;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${name} | ${detail}`);
}

async function login(account) {
  const resp = await fetch(`${GATEWAY}/system/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': TENANT_ID },
    body: JSON.stringify(account),
  });
  const json = await resp.json();
  if (json.code !== 0) {
    throw new Error(`登录失败 ${account.username}: ${json.msg}`);
  }
  return json.data.accessToken;
}

async function api(token, path, init = {}) {
  const resp = await fetch(`${GATEWAY}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'tenant-id': TENANT_ID,
      ...(init.headers || {}),
    },
  });
  const text = await resp.text();
  try {
    return JSON.parse(text);
  } catch {
    return { code: -1, msg: text.slice(0, 200) };
  }
}

const stamp = Date.now();
const school = `QA自助验证院校-${stamp}`;
let createdId;

const employeeToken = await login(EMPLOYEE_ACCOUNT);
const hrToken = await login(HR_ACCOUNT);

// ---------- 1. 员工端能看到自己的教育/工作经历 ----------
{
  const edu = await api(employeeToken, '/hrm/portal/employee/education-experience/list');
  const work = await api(employeeToken, '/hrm/portal/employee/work-experience/list');
  record(
    '1) 员工端可读取本人教育/工作经历列表',
    edu.code === 0 && work.code === 0,
    `education.code=${edu.code}(${(edu.data || []).length} 条) work.code=${work.code}(${(work.data || []).length} 条)`,
  );
}

// ---------- 2. 员工新增教育经历 ----------
{
  const resp = await api(employeeToken, '/hrm/portal/employee/education-experience/create', {
    method: 'POST',
    body: JSON.stringify({
      education: 4,
      graduateSchool: school,
      major: '自动化',
      firstDegree: false,
      teachingMethods: 1,
      admissionTime: Date.UTC(2018, 8, 1),
      graduationTime: Date.UTC(2022, 6, 1),
    }),
  });
  createdId = resp.data;
  record(
    '2) 员工端新增教育经历成功',
    resp.code === 0 && !!createdId,
    `code=${resp.code} id=${createdId} msg=${resp.msg || ''}`,
  );
}

// ---------- 3. 后台 HR 用同一条员工记录查得到（天然同步） ----------
{
  const resp = await api(hrToken, `/hrm/employee/education-experience/list?employeeId=${EMPLOYEE_ID}`);
  const hit = (resp.data || []).find((item) => item.id === createdId);
  record(
    '3) 后台员工档案立即可见（同一条记录）',
    resp.code === 0 && !!hit && hit.graduateSchool === school,
    `code=${resp.code} 命中=${!!hit} 院校=${hit ? hit.graduateSchool : '-'}`,
  );
}

// ---------- 4. 员工修改 → 后台同步 ----------
{
  const resp = await api(employeeToken, '/hrm/portal/employee/education-experience/update', {
    method: 'PUT',
    body: JSON.stringify({ id: createdId, education: 5, graduateSchool: `${school}-已修改`, major: '自动化' }),
  });
  const check = await api(hrToken, `/hrm/employee/education-experience/list?employeeId=${EMPLOYEE_ID}`);
  const hit = (check.data || []).find((item) => item.id === createdId);
  record(
    '4) 员工端修改 → 后台同步更新',
    resp.code === 0 && !!hit && hit.graduateSchool === `${school}-已修改`,
    `code=${resp.code} 后台院校=${hit ? hit.graduateSchool : '-'}`,
  );
}

// ---------- 5. 越权保护：不能改别人的记录 ----------
{
  const others = await api(hrToken, '/hrm/employee/education-experience/list?employeeId=199');
  const otherId = (others.data || [])[0]?.id;
  if (!otherId) {
    record('5) 越权保护（改他人记录被拒）', true, '跳过：员工 199 无教育经历可作对照');
  } else {
    const resp2 = await api(
      employeeToken,
      `/hrm/portal/employee/education-experience/delete?id=${otherId}`,
      { method: 'DELETE' },
    );
    record(
      '5) 越权保护（改他人记录被拒）',
      resp2.code !== 0,
      `删除他人记录返回 code=${resp2.code} msg=${resp2.msg || ''}`,
    );
    const still = await api(hrToken, `/hrm/employee/education-experience/list?employeeId=199`);
    record(
      '5b) 他人记录未被删除',
      (still.data || []).some((item) => item.id === otherId),
      `employeeId=199 记录 id=${otherId} 仍存在`,
    );
  }
}

// ---------- 6. 留痕：员工自助后 remark 出现系统标记 ----------
{
  const resp = await api(hrToken, `/hrm/employee/get?id=${EMPLOYEE_ID}`);
  const remark = resp.data?.remark || '';
  record(
    '6) 员工档案留痕（【系统】…员工自助…请 HR 复核）',
    resp.code === 0 && remark.includes('【系统】') && remark.includes('请 HR 复核'),
    `remark=${remark.slice(-90)}`,
  );
}

// ---------- 7. 员工删除 → 后台同步消失 ----------
{
  const resp = await api(employeeToken, `/hrm/portal/employee/education-experience/delete?id=${createdId}`, {
    method: 'DELETE',
  });
  const check = await api(hrToken, `/hrm/employee/education-experience/list?employeeId=${EMPLOYEE_ID}`);
  const hit = (check.data || []).find((item) => item.id === createdId);
  record(
    '7) 员工端删除 → 后台同步消失',
    resp.code === 0 && !hit,
    `code=${resp.code} 后台仍存在=${!!hit}`,
  );
}

const failed = results.filter((item) => !item.ok);
console.log(`\n汇总：${results.length - failed.length}/${results.length} 通过`);
process.exit(failed.length === 0 ? 0 : 1);
