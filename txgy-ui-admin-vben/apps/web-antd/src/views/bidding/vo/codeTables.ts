/* 招投标代码值字典（代码 → 中文，用于项目 / 招标项目 / 标段三处主档行内编辑的回显与下拉选项）
 * 依据：《VO 数据对象规范（国家V2.0模版·陕西适配全量版）》第十章陕西代码集（表名后缀「（陕西1）」）。
 * 取值口径：**以陕西 V2.0 为准**；国家、陕西都有的概念以陕西为准（README §1.1 / 关键约定）。
 * 2026-09-18 已把本表整表对回规范：修正「招标情形」「项目交易分类」两项；
 *   核对结论、未改动项及理由见 docs/前端规范-全域接口说明/29-前端代码集与VO规范核对清单-2026-09-18.md。
 *   **新增/修改选项前先查该清单**，每个选项值都要能追溯到规范里的具体表名，不要凭印象写。
 * 注意：
 *   · 展示/回显时遇未知代码一律原样展示（不清楚的不要擅自翻译）；
 *   · 下拉选项统一做「当前值并集」——若记录里存的代码不在表中，自动并入该值，保证已存数据始终可选中/不回显丢失；
 *   · TENDER_PROJECT_TYPE（招标项目类型）按公共资源交易信息分类类目表给出**常用子集**（含用户口径：A07→水利），
 *     完整类目表较大，待字典接口落地后以接口为准；当前值不在子集时仍可随记录回显。 */
export interface CodeOption {
  value: string;
  label: string;
}
export interface CodeTable {
  label: string;
  options: CodeOption[];
}

/** 主体类别（招标人类别 / 招标代理机构类别 共用） */
const BODY_TYPE_OPTIONS: CodeOption[] = [
  { value: '1', label: '自然人' },
  { value: '2', label: '法人' },
  { value: '3', label: '联合体' },
  { value: '4', label: '部队' },
  { value: '5', label: '港澳企业' },
  { value: '6', label: '境外企业' },
  { value: '7', label: '二级单位（非法人）' },
  { value: '9', label: '其他' },
];

/** 招标项目类型（公共资源交易分类——常用工程子集，详见文件头注） */
const TRADE_CATEGORY_OPTIONS: CodeOption[] = [
  { value: 'A01', label: '房屋建筑工程' },
  { value: 'A02', label: '市政基础设施工程' },
  { value: 'A03', label: '公路工程' },
  { value: 'A04', label: '铁路工程' },
  { value: 'A05', label: '民航工程' },
  { value: 'A06', label: '水运工程' },
  { value: 'A07', label: '水利工程' },
  { value: 'A08', label: '电力工程' },
  { value: 'B01', label: '货物采购' },
  { value: 'C01', label: '服务采购' },
  { value: '9', label: '其他' },
];

/** 招标方式（1.10.5；招标项目/标段共用） */
const TENDER_MODE_OPTIONS: CodeOption[] = [
  { value: '1', label: '公开招标' },
  { value: '2', label: '邀请招标' },
  { value: '9', label: '其他' },
];

/** 招标组织形式（1.10.6；招标项目/项目共用） */
const TENDER_ORGANIZE_FORM_OPTIONS: CodeOption[] = [
  { value: '1', label: '自行招标' },
  { value: '2', label: '委托招标' },
  { value: '9', label: '其他' },
];

/** 是否（1.10.2）：1 是 / 0 否 */
const YES_NO_OPTIONS: CodeOption[] = [
  { value: '1', label: '是' },
  { value: '0', label: '否' },
];

