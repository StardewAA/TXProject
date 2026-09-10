/* 业务链草稿（前端本地）：后端接口未就绪期间，按 VO 规范先落结构 —— 字段名/附件结构完全对齐规范，
 * 待后端接口就绪后把 upsert/load 换成 API 调用即可，字段无需改动。
 * 链路顺序（用户明确口径）：先建「项目」→ 再建「招标项目」→ 再建「标段」→ 最后对「标段」发「公告/公示」。 */
import { newAttachmentGuid, type AttachmentRecord } from './attachmentStages';

/** 一条环节记录：字段值（VO 英文名 → 值）+ 该环节的附件集（可多个） */
export interface ChainRecord {
  /** 本地草稿主键（后端就绪后由后端 id 取代） */
  id: string;
  /** VO 字段值 */
  fields: Record<string, string>;
  /** 附件集（AttachmentSetVO 列表，任意环节均可挂多个） */
  attachments: AttachmentRecord[];
  updatedAt: number;
}

export interface ChainDraft {
  projects: ChainRecord[];
  tenderProjects: ChainRecord[];
  bidSections: ChainRecord[];
  /** 招标文件 / 答疑澄清文件（TenderDocVO）：标段之后、公告之前 */
  tenderDocs: ChainRecord[];
  /** 招标控制价文件（ControlPriceDocVO，如有） */
  controlPriceDocs: ChainRecord[];
}

const KEY = 'bid-vo-chain-v1';

export function emptyDraft(): ChainDraft {
  return { projects: [], tenderProjects: [], bidSections: [], tenderDocs: [], controlPriceDocs: [] };
}

export function loadDraft(): ChainDraft {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return emptyDraft();
    const d = JSON.parse(raw) as Partial<ChainDraft>;
    return {
      projects: d.projects ?? [],
      tenderProjects: d.tenderProjects ?? [],
      bidSections: d.bidSections ?? [],
      tenderDocs: d.tenderDocs ?? [],
      controlPriceDocs: d.controlPriceDocs ?? [],
    };
  } catch {
    return emptyDraft();
  }
}

export function saveDraft(d: ChainDraft): void {
  localStorage.setItem(KEY, JSON.stringify(d));
}

export function newRecord(seed: Record<string, string> = {}): ChainRecord {
  return { id: newAttachmentGuid(), fields: { ...seed }, attachments: [], updatedAt: Date.now() };
}

/** 取字段值（按 VO 英文名；缺省空串） */
export function fieldOf(r: ChainRecord, code: string): string {
  return r.fields[code] ?? '';
}

/** 业务链摘要（用于步骤条与选择器展示） */
export function projectTitle(r: ChainRecord): string {
  return r.fields.PROJECT_NAME || r.fields.PROJECT_CODE || '（未命名项目）';
}
export function tenderProjectTitle(r: ChainRecord): string {
  return r.fields.TENDER_PROJECT_NAME || r.fields.TENDER_PROJECT_CODE || '（未命名招标项目）';
}
export function bidSectionTitle(r: ChainRecord): string {
  return r.fields.BID_SECTION_NAME || r.fields.BID_SECTION_CODE || '（未命名标段）';
}

/** 按字段值查记录（如按 TENDER_PROJECT_CODE 找招标项目） */
export function findByField(list: ChainRecord[], code: string, value: string): ChainRecord | undefined {
  if (!value) return undefined;
  return list.find((r) => (r.fields[code] ?? '') === value);
}

/** 标段所属招标项目编号（BidSectionVO.TENDER_PROJECT_CODE） */
export function sectionOwnerCode(r: ChainRecord): string {
  return r.fields.TENDER_PROJECT_CODE ?? '';
}

/** 招标项目所属项目编号（TenderProjectVO.PROJECT_CODE） */
export function tenderProjectOwnerCode(r: ChainRecord): string {
  return r.fields.PROJECT_CODE ?? '';
}
