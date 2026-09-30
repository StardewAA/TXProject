/**
 * CRM AI 助手 —— 查询条件解析（纯逻辑模块，零依赖、无副作用）
 *
 * 为什么单独拆出来：
 *   1) 这些函数决定「用户这句话到底筛什么」，是 AI 答错/答偏的高发区，
 *      必须能脱离浏览器、接口、字典单独测试（见 queryConditions.selftest.ts）；
 *   2) 只做纯字符串 / 时间判断，不 import 任何 API、组件、字典，
 *      因此可以用 `node --experimental-strip-types` 直接跑回归自测。
 *
 * 设计约定（针对「换个说法就失效」这类问题）：
 *   · 先做用词归一化，再用规则匹配，而不是逐条罗列短语；
 *   · 解析不出条件时，调用方必须**反问**，绝不静默按「全部」返回。
 */

/* --------------------------- 逻辑删除 + 按业务 ID 去重 --------------------------- */

/**
 * 判断一条记录是否已被逻辑删除
 *
 * 业务口径：只有「未打软删除标记」的记录才算系统当前有效存在的数据；
 * 软删除记录一律视为不存在，不参与检索、不返回结果。
 *
 * 两道防线：
 *   1) 后端：各分页/详情接口由 MyBatis-Plus 逻辑删除自动过滤 deleted=0（主防线）；
 *   2) 前端：本函数 + onlyAlive() 兜底 —— 若响应里带出任何删除标记
 *      （deleted / isDeleted / delFlag，值为 1/'1'/true/'true'/字节数组等），
 *      直接在 AI 层剔除，任何查询场景都不展示。
 */
export function isDeletedRow(r: any): boolean {
  if (!r || typeof r !== 'object') {
    return false;
  }
  const d = r.deleted ?? r.isDeleted ?? r.delFlag;
  if (d === null || d === undefined || d === '' || d === false) {
    return false;
  }
  if (d === 0 || d === '0') {
    return false;
  }
  if (d === 1 || d === '1' || d === true || d === 'true') {
    return true;
  }
  // 兼容 Buffer / {type:'Buffer', data:[1]} 这类序列化形式
  if (typeof d === 'object') {
    const bytes = Array.isArray((d as any).data)
      ? (d as any).data
      : Array.isArray(d)
        ? d
        : null;
    if (bytes && bytes.length > 0) {
      return Number(bytes[0]) !== 0;
    }
    return false;
  }
  // 其它形式：先按明确的「未删除」写法排除，再按数字/布尔语义兜底
  const s = String(d).trim().toLowerCase();
  if (['false', 'n', 'no'].includes(s)) {
    return false;
  }
  if (['y', 'yes'].includes(s)) {
    return true;
  }
  const n = Number(s);
  return Number.isFinite(n) ? n !== 0 : false;
}

/** 剔除已逻辑删除的记录（软删除数据一律不展示） */
export function onlyAlive<T>(rows: T[]): T[] {
  return (rows || []).filter((r) => !isDeletedRow(r));
}

/**
 * 按业务唯一 ID 去重：同一实体只保留一条
 * 没有 ID 的行无法判定唯一性，原样保留（不误删）
 */
export function dedupeById<T extends Record<string, any>>(
  rows: T[],
  idOf: (r: T) => any = (r: any) => r?.id,
): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const r of rows || []) {
    const raw = idOf(r);
    if (raw === null || raw === undefined || raw === '') {
      out.push(r);
      continue;
    }
    const key = String(raw);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
}

/* ------------------------------ 时间口径（今天 / 本月） ------------------------------ */

export function nowMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function isCurrentMonth(v: any): boolean {
  if (!v) return false;
  // 兼容毫秒时间戳：后端 LocalDateTime 以时间戳序列化（如 1789454512000），
  // 直接 startsWith('2026-09') 永远匹配不上，会导致「本月」查询返回空
  const n = Number(v);
  if (Number.isFinite(n) && String(v).trim().length >= 12) {
    const d = new Date(n);
    if (!Number.isNaN(d.getTime())) {
      const now = new Date();
      return (
        d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()
      );
    }
  }
  return String(v).startsWith(nowMonth());
}

