/**
 * 四类公告 · 文书分节模板（HTML 生成器与 PDF 生成器共用的「单一数据源」）。
 *
 * 设计（2026-09-22 定稿，见 docs/四类公告布局设计与内部流转检查-2026-09-22.md）：
 *   · 一类型一张专属分节布局：分节标题、字段序、字段数按法定文书结构设计，切类型后肉眼可辨；
 *   · 只收「可展示字段」（SOCIAL 子集，payloadFieldMeta.ts 口径）；GOVERNMENT 政务报送字段
 *     （平台码/责任人/时间戳/统一交易码等）不进文书分节——由平台/系统侧维护；
 *   · 标签真源 = payloadLabel.ts 的 PAYLOAD_LABEL（公共资源数据规范 V2.0 字段清单）；
 *   · 值格式化 = fmtFieldValue（时间类 yyyyMMddHHmmss / epoch ms → yyyy-MM-dd HH:mm）；
 *   · HTML（noticeHtmlConverter）与 PDF（pdfGen）两端都从 buildNoticeSections() 取数，
 *     保证展示与版式文件内容一致。
 *
 * 三级编号（2026-09-23 补强，见 docs/电子证照前前端契约/后端变更请求-本站公告三级编号跟踪扇出-*.md）：
 *   · 首节改为「项目编号 / 项目名称 / 招标项目编号 / 标段（包）编号 / 标段（包）数量」编号块
 *     （编号口径唯一定义在 noticeCodes.ts），模板里原有的编号字段在首节跳过，避免同一编号印两遍；
 *   · 理由：这三种编号就是门户「我的关注」与投标流程跟踪的三级跟踪键，文书必须印全且可核对。
 */
import { buildCodeItems, CODE_PAYLOAD_KEYS } from './noticeCodes';
import { fmtFieldValue, PAYLOAD_LABEL } from './payloadLabel';

export interface NoticeSectionItem {
  key: string;
  label: string;
  value: string;
}

export interface NoticeSection {
  /** 文书分节标题，如「一、项目与标段」 */
  title: string;
  items: NoticeSectionItem[];
}

/** 分节模板：类型 → 分节（标题 + 字段 key 有序列表）。key 须在 PAYLOAD_LABEL 有标签，否则分节内跳过。 */
const SECTION_TEMPLATES: Record<string, Array<{ title: string; keys: string[] }>> = {
  TENDER_NOTICE: [
    { title: '一、项目与标段', keys: ['TENDER_PROJECT_CODE', 'NOTICE_BID_NUM', 'BID_SECTION_CODES'] },
    {
      title: '二、招标文件获取',
      keys: ['NOMINAL_FEE', 'FILE_RECEIVE_ADDRESS', 'DOC_GET_START_TIME', 'DOC_GET_END_TIME', 'TENDER_DOC_GET_METHOD'],
    },
    {
      title: '三、投标文件递交与开标',
      keys: ['BID_DOC_REFER_END_TIME', 'BID_DOC_REFER_METHOD', 'BID_URL', 'BID_OPEN_TIME', 'BID_OPEN_METHOD', 'IS_POSTPONE', 'QUAL_TYPE'],
    },
    {
      title: '四、资格要求与联合体',
      keys: ['SYNDICATED_FLAG', 'CONSORTIUM_REQUIREMENT', 'COMPANY_QUALI_DEMAND', 'PM_QUALI_DEMAND', 'BID_OTHER_CONDITION'],
    },
    { title: '五、发布媒介与性质', keys: ['NOTICE_MEDIA', 'URL', 'NOTICE_NATURE', 'BULLETIN_TYPE'] },
    { title: '六、其它', keys: ['NOTICE_SEND_TIME'] },
  ],
  CHANGE_NOTICE: [
    { title: '一、原公告指向', keys: ['TENDER_PROJECT_CODE', 'NOTICE_BID_NUM', 'BID_SECTION_CODES'] },
    { title: '二、变更事项', keys: ['CHANGE_CONTENT', 'BID_END_TIME', 'IS_POSTPONE'] },
    { title: '三、发布媒介与类型', keys: ['NOTICE_MEDIA', 'URL', 'BULLETIN_TYPE'] },
  ],
  WIN_CANDIDATE_PUBLICITY: [
    {
      title: '一、公示指向与期限',
      keys: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODES', 'PUBLICITY_TYPE', 'NOTICE_MEDIA', 'PUBLICITY_START_TIME', 'PUBLICITY_END_TIME'],
    },
    {
      title: '二、第一中标候选人',
      keys: [
        'RECOMMEND_RANKING_FIRST',
        'RECOMMEND_RANKING_FIRST_LEGAL_CODE',
        'RECOMMEND_RANKING_FIRST_LEGAL_PM_NAME',
        'RECOMMEND_RANKING_FIRST_LEGAL_PM_CERTIFICATES',
        'RECOMMEND_RANKING_FIRST_WIN_PRICE',
        'RECOMMEND_RANKING_FIRST_AWARD',
      ],
    },
    {
      title: '三、第二中标候选人',
      keys: [
        'RECOMMEND_RANKING_SECOND',
        'RECOMMEND_RANKING_SECOND_LEGAL_CODE',
        'RECOMMEND_RANKING_SECOND_LEGAL_PM_NAME',
        'RECOMMEND_RANKING_SECOND_LEGAL_PM_CERTIFICATES',
        'RECOMMEND_RANKING_SECOND_WIN_PRICE',
        'RECOMMEND_RANKING_SECOND_AWARD',
      ],
    },
    {
      title: '四、第三中标候选人',
      keys: [
        'RECOMMEND_RANKING_THIRED',
        'RECOMMEND_RANKING_THIRED_EGAL_CODE',
        'RECOMMEND_RANKING_THIRED_EGAL_PM_NAME',
        'RECOMMEND_RANKING_THIRED_EGAL_PM_CERTIFICATES',
        'RECOMMEND_RANKING_THIRED_WIN_PRICE',
        'RECOMMEND_RANKING_THIRED_AWARD',
      ],
    },
    { title: '五、异议与其它', keys: ['OBJECTION_METHOD'] },
  ],
  WIN_RESULT_PUBLICITY: [
    { title: '一、公示指向', keys: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'NOTICE_MEDIA', 'NOTICE_NATURE', 'BULLETIN_TYPE'] },
    { title: '二、中标人', keys: ['WIN_BIDDER_NAME', 'BIDDER_CODE_TYPE', 'WIN_BIDDER_CODE', 'UNION_CODE', 'UNION_NAME'] },
    { title: '三、中标价格', keys: ['PRICE_FORM_CODE', 'BID_AMOUNT', 'BID_CURRENCY', 'OTHER_BID_PRICE'] },
    { title: '四、履约与说明', keys: ['TIME_LIMIT', 'QUALITY', 'WIN_INSTRUCTION', 'CHANGE_CONTENT'] },
  ],
};

