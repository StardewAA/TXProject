import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmSalaryPieceRecordApi {
  /** 计件计量明细 */
  export interface PieceRecord {
    id?: number; // 明细编号
    employeeId?: number; // 员工编号
    employeeName?: string; // 员工姓名
    jobNumber?: string; // 工号
    year?: number; // 年
    month?: number; // 月
    itemName?: string; // 工程量/工序名称
    unit?: string; // 单位
    quantity?: number; // 完成工程量
    unitPrice?: number; // 计件单价
    amount?: number; // 计件金额（后端计算）
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }
}

/** 保存计件计量明细（无编号新增、有编号修改） */
export function savePieceRecord(
  data: HrmSalaryPieceRecordApi.PieceRecord,
) {
  return requestClient.post<number>('/hrm/salary/piece-record/save', data);
}

/** 删除计件计量明细 */
export function deletePieceRecord(id: number) {
  return requestClient.delete<boolean>('/hrm/salary/piece-record/delete', {
    params: { id },
  });
}

/** 查询计件计量明细分页 */
export function getPieceRecordPage(
  params: PageParam & {
    employeeId?: number;
    itemName?: string;
    month?: number;
    year?: number;
  },
) {
  return requestClient.get<PageResult<HrmSalaryPieceRecordApi.PieceRecord>>(
    '/hrm/salary/piece-record/page',
    { params },
  );
}