/**
 * 是否为「今天」（兼容毫秒时间戳与 YYYY-MM-DD HH:mm:ss 字符串）
 *
 * 用于「今日流入公海的客户」这类按天口径的筛选 —— 只取当天记录，
 * 绝不等于「全部公海客户」。
 */
export function isToday(v: any): boolean {
  if (!v) return false;
  let d: Date | null = null;
  const n = Number(v);
  if (Number.isFinite(n) && String(v).trim().length >= 12) {
    d = new Date(n);
  } else {
    // 兼容 "2026-09-20 09:21:22" / "2026-09-20T09:21:22"
    d = new Date(String(v).trim().replace('T', ' ').replace(/-/g, '/'));
  }
  if (!d || Number.isNaN(d.getTime())) return false;
  const now = new Date();
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  );
}

/**
 * 暂未支持的历史时间口径
 *
 * 目前只实现「今天」「本月」两种时间筛选；遇到「上周 / 昨天 / 上月 / 去年」这类条件时，
 * 必须明确告知，绝不把时间条件静默丢掉后按「全部」返回 ——
 * 那会让用户以为「系统里就这些数据」。
 */
export const UNSUPPORTED_TIME_RE =
  /上周|本周|这周|周内|昨天|昨日|前天|上月|上个月|去月|去年|上一年|上个季度|本季度|上季度|去年同|前年/;

/** 问句是否要求了「暂未支持的历史时间口径」（同时说了今天/本月时按已支持处理） */
export function isUnsupportedTimeAsk(q: string): boolean {
  const s = (q || '').trim();
  if (!s || !UNSUPPORTED_TIME_RE.test(s)) {
    return false;
  }
  return !/本月|当月|这个月|今天|今日/.test(s);
}

/* ------------------------------ 归属范围（数据权限） ------------------------------ */

/**
 * 计算有效归属范围（业务规则，必须严格区分，严禁混淆）
 *
 * 1. 「列出所有 XX / 全部 XX」    → 我负责的 + 下属负责的（管理者视角）
 * 2. 「列出我的 XX / 我负责的 XX」→ 仅本人负责（sceneType = 1），坚决排除下属数据
 * 3. 未指定归属（默认）           → 同「列出所有」：我负责的 + 下属负责的
 * 4. 「我的下属 / 团队成员」      → 仅下属负责（sceneType = 3）
 *
 * 说明：模型给的 sceneType 不能直接使用 —— 它默认返回 mine，
 * 会把「所有 XX」「未转化的线索」这类问法错误地缩到本人名下，导致下属数据整体消失。
 * 越权访问仍由后端数据权限强制拦截，这里只负责把业务口径翻译成查询条件。
 */
export function pickScopeSceneType(q: string): number | undefined {
  const s = q || '';
  const isSubordinate = /下属|下级|手下|团队成员|下属负责/.test(s);
  const isTeam = /团队|我们组|我们部门|本组/.test(s);
  // ②「我的」：排除同时提到下属/团队的情况（如「我的下属」应走下属口径）
  if (/我的|我负责|我名下|本人|自己/.test(s) && !isSubordinate && !isTeam) {
    return 1;
  }
  // ④「我的下属 / 团队成员」→ 仅下属
  if (isSubordinate) {
    return 3;
  }
  // ①③「所有 / 全部 / 未指定」→ 我负责的 + 下属负责的（不限定归属）
  return undefined;
}

/* --------------------------- 业务规则问答 vs 查询数据 --------------------------- */

/** 规则类问法：用户问的是“制度 / 口径 / 怎么算 / 区别”，不是在要数据列表 */
const RULE_ASK_RE =
  /规则|制度|机制|流程|政策|口径|标准|条件|怎么算|怎么计算|如何计算|怎么统计|如何统计|多久|多长时间|多少天|几天|判定|定义|含义|是什么意思|是什么|什么是|解释一下|说明一下|怎么回事|怎么样|怎么办|怎么回收|如何回收|怎么流转|如何流转|怎么转化|如何转化|怎么操作|如何操作|区别|不同|差异|能做什么|能干什么|有哪些功能|支持哪些|你会什么/;