/** 未知类型兜底：无分节输出 */
const FALLBACK_SECTION_TEMPLATE: Array<{ title: string; keys: string[] }> = [];

/**
 * 按公告类型从 payload 构建文书分节（空值/未知标签字段跳过）。
 * HTML 与 PDF 两端统一从此取数，保证「展示内容 = 版式内容」。
 *
 * 三级编号（2026-09-23 补强）：`root` 传入公告根字段（项目编号/项目名称）后，**首节**改用
 * noticeCodes.buildCodeItems() 产出的「项目编号 → 项目名称 → 招标项目编号 → 标段（包）编号 →
 * 标段（包）数量」编号块，并跳过模板里原样的编号字段 —— 避免同一条编号既在编号块又在模板行里
 * 出现两次。理由：编号是关注/跟踪的键（notice-follow 三级），文书必须印全，且只印一次。
 * 传入 root 之前的老调用点（不传第三参）行为不变，编号仍由模板行带出。
 */
export function buildNoticeSections(
  noticeType?: null | string,
  payload?: null | Record<string, unknown>,
  root?: null | { projectCode?: unknown; projectName?: unknown },
): NoticeSection[] {
  const codeItems = buildCodeItems({
    payload,
    projectCode: root?.projectCode,
    projectName: root?.projectName,
  });
  const template = noticeType ? (SECTION_TEMPLATES[noticeType] ?? FALLBACK_SECTION_TEMPLATE) : FALLBACK_SECTION_TEMPLATE;
  const out: NoticeSection[] = [];
  template.forEach((group, index) => {
    const items: NoticeSectionItem[] = [];
    // 首节承载三级编号：跟踪键先亮相，其余字段照模板序（编号字段由编号块统一产出，此处跳过防重复）
    if (index === 0) items.push(...codeItems.map((it) => ({ key: it.key, label: it.label, value: it.value })));
    for (const key of group.keys) {
      if (index === 0 && CODE_PAYLOAD_KEYS.has(key)) continue;
      const label = PAYLOAD_LABEL[key];
      if (!label) continue; // 字典未收录：不臆造标签，跳过
      const value = fmtFieldValue(payload?.[key]);
      if (!value) continue; // 空值不占位
      items.push({ key, label, value });
    }
    if (items.length) out.push({ title: group.title, items });
  });
  // 模板未注册（未知类型）或首节为空时，编号块单独成节：编号一定要进文书（读者照编号去跟踪/核对）
  if (!out.length && codeItems.length) {
    out.push({ title: '一、项目与编号', items: codeItems.map((it) => ({ key: it.key, label: it.label, value: it.value })) });
  }
  return out;
}
