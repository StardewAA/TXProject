/**
 * 公告「三级编号」单一数据源 —— 项目编号 / 招标项目编号 / 标段（包）编号。
 *
 * 为什么要有这一层（2026-09-23 定稿）：
 *   · 门户「我的关注」与投标流程跟踪按**三种编号**建关注键，与 notice-follow 的
 *     targetType 一一对应：PROJECT=项目编号 / TENDER_PROJECT=招标项目编号 / SECTION=标段（包）编号；
 *   · 公告发布端（后台发布工作台、前台「创建公告」弹窗）必须把**同一套三级编号**写进公告，
 *     否则"发布出去的公告"与"用户跟踪的编号"对不上：发布成功却没人收到动态（本站公告跟踪断链）。
 *     现实缺陷：发布端此前把「招标项目编号」写进了公告的项目编号槽位（projectCode），
 *     项目编号本身反而丢失 —— 三级只剩一级，且那一级还错位。
 *   · 文书（正文 HTML / 版式 PDF / 详情页 / 大厅预览）也必须把三级编号印出来：
 *     跟踪键是编号，读者要能照着编号去关注/核对，不能只显示名称。
 *   故编号的"取值口径、展示标签、跟踪档位"全部收敛到本文件，其它模块只消费，不再各写一套。
 *
 * 边界：本文件只管**取值与标签**，不写业务规则（是否必填、能否发布由 VO 规范与后端守卫决定）。
 */

/** 跟踪维度级别（与 notice-follow targetType、门户「我的关注」筛选器一致） */
export type NoticeTrackLevel = 'PROJECT' | 'SECTION' | 'TENDER_PROJECT';

export interface NoticeTrackTier {
  /** 该级当前编号（空串=该级未关联编号）；**跟踪键永远用完整编号**（后端按完整编号精确匹配） */
  code: string;
  /** 展示短号：标段级=后 3 位（001/002），其余级=code 本身 */
  codeShort: string;
  /** 编号口径名（写进文书/提示里的字段名） */
  codeLabel: string;
  /** 维度名（与门户关注筛选器同文案：项目/招标项目/标段） */
  levelLabel: string;
  level: NoticeTrackLevel;
  /** 编号非空 → 该级关注者发布后能收到动态（收不到时前端只提示，不阻断发布） */
  trackable: boolean;
}

export interface NoticeCodeItem {
  key: string;
  label: string;
  value: string;
}

/** 公告数据集里与三级编号相关的 payload 字段（分节模板里遇到即跳过，由 codeItems 统一产出，避免重复行） */
export const CODE_PAYLOAD_KEYS: ReadonlySet<string> = new Set([
  'BID_SECTION_CODE',
  'BID_SECTION_CODES',
  'NOTICE_BID_NUM',
  'TENDER_PROJECT_CODE',
]);

/** 标段编号分隔符：规范口径是**半角分号**，爬虫/历史数据里也出现过中文分号、顿号、逗号（与后端同口径） */
const SECTION_SEPARATORS = /[;；,，、\s]+/;

/** 清洗编号：去空白；把 'null'/'undefined'/占位符等字符串化脏值一律当空（后端 JSON 里常见 "null"） */
export function cleanCode(value: unknown): string {
  const s = String(value ?? '').trim();
  if (!s) return '';
  return /^(null|undefined|nan|—|-|无)$/i.test(s) ? '' : s;
}

/** 标段（包）编号集合：兼容单数键 BID_SECTION_CODE 与复数键 BID_SECTION_CODES，多种分隔符切分 */
export function sectionCodesOf(payload?: null | Record<string, unknown>): string[] {
  const raw = cleanCode(payload?.BID_SECTION_CODES) || cleanCode(payload?.BID_SECTION_CODE);
  if (!raw) return [];
  return [...new Set(raw.split(SECTION_SEPARATORS).map((s) => s.trim()).filter(Boolean))];
}

/** 招标项目编号（公告数据集字段 TENDER_PROJECT_CODE，单值） */
export function tenderProjectCodeOf(payload?: null | Record<string, unknown>): string {
  return cleanCode(payload?.TENDER_PROJECT_CODE);
}

/** 标段编号短序号：完整 23 位 = 招标项目编号(20) + 序列(3)；展示只取后 3 位（用户口径：录入/展示 001、002）。
 * 若编号不以当前招标项目编号开头（异常数据），长度超过 3 位也取后 3 位，否则原样。 */
export function shortSectionCode(code: string, tenderProjectCode?: string): string {
  const s = code.trim();
  if (!s) return s;
  if (tenderProjectCode && s.startsWith(tenderProjectCode) && s.length > tenderProjectCode.length) {
    return s.slice(tenderProjectCode.length);
  }
  return s.length > 3 ? s.slice(-3) : s;
}

