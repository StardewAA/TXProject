/**
 * bidding 通用附件域（契约来源：《前端规范-全域接口说明/29-bidding通用附件域与关注域-前端契约-2026-09-27.md》）
 *  POST /bidding/attachment/save   → 绑定附件（同一 (bizType,bizCode,fileId) 幂等返回原行）
 *  DELETE /bidding/attachment/delete?id= → 解绑附件（逻辑删除，幂等）
 *  GET  /bidding/attachment/list?bizType=&bizCode= → 按业务实体一对 N 查附件（按 seq 排序）
 * 上传复用 POST /bidding/file/upload（api/bidding/file 的 uploadBiddingFile；响应带 fileId/url/sha256）
 * 口径（规范 §5 三-5 / 红线 8）：fileId 是业务表落库关联键（权威，三件套之一），URL 仅用于即时预览；
 *   附件类型 35 类字典 AttachmentItemVO（契约 §2.6 常用项照录，前端下拉可选常用项）；
 *   INFECTED 行不下发下载 URL（virusStatus 标识；预览走 FileApi 签名下载）。
 * 鉴权：三个接口均登录即可（@PreAuthorize("isAuthenticated()")）。
 */
import { requestClient } from '#/api/request';

/** 归属实体（与 FlowBizTypeEnum 对齐的六种；BID_TRACK/PREQUAL_APPLY 为 2026-10-02 契约新增：投标文件/资格预审材料多文件化，见
 *  txgy-module-bidding/docs/电子证照前前端契约/后端变更请求-投标与资格预审多文件化-2026-10-02.md） */
export type AttachmentBizType =
  | 'PROJECT'
  | 'TENDER_PROJECT'
  | 'BID_SECTION'
  | 'ANNOUNCEMENT'
  | 'BID_TRACK'
  | 'PREQUAL_APPLY';

/** 附件类型（35 类字典常用项，契约 §2.6 照录） */
export const ATTACHMENT_TYPE_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '项目审批文件', value: 'PROJECT_APPROVAL_FILE' },
  { label: '招标文件', value: 'TENDER_FILE' },
  { label: '招标文件附件', value: 'TENDER_FILE_ATTACHS' },
  { label: '招标公告', value: 'TENDER_NOTICE' },
  { label: '邀请函', value: 'INVITE_DOC' },
  { label: '公示文件', value: 'PUBLICITY_DOC' },
  { label: '中标通知', value: 'WIN_BID_NOTICE' },
  { label: '合同', value: 'CONTRACT_DOC' },
  { label: '资格文件', value: 'QUALIFICATION_DOC' },
  { label: '评标报告', value: 'EVALUATION_REPORT' },
  { label: '异议函', value: 'OBJECTION_FILE' },
  { label: '投诉函', value: 'COMPLAIN_FILE' },
];

/** 附件行（AttachmentRespVO 全字段映射） */
export interface AttachmentItem {
  /** 附件行主键（雪花 Long 序列化为字符串；联调实证 id="2100736607181295618"） */
  id: number | string;
  /** 归属实体：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT */
  bizType?: string;
  /** 归属实体业务编号 */
  bizCode?: string;
  /** 附件类型（35 类字典 AttachmentItemVO） */
  attachmentType?: string;
  /** 附件集名称（AttachmentSetVO.ATTACHMENT_NAME） */
  attachmentName?: string;
  /** FileApi 文件主键（一致性依据） */
  fileId?: number;
  /** 对象存储访问地址（SOCIAL 公开；预览用） */
  url?: string;
  /** 文件名 */
  fileName?: string;
  /** 文件 SHA-256 */
  sha256?: string;
  /** 文件大小（字节） */
  size?: number;
  /** MIME 类型 */
  mimeType?: string;
  /** 病毒扫描：UNKNOWN/CLEAN/INFECTED（INFECTED 不下发下载 URL） */
  virusStatus?: string;
  /** 同实体内附件序号 */
  seq?: number;
  /** 备注 */
  remark?: string;
  /** 创建时间（epoch 毫秒） */
  createTime?: number;
}

/** 绑定附件入参（AttachmentSaveReqVO） */
export interface AttachmentSaveReq {
  /** 必填：PROJECT/TENDER_PROJECT/BID_SECTION/ANNOUNCEMENT */
  bizType: AttachmentBizType;
  /** 必填：对应业务编号 */
  bizCode: string;
  /** 必填：上传接口返回（fileId 雪花 Long，序列化兼容 number|string） */
  fileId: number | string;
  /** 可选：35 类字典 */
  attachmentType?: string;
  /** 可选：附件集名称 */
  attachmentName?: string;
  /** 可选：预览用 */
  url?: string;
  /** 可选 */
  fileName?: string;
  /** 可选 */
  sha256?: string;
  /** 可选：文件大小（字节） */
  size?: number;
  /** 可选 */
  remark?: string;
}

/** 绑定附件（对同一 (bizType,bizCode,fileId) 幂等：重复绑定返回原行） */
export function saveAttachment(req: AttachmentSaveReq) {
  return requestClient.post<AttachmentItem>('/bidding/attachment/save', req);
}

/** 解绑附件（逻辑删除；行不存在也返回成功） */
export function deleteAttachment(id: number | string) {
  return requestClient.delete<boolean>(`/bidding/attachment/delete?id=${id}`);
}

/** 按业务实体查附件列表（一对 N；按 seq 排序） */
export function listAttachments(bizType: AttachmentBizType, bizCode: string) {
  return requestClient.get<AttachmentItem[]>('/bidding/attachment/list', {
    params: { bizCode, bizType },
  });
}

/** 附件是否可安全预览/下载（INFECTED 不下发下载 URL） */
export function isAttachmentDownloadable(item: Pick<AttachmentItem, 'virusStatus' | 'url'>): boolean {
  return item.virusStatus !== 'INFECTED' && !!item.url;
}

/** 附件类型中文（35 类常用项；未知类型回显原值） */
export function attachmentTypeText(type?: string): string {
  if (!type) return '通用附件';
  return ATTACHMENT_TYPE_OPTIONS.find((t) => t.value === type)?.label ?? type;
}

/** 附件大小（字节 → 可读） */
export function fmtAttachmentSize(size?: number | null): string {
  if (!size || size <= 0) return '—';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 病毒状态 → 角标文案/颜色 */
export function virusStatusTag(status?: string): { color: string; text: string } {
  if (status === 'INFECTED') return { color: 'red', text: '已感染' };
  if (status === 'CLEAN') return { color: 'green', text: '安全' };
  return { color: 'default', text: '扫描中' };
}
