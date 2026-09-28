import { requestClient } from '#/api/request';

export namespace HrmEmployeeQuitHandoverApi {
  /** 离职交接明细 */
  export interface HandoverItem {
    id?: number; // 交接明细编号
    employeeId?: number; // 员工编号
    itemType?: number; // 物品类型
    itemTypeName?: string; // 物品类型名称
    itemName?: string; // 物品名称
    quantity?: number; // 数量
    unit?: string; // 单位
    status?: number; // 交接状态
    statusName?: string; // 交接状态名称
    confirmTime?: Date; // 交回确认时间
    remark?: string; // 备注
    createTime?: Date; // 创建时间
  }

  /** 离职交接进度 */
  export interface HandoverProgress {
    employeeId?: number;
    hasQuitInfo?: boolean; // 是否存在离职单
    employeeName?: string;
    jobNumber?: string;
    planQuitTime?: number;
    handoverStatus?: number; // 0未开始 1进行中 2已确认 3无需交接
    handoverStatusName?: string;
    handoverToEmployeeId?: number;
    handoverToEmployeeName?: string;
    handoverConfirmTime?: number;
    handoverRemark?: string;
    itemCount?: number;
    pendingItemCount?: number;
    lostItemCount?: number;
    confirmable?: boolean;
    items?: HandoverItem[];
    salarySettlementTime?: number; // 薪资结算时间
    salaryCutoffDate?: string; // 计薪截止日
    salarySettled?: boolean; // 工资是否已结清
    salarySettleTime?: number; // 工资结清时间
    salarySettleRemark?: string; // 结算说明
    closed?: boolean; // 离职闭环是否完成
  }
}

/** 交接物品类型（对齐后端 HrmEmployeeQuitHandoverItemTypeEnum） */
export const HrmQuitHandoverItemType = {
  TOOL: 1,
  LABOR_SUPPLY: 2,
  CERTIFICATE: 3,
  ARCHIVE: 4,
  FINANCE: 5,
  OTHER: 6,
} as const;

/** 交接物品类型选项 */
export const HrmQuitHandoverItemTypeOptions = [
  { label: '工器具', value: HrmQuitHandoverItemType.TOOL },
  { label: '劳保用品', value: HrmQuitHandoverItemType.LABOR_SUPPLY },
  { label: '证件资料', value: HrmQuitHandoverItemType.CERTIFICATE },
  { label: '档案文件', value: HrmQuitHandoverItemType.ARCHIVE },
  { label: '财务借支', value: HrmQuitHandoverItemType.FINANCE },
  { label: '其他', value: HrmQuitHandoverItemType.OTHER },
] as const;

/** 交接明细状态（对齐后端 HrmEmployeeQuitHandoverItemStatusEnum） */
export const HrmQuitHandoverItemStatus = {
  PENDING: 0,
  RETURNED: 1,
  LOST: 2,
  WAIVED: 3,
} as const;

/** 交接明细状态选项 */
export const HrmQuitHandoverItemStatusOptions = [
  { label: '待交回', value: HrmQuitHandoverItemStatus.PENDING },
  { label: '已交回', value: HrmQuitHandoverItemStatus.RETURNED },
  { label: '缺失未交回', value: HrmQuitHandoverItemStatus.LOST },
  { label: '无需交接', value: HrmQuitHandoverItemStatus.WAIVED },
] as const;

/** 查询离职交接进度（含明细） */
export function getQuitHandoverProgress(employeeId: number) {
  return requestClient.get<HrmEmployeeQuitHandoverApi.HandoverProgress>(
    '/hrm/employee/quit-handover/progress',
    { params: { employeeId } },
  );
}

/** 保存离职交接明细 */
export function saveQuitHandoverItem(
  data: HrmEmployeeQuitHandoverApi.HandoverItem,
) {
  return requestClient.post<number>('/hrm/employee/quit-handover/item-save', data);
}

/** 删除离职交接明细 */
export function deleteQuitHandoverItem(id: number) {
  return requestClient.delete<boolean>('/hrm/employee/quit-handover/item-delete', {
    params: { id },
  });
}

/** 确认离职交接完成 */
export function confirmQuitHandover(data: {
  employeeId: number;
  handoverToEmployeeId?: number;
  remark?: string;
}) {
  return requestClient.put<boolean>('/hrm/employee/quit-handover/confirm', data);
}

/** 标记为无需交接 */
export function waiveQuitHandover(data: {
  employeeId: number;
  remark: string;
}) {
  return requestClient.put<boolean>('/hrm/employee/quit-handover/waive', data);
}

/** 确认离职工资已结清 */
export function settleQuitSalary(data: {
  employeeId: number;
  salaryCutoffDate?: string;
  salarySettleRemark?: string;
}) {
  return requestClient.put<boolean>('/hrm/employee/quit-handover/settle', data);
}
