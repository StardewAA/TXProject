/* 项目域三级实体的前端配置：VO 数据集、规范字段分组、规范码 → 主列（camelCase）映射、写入口径。
 * 依据后端契约（txgy-module-bidding Project/TenderProject/BidSection Controller + SaveReqVO）：
 *   · payload = 数据集字段全集（key=VO规范英文名，服务端按 record canonicalize 补 null、丢多余键）；
 *   · 主列（camelCase）= 列表/追溯展示用，服务端 BeanUtils.toBean 写入，缺失可由 payload 回填编号；
 *   · 必填仅校验 编号 + 名称（PROJECT_CODE/PROJECT_NAME、TENDER_PROJECT_CODE/TENDER_PROJECT_NAME、
 *     BID_SECTION_CODE/BID_SECTION_NAME），其余规范必填由前端提示、不阻断（后端不拒）。
 * 注意：主列 approvalDeptName/approvalDocNo 与规范字段名不同（规范为 APPROVAL_AUTHORITY/APPROVAL_NUMBER），故映射时对齐。 */
import type { VoGroup } from './VoStageForm.vue';

export type EntityKey = 'bidSection' | 'project' | 'tenderProject';

export interface EntityDef {
  key: EntityKey;
  /** VO 数据集名（voSpec.gen.ts） */
  vo: string;
  /** 附件环节（attachmentStages.ts） */
  stage: string;
  title: string;
  /** 编号 / 名称的规范字段码（后端必填两项） */
  codeField: string;
  nameField: string;
  /** 编号由系统生成（契约《编号体系归位》：17/20/23 位内部编号前端不显示不输入），保存后从详情回显 */
  codeAuto?: boolean;
  /** 规范字段码 → 主列 camelCase（列表展示字段） */
  mainMap: Record<string, string>;
  groups: VoGroup[];
  /** 上级归属：本实体字段码 ← 上级实体 */
  parent?: { from: EntityKey; parentField: string; self: string; };
}

const projectGroups: VoGroup[] = [
  { title: '一、项目标识', hint: '用于确认项目主体和建设地点；项目编号由系统生成，正式名称应与立项/批准文件一致，行政区划和建设地点应真实准确。依据：项目数据集 ProjectVO；招标公告和公示信息发布管理办法第5条。', codes: ['PROJECT_CODE', 'PROJECT_NAME', 'REGION_CODE', 'RELATE_CODE', 'INVEST_PROJECT_CODE', 'ADDRESS'] },
  { title: '二、项目法人与联系人', hint: '项目法人与经办联系人信息；报送单位代码为向上级报送平台的单位识别码（政务口径）。', codes: ['LEGAL_PERSON', 'CONTACTOR', 'CONTACT_INFORMATION', 'REPORT_COMPANY_CODE_TYPE', 'REPORT_COMPANY_CODE'] },
  { title: '三、项目概况与规模', hint: '请说明项目建设内容、范围、规模和地点，后续招标公告需从这里形成一致的项目概况。依据：招标投标法第16条、发改委令第10号第5条。', codes: ['INDUSTRIES_TYPE', 'PROJECT_SCALE'] },
  { title: '四、资金来源', hint: '应明确资金来源及出资比例；依法必须招标项目的公告应载明资金来源。依据：发改委令第10号第5条。', codes: ['FUND_SOURCE', 'CONTRIBUTION_SCALE'] },
  { title: '五、项目审批', hint: '填写可追溯的审批文件名称、文号和审批单位，作为项目立项依据；不要用内部简称替代正式文号。', codes: ['APPROVAL_NAME', 'APPROVAL_NUMBER', 'APPROVAL_AUTHORITY'] },
  { title: '六、建立时间', hint: '序号 15：项目创建时间', codes: ['CREATE_TIME'] },
];

const tenderProjectGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: '先选择上级项目，再维护招标项目；招标项目编号由系统生成，统一交易标识码在依法必招场景必填，名称应与招标方案及公告保持一致。', codes: ['PROJECT_CODE', 'TENDER_PROJECT_CODE', 'UNIFIED_DEAL_CODE', 'TENDER_PROJECT_NAME', 'TENDER_PROJECT_TYPE', 'REGION_CODE'] },
  { title: '二、招标内容与范围及招标方案说明', hint: '请完整说明招标内容、范围、规模及主要技术/服务边界，避免只填写项目简称。依据：招标投标法第16条、发改委令第10号第5条。', codes: ['TENDER_CONTENT'] },
  { title: '三、招标人', hint: '招标人名称、主体类别和统一社会信用代码应与主体档案一致；公告必须载明招标人及联系方式。依据：发改委令第10号第5条。', codes: ['TENDERER_NAME', 'TENDERER_CODE_TYPE', 'TENDERER_CODE'] },
  { title: '四、招标代理机构', hint: '非自行招标时填写代理机构主体信息，并保持名称、代码和联系方式一致；自行招标时按规范留空。', codes: ['TENDER_AGENCY_NAME', 'TENDER_AGENCY_CODE_TYPE', 'TENDER_AGENCY_CODE'] },
  { title: '五、招标方式与组织', hint: '招标方式、资格审查方式和组织形式决定后续公告及文件获取口径，请按规范代码选择。依据：实施条例第15条。', codes: ['TENDER_MODE', 'QULIFICATION_CHECK_MODE', 'TENDER_ORGANIZE_FORM'] },
  { title: '六、建立时间', hint: '序号 17：招标项目建立时间', codes: ['CREATE_TIME'] },
  { title: '七、监督与审核', hint: '序号 18、21~24：监督部门、业主、审核部门', codes: ['SUPERVISE_DEPT_NAME', 'OWNER_NAME', 'SUPERVISE_DEPT_CODE', 'APPROVE_DEPT_NAME', 'APPROVE_DEPT_CODE'] },
];

const bidSectionGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: '先核对招标项目归属；标段（包）编号由系统生成，名称、序号和类型应与招标文件一致。', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'BID_SECTION_NAME', 'IS_NOMEET_BIDDING', 'BID_ECTION_NUM'] },
  { title: '二、招标方式与区域', hint: '按规范代码填写招标方式、行政区划、交易场所和标段分类，不要使用自定义中文代码。', codes: ['BID_TENDER_MODE', 'REGION_CODE', 'TRA_PLACE_CODE', 'TENDER_PROJECT_CLASSIFY_CODE'] },
  { title: '三、标段内容', hint: '请说明本标段（包）的工作内容、范围和边界，作为投标人理解和公告披露的基础。依据：发改委令第10号第5条。', codes: ['BID_SECTION_CONTENT', 'BID_SECTION_TYPE'] },
  { title: '四、合同估算价与工期', hint: '金额保持元单位和字符串精度，币种/单位/工期必须相互匹配；公告披露时应与招标文件一致。', codes: ['CONTRACT_RECKON_PRICE', 'CURRENCY_CODE', 'PRICE_UNIT', 'TIME_LIMIT'] },
  { title: '五、建立时间与备注', hint: '序号 15、18', codes: ['CREATE_TIME', 'REMARK'] },
  { title: '六、电子化与开标方式', hint: '序号 16：是否电子招投标', codes: ['IS_WEB_BIDDING'] },
  { title: '七、投标人资格条件', hint: '请明确资质等级、业绩、人员、信用等资格能力要求，不得设置与项目无关或排斥潜在投标人的条件。依据：发改委令第10号第5、18条。', codes: ['BID_QUALIFICATION'] },
];

export const PROJECT_DEF: EntityDef = {
  key: 'project',
  vo: 'ProjectVO',
  stage: 'PROJECT',
  title: '项目',
  codeField: 'PROJECT_CODE',
  nameField: 'PROJECT_NAME',
  codeAuto: true,
  mainMap: {
    PROJECT_CODE: 'projectCode',
    PROJECT_NAME: 'projectName',
    REGION_CODE: 'regionCode',
    INVEST_PROJECT_CODE: 'investProjectCode',
    RELATE_CODE: 'relateCode',
    ADDRESS: 'address',
    LEGAL_PERSON: 'legalPerson',
    INDUSTRIES_TYPE: 'industriesType',
    FUND_SOURCE: 'fundSource',
    CONTRIBUTION_SCALE: 'contributionScale',
    PROJECT_SCALE: 'projectScale',
    /* 规范为 APPROVAL_AUTHORITY（审批单位）、APPROVAL_NUMBER（审批文号）；主列命名不同，此处对齐 */
    APPROVAL_AUTHORITY: 'approvalDeptName',
    APPROVAL_NUMBER: 'approvalDocNo',
    REPORT_COMPANY_CODE_TYPE: 'reportCompanyCodeType',
    REPORT_COMPANY_CODE: 'reportCompanyCode',
  },
  groups: projectGroups,
};

