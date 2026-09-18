/* 附件环节规则（严格按 VO）：AttachmentItemVO=附件采集项（35 类，中文名/英文名/是否必传照录规范）；
 * AttachmentSetVO=附件集结构（GUID/关联数据集标识/数量/名称/类型/文件名/URL），本模块用它描述"一个附件"。
 * 规范口径（20号令 A.1.2/A.1.4）：数据集以 ATTACHMENT_CODE（附件关联标识号）关联附件集；
 * 多个附件关联标识号以半角分号隔开 —— 因此每个环节都可挂多个附件，且可跨多个采集项分类。 */
import { ATTACHMENT_ITEMS, type VoField } from './voSpec.gen';

/** 一个附件（= AttachmentSetVO 一条记录；字段名对应规范英文名） */
export interface AttachmentRecord {
  /** ATTACHMENT_GUID 附件唯一标识 */
  guid: string;
  /** ATTACHMENT_SET_CODE 附件关联数据集标识符（取附件采集项英文名） */
  setCode: string;
  /** ATTACHMENT_NAME 附件名称 */
  name: string;
  /** ATTACHMENT_TYPE 附件类型（pdf/doc/jpg…） */
  type: string;
  /** ATTACHMENT_FILE_NAME 附件文件名 */
  fileName: string;
  /** URL 附件URL地址（规范必填） */
  url: string;
  /** 文件服务 file_id（预签名链路可得；后端上传链路无） */
  fileId?: number;
  /** 文件大小（字节，业务展示用，非规范字段） */
  size?: number;
}

/** 采集项按英文名索引（35 类） */
const ITEM_BY_CODE = new Map(ATTACHMENT_ITEMS.map((f) => [f.code, f]));
export function attachmentItem(code: string): undefined | VoField {
  return ITEM_BY_CODE.get(code);
}
export function attachmentItemLabel(code: string): string {
  return ITEM_BY_CODE.get(code)?.label ?? code;
}
export function attachmentItemRequired(code: string): boolean {
  return ITEM_BY_CODE.get(code)?.required ?? false;
}

/** 环节 → 允许的附件采集项（依各采集项规范备注"关联数据集"归属） */
export const STAGE_ATTACHMENT_ITEMS: Record<string, string[]> = {
  PROJECT: ['PROJECT_APPROVAL_FILE', 'PROJECT_SOURCE_PROOF', 'OTHER_FILE'],
  TENDER_PROJECT: ['OTHER_FILE', 'Contract_FILE'],
  BID_SECTION: ['TENDER_FILE', 'TENDER_FILE_ATTACHS', 'QUALIFICATION_DOC', 'QUALIFICATION_CLARIFY_FILE', 'CONTROL_FILE', 'OTHER_FILE'],
  TENDER_DOC: ['TENDER_FILE', 'TENDER_FILE_ATTACHS', 'FILE_EX_TDOC', 'CONTROL_FILE'],
  CONTROL_PRICE_DOC: ['CONTROL_FILE', 'OTHER_FILE'],
  QUALIFICATION_NOTICE: ['QUALIFICATION_NOTICE', 'QUALIFICATION_DOC', 'QUALIFICATION_CLARIFY_FILE', 'QUALIFICATION_LIST', 'OTHER_FILE'],
  NOTICE_TENDER: ['TENDER_NOTICE', 'FILE_EX_TDOC', 'OTHER_FILE'],
  NOTICE_CHANGE: ['TENDER_CHANGE_NOTICE', 'OTHER_FILE'],
  NOTICE_CANDIDATE: ['PUBLICITY_DOC', 'OTHER_FILE'],
  NOTICE_RESULT: ['WIN_BID_NOTICE', 'OTHER_FILE'],
  WIN_NOTICE: ['WIN_NOTICE_FILE', 'OTHER_FILE'],
  CONTRACT: ['CONTRACT_DOC', 'CONTRACT_SING_FILE', 'PERFORMANCE_FILE', 'OTHER_FILE'],
  OBJECTION: ['OBJECTION_FILE', 'INQUIRY_FILE', 'COMPLAIN_FILE'],
};

/** 某环节允许的采集项字段（VO 行） */
export function stageItems(stage: string): VoField[] {
  const codes = STAGE_ATTACHMENT_ITEMS[stage] ?? ['OTHER_FILE'];
  return codes.map((c) => ITEM_BY_CODE.get(c)).filter((f): f is VoField => !!f);
}

/** ATTACHMENT_GUID：规范要求附件唯一标识（C..50）；优先 UUID 去掉横线，长度合规 */
export function newAttachmentGuid(): string {
  const c = globalThis.crypto as Crypto | undefined;
  const raw = c && typeof c.randomUUID === 'function'
    ? c.randomUUID()
    : 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
        const r = (Math.random() * 16) | 0;
        const v = ch === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
      });
  return raw.replace(/-/g, '');
}

/** 由文件名推断附件类型（ATTACHMENT_TYPE：doc/docx/jpg/wps/pdf…） */
export function typeOfFileName(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 ? fileName.slice(dot + 1).toLowerCase() : '';
}

/** 构造一条附件记录 */
export function makeAttachment(
  setCode: string,
  f: { fileId?: number; name: string; size?: number; type?: string; url: string; },
  nameOverride?: string,
): AttachmentRecord {
  return {
    guid: newAttachmentGuid(),
    setCode,
    name: nameOverride || f.name,
    type: f.type && !f.type.includes('/') ? f.type : typeOfFileName(f.name),
    fileName: f.name,
    url: f.url,
    fileId: f.fileId,
    size: f.size,
  };
}

/** ATTACHMENT_CODE 附件关联标识号：多个以半角分号隔开（规范口径） */
export function attachmentCodeOf(list: AttachmentRecord[]): string {
  return list.map((a) => a.guid).filter(Boolean).join(';');
}

/** 缺必传采集项的提示（返回缺失采集项中文名） */
export function missingRequiredItems(stage: string, list: AttachmentRecord[]): string[] {
  return stageItems(stage)
    .filter((it) => it.required && !list.some((a) => a.setCode === it.code))
    .map((it) => it.label + '（' + it.code + '）');
}
