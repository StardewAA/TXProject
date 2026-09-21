/**
 * AI 助手「查询条件解析」回归自测清单
 *
 * 为什么要有它：
 *   条件解析决定「用户这句话到底筛什么」，历史上多次出现「一种说法修好了、
 *   换一种说法又漏了」。因此把已知口径固化成用例，改造 queryConditions.ts /
 *   mock.ts 之后**先跑这份清单，全绿再部署**。
 *
 * 运行方式（本机无需安装 Node，用 Docker 即可）：
 *   docker run --rm -v "<仓库>/txgy-ui-admin-vben:/app" -w /app \
 *     node:22-bookworm-slim \
 *     node --experimental-strip-types \
 *     apps/web-antd/src/views/crm/ai-chat/queryConditions.selftest.ts
 *
 * 判定标准：每条都必须与「期望」一致；出现任何 FAIL 都不要部署。
 */

import {
  dedupeById,
  followConditionLabel,
  isAuditStatusAsk,
  isCurrentMonth,
  isRuleQuestion,
  isToday,
  isUnsupportedTimeAsk,
  onlyAlive,
  pickAuditStatuses,
  pickFollowUpStatus,
  pickScopeSceneType,
  pickTransformStatus,
  transformConditionLabel,
} from './queryConditions.ts';

let total = 0;
const failures: string[] = [];
let section = '';

function label(): string {
  return section ? `[${section}] ` : '';
}

/** 逐个断言 */
function check(desc: string, actual: any, expected: any): void {
  total += 1;
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a !== e) {
    failures.push(`${label()}${desc} → 实际 ${a}，期望 ${e}`);
  }
}

function expect(desc: string, actual: any, expected: any): void {
  check(desc, actual, expected);
}

/** 分组 */
function group(name: string, fn: () => void): void {
  section = name;
  fn();
  section = '';
}

/* ====================== 1. 合同 / 报价单：审批状态 ====================== */
group('审批状态', () => {
  // 10 审批中（待审核）—— 这一族最容易漏，必须逐种说法覆盖
  const pending = [
    '待审核合同',
    '没审核的合同',
    '没审批的合同',
    '未审核合同',
    '未审批合同',
    '待审批的合同',
    '审批中的合同',
    '审核中的合同',
    '还没审核的合同',
    '还没审批的合同',
    '等待审核的合同',
    '尚未审核的合同',
    '没审的合同',
    '待审合同',
  ];
  for (const q of pending) {
    expect(q, pickAuditStatuses(q), [10]);
  }

  // 20 审核通过
  for (const q of [
    '审核通过的合同',
    '审批通过的合同',
    '已经审核通过的合同',
    '已审核的合同',
    '已审批的合同',
    '审核完成的合同',
    '已批准的合同',
  ]) {
    expect(q, pickAuditStatuses(q), [20]);
  }

  // 30 审核不通过
  for (const q of [
    '审核不通过的合同',
    '审批不通过的合同',
    '未通过的合同',
    '没通过的合同',
    '被驳回的合同',
    '被打回的合同',
  ]) {
    expect(q, pickAuditStatuses(q), [30]);
  }

  // 0 未提交 / 40 已取消
  expect('未提交的合同', pickAuditStatuses('未提交的合同'), [0]);
  expect('草稿合同', pickAuditStatuses('草稿合同'), [0]);
  expect('作废的合同', pickAuditStatuses('作废的合同'), [40]);

  // 否定问法：尚未通过审核的
  expect(
    '没有审核通过的合同',
    pickAuditStatuses('没有审核通过的合同'),
    [0, 10, 30, 40],
  );
  expect(
    '还没审核过的合同',
    pickAuditStatuses('还没审核过的合同'),
    [0, 10, 30, 40],
  );

  // 不带状态条件：绝不能误筛（返回空数组 = 不按状态筛选）
  for (const q of [
    '列出所有合同',
    '查询西安石油的合同',
    '本月所有合同',
    '报价单列表',
  ]) {
    expect(q, pickAuditStatuses(q), []);
  }

  // 报价单同样适用
  expect('审核通过的报价单', pickAuditStatuses('审核通过的报价单'), [20]);
  expect('没审批的报价单', pickAuditStatuses('没审批的报价单'), [10]);
});

