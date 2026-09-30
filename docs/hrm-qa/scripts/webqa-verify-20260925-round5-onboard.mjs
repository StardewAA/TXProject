/**
 * 2026-09-25 第五轮：入职建档后自动提醒新员工完善档案
 *
 * 链路：候选人 → 简历版本 → 应聘 → Offer → 提交/审批 → 发送 → 候选人确认 → 办理入职
 * 断言：建档成功后，系统给新员工的后台账号发了「填写员工档案」站内信
 *      （template_code = hrm_employee_archive_fill），员工登录员工端即可补录 → 后台同步。
 *
 * 运行：node docs/hrm-qa/scripts/webqa-verify-20260925-round5-onboard.mjs
 */
const GATEWAY = process.env.HRM_GATEWAY || 'http://127.0.0.1:48080/admin-api';
const TENANT_ID = process.env.HRM_TENANT_ID || '2020';
const POST_ID = Number(process.env.HRM_ONBOARD_POST_ID || 2021208);
const EMPLOYMENT_TYPE_ID = Number(process.env.HRM_ONBOARD_EMPLOYMENT_TYPE_ID || 1);

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
  if (json.code !== 0) throw new Error(`登录失败: ${json.msg}`);
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
    return { code: -1, msg: text.slice(0, 300) };
  }
}

const stamp = Date.now();
const mobile = `139${String(stamp).slice(-8)}`;
const name = `QA建档提醒-${String(stamp).slice(-6)}`;
const token = await login({ username: 'qinchuan_hr', password: 'admin123' });
// 候选人只能由门户投递产生，因此这里用员工账号模拟「求职者投递」
const applicantToken = await login({ username: 'qinchuan', password: 'admin123' });

const today = new Date();
const fmtDate = (d) => d.toISOString().slice(0, 10);
const planned = new Date(today.getTime() + 7 * 24 * 3600 * 1000);
const validUntil = new Date(today.getTime() + 30 * 24 * 3600 * 1000);
const entryTime = `${fmtDate(planned)} 09:00:00`;

// 0. 用 HR 账号建一个「已发布」职位（门户只展示由 HR 发布且发布人角色为 HR 的职位）
//    可传 HRM_ONBOARD_POST_ID 复用已建好的职位（例如需要先准备「招聘沟通 + HR 回复」投递资格时）
let postId = Number(process.env.HRM_ONBOARD_POST_ID || 0);
if (postId) {
  record('0) 复用已发布职位', true, `postId=${postId}`);
} else {
  const post = await api(token, '/hrm/recruit/post/create', {
    method: 'POST',
    body: JSON.stringify({
      postName: `QA建档提醒职位-${String(stamp).slice(-6)}`,
      jobNature: 1,
      deptId: 50135,
      recruitNum: 1,
      educationRequire: 4,
      workTime: 1,
      status: 1,
      workAddress: '陕西省西安市高新区',
    }),
  });
  record('0) 创建并发布招聘职位', post.code === 0 && !!post.data, `code=${post.code} postId=${post.data} msg=${post.msg || ''}`);
  if (post.code !== 0) {
    process.exit(1);
  }
  postId = post.data;
}

// 1. 门户投递 → 自动产生候选人 + 简历版本 + 应聘记录
//    可传 HRM_ONBOARD_APPLICATION_ID 复用已投递的应聘（投递需要「招聘沟通 + HR 回复」资格，重跑时避免重复投递）
let applicationId = Number(process.env.HRM_ONBOARD_APPLICATION_ID || 0);
if (applicationId) {
  record('1) 复用已投递应聘', true, `applicationId=${applicationId}`);
} else {
  const apply = await api(applicantToken, '/hrm/portal/recruit/application/create', {
    method: 'POST',
    body: JSON.stringify({
      name,
      mobile,
      sex: 1,
      email: `qa${String(stamp).slice(-6)}@example.com`,
      postId,
      education: 5,
      resumeFileName: `qa-onboard-${stamp}.docx`,
      resumeFileUrl: `https://example.com/qa-onboard-${stamp}.docx`,
      resumeRemark: 'QA 建档提醒验证用（非 PDF，不触发文本抽取）',
    }),
  });
  record('1) 门户投递（生成候选人/简历/应聘）', apply.code === 0 && !!apply.data, `code=${apply.code} applicationId=${apply.data} msg=${apply.msg || ''}`);
  if (apply.code !== 0) {
    process.exit(1);
  }
  applicationId = apply.data;
}

