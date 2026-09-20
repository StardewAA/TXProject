import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmContractExpireApi } from '#/api/hrm/contract/expire';

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmContractExpireApi.ContractExpire>['columns'] {
  return [
    { field: 'employeeName', title: '员工姓名', width: 120 },
    { field: 'jobNumber', title: '工号', width: 120 },
    { field: 'no', title: '合同编号', minWidth: 220 },
    { field: 'signCompany', title: '签署用工主体', minWidth: 200 },
    { field: 'startTime', title: '合同开始', width: 170 },
    { field: 'endTime', title: '合同到期', width: 170 },
    {
      field: 'remainDays',
      title: '剩余天数',
      width: 110,
      slots: { default: 'remainDays' },
    },
    {
      field: 'urgency',
      title: '紧急程度',
      width: 110,
      slots: { default: 'urgency' },
    },
  ];
}
