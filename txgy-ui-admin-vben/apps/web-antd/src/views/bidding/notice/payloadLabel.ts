/**
 * 公告权威数据集字段 中文标签字典（节选·已核条目，key=规范英文名称）。
 * 标签真源=公共资源交易平台系统数据规范 V2.0 / 陕西 V2.0 字段清单（后端 NoticePayloadUtils + 数据集 record）；
 * 后端字典接口（契约 §四 F-1）落地后以接口为准，本字典只作离线兜底；未收录键渲染时原样展示。
 * 时间类值（yyyyMMddHHmmss / epoch ms）统一转 yyyy-MM-dd HH:mm 展示。
 */

/** 已核标签（增补请对照规范字段表，勿臆造） */
export const PAYLOAD_LABEL: Record<string, string> = {
  TENDER_PROJECT_CODE: '招标项目编号',
  PROJECT_CODE: '项目编号',
  NOTICE_BID_NUM: '公告标段（包）数量',
  BID_SECTION_CODES: '相关标段（包）编号',
  BID_SECTION_CODE: '标段（包）编号',
  UNIFIED_DEAL_CODES: '统一交易标识码',
  UNIFIED_DEAL_CODE: '统一交易标识码',
  NOTICE_NAME: '公告名称',
  PUBLICITY_NAME: '公示名称',
  NOTICE_CONTENT: '公告内容',
  PUBLICITY_CONTENT: '公示内容',
  CHANGE_CONTENT: '变更内容',
  NOTICE_MEDIA: '公告媒介',
  NOTICE_SEND_TIME: '公告发布时间',
  URL: '公告链接',
  NOTICE_NATURE: '公告性质',
  BULLETIN_TYPE: '公告类型',
  PUBLICITY_TYPE: '公示类型',
  NOMINAL_FEE: '标书费（名义费用）',
  FILE_RECEIVE_ADDRESS: '文件获取地点',
  DOC_GET_START_TIME: '文件获取开始时间',
  DOC_GET_END_TIME: '文件获取截止时间',
  TENDER_DOC_GET_METHOD: '文件获取方式',
  BID_DOC_REFER_END_TIME: '递交截止时间',
  BID_DOC_REFER_METHOD: '递交方式',
  BID_END_TIME: '投标截止时间',
  BID_OPEN_TIME: '开标时间',
  BID_OPEN_METHOD: '开标方式',
  BID_URL: '开标网址',
  PUBLICITY_START_TIME: '公示开始时间',
  PUBLICITY_END_TIME: '公示结束时间',
  IS_CHANGE: '是否变更公示',
  IS_POSTPONE: '是否延期',
  SYNDICATED_FLAG: '是否接受联合体投标',
  CONSORTIUM_REQUIREMENT: '联合体要求',
  COMPANY_QUALI_DEMAND: '企业资质要求',
  PM_QUALI_DEMAND: '项目负责人资格要求',
  BID_OTHER_CONDITION: '其他投标条件',
  QUAL_TYPE: '资格审查方式',
  WIN_BIDDER_NAME: '中标人名称',
  WIN_BIDDER_CODE: '中标人代码',
  BIDDER_CODE_TYPE: '主体类别',
  PRICE_FORM_CODE: '价格形式代码',
  // ===== 中标候选人公示补充（2026-09-29 补齐：契约字段全集 SOCIAL 子集，用词对齐 payloadFieldMeta.ts）=====
  RECOMMEND_RANKING_FIRST: '推荐单位一',
  RECOMMEND_RANKING_FIRST_LEGAL_CODE: '推荐单位一社会统一信用代码',
  RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME: '推荐单位一项目负责人姓名',
  RECOMMEND_RANKING_FIRST_LEGAL_PM_CERTIFICATES: '推荐单位一项目负责人相关证书和编号',
  RECOMMEND_RANKING_FIRST_WIN_PRICE: '推荐单位一中标价',
  RECOMMEND_RANKING_FIRST_AWARD: '推荐单位一获奖情况',
  RECOMMEND_RANKING_SECOND: '推荐单位二',
  RECOMMEND_RANKING_SECOND_LEGAL_CODE: '推荐单位二社会统一信用代码',
  RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME: '推荐单位二项目负责人姓名',
  RECOMMEND_RANKING_SECOND_LEGAL_PM_CERTIFICATES: '推荐单位二项目负责人相关证书和编号',
  RECOMMEND_RANKING_SECOND_WIN_PRICE: '推荐单位二中标价',
  RECOMMEND_RANKING_SECOND_AWARD: '推荐单位二获奖情况',
  RECOMMEND_RANKING_THIRED: '推荐单位三',
  RECOMMEND_RANKING_THIRED_EGAL_CODE: '推荐单位三社会统一信用代码',
  RECOMMEND_RANKING_THIRED_EGAL_PM_NAME: '推荐单位三项目负责人姓名',
  RECOMMEND_RANKING_THIRED_EGAL_PM_CERTIFICATES: '推荐单位三项目负责人相关证书和编号',
  RECOMMEND_RANKING_THIRED_WIN_PRICE: '推荐单位三中标价',
  RECOMMEND_RANKING_THIRED_AWARD: '推荐单位三获奖情况',
  OBJECTION_METHOD: '提出异议的渠道和方式',
  // ===== 中标结果公示补充（2026-09-29）=====
  BID_AMOUNT: '中标金额',
  BID_CURRENCY: '价格币种代码',
  OTHER_BID_PRICE: '费率或其它类型结果',
  TIME_LIMIT: '中标工期',
  QUALITY: '中标质量等级',
  WIN_INSTRUCTION: '中标说明',
  UNION_CODE: '联合体代码',
  UNION_NAME: '联合体名称',
  WIN_AMOUNT: '中标金额',
  IS_NOMEET_BIDDING: '是否流标',
  BULLETIN_DUTY: '公告责任人',
  REMARK: '备注',
  ATTACHMENT_CODE: '附件标识',
  SUBMIT_TIMESTAMP: '提交时间戳',
  DATA_TIMESTAMP: '数据时间戳',
  FROM_TRADE_PLAT_CODE: '来源交易平台代码',
  TRADE_PLAT: '交易平台',
  PLATFORM_CODE: '平台代码',
  PUB_SERVICE_PLAT_CODE: '公共服务平台代码',
  PUB_SERVICE_PLAT: '公共服务平台',
  BULLETIN_DUTY_CODE_TYPE: '责任人类别代码',
};