/* =================== 2. 审批状态「兜底反问」的触发条件 =================== */
group('审批状态意图', () => {
  expect('合同审核情况', isAuditStatusAsk('合同审核情况'), true);
  expect('合同的审核状态', isAuditStatusAsk('合同的审核状态'), true);
  expect('没审批的合同', isAuditStatusAsk('没审批的合同'), true);
  // 与审批无关的查询不应触发反问
  expect('列出所有合同', isAuditStatusAsk('列出所有合同'), false);
  expect('查询西安石油的合同', isAuditStatusAsk('查询西安石油的合同'), false);
  expect('列出所有线索', isAuditStatusAsk('列出所有线索'), false);
});

/* ====================== 3. 跟进状态（客户 / 线索） ====================== */
group('跟进状态', () => {
  for (const q of [
    '待跟进的客户',
    '需要跟进的客户',
    '还没跟进的客户',
    '哪些客户等着跟进',
    '没人跟进的客户',
    '未跟进的线索',
    '没跟进的线索',
    '需要联系的客户',
  ]) {
    expect(q, pickFollowUpStatus(q), false);
  }

  for (const q of [
    '我的已跟进线索',
    '已跟进的客户',
    '跟进过的线索',
    '已经联系过的客户',
  ]) {
    expect(q, pickFollowUpStatus(q), true);
  }

  // 同时提到正反口径 → 该维度不筛选（两者都给）
  expect(
    '已跟进和待跟进的线索',
    pickFollowUpStatus('已跟进和待跟进的线索'),
    undefined,
  );
  // 完全没提 → 不筛选
  expect('列出所有线索', pickFollowUpStatus('列出所有线索'), undefined);
  expect('查询演示客户', pickFollowUpStatus('查询演示客户'), undefined);

  // 条件文案
  expect('已跟进标签', followConditionLabel('我的已跟进线索'), '已跟进');
  expect('待跟进标签', followConditionLabel('待跟进的客户'), '待跟进');
  expect('无条件标签', followConditionLabel('列出所有线索'), '');
  expect(
    '同时提到时不给标签',
    followConditionLabel('已跟进和待跟进的线索'),
    '',
  );
});

/* ====================== 4. 转化状态（线索） ====================== */
group('转化状态', () => {
  expect('已转化的线索', pickTransformStatus('已转化的线索'), true);
  expect('已经成客户的线索', pickTransformStatus('已经转成客户的线索'), true);
  expect('未转化的线索', pickTransformStatus('未转化的线索'), false);
  expect('没成交的线索', pickTransformStatus('没成交的线索'), false);
  expect('列出所有线索', pickTransformStatus('列出所有线索'), undefined);
  expect('已转化标签', transformConditionLabel('已转化的线索'), '已转化');
  expect('未转化标签', transformConditionLabel('未转化的线索'), '未转化');
  // 与跟进口径互不干扰
  expect(
    '我的已跟进且未转化的线索',
    pickTransformStatus('我的已跟进且未转化的线索'),
    false,
  );
});

/* ====================== 5. 归属范围（数据权限口径） ====================== */
group('归属范围', () => {
  // 我的 → 仅本人
  for (const q of [
    '列出我的线索',
    '我负责的客户',
    '我名下的合同',
    '我自己跟进的客户',
  ]) {
    expect(q, pickScopeSceneType(q), 1);
  }
  // 所有 / 未指定 → 我 + 下属
  for (const q of [
    '列出所有线索',
    '全部合同',
    '所有客户',
    '列出线索',
    '查询报价单',
  ]) {
    expect(q, pickScopeSceneType(q), undefined);
  }
  // 下属 / 团队 → 仅下属
  for (const q of ['我下属的客户', '团队成员的线索', '手下负责的客户']) {
    expect(q, pickScopeSceneType(q), 3);
  }
  // 「我的下属」应走下属口径，而不是「我的」
  expect('我的下属的客户', pickScopeSceneType('我的下属的客户'), 3);
});

