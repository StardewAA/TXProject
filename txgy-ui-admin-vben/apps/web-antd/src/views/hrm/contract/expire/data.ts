import type { HrmContractExpireApi } from '#/api/hrm/contract/expire';
import type { TableColumnsType } from 'ant-design-vue';

/**
 * 列表字段
 *
 * <p>注意：本页用 ant-design-vue 的 Table 渲染（配合 #bodyCell 插槽做格式化），
 * 因此列必须用 dataIndex / key 声明。曾误用 vxe-table 的 field 写法，
 * 结果表格有行数据但每个单元格都是空的，页面表现为「一片空白」。</p>
 */
export function useGridColumns(): TableColumnsType {
  return [
    { title: '员工姓名', dataIndex: 'employeeName', key: 'employeeName', width: 120 },
    { title: '工号', dataIndex: 'jobNumber', key: 'jobNumber', width: 120 },
    { title: '合同编号', dataIndex: 'no', key: 'no', width: 220 },
    { title: '签署用工主体', dataIndex: 'signCompany', key: 'signCompany', width: 220 },
    { title: '合同开始', dataIndex: 'startTime', key: 'startTime', width: 130 },
    { title: '合同到期', dataIndex: 'endTime', key: 'endTime', width: 130 },
    { title: '剩余天数', dataIndex: 'remainDays', key: 'remainDays', width: 110 },
    { title: '紧急程度', dataIndex: 'urgency', key: 'urgency', width: 110 },
  ];
}
