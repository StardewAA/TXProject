import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace HrmEmployeeApprovalApi {
  /** 发起审批参数 */
  export interface StartApproval {
    employeeId: number;
    type: number;
    reason?: string;
    effectTime?: string;
    remark?: string;
    /** 调岗/晋升的目标任职信息（type=2 时使用） */
    targetDeptId?: number;
    targetPostName?: string;
    targetPostLevel?: string;
    targetLeaderEmployeeId?: number;
    targetWorkAddress?: string;
    /** 离职信息（type=3 时使用） */
    quitType?: number;
    quitReason?: number;
    salarySettlementTime?: string;
  }

  /** 审批申请单 */
  export interface ApprovalRecord {
    id?: number;
    employeeId?: number;
    employeeName?: string;
    jobNumber?: string;
    type?: number;
    reason?: string;
    effectTime?: string;
    remark?: string;
    status?: number;
    applied?: boolean;
    applicantName?: string;
    applyTime?: string;
    approverName?: string;
    approveTime?: string;
    approveComment?: string;
  }
}

/** 审批类型：与后端 HrmEmployeeApprovalTypeEnum 一致 */
export const HrmEmployeeApprovalTypeOptions = [
  { label: '转正（hrm_regular_approval）', value: 1 },
  { label: '调岗/晋升（hrm_transfer_approval）', value: 2 },
  { label: '离职（hrm_quit_approval）', value: 3 },
] as const;

/** 发起异动审批，返回 BPM 流程实例编号 */
export function startEmployeeApproval(
  data: HrmEmployeeApprovalApi.StartApproval,
) {
  return requestClient.post<number>('/hrm/employee/approval/create', data);
}

/** 异动审批分页 */
export function getEmployeeApprovalPage(
  params: PageParam & { employeeId?: number; status?: number; type?: number },
) {
  return requestClient.get<PageResult<HrmEmployeeApprovalApi.ApprovalRecord>>(
    '/hrm/employee/approval/page',
    { params },
  );
}

/** 审批通过/驳回 */
export function approveEmployeeApproval(data: {
  id: number;
  employeeId?: number;
  type?: number;
  approved: boolean;
  comment?: string;
}) {
  return requestClient.put<boolean>('/hrm/employee/approval/approve', data);
}

/** 审批状态选项 */
export const HrmEmployeeApprovalStatusOptions = [
  { label: '待审批', value: 1 },
  { label: '已通过', value: 2 },
  { label: '已驳回', value: 3 },
] as const;