export const TENDER_PROJECT_DEF: EntityDef = {
  key: 'tenderProject',
  vo: 'TenderProjectVO',
  stage: 'TENDER_PROJECT',
  title: '招标项目',
  codeField: 'TENDER_PROJECT_CODE',
  nameField: 'TENDER_PROJECT_NAME',
  codeAuto: true,
  parent: { self: 'PROJECT_CODE', from: 'project', parentField: 'PROJECT_CODE' },
  mainMap: {
    TENDER_PROJECT_CODE: 'tenderProjectCode',
    PROJECT_CODE: 'projectCode',
    TENDER_PROJECT_NAME: 'tenderProjectName',
    TENDER_PROJECT_TYPE: 'tenderProjectType',
    UNIFIED_DEAL_CODE: 'unifiedDealCode',
    REGION_CODE: 'regionCode',
    TENDER_CONTENT: 'tenderContent',
    TENDERER_NAME: 'tendererName',
    TENDERER_CODE_TYPE: 'tendererCodeType',
    TENDERER_CODE: 'tendererCode',
    TENDER_AGENCY_NAME: 'tenderAgencyName',
    TENDER_AGENCY_CODE_TYPE: 'tenderAgencyCodeType',
    TENDER_AGENCY_CODE: 'tenderAgencyCode',
    TENDER_MODE: 'tenderMode',
    QULIFICATION_CHECK_MODE: 'qulificationCheckMode',
    TENDER_ORGANIZE_FORM: 'tenderOrganizeForm',
    SUPERVISE_DEPT_NAME: 'superviseDeptName',
    SUPERVISE_DEPT_CODE: 'superviseDeptCode',
    OWNER_NAME: 'ownerName',
    APPROVE_DEPT_NAME: 'approveDeptName',
    APPROVE_DEPT_CODE: 'approveDeptCode',
  },
  groups: tenderProjectGroups,
};

export const BID_SECTION_DEF: EntityDef = {
  key: 'bidSection',
  vo: 'BidSectionVO',
  stage: 'BID_SECTION',
  title: '标段（包）',
  codeAuto: true,
  codeField: 'BID_SECTION_CODE',
  nameField: 'BID_SECTION_NAME',
  parent: { self: 'TENDER_PROJECT_CODE', from: 'tenderProject', parentField: 'TENDER_PROJECT_CODE' },
  mainMap: {
    BID_SECTION_CODE: 'bidSectionCode',
    TENDER_PROJECT_CODE: 'tenderProjectCode',
    BID_SECTION_NAME: 'bidSectionName',
    UNIFIED_DEAL_CODE: 'unifiedDealCode',
    BID_ECTION_NUM: 'bidEctionNum',
    BID_TENDER_MODE: 'bidTenderMode',
    REGION_CODE: 'regionCode',
    TRA_PLACE_CODE: 'traPlaceCode',
    BID_SECTION_CONTENT: 'bidSectionContent',
    BID_SECTION_TYPE: 'bidSectionType',
    CONTRACT_RECKON_PRICE: 'contractReckonPrice',
    CURRENCY_CODE: 'currencyCode',
    PRICE_UNIT: 'priceUnit',
    TIME_LIMIT: 'timeLimit',
    BID_QUALIFICATION: 'bidQualification',
    IS_WEB_BIDDING: 'isWebBidding',
    IS_NOMEET_BIDDING: 'isNomeetBidding',
    REMARK: 'remark',
  },
  groups: bidSectionGroups,
};

export const ENTITY_DEFS: Record<EntityKey, EntityDef> = {
  project: PROJECT_DEF,
  tenderProject: TENDER_PROJECT_DEF,
  bidSection: BID_SECTION_DEF,
};

/** 由后端记录还原表单字段（规范码 → 值）：先取 fields（数据集全量），再用主列覆盖同名规范码 */
export function recordToFields(def: EntityDef, record: null | Record<string, unknown> | undefined): Record<string, string> {
  if (!record) return {};
  const out: Record<string, string> = {};
  const fields = record.fields as Record<string, unknown> | undefined;
  if (fields) {
    for (const [k, v] of Object.entries(fields)) {
      if (v !== null && v !== undefined && String(v) !== '') out[k] = String(v);
    }
  }
  for (const [code, col] of Object.entries(def.mainMap)) {
    const v = record[col];
    if (v !== null && v !== undefined && String(v) !== '') out[code] = String(v);
  }
  return out;
}