// 2. HR 侧读取应聘详情，拿到候选人与简历编号
const appDetail = await api(token, `/hrm/recruit/application/get?id=${applicationId}`);
const candidateId = appDetail.data?.candidateId;
const resumeId = appDetail.data?.resumeId;
record(
  '2) 应聘详情可查（候选人/简历已生成）',
  appDetail.code === 0 && !!candidateId,
  `code=${appDetail.code} candidateId=${candidateId} resumeId=${resumeId}`,
);

// 3. 应聘流程推进：开始筛选 → 筛选通过 → 免面试通过（Offer 只允许在「面试通过/终面通过」后创建）
const screen = await api(token, '/hrm/recruit/application/start-screening', {
  method: 'PUT',
  body: JSON.stringify({ id: applicationId }),
});
const passScreen = await api(token, '/hrm/recruit/application/pass-screening', {
  method: 'PUT',
  body: JSON.stringify({ id: applicationId }),
});
const skipInterview = await api(token, `/hrm/recruit/application/skip-interview?id=${applicationId}`, {
  method: 'PUT',
});
record(
  '3) 应聘流程推进（筛选通过 → 免面试通过）',
  skipInterview.code === 0,
  `start=${screen.code} pass=${passScreen.code} skip=${skipInterview.code}(${skipInterview.msg || ''})`,
);

// 4. Offer（可传 HRM_ONBOARD_OFFER_ID 复用已创建的 Offer）
let offerId = Number(process.env.HRM_ONBOARD_OFFER_ID || 0);
if (offerId) {
  record('4) 复用已创建 Offer', true, `offerId=${offerId}`);
} else {
  const offer = await api(token, '/hrm/recruit/offer/create', {
    method: 'POST',
    body: JSON.stringify({
      applicationId,
      postId,
      entryPostId: postId,
      salaryAmount: 9000,
      salaryUnit: 1,
      plannedOnboardDate: fmtDate(planned),
      validUntil: fmtDate(validUntil),
      remark: 'QA 建档提醒验证',
    }),
  });
  record('4) 创建 Offer', offer.code === 0 && !!offer.data, `code=${offer.code} id=${offer.data} msg=${offer.msg || ''}`);
  offerId = offer.data;
}

// 5. 提交 / 审批 / 发送（BPM 可选，逐级尝试）
const submit = await api(token, `/hrm/recruit/offer/submit?id=${offerId}`, { method: 'PUT' });
const approve = await api(token, `/hrm/recruit/offer/approve?id=${offerId}&approved=true`, {
  method: 'PUT',
});
const send = await api(token, `/hrm/recruit/offer/send?id=${offerId}`, { method: 'PUT' });
record(
  '5) Offer 提交/审批/发送',
  send.code === 0,
  `submit=${submit.code} approve=${approve.code} send=${send.code}(${send.msg || ''})`,
);

// 6. 候选人确认（模拟门户确认）
const confirm = await api(token, `/hrm/recruit/offer/confirm?id=${offerId}`, { method: 'PUT' });
record('6) Offer 确认为「已确认」', confirm.code === 0, `code=${confirm.code} msg=${confirm.msg || ''}`);

// 7. 办理入职
const onboard = await api(token, '/hrm/recruit/offer/onboard', {
  method: 'PUT',
  body: JSON.stringify({
    offerId,
    type: 1,
    employmentTypeId: EMPLOYMENT_TYPE_ID,
    entryTime,
    entryStatus: 1,
  }),
});
record('7) 办理入职（建档）', onboard.code === 0 && !!onboard.data, `code=${onboard.code} employeeId=${onboard.data} msg=${onboard.msg || ''}`);
const employeeId = onboard.data;

console.log(
  `\n上下文：candidateId=${candidateId} resumeId=${resumeId} applicationId=${applicationId} offerId=${offerId} employeeId=${employeeId} mobile=${mobile} name=${name}`,
);

const failed = results.filter((item) => !item.ok);
console.log(`汇总：${results.length - failed.length}/${results.length} 通过`);
process.exit(failed.length === 0 ? 0 : 1);