/** 强规则信号：即使句子里出现「列出/查看/有哪些」也仍按规则问答处理 */
const STRONG_RULE_RE =
  /规则|制度|机制|流程|口径|标准|怎么算|怎么计算|如何计算|多久|多长时间|多少天|什么条件|含义|是什么意思|定义|怎么回收|如何回收|怎么流转|如何流转|怎么转化|如何转化/;

/** 明确的「要数据列表」问法 */
const DATA_ASK_RE =
  /^(列出|列一下|列举|列个|查看|查一下|查询|查下|看一下|看看|展示|显示|给我看看|给我看|找一下|帮我找|搜索|筛选)|有哪些/;

/** CRM 业务域词：避免把无关闲聊判成业务规则问答 */
const CRM_DOMAIN_RE =
  /公海|回收|认领|客户|线索|联系人|商机|阶段|赢单|输单|合同|回款|逾期|账期|待办|提醒|跟进|下次联系|售后|回访|质保|工单|报价|产品|WMS|库存|CRM|能做什么|能干什么|有哪些功能|支持哪些|你会什么/;

/**
 * 是否属于「业务规则 / 制度咨询」
 *
 * 判定依据：
 *   1. 必须落在 CRM 业务域内，且出现规则类问法；
 *   2. 若同时是明确的「列出 / 查看 / 有哪些」数据请求，且没有强规则信号
 *      （规则、制度、流程、怎么算、多久……），则仍按数据查询处理：
 *      例如「不同类型的客户有哪些」= 查数据；「客户公海回收的规则是什么」= 问规则。
 */
export function isRuleQuestion(q: string): boolean {
  const s = (q || '').trim();
  if (!s || !CRM_DOMAIN_RE.test(s) || !RULE_ASK_RE.test(s)) {
    return false;
  }
  if (DATA_ASK_RE.test(s) && !STRONG_RULE_RE.test(s)) {
    return false;
  }
  return true;
}

/* --------------------------- 跟进 / 转化状态口径 --------------------------- */

/** 反向口径：待跟进（未跟进）—— 口语同义「等着跟进 / 还没跟 / 没人跟」 */
const NEED_FOLLOW_RE =
  /待跟进|需要跟进|需跟进|要跟进|需要被跟进|未跟进|没跟进|没有跟进|等着跟进|等跟进|还没跟进|没人跟进|需要联系/;

/** 正向口径：已跟进（followUpStatus = true） */
const ALREADY_FOLLOW_RE = /已跟进|跟进过|跟过|已联系|联系过|已回访/;

/** 反向口径：未转化 */
const NO_TRANSFORM_RE = /未转化|没转化|没有转化|尚未转化|未成交|没成交/;

/** 正向口径：已转化（含「已经成客户」「转成客户」等口语说法） */
const HAS_TRANSFORM_RE =
  /已转化|转化成|转化成功|转为客户|转成客户|已成客户|成客户|已成单|已成交|已下单/;

/**
 * 跟进状态筛选：true = 已跟进、false = 待跟进、undefined = 不按跟进状态筛选
 *
 * 同时提到正反两种口径（如「已跟进和待跟进的线索」）时返回 undefined，
 * 表示该维度不筛选、两者都给。
 */
export function pickFollowUpStatus(q: string): boolean | undefined {
  const s = (q || '').trim();
  const need = NEED_FOLLOW_RE.test(s);
  const done = ALREADY_FOLLOW_RE.test(s);
  if (need && done) return undefined;
  if (done) return true;
  if (need) return false;
  return undefined;
}

/** 转化状态筛选：true = 已转化、false = 未转化、undefined = 不筛选 */
export function pickTransformStatus(q: string): boolean | undefined {
  const s = (q || '').trim();
  const no = NO_TRANSFORM_RE.test(s);
  const done = HAS_TRANSFORM_RE.test(s);
  if (no && done) return undefined;
  if (done) return true;
  if (no) return false;
  return undefined;
}