/** 组装写入体：payload=规范字段全集（含 ATTACHMENT_CODE）+ 主列 camelCase */
export function assembleSave(
  def: EntityDef,
  fields: Record<string, string>,
  attachmentCode: string,
  id?: number | string,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(fields)) {
    if (v !== null && v !== undefined && String(v).trim() !== '') payload[k] = v;
  }
  if (attachmentCode) payload.ATTACHMENT_CODE = attachmentCode;
  const data: Record<string, unknown> = { payload };
  if (id !== undefined && id !== null && id !== '') data.id = id;
  for (const [code, col] of Object.entries(def.mainMap)) {
    const v = fields[code];
    if (v !== undefined && String(v).trim() !== '') data[col] = v;
  }
  return data;
}

/* ============================================================================
 * 主档卡片配置（MainCardTable 行内编辑用，按实体维护）
 *   · groups：分组标题 + 规范码（顺序 = VO 规范表格序号升序；不含平台技术字段与父级归属码）
 *   · editable / readonly：可编辑 / 不可编辑（系统生成）字段划分；两者互斥且覆盖全量展示字段
 * ============================================================================ */
export interface MasterGroupDef {
  title: string;
  codes: string[];
}
export interface MasterCardConfig {
  groups: MasterGroupDef[];
  editable: string[];
  readonly: string[];
}

const PROJECT_MASTER: MasterCardConfig = {
  groups: [
    { title: '一、项目标识', codes: ['PROJECT_CODE', 'PROJECT_NAME', 'REGION_CODE', 'RELATE_CODE', 'INVEST_PROJECT_CODE', 'ADDRESS', 'PROJECT_TRADE_TYPE'] },
    { title: '二、项目法人与联系人', codes: ['LEGAL_PERSON', 'CONTACTOR', 'CONTACT_INFORMATION', 'REPORT_DUTY'] },
    { title: '三、项目概况与规模', codes: ['INDUSTRIES_TYPE', 'PROJECT_SCALE', 'BUIDING_AREA'] },
    {
      title: '四、资金来源',
      codes: ['FUND_SOURCE', 'CONTRIBUTION_SCALE', 'INVEST_RECKON', 'OTHER_OWNED_FUND', 'OTHER_OWNED_FUND _ RATIO', 'PRIVATE_FUND', 'PRIVATE_FUND_RATIO', 'FOREIGN_FUND', 'FOREIGN_FUND_RATIO', 'FOREIGN_PRIVATE_FUND', 'FOREIGN_PRIVATE_FUND_RATIO'],
    },
    { title: '五、项目审批', codes: ['APPROVAL_NAME', 'APPROVAL_NUMBER', 'APPROVAL_AUTHORITY'] },
    { title: '六、建立时间', codes: ['CREATE_TIME'] },
    { title: '七、招标组织', codes: ['ORGANIZE_FORM', 'BIDDING_SITUATION'] },
    { title: '八、其他说明', codes: ['OTHER_INSTRUCTIONS', 'REPORT_COMPANY_CODE_TYPE', 'REPORT_COMPANY_CODE'] },
  ],
  editable: [
    'PROJECT_NAME', 'PROJECT_TRADE_TYPE', 'REGION_CODE', 'ADDRESS',
    'PROJECT_SCALE', 'INDUSTRIES_TYPE', 'RELATE_CODE', 'INVEST_PROJECT_CODE', 'BUIDING_AREA',
    'FUND_SOURCE', 'CONTRIBUTION_SCALE', 'INVEST_RECKON', 'OTHER_OWNED_FUND', 'OTHER_OWNED_FUND _ RATIO', 'PRIVATE_FUND', 'PRIVATE_FUND_RATIO', 'FOREIGN_FUND', 'FOREIGN_FUND_RATIO', 'FOREIGN_PRIVATE_FUND', 'FOREIGN_PRIVATE_FUND_RATIO',
    'APPROVAL_NAME', 'APPROVAL_NUMBER', 'APPROVAL_AUTHORITY',
    'LEGAL_PERSON', 'CONTACTOR', 'CONTACT_INFORMATION', 'REPORT_DUTY',
    'ORGANIZE_FORM', 'BIDDING_SITUATION',
    'OTHER_INSTRUCTIONS', 'REPORT_COMPANY_CODE_TYPE', 'REPORT_COMPANY_CODE',
  ],
  readonly: ['PROJECT_CODE', 'CREATE_TIME'],
};

