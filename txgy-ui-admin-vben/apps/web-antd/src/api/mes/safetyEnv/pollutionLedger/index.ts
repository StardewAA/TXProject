import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace MesPollutionLedgerApi {
  /** MES 安全环保检测-污染/危废暂存台账 */
  export interface Ledger {
    id?: number; // 编号
    sourceCheckId?: number; // 来源判定记录ID
    sourceRecordNo?: string; // 来源判定记录编号(PC-...)
    stage?: string; // 环节
    bizNo?: string; // 关联单号
    batchNo?: string; // 批次号
    itemCode?: string; // 物料/产品编码
    itemName?: string; // 物料/产品名称
    itemSpec?: string; // 规格
    disposition?: string; // 处置方式
    storageMethod?: string; // 最终存储方法
    location?: string; // 去向/库位
    marked?: boolean; // 是否标记
    status?: string; // 台账状态 STORED/PROCESSING/REUSED/DISCHARGED/DISPOSED
    statusBy?: string; // 最近流转人
    statusTime?: number; // 最近流转时间
    remark?: string; // 备注
    createTime?: number; // 创建时间
  }

  /** 处置流转参数 */
  export interface StatusPayload {
    id: number;
    status: string;
    remark?: string;
  }

  /** 台账流转历史行：登记/处置/闭环 每步一行(只增不改) */
  export interface LedgerHistoryLog {
    id?: number;
    ledgerId?: number;
    sourceRecordNo?: string;
    fromStatus?: string; // 空=初始登记
    toStatus?: string;
    operator?: string;
    opTime?: number;
    remark?: string;
    createTime?: number;
  }
}

/** 查询台账分页(可按来源判定记录精确反查) */
export function getPollutionLedgerPage(params: PageParam & { sourceRecordNo?: string }) {
  return requestClient.get<PageResult<MesPollutionLedgerApi.Ledger>>(
    '/mes/safety-env/pollution-ledger/page',
    { params },
  );
}

/** 处置流转(终态清批次污染戳) */
export function updatePollutionLedgerStatus(data: MesPollutionLedgerApi.StatusPayload) {
  return requestClient.put('/mes/safety-env/pollution-ledger/status', data);
}

/** 查询台账流转历史(登记→处置→闭环，时间正序) */
export function getPollutionLedgerHistory(ledgerId: number) {
  return requestClient.get<MesPollutionLedgerApi.LedgerHistoryLog[]>(
    `/mes/safety-env/pollution-ledger/history?ledgerId=${ledgerId}`,
  );
}
