import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ReportDatasetApi } from '#/api/report/dataset';

import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';

import { z } from '#/adapter/form';

/** 业务分组的预置候选项（用户可在页面上继续输入新分组） */
export const DEFAULT_BIZ_GROUPS = ['生产', '品质', '设备', '库存', '自定义'];

/** 新增/修改数据集的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'code',
      label: '数据集编码',
      component: 'Input',
      componentProps: {
        disabled: true,
        placeholder: '系统自动生成',
      },
        placeholder: '如 mes_feedback_detail，租户内唯一',
      },
      rules: 'required',
    },
    {
      fieldName: 'name',
      label: '数据集名称',
      component: 'Input',
      componentProps: {
        placeholder: '如 生产报工明细台账',
      },
      rules: 'required',
    },
    {
      fieldName: 'category',
      label: '分类（域）',
      component: 'Input',
      componentProps: {
        placeholder: '如 MES / WMS / ERP',
      },
    },
    {
      fieldName: 'bizGroups',
      label: '业务分组',
      component: 'Select',
      componentProps: {
        mode: 'tags',
        placeholder: '可多选；输入新分组名回车即新建',
        options: DEFAULT_BIZ_GROUPS.map((group) => ({
          label: group,
          value: group,
        })),
      },
      fieldName: 'sqlText',
      label: 'SQL 语句',
      component: 'Textarea',
      componentProps: {
        placeholder:
          '仅允许 SELECT / WITH 查询；参数用 ${name} 占位；禁止分号与注释符',
        rows: 6,
      },
      rules: 'required',
    },
    {
      fieldName: 'tenantColumn',
      label: '租户过滤列',
      component: 'Input',
      componentProps: {
        placeholder: '必填，如 tenant_id；该列必须出现在 SQL 的 SELECT 列表中',
      },
      rules: 'required',
      defaultValue: 'tenant_id',
    },
    {
      fieldName: 'maxRows',
      label: '最大返回行数',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 1,
        step: 100,
      },
      defaultValue: 5000,
    },
    {
      fieldName: 'timeoutSeconds',
      label: '查询超时（秒）',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 1,
        step: 5,
      },
      defaultValue: 30,
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        optionType: 'button',
        options: [
          { label: '开启', value: CommonStatusEnum.ENABLE },
          { label: '关闭', value: CommonStatusEnum.DISABLE },
        ],
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
    {
      fieldName: 'owner',
      label: '责任人',
      component: 'Input',
      componentProps: {
        placeholder: '数据集维护责任人',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '用途说明、口径说明等',
        rows: 3,
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '数据集编码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入数据集编码',
      },
    },
    {
      fieldName: 'name',
      label: '数据集名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入数据集名称',
      },
    },
    {
      fieldName: 'category',
      label: '分类（域）',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '如 MES',
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: '开启', value: CommonStatusEnum.ENABLE },
          { label: '关闭', value: CommonStatusEnum.DISABLE },
        ],
        placeholder: '请选择状态',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<ReportDatasetApi.Dataset>['columns'] {
  return [
    {
      field: 'code',
      title: '数据集编码',
      minWidth: 180,
    },
    {
      field: 'name',
      title: '数据集名称',
      minWidth: 180,
    },
    {
      field: 'category',
      title: '分类',
      width: 100,
    },
    {
      field: 'bizGroups',
      title: '分组',
      minWidth: 150,
      formatter: ({ cellValue }) =>
        Array.isArray(cellValue) ? cellValue.join(' / ') : '',
      field: 'tenantColumn',
      title: '租户过滤列',
      width: 130,
    },
    {
      field: 'maxRows',
      title: '行数上限',
      width: 110,
    },
    {
      field: 'params',
      title: '参数个数',
      width: 110,
      formatter: ({ cellValue }) => (cellValue ? cellValue.length : 0),
    },
    {
      field: 'tenantId',
      title: '归属',
      width: 110,
      formatter: ({ cellValue }) => (cellValue === 0 ? '平台级' : '本租户'),
    },
    {
      field: 'status',
      title: '状态',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.COMMON_STATUS },
      },
    },
    {
      field: 'owner',
      title: '责任人',
      width: 120,
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