/** 跟进状态的中文条件文案（用于表格标题 / 无数据说明） */
export function followConditionLabel(q: string): string {
  const v = pickFollowUpStatus(q);
  if (v === true) return '已跟进';
  if (v === false) return '待跟进';
  return '';
}

/** 转化状态的中文条件文案 */
export function transformConditionLabel(q: string): string {
  const v = pickTransformStatus(q);
  if (v === true) return '已转化';
  if (v === false) return '未转化';
  return '';
}

/* --------------------------- 审批状态（合同 / 报价单） --------------------------- */

/**
 * 审批用词归一化
 *
 * 「审批」与「审核」在本系统是同一个动作的两种叫法：
 *   待审批 = 待审核、没审批 = 没审核、审批通过 = 审核通过、审批中 = 审核中。
 * 先归一化再匹配，避免「只列举某一种说法」导致换种说法就漏判。
 */
export function normalizeAuditWords(q: string): string {
  return (q || '')
    .trim()
    .replace(/审批/g, '审核')
    .replace(/(待|没|未|已|正在)批(?!准)/g, '$1审');
}

/**
 * 审批状态规则（在归一化后的文本上匹配）
 *
 * 与后端 CrmAuditStatusEnum 一致：0 未提交 / 10 审批中 / 20 审核通过 /
 * 30 审核不通过 / 40 已取消。
 * 顺序：先「不通过」→ 再「通过」→ 再「进行中」→ 最后「未提交」，
 * 避免「审核不通过」「没审核过」被「审核通过」误吃。
 */
export const AUDIT_STATUS_RULES: Array<{ status: number; re: RegExp }> = [
  // 30 审核不通过（已驳回 / 被打回）
  {
    status: 30,
    re: /审核不通过|审核未通过|未通过|没通过|不通过|审核不过|未批准|没批准|不批准|驳回|打回|被拒|拒绝/,
  },
  // 20 审核通过（审批已完成）
  {
    status: 20,
    re: /审核通过|审核完成|审核完毕|已通过|已审核|审核过|已批准|已批/,
  },
  // 10 审批中 = 待审核 / 等待审核 / 还没审核 / 未审核 / 没审核（已提交、尚未完成）
  //   ⚠️ 这类绝不能返回全部合同
  {
    status: 10,
    re: /审核中|待审核|等待审核|正在审核|还没审核|没有审核|未审核|没审核|尚未审核|审核处理中|发起审核|提交审核|待审|没审/,
  },
  // 0 未提交（草稿）：尚未发起审批
  { status: 0, re: /未提交|没提交|待提交|草稿|未发起|还没提交/ },
  { status: 40, re: /已取消|取消的|作废/ },
];

/**
 * 问句是否在问「审批状态」
 *
 * 用于：匹配不到任何状态时**反问**，而不是静默把全部合同返回 ——
 * 宁可多问一句，也绝不把「带条件的查询」当成「查全部」。
 */
export function isAuditStatusAsk(q: string): boolean {
  const s = normalizeAuditWords(q);
  return /审核/.test(s) && /合同|报价/.test(s);
}

/**
 * 从问句里解析用户要筛的审批状态
 *
 * 返回空数组 = 没有解析出状态条件（由调用方结合 isAuditStatusAsk 决定是否反问）
 * 例：「帮我查询审核通过的合同」→ [20]；「没审批的合同」→ [10]
 */
export function pickAuditStatuses(q: string): number[] {
  const s = normalizeAuditWords(q);
  if (!s) return [];
  // 否定问法：「没有审核通过的合同 / 还没审核过的合同 / 未审核通过的合同」
  //   = 尚未通过审核的（未提交 / 审批中 / 不通过 / 已取消）
  //   注意：这里只匹配「否定 + 审核通过/审核过」，不要把「未通过」「没通过」也算进来
  //   —— 那两个属于 30 审核不通过，已在规则里处理。
  if (/(没有|不是|尚未|还没|未曾|没|未)[^，。]{0,4}(审核通过|审核过)/.test(s)) {
    return [0, 10, 30, 40];
  }
  const hit: number[] = [];
  for (const r of AUDIT_STATUS_RULES) {
    if (r.re.test(s)) hit.push(r.status);
  }
  return hit;
}
