/* 项目域附件暂存（临时兜底，待后端提供附件集持久化接口）
 * 现状（已核实后端）：Project/TenderProject/BidSection 的 Controller、DO、fields_json 均无附件结构，
 *   仅能通过 payload.ATTACHMENT_CODE（BidSectionVO/ProjectVO 规范字段，多个 GUID 半角分号隔开）记录标识；
 *   服务端 canonicalize 会丢弃多余键，故附件明细（名称/类型/URL）无法随实体落库。
 * 因此本阶段：GUID 照规范写入 ATTACHMENT_CODE；附件明细存本地（key = 实体 + 编号），供本页回显。
 * 后端就绪后：改为按实体 id 读写附件集（AttachmentSetVO 列表），并删除本文件。 */
import type { AttachmentRecord } from './attachmentStages';

const PREFIX = 'bid-att-v1:';

function key(entity: string, code: string): string {
  return PREFIX + entity + ':' + code;
}

export function loadAttachments(entity: string, code: string): AttachmentRecord[] {
  if (!code) return [];
  try {
    const raw = localStorage.getItem(key(entity, code));
    if (!raw) return [];
    const arr = JSON.parse(raw) as AttachmentRecord[];
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

export function saveAttachments(entity: string, code: string, list: AttachmentRecord[]): void {
  if (!code) return;
  try {
    if (!list.length) {
      localStorage.removeItem(key(entity, code));
      return;
    }
    localStorage.setItem(key(entity, code), JSON.stringify(list));
  } catch {
    /* 本地容量/隐私模式：忽略（附件仍会以 ATTACHMENT_CODE 上报） */
  }
}