export const UNKNOWN_FIELD_LABEL = '（未收录标签）';

const p2 = (n: number): string => String(n).padStart(2, '0');
/** 字段值格式化：ISO yyyy-MM-ddTHH:mm(:ss) / 14 位 yyyyMMddHHmmss / epoch 毫秒 → yyyy-MM-dd HH:mm；其余原样 */
export function fmtFieldValue(v: unknown): string {
  if (v === null || v === undefined) return '';
  const s = String(v).trim();
  if (!s || s === 'null' || s === 'undefined') return '';
  // ISO 时间（yyyy-MM-ddTHH:mm:ss，后端 2026-09-10 联调口径，见 notice 域联调文档 §4）
  const iso = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(s);
  if (iso) {
    return iso[1] + '-' + iso[2] + '-' + iso[3] + ' ' + iso[4] + ':' + iso[5];
  }
  if (/^\d{14}$/.test(s)) {
    const d = new Date(Number(s.slice(0, 4)), Number(s.slice(4, 6)) - 1, Number(s.slice(6, 8)), Number(s.slice(8, 10)), Number(s.slice(10, 12)));
    if (!Number.isNaN(+d)) return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
    return s;
  }
  if (/^\d{12,13}$/.test(s)) {
    const d = new Date(Number(s));
    if (!Number.isNaN(+d)) return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate()) + ' ' + p2(d.getHours()) + ':' + p2(d.getMinutes());
  }
  return s;
}

/** 组内是否时间类（启发式） */
function isTimeKey(k: string): boolean {
  return /_TIME$|_TIME_|SEND_TIME|START_TIME|END_TIME|TIMESTAMP/.test(k);
}

/** 是否资格/条件类（启发式） */
function isQualiKey(k: string): boolean {
  return /QUALI|DEMAND|CONDITION|REQUIREMENT|METHOD|ADDRESS|FLAG|POSTPONE|FEE|PRICE_FORM/.test(k);
}

/** 是否结果类（中标/排名） */
function isResultKey(k: string): boolean {
  return /^WIN_|^RECOMMEND_|RANKING|WIN_PRICE|BIDDER_CODE/.test(k);
}

export interface PayloadItem { key: string; label: string; value: string; known: boolean; }
export interface PayloadSection { title: string; items: PayloadItem[]; }

/** 按数据集声明序规整 payload 并分组（空值跳过；组序：标识/时间/资格与条件/结果/其它） */
export function groupPayload(payload?: null | Record<string, unknown>): PayloadSection[] {
  const entries = payload ? Object.entries(payload) : [];
  const groups: PayloadSection[] = [
    { title: '项目与公告标识', items: [] },
    { title: '时间与节点', items: [] },
    { title: '资格与获取条件', items: [] },
    { title: '中标/结果', items: [] },
    { title: '其它（未分组）', items: [] },
  ];
  for (const [k, raw] of entries) {
    const value = fmtFieldValue(raw);
    if (!value) continue;
    const known = Object.prototype.hasOwnProperty.call(PAYLOAD_LABEL, k);
    const item: PayloadItem = { key: k, label: PAYLOAD_LABEL[k] || k, value, known };
    const gi = isTimeKey(k) ? 1 : isQualiKey(k) ? 2 : isResultKey(k) ? 3 : 0;
    if (gi === 0 && /^TENDER_|^PROJECT_|^NOTICE_|^PUBLICITY_|^BULLETIN_|^UNIFIED_|^BID_SECTION|^URL/.test(k)) groups[0]!.items.push(item);
    else groups[gi]!.items.push(item);
  }
  const none = groups[4]!;
  const top = groups.slice(0, 4).filter((g) => g.items.length);
  const rest = none.items.length ? [none] : [];
  return top.concat(rest);
}