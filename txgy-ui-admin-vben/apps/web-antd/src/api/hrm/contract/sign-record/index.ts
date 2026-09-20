import type { PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmContractSignRecordApi {
  /** 合同签署记录 */
  export interface ContractSignRecord {
    id?: number; // 编号
    contractId?: number; // 合同编号
    employeeId?: number; // 员工编号
    employeeName?: string; // 员工姓名
    jobNumber?: string; // 员工工号
    contractNo?: string; // 合同编号（冗余）
    signEmployerId?: number; // 签署用工主体编号
    signEmployerName?: string; // 签署用工主体名称
    signPerson?: string; // 签署人
    signWay?: number; // 签署方式：1 线下签署，2 电子签章
    signTime?: Date; // 签署时间
    fileUrls?: string[]; // 签署件附件
    status?: number; // 状态：0 待签，1 已签，2 已作废
    urgeCount?: number; // 催办次数
    lastUrgeTime?: Date; // 最后催办时间
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }

  /** 分页查询参数 */
  export interface SignRecordPageParams {
    pageNo?: number;
    pageSize?: number;
    contractId?: number;
    employeeId?: number;
    contractNo?: string;
    signWay?: number;
    status?: number;
  }
}

/** 查询合同签署记录分页 */
export function getContractSignRecordPage(
  params: HrmContractSignRecordApi.SignRecordPageParams,
) {
  return requestClient.get<
    PageResult<HrmContractSignRecordApi.ContractSignRecord>
  >('/hrm/contract/sign-record/page', { params });
}

/** 查询合同签署记录详情 */
export function getContractSignRecord(id: number) {
  return requestClient.get<HrmContractSignRecordApi.ContractSignRecord>(
    '/hrm/contract/sign-record/get',
    { params: { id } },
  );
}

/** 查询指定合同的签署记录 */
export function getContractSignRecordListByContract(contractId: number) {
  return requestClient.get<
    HrmContractSignRecordApi.ContractSignRecord[]
  >('/hrm/contract/sign-record/list-by-contract', {
    params: { contractId },
  });
}

/** 新增合同签署记录 */
export function createContractSignRecord(
  data: HrmContractSignRecordApi.ContractSignRecord,
) {
  return requestClient.post<number>('/hrm/contract/sign-record/create', data);
}

/** 修改合同签署记录 */
export function updateContractSignRecord(
  data: HrmContractSignRecordApi.ContractSignRecord,
) {
  return requestClient.put<boolean>('/hrm/contract/sign-record/update', data);
}

/** 删除合同签署记录 */
export function deleteContractSignRecord(id: number) {
  return requestClient.delete<boolean>('/hrm/contract/sign-record/delete', {
    params: { id },
  });
}

/** 批量催办未签署合同 */
export function urgeContractSignRecords(ids: number[]) {
  return requestClient.put<number>('/hrm/contract/sign-record/urge', ids);
}
