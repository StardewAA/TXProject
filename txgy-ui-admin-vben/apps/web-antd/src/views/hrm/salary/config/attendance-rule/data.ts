import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmSalaryAttendanceRuleApi } from '#/api/hrm/salary/attendance-rule';

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmSalaryAttendanceRuleApi.AttendanceRule>['columns'] {
  return [
    { field: 'name', title: '规则名称', minWidth: 220 },
    { field: 'fullAttendanceDays', title: '满勤天数', width: 100 },
    { field: 'absenceDeductRate', title: '缺勤扣款比例(%)', width: 140 },
    { field: 'fullAttendanceBonusRate', title: '满勤奖比例(%)', width: 130 },
    { field: 'personalLeaveRate', title: '事假扣(%)', width: 110 },
    { field: 'sickLeaveRate', title: '病假扣(%)', width: 110 },
    { field: 'lateToAbsenceTimes', title: '迟到折算(次/天)', width: 140 },
    {
      field: 'defaultFlag',
      title: '默认',
      width: 80,
      formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '启用' : '停用'),
    },
    { title: '操作', width: 160, fixed: 'right', slots: { default: 'actions' } },
  ];
}

/** 列表搜索 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '规则名称',
      component: 'Input',
      componentProps: { placeholder: '请输入规则名称', allowClear: true },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

/**
 * 新增/修改表单
 *
 * @param rateLimits 各计薪项的比例上限（%），来自「薪资管理 → 比例上限」配置，
 *                   未传入时按 100% 兜底；上限不再写死在表单里。
 *
 * 注意：这里**不设置 InputNumber 的 max**。max 会在失焦时把超限的历史值静默夹取到上限，
 * 造成“打开弹窗什么都没改，保存后金额口径变了”。改为提交时按配置上限校验并明确提示。
 */
export function useFormSchema(
  rateLimits: Record<string, number | undefined> = {},
): VbenFormSchema[] {
  const limitOf = (code: string) => rateLimits[code] ?? 100;
  const limitHelp = (code: string) =>
    `上限 ${limitOf(code)}%，超出将无法保存（可在「薪资管理 → 比例上限」按项调整）`;
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
    },
    {
      fieldName: 'name',
      label: '规则名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '例如：防腐保温工程队标准计薪规则',
        maxlength: 128,
        allowClear: true,
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'fullAttendanceDays',
      label: '满勤天数',
      component: 'InputNumber',
      rules: 'required',
      defaultValue: 26,
      componentProps: { min: 1, precision: 2, class: 'w-full', placeholder: '例如：26' },
      help: '当月应出勤天数，用于折算日薪',
    },
    {
      fieldName: 'dailyWageMode',
      label: '日薪折算方式',
      component: 'Select',
      defaultValue: 1,
      componentProps: {
        options: [
          { label: '月薪 ÷ 满勤天数', value: 1 },
          { label: '按日薪直接取', value: 2 },
        ],
      },
    },
    {
      fieldName: 'absenceDeductRate',
      label: '缺勤扣款比例(%)',
      component: 'InputNumber',
      defaultValue: 100,
      componentProps: {
        min: 0,
        precision: 2,
        class: 'w-full',
      },
      help: `100 表示缺 1 天扣 1 天日薪；50 表示只扣一半。${limitHelp('absenceDeductRate')}`,
    },
    {
      fieldName: 'fullAttendanceBonusRate',
      label: '满勤奖比例(%)',
      component: 'InputNumber',
      defaultValue: 0,
      componentProps: {
        min: 0,
        precision: 2,
        class: 'w-full',
      },
      help: `按日薪计算，0 表示无满勤奖。${limitHelp('fullAttendanceBonusRate')}`,
    },
    {
      fieldName: 'personalLeaveRate',
      label: '事假扣款比例(%)',
      component: 'InputNumber',
      defaultValue: 100,
      componentProps: {
        min: 0,
        precision: 2,
        class: 'w-full',
      },
      help: limitHelp('personalLeaveRate'),
    },
    {
      fieldName: 'sickLeaveRate',
      label: '病假扣款比例(%)',
      component: 'InputNumber',
      defaultValue: 60,
      componentProps: {
        min: 0,
        precision: 2,
        class: 'w-full',
      },
      help: limitHelp('sickLeaveRate'),
    },
    {
      fieldName: 'lateToAbsenceTimes',
      label: '迟到早退折算',
      component: 'InputNumber',
      defaultValue: 0,
      componentProps: { min: 0, class: 'w-full' },
      help: '填 3 表示迟到早退 3 次折算 1 天缺勤；0 表示不折算',
    },
    {
      fieldName: 'defaultFlag',
      label: '设为默认规则',
      component: 'Switch',
      defaultValue: false,
      componentProps: { checkedChildren: '是', unCheckedChildren: '否' },
      help: '默认规则用于月度工资表自动计薪',
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      defaultValue: 0,
      componentProps: {
        options: [
          { label: '启用', value: 0 },
          { label: '停用', value: 1 },
        ],
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: { rows: 2, maxlength: 500, allowClear: true },
      formItemClass: 'col-span-2',
    },
  ];
}
