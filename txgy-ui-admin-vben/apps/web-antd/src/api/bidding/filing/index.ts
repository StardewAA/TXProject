import { requestClient } from '#/api/request';

/**
 * 项目备案（FilingController，招投标域）
 * 权限：bidding:filing:create（提交）、bidding:filing:update（回执登记）
 * 幂等：提交按 (filingType, projectCode) 判重，重复提交后端返回 FILING_ALREADY_SUBMITTED。
 * 说明：filingType 为自由文本（后端无 FilingTypeEnum），取值由业务约定（如「招标备案」「中标备案」）；
 *      提交/回执里的 LocalDateTime 字段请求侧时间格式未在契约中约定，故页面不提交，留空由服务端取当前时间。
 */
export namespace FilingApi {
  /** 备案提交（FilingSubmitReqVO） */
  export interface SubmitReq {
    /** 备案类型（必填，自由文本） */
    filingType: string;
    /** 项目编号（必填） */
    projectCode: string;
    tenderProjectCode?: string;
    unifiedDealCode?: string;
    tenderMode?: string;
    orgForm?: string;
    superviseDeptCode?: string;
    superviseDeptName?: string;
    /** LocalDateTime；不提交 */
    submitTime?: null | number | string;
    /** LocalDateTime；不提交 */
    dataTimestamp?: null | number | string;
    attachmentFileIds?: number[];
  }

  /** 备案提交回执（FilingSubmitRespVO） */
  export interface SubmitResp {
    receiptNo?: string;
    status?: string;
    /** LocalDateTime → epoch 毫秒 */
    submitTime?: null | number | string;
  }

  /** 回执登记（FilingResultReqVO）：receiptNo + resultStatus 必填 */
  export interface ResultReq {
    receiptNo: string;
    resultStatus: string;
    opinion?: string;
  }
}

/** 备案状态码表（逐字取自 FilingStatusEnum） */
export const FILING_STATUS_OPTIONS = [
  { label: '待受理（PENDING）', value: 'PENDING' },
  { label: '已受理（ACCEPTED）', value: 'ACCEPTED' },
  { label: '已退回（REJECTED）', value: 'REJECTED' },
];

/** 提交备案（POST /bidding/filing/submit；权限 bidding:filing:create，后端按 filingType+projectCode 幂等） */
export function submitFiling(data: FilingApi.SubmitReq) {
  return requestClient.post<FilingApi.SubmitResp>('/bidding/filing/submit', data);
}

/** 登记备案回执（POST /bidding/filing/receive-result；权限 bidding:filing:update） */
export function receiveFilingResult(data: FilingApi.ResultReq) {
  return requestClient.post<boolean>('/bidding/filing/receive-result', data);
}