/** 项目名称：公告根字段快照优先，其次数据集的 PROJECT_NAME */
function projectNameOf(
  projectName: unknown,
  payload?: null | Record<string, unknown>,
): string {
  return cleanCode(projectName) || cleanCode(payload?.PROJECT_NAME);
}

/**
 * 三级跟踪档位（发布端「跟踪档位」条 + 发布前缺档提示的唯一口径）。
 *
 * @param input projectCode=公告根字段（**真实项目编号**，不是招标项目编号）；payload=公告数据集字段
 */
export function buildTrackTiers(input: {
  payload?: null | Record<string, unknown>;
  projectCode?: unknown;
}): NoticeTrackTier[] {
  const payload = input.payload ?? {};
  const sections = sectionCodesOf(payload);
  const tp = tenderProjectCodeOf(payload);
  const rows: Array<[NoticeTrackLevel, string, string, string]> = [
    ['PROJECT', '项目', '项目编号', cleanCode(input.projectCode)],
    ['TENDER_PROJECT', '招标项目', '招标项目编号', tp],
  ];
  // 标段级**逐标段一条**（2026-10-02 修复）：此前把多个标段 join('；') 成单个跟踪键，
  // 后端按完整编号精确匹配 → 合并键永不命中，跟踪形同虚设；改为每个标段独立跟踪。
  if (sections.length) {
    for (const s of sections) rows.push(['SECTION', '标段', '标段（包）编号', s]);
  } else {
    rows.push(['SECTION', '标段', '标段（包）编号', '']);
  }
  return rows.map(([level, levelLabel, codeLabel, code]) => ({
    code,
    codeLabel,
    codeShort: level === 'SECTION' && code ? shortSectionCode(code, tp) : code,
    level,
    levelLabel,
    trackable: !!code,
  }));
}

/**
 * 缺档提示：缺哪一级，哪一级的关注者在「投标流程管理」就收不到这条公告的动态。
 * 返回空串 = 三级齐备（发布后三级关注者都能被通知——是否真通知取决于后端扇出，见契约文档）。
 */
export function missingTierHint(tiers: NoticeTrackTier[]): string {
  const missing = tiers.filter((t) => !t.trackable).map((t) => t.levelLabel);
  if (!missing.length) return '';
  return `缺 ${missing.join('、')} 编号：发布后按${missing.join('/')}编号跟踪的用户收不到这条公告的动态（在「投标流程管理」里看不到）`;
}

/** 跟踪档位一句话摘要（列表行/详情提示用） */
export function trackSummary(tiers: NoticeTrackTier[]): string {
  const parts = tiers.filter((t) => t.trackable).map((t) => `${t.levelLabel} ${t.codeShort || t.code}`);
  return parts.length ? parts.join(' · ') : '未关联任何三级编号（发布后无人能被通知）';
}

/**
 * 文书编号行（正文 HTML / 版式 PDF / 详情页 / 大厅预览共用）。
 * 顺序即"从粗到细"的三级：项目编号 → 项目名称 → 招标项目编号 → 标段（包）编号 → 标段（包）数量。
 * 空值不占位（与分节模板一致：没有的字段不印空行）。
 */
export function buildCodeItems(input: {
  payload?: null | Record<string, unknown>;
  projectCode?: unknown;
  projectName?: unknown;
}): NoticeCodeItem[] {
  const payload = input.payload ?? {};
  const sections = sectionCodesOf(payload);
  const tp = tenderProjectCodeOf(payload);
  const bidNum = cleanCode(payload.NOTICE_BID_NUM) || (sections.length ? String(sections.length) : '');
  const rows: Array<[string, string, string]> = [
    ['PROJECT_CODE', '项目编号', cleanCode(input.projectCode)],
    ['PROJECT_NAME', '项目名称', projectNameOf(input.projectName, payload)],
    ['TENDER_PROJECT_CODE', '招标项目编号', tp],
  ];
  // 标段（包）编号：一个标段一行，展示短序号 001/002（完整 23 位只在文书留档，界面不堆长串）
  for (let i = 0; i < sections.length; i += 1) {
    const label = sections.length > 1 ? '标段（包）编号 ' + (i + 1) : '标段（包）编号';
    rows.push(['BID_SECTION_CODES_' + (i + 1), label, shortSectionCode(sections[i], tp)]);
  }
  rows.push(['NOTICE_BID_NUM', '标段（包）数量', bidNum]);
  return rows
    .filter(([, , value]) => !!value)
    .map(([key, label, value]) => ({ key, label, value }));
}
