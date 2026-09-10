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
  /** 规范字段码 → 主列 camelCase（列表展示字段） */
  mainMap: Record<string, string>;
  groups: VoGroup[];
  /** 上级归属：本实体字段码 ← 上级实体 */
  parent?: { self: string; from: EntityKey; parentField: string };
}

const projectGroups: VoGroup[] = [
  { title: '一、项目标识', hint: 'ProjectVO 序号 1~6：项目编号/名称/行政区划/关联项目/投资项目统一代码/建设地点', codes: ['PROJECT_CODE', 'PROJECT_NAME', 'REGION_CODE', 'RELATE_CODE', 'INVEST_PROJECT_CODE', 'ADDRESS'] },
  { title: '二、项目概况与规模', hint: '序号 8、11：行业类别（GB/T 4754-2017）、项目规模', codes: ['PROJECT_SCALE', 'INDUSTRIES_TYPE'] },
  { title: '三、资金来源', hint: '序号 9、10：资金来源、出资比例', codes: ['FUND_SOURCE', 'CONTRIBUTION_SCALE'] },
  { title: '四、项目审批', hint: '序号 12~14：审批文件名称/文号/审批单位（写入主列中审批部门与文号）', codes: ['APPROVAL_NAME', 'APPROVAL_NUMBER', 'APPROVAL_AUTHORITY'] },
  { title: '五、项目法人与联系人', hint: '序号 7、36、37', codes: ['LEGAL_PERSON', 'CONTACTOR', 'CONTACT_INFORMATION'] },
  { title: '六、建立时间', hint: '序号 15：项目创建时间', codes: ['CREATE_TIME'] },
];

const tenderProjectGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'TenderProjectVO 序号 1~6（先有项目，再有招标项目）', codes: ['PROJECT_CODE', 'TENDER_PROJECT_CODE', 'TENDER_PROJECT_NAME', 'TENDER_PROJECT_TYPE', 'REGION_CODE'] },
  { title: '二、招标内容与范围及招标方案说明', hint: '序号 7（自由文本 C..ul）', codes: ['TENDER_CONTENT'] },
  { title: '三、招标人', hint: '序号 8~10：名称/类别/代码（多个以半角分号隔开）', codes: ['TENDERER_NAME', 'TENDERER_CODE_TYPE', 'TENDERER_CODE'] },
  { title: '四、招标代理机构', hint: '序号 11~13：非自行招标时必填', codes: ['TENDER_AGENCY_NAME', 'TENDER_AGENCY_CODE_TYPE', 'TENDER_AGENCY_CODE'] },
  { title: '五、招标方式与组织', hint: '序号 14~16：招标方式/资格审查方式/招标组织形式', codes: ['TENDER_MODE', 'QULIFICATION_CHECK_MODE', 'TENDER_ORGANIZE_FORM'] },
  { title: '六、监督与审核', hint: '序号 18、21~24：监督部门、业主、审核部门', codes: ['SUPERVISE_DEPT_NAME', 'SUPERVISE_DEPT_CODE', 'OWNER_NAME', 'APPROVE_DEPT_NAME', 'APPROVE_DEPT_CODE'] },
  { title: '七、建立时间', hint: '序号 17：招标项目建立时间', codes: ['CREATE_TIME'] },
];

const bidSectionGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'BidSectionVO 序号 1~3、6、9、10：招标项目编号/标段编号/名称/序号/是否存在否决投标/标段类型', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'BID_SECTION_NAME', 'BID_ECTION_NUM', 'BID_SECTION_TYPE'] },
  { title: '二、标段内容', hint: '序号 11（自由文本 C..ul）', codes: ['BID_SECTION_CONTENT'] },
  { title: '三、招标方式与区域', hint: '序号 5~8、22：招标方式/行政区划/交易场地代码/标段分类代码', codes: ['BID_TENDER_MODE', 'REGION_CODE', 'TRA_PLACE_CODE', 'TENDER_PROJECT_CLASSIFY_CODE'] },
  { title: '四、合同估算价与工期', hint: '序号 12~14、17：估算价（字符串保真）/币种/单位/工期', codes: ['CONTRACT_RECKON_PRICE', 'CURRENCY_CODE', 'PRICE_UNIT', 'TIME_LIMIT'] },
  { title: '五、投标人资格条件', hint: '序号 23（自由文本 C..ul）', codes: ['BID_QUALIFICATION'] },
  { title: '六、电子化与开标方式', hint: '序号 16、4：是否电子招投标 / 是否不见面开标', codes: ['IS_WEB_BIDDING', 'IS_NOMEET_BIDDING'] },
  { title: '七、建立时间与备注', hint: '序号 15、18', codes: ['CREATE_TIME', 'REMARK'] },
];

export const PROJECT_DEF: EntityDef = {
  key: 'project',
  vo: 'ProjectVO',
  stage: 'PROJECT',
  title: '项目',
  codeField: 'PROJECT_CODE',
  nameField: 'PROJECT_NAME',
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
  parent: { self: 'PROJECT_CODE', from: 'project', parentField: 'PROJECT_CODE' },
  mainMap: {
    TENDER_PROJECT_CODE: 'tenderProjectCode',
    PROJECT_CODE: 'projectCode',
    TENDER_PROJECT_NAME: 'tenderProjectName',
    TENDER_PROJECT_TYPE: 'tenderProjectType',
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
export function recordToFields(def: EntityDef, record: Record<string, unknown> | undefined | null): Record<string, string> {
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