/* ====================== 6. 时间口径（今天 / 本月 / 不支持） ====================== */
group('时间口径', () => {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const todayString = `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} 09:21:22`;
  const yesterday = Date.now() - 2 * 24 * 3600 * 1000;

  expect('今天(毫秒时间戳)', isToday(Date.now()), true);
  expect('今天(字符串)', isToday(todayString), true);
  expect('不是今天', isToday(yesterday), false);
  expect('空值不是今天', isToday(null), false);

  expect('本月(毫秒时间戳)', isCurrentMonth(Date.now()), true);
  expect('不是本月', isCurrentMonth(Date.now() - 40 * 24 * 3600 * 1000), false);
  expect('空值不是本月', isCurrentMonth(undefined), false);

  // 暂未支持的时间口径：必须能识别出来（用于明确告知，而不是静默查全部）
  for (const q of [
    '上个月的合同',
    '上个月的线索',
    '去年成交的客户',
    '上周的回款',
    '昨天的跟进记录',
    '上个季度的商机',
  ]) {
    expect(q, isUnsupportedTimeAsk(q), true);
  }
  for (const q of [
    '本月所有合同',
    '今天的待办',
    '列出所有线索',
    '客户公海回收的规则是什么',
  ]) {
    expect(q, isUnsupportedTimeAsk(q), false);
  }
});

/* ====================== 7. 业务规则问答 vs 查数据 ====================== */
group('规则问答判定', () => {
  for (const q of [
    '客户公海回收的规则是什么',
    '商机阶段怎么流转',
    '回款逾期怎么算',
    '跟进记录和下次联系时间有什么区别',
    '质保期是什么',
    '你能做什么',
  ]) {
    expect(q, isRuleQuestion(q), true);
  }
  for (const q of [
    '列出所有线索',
    '今日带班要联系的客户有哪些',
    '客户公海有哪些客户',
    '查看我的待办',
    '查询待回访任务',
    '不同类型的客户有哪些',
    '30天没有跟进即将流入公海的客户',
    '今天天气怎么样',
    '帮我给演示客户新建一个3天后电话回访的待办',
  ]) {
    expect(q, isRuleQuestion(q), false);
  }
});

/* ====================== 8. 逻辑删除过滤 + 按业务 ID 去重 ====================== */
group('软删除与去重', () => {
  const rows = [
    { id: 1, name: '正常' },
    { id: 2, name: '已删1', deleted: 1 },
    { id: 3, name: '正常2', deleted: '0' },
    { id: 4, name: '已删true', deleted: true },
    { id: 5, name: '已删字节', deleted: { type: 'Buffer', data: [1] } },
    { id: 6, name: '未删字节', deleted: { type: 'Buffer', data: [0] } },
    { id: 7, name: '已删字符串', deleted: '1' },
    { id: 8, name: '正常3', deleted: false },
  ];
  expect(
    '软删除记录被剔除',
    onlyAlive(rows).map((r) => r.id),
    [1, 3, 6, 8],
  );

  expect(
    '按业务 ID 去重',
    dedupeById([
      { id: 1, v: 'a' },
      { id: 1, v: 'b' },
      { id: 2, v: 'c' },
      {},
      { name: '无ID' },
    ]).length,
    4,
  );
  expect(
    '去重保留首条',
    dedupeById([
      { id: 1, v: 'a' },
      { id: 1, v: 'b' },
    ])[0].v,
    'a',
  );
  expect('空数组安全', dedupeById([] as any[]).length, 0);
  expect('空数组安全(过滤)', onlyAlive([] as any[]).length, 0);
});

/* ============================== 结果汇总 ============================== */

const passed = total - failures.length;
console.log(`\n==== AI 查询条件自测：${passed}/${total} 通过 ====\n`);
if (failures.length > 0) {
  console.log('未通过的用例：');
  for (const f of failures) {
    console.log('  ❌ ' + f);
  }
  console.log('');
  (globalThis as any).process?.exit?.(1);
} else {
  console.log('全部通过 ✅ 可以部署\n');
}