/** 规范码 → 代码表 */
export const CODE_TABLES: Record<string, CodeTable> = {
  TENDER_MODE: {
    label: '招标方式',
    options: TENDER_MODE_OPTIONS,
  },
  QULIFICATION_CHECK_MODE: {
    label: '资格审查方式',
    options: [
      { value: '1', label: '资格预审' },
      { value: '2', label: '资格后审' },
    ],
  },
  TENDER_ORGANIZE_FORM: {
    label: '招标组织形式',
    options: TENDER_ORGANIZE_FORM_OPTIONS,
  },
  TENDERER_CODE_TYPE: { label: '招标人类别', options: BODY_TYPE_OPTIONS },
  TENDER_AGENCY_CODE_TYPE: { label: '招标代理机构类别', options: BODY_TYPE_OPTIONS },
  TENDER_PROJECT_TYPE: { label: '招标项目类型', options: TRADE_CATEGORY_OPTIONS },

  /* ===== 项目（ProjectVO） ===== */
  /* 依据规范第十章「项目交易分类（陕西1）」12 项字母码；
   * 后端 DictTypeConstants.PROJECT_TRADE_CATEGORY 亦标注「项目交易分类（陕西1）」。
   * 2026-09-18 修正：原为 1/2/3/4/9，与规范陕西代码集无交集。 */
  PROJECT_TRADE_TYPE: {
    label: '项目交易分类',
    options: [
      { value: 'A', label: '房屋建筑工程' },
      { value: 'B', label: '市政基础设施工程' },
      { value: 'GD', label: '轨道交通工程' },
      { value: 'F', label: '水利工程' },
      { value: 'L', label: '电力工程' },
      { value: 'E', label: '公路工程' },
      { value: 'D', label: '园林绿化' },
      { value: 'Y', label: '其他工程' },
      { value: 'TL', label: '铁路工程' },
      { value: 'TX', label: '通讯工程' },
      { value: 'GLYH', label: '公路养护' },
      { value: 'WZB', label: '外资办' },
    ],
  },
  RELATE_CODE: {
    label: '是否固定资产',
    options: [
      { value: '1', label: '是' },
      { value: '0', label: '否' },
    ],
  },
  ORGANIZE_FORM: {
    label: '招标组织形式',
    options: TENDER_ORGANIZE_FORM_OPTIONS,
  },
  /* 依据规范第十章「招标情形（陕西1）」三档；与后端 ShaanxiBiddingSituationEnum（CODE_1/2/3）一致。
   * 2026-09-18 修正：原为 1 依法必须招标 / 2 自愿招标 / 9 其他——代码 2 语义错位（规范 2=其他情形的依法招标，
   * 「自愿招标」是 3），且缺 3、多 9。该字段 dtype 为 C1（1/2/9 长度均合法，校验不会报错），
   * 且 pub=政务公开，错值会静默上报政务平台。 */
  BIDDING_SITUATION: {
    label: '招标情形',
    options: [
      { value: '1', label: '国有资金控股或占主导地位的依法招标' },
      { value: '2', label: '其他情形的依法招标' },
      { value: '3', label: '自愿招标' },
    ],
  },

  /* ===== 标段（BidSectionVO） ===== */
  BID_TENDER_MODE: {
    label: '标段（包）招标方式',
    options: TENDER_MODE_OPTIONS,
  },
  IS_NOMEET_BIDDING: { label: '是否不见面开标项目', options: YES_NO_OPTIONS },
  IS_WEB_BIDDING: { label: '是否网上招投标', options: YES_NO_OPTIONS },
  CURRENCY_CODE: {
    label: '标段合同估算价币种',
    options: [
      { value: '156', label: '人民币（CNY）' },
      { value: '840', label: '美元（USD）' },
      { value: '978', label: '欧元（EUR）' },
    ],
  },
  PRICE_UNIT: {
    label: '金额单位',
    options: [{ value: '0', label: '元' }],
  },
  /* 存疑·未核实（2026-09-18 整表核对时发现，见 29 号核对清单）——**先别改**：
   * 现有选项是 1 位（1/2/3/9），但字段 dtype 是 C2（2 位），且规范 1.10.20「标段（包）类型」
   * 是二级 2 位／三级复合码（A-工程 → 10 房建 → A1001 施工…），三者互相矛盾。
   * 需先定清本字段该装哪一级码，否则改了仍是错的。 */
  BID_SECTION_TYPE: {
    label: '标段（包）类型',
    options: [
      { value: '1', label: '工程' },
      { value: '2', label: '货物' },
      { value: '3', label: '服务' },
      { value: '9', label: '其他' },
    ],
  },
};

/** 某规范码的下拉选项；若 current 不在表中则并入，保证已存值可选/可回显。 */
export function codeOptions(code: string, current?: null | string): CodeOption[] {
  const tbl = CODE_TABLES[code];
  if (!tbl) return [];
  const opts = tbl.options.slice();
  const cur = current?.trim();
  if (cur && !opts.some((o) => o.value === cur)) {
    opts.push({ value: cur, label: cur });
  }
  return opts;
}

/** 代码 → 中文；空返回 '—'；未知代码返回原样。 */
export function codeLabel(code: string, value?: null | string): string {
  if (value === undefined || value === null) return '—';
  const raw = String(value).trim();
  if (!raw) return '—';
  return CODE_TABLES[code]?.options.find((o) => o.value === raw)?.label ?? raw;
}