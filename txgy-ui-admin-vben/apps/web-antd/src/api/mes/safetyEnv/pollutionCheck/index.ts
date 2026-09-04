import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesSetPollutionCheckApi {
  /** MES 安全环保检测-污染判定记录 */
  export interface PollutionCheck {
    id?: number; // 编号
    recordNo?: string; // 记录编号
    stage?: string; // 环节
    bizNo?: string; // 关联单号
    batchNo?: string; // 批次号
    itemCode?: string; // 物料/产品编码
    itemName?: string; // 物料/产品名称
    itemSpec?: string; // 规格
    aiResult?: string; // AI 初筛结果 CLEAN/POLLUTED/UNCERTAIN
    aiConfidence?: number; // AI 置信度(%)
    aiReason?: string; // AI 判定依据
    suggestedStorage?: string; // AI 推荐存储方法
    reviewResult?: string; // 人工复核结果 CLEAN/POLLUTED，空=待复核
    storageMethod?: string; // 最终存储方法
    disposition?: string; // 处置方式
    location?: string; // 去向/库位
    marked?: boolean; // 是否标记
    reviewBy?: string; // 复核人
    reviewTime?: number; // 复核时间
    remark?: string; // 备注
    createTime?: number; // 创建时间
  }

  /** AI 初筛建议 */
  export interface AiSuggestion {
    aiResult?: string;
    aiConfidence?: number;
    aiReason?: string;
    suggestedStorage?: string;
  }

  /** 人工复核参数 */
  export interface ReviewPayload {
    id: number;
    reviewResult: string;
    storageMethod?: string;
    disposition?: string;
    location?: string;
    marked?: boolean;
    remark?: string;
  }

  /** 判定履历行：一次操作(创建/改单/复核)后的 AI+人工 完整快照(只增不改) */
  export interface CheckHistoryLog {
    id?: number;
    checkId?: number;
    recordNo?: string;
    opType?: string; // CREATE/UPDATE/REVIEW
    itemName?: string;
    aiResult?: string;
    aiConfidence?: number;
    aiReason?: string;
    suggestedStorage?: string;
    reviewResult?: string;
    storageMethod?: string;
    disposition?: string;
    location?: string;
    marked?: boolean;
    remark?: string;
    opBy?: string; // 操作人
    opTime?: number; // 操作时间
    createTime?: number;
  }
}

/** 查询污染判定分页 */
export function getPollutionCheckPage(params: PageParam) {
  return requestClient.get<PageResult<MesSetPollutionCheckApi.PollutionCheck>>(
    '/mes/safety-env/pollution-check/page',
    { params },
  );
}

/** 查询污染判定详情 */
export function getPollutionCheck(id: number) {
  return requestClient.get<MesSetPollutionCheckApi.PollutionCheck>(
    `/mes/safety-env/pollution-check/get?id=${id}`,
  );
}

/** 新增污染判定 */
export function createPollutionCheck(data: MesSetPollutionCheckApi.PollutionCheck) {
  return requestClient.post<number>('/mes/safety-env/pollution-check/create', data);
}

/** 修改污染判定(仅待复核) */
export function updatePollutionCheck(data: MesSetPollutionCheckApi.PollutionCheck) {
  return requestClient.put('/mes/safety-env/pollution-check/update', data);
}

/** AI 初筛预览(不落库) */
export function prescreenPollutionCheck(data: MesSetPollutionCheckApi.PollutionCheck) {
  return requestClient.post<MesSetPollutionCheckApi.AiSuggestion>(
    '/mes/safety-env/pollution-check/prescreen',
    data,
  );
}

/** 人工复核(终态收口：无污染/有污染) */
export function reviewPollutionCheck(data: MesSetPollutionCheckApi.ReviewPayload) {
  return requestClient.post('/mes/safety-env/pollution-check/review', data);
}

/** 删除污染判定(仅待复核) */
export function deletePollutionCheck(id: number) {
  return requestClient.delete(`/mes/safety-env/pollution-check/delete?id=${id}`);
}

/** 查询污染判定履历(创建→改单→复核，时间正序) */
export function getPollutionCheckHistory(checkId: number) {
  return requestClient.get<MesSetPollutionCheckApi.CheckHistoryLog[]>(
    `/mes/safety-env/pollution-check/history?checkId=${checkId}`,
  );
}
