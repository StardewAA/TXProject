/**
 * bidding 开标域（契约来源：后端变更请求-投标与资格预审多文件化-2026-10-02.md R5 + 后端已落地
 *   BidOpeningController GET /bidding/opening/page?bidSectionCode= → CommonResult<List<OpeningRespVO>>）
 * 权限：bidding:opening:query
 */
import { requestClient } from '#/api/request';

/** 开标记录行（OpeningRespVO 全字段映射） */
export interface OpeningRecord {
  id?: number | string;
  tenderProjectCode?: string;
  bidSectionCode?: string;
  /** 开标时间（LocalDateTime → epoch 毫秒） */
  openingTime?: null | number | string;
  /** 开标状态：PENDING/DECRYPTING/DONE */
  openingStatus?: string;
  /** 房间状态：WAITING/DECRYPTING/OPENING/DONE */
  roomStatus?: string;
  roomOpenedTime?: null | number | string;
  roomClosedTime?: null | number | string;
  /** 投标人数量 */
  bidderCount?: number;
  /** 唱标清单 JSON */
  openList?: string;
  /** 开标主持人 */
  operatorName?: string;
}

/** 开标状态文案（缺字典回显原码） */
export const OPENING_STATUS_TEXT: Record<string, string> = {
  PENDING: '待开标',
  DECRYPTING: '解密中',
  DONE: '已开标',
};
export const ROOM_STATUS_TEXT: Record<string, string> = {
  WAITING: '等待进房',
  DECRYPTING: '解密中',
  OPENING: '开标中',
  DONE: '已结束',
};

/** 契约 R5：开标记录（按标段过滤；bidSectionCode 空=全部） */
export function getBidOpeningList(bidSectionCode?: string) {
  return requestClient.get<OpeningRecord[]>('/bidding/opening/page', {
    params: bidSectionCode ? { bidSectionCode } : {},
  });
}