const TENDER_PROJECT_MASTER: MasterCardConfig = {
  groups: [
    { title: '一、招标项目标识', codes: ['TENDER_PROJECT_CODE', 'UNIFIED_DEAL_CODE', 'TENDER_PROJECT_NAME', 'TENDER_PROJECT_TYPE', 'REGION_CODE'] },
    { title: '二、招标内容与范围', codes: ['TENDER_CONTENT'] },
    { title: '三、招标人与代理机构', codes: ['TENDERER_NAME', 'TENDERER_CODE_TYPE', 'TENDERER_CODE', 'TENDER_AGENCY_NAME', 'TENDER_AGENCY_CODE_TYPE', 'TENDER_AGENCY_CODE'] },
    { title: '四、招标方式', codes: ['TENDER_MODE', 'QULIFICATION_CHECK_MODE', 'TENDER_ORGANIZE_FORM'] },
    { title: '五、建立时间', codes: ['CREATE_TIME'] },
    { title: '六、监督与审核', codes: ['SUPERVISE_DEPT_NAME', 'OWNER_NAME', 'SUPERVISE_DEPT_CODE', 'APPROVE_DEPT_NAME', 'APPROVE_DEPT_CODE'] },
  ],
  editable: [
    'TENDER_PROJECT_NAME', 'TENDER_PROJECT_TYPE', 'UNIFIED_DEAL_CODE', 'REGION_CODE', 'TENDER_CONTENT',
    'TENDERER_NAME', 'TENDERER_CODE_TYPE', 'TENDERER_CODE',
    'TENDER_AGENCY_NAME', 'TENDER_AGENCY_CODE_TYPE', 'TENDER_AGENCY_CODE',
    'TENDER_MODE', 'QULIFICATION_CHECK_MODE', 'TENDER_ORGANIZE_FORM',
    'SUPERVISE_DEPT_NAME', 'SUPERVISE_DEPT_CODE', 'OWNER_NAME', 'APPROVE_DEPT_NAME', 'APPROVE_DEPT_CODE',
  ],
  readonly: ['TENDER_PROJECT_CODE', 'CREATE_TIME'],
};

const BID_SECTION_MASTER: MasterCardConfig = {
  groups: [
    // 标段编号 = 招标项目编号(20位) + 序列号(3位)：表单不填全段，只填「标段序号 BID_ECTION_NUM」；
    // 保存时 saveFor 前端拼完整编号，BID_SECTION_CODE 仍 readonly 展示系统编号。
    { title: '一、标段标识', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_NAME', 'BID_ECTION_NUM', 'UNIFIED_DEAL_CODE'] },
    { title: '二、招标信息', codes: ['IS_NOMEET_BIDDING', 'BID_TENDER_MODE', 'IS_WEB_BIDDING'] },
    { title: '三、行政区域', codes: ['REGION_CODE', 'TRA_PLACE_CODE'] },
    { title: '四、标段内容', codes: ['BID_SECTION_CONTENT', 'BID_SECTION_TYPE', 'TENDER_PROJECT_CLASSIFY_CODE'] },
    { title: '五、合同估算', codes: ['CONTRACT_RECKON_PRICE', 'CURRENCY_CODE', 'PRICE_UNIT', 'TIME_LIMIT'] },
    { title: '六、建立时间与备注', codes: ['CREATE_TIME', 'REMARK'] },
    { title: '七、资格条件', codes: ['BID_QUALIFICATION'] },
  ],
  editable: [
    'BID_SECTION_NAME',
    'BID_SECTION_CONTENT', 'BID_SECTION_TYPE', 'TENDER_PROJECT_CLASSIFY_CODE',
    'BID_TENDER_MODE', 'BID_ECTION_NUM', 'IS_NOMEET_BIDDING', 'IS_WEB_BIDDING',
    'CONTRACT_RECKON_PRICE', 'CURRENCY_CODE', 'PRICE_UNIT', 'TIME_LIMIT',
    'BID_QUALIFICATION',
    'REGION_CODE', 'TRA_PLACE_CODE',
    'REMARK',
  ],
  readonly: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'UNIFIED_DEAL_CODE', 'CREATE_TIME'],
};

export const MASTER_CONFIG: Record<EntityKey, MasterCardConfig> = {
  project: PROJECT_MASTER,
  tenderProject: TENDER_PROJECT_MASTER,
  bidSection: BID_SECTION_MASTER,
};

