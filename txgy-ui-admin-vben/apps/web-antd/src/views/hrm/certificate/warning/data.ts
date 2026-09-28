import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateWarningApi } from '#/api/hrm/certificate/warning';

import {
  HrmCertificateWarningNotifyStatusLabel,
  HrmCertificateWarningStatus,
  HrmCertificateWarningStatusColor,
  HrmCertificateWarningStatusOptions,
  HrmCertificateWarningType,
  HrmCertificateWarningTypeOptions,
} from '../constants';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'type',
      label: '预警类型',
      component: 'Select',
      componentProps: {
        options: [...HrmCertificateWarningTypeOptions],
        placeholder: '请选择预警类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '处理状态',
      component: 'Select',
      componentProps: {
        options: [...HrmCertificateWarningStatusOptions],
        placeholder: '请选择处理状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'notifyStatus',
      label: '通知状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '待通知', value: 0 },
          { label: '已通知', value: 1 },
          { label: '通知失败', value: 2 },
        ],
        placeholder: '请选择通知状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'warningDateStart',
      label: '预警日期起',
      component: 'DatePicker',
      componentProps: {
        valueFormat: 'YYYY-MM-DD',
        class: 'w-full',
        placeholder: '请选择起始日期',
      },
    },
    {
      fieldName: 'warningDateEnd',
      label: '预警日期止',
      component: 'DatePicker',
      componentProps: {
        valueFormat: 'YYYY-MM-DD',
        class: 'w-full',
        placeholder: '请选择结束日期',
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmCertificateWarningApi.CertificateWarning>['columns'] {
  return [
    { field: 'employeeName', title: '员工姓名', width: 110 },
    { field: 'employeeJobNumber', title: '工号', width: 140 },
    { field: 'certificateTypeName', title: '证件类型', minWidth: 140 },
    { field: 'certificateName', title: '证件名称', minWidth: 170 },
    { field: 'certificateNo', title: '证件编号', minWidth: 160 },
    {
      field: 'type',
      title: '预警类型',
      width: 110,
      align: 'center',
      slots: { default: 'type' },
    },
    {
      field: 'warningDate',
      title: '预警日期',
      width: 130,
      align: 'center',
    },
    {
      field: 'expireDate',
      title: '到期/复审日',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'remainDays',
      title: '剩余天数',
      width: 110,
      align: 'center',
      slots: { default: 'remainDays' },
    },
    {
      field: 'status',
      title: '处理状态',
      width: 110,
      align: 'center',
      slots: { default: 'status' },
    },
    {
      field: 'notifyStatus',
      title: '通知状态',
      width: 110,
      align: 'center',
      slots: { default: 'notifyStatus' },
    },
    { field: 'handleRemark', title: '处理意见', minWidth: 180 },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 预警处理表单，按当前状态给出可选的目标状态 */
export function useHandleFormSchema(
  currentStatus?: number,
): VbenFormSchema[] {
  const isPending = currentStatus === HrmCertificateWarningStatus.PENDING;
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
      fieldName: 'status',
      label: '目标状态',
      component: 'RadioGroup',
      rules: 'required',
      componentProps: {
        options: isPending
          ? [
              { label: '已处理', value: HrmCertificateWarningStatus.HANDLED },
              { label: '已忽略', value: HrmCertificateWarningStatus.IGNORED },
            ]
          : [
              {
                label: '重新启用为待处理',
                value: HrmCertificateWarningStatus.PENDING,
              },
            ],
        buttonStyle: 'solid',
        optionType: 'button',
      },
    },
    {
      fieldName: 'handleRemark',
      label: '处理意见',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入处理意见，例如已通知员工办理续证',
        maxlength: 500,
        rows: 3,
        allowClear: true,
      },
    },
  ];
}

/** 剩余天数展示文案 */
export function formatRemainDays(remainDays?: number): string {
  if (remainDays === undefined || remainDays === null) {
    return '-';
  }
  return remainDays < 0 ? `已过期 ${Math.abs(remainDays)} 天` : `${remainDays} 天`;
}

/** 剩余天数标签颜色 */
export function getRemainDaysColor(remainDays?: number): string {
  if (remainDays === undefined || remainDays === null) {
    return 'default';
  }
  return remainDays < 0 ? 'error' : 'warning';
}

/** 预警类型文案 */
export function getWarningTypeLabel(type?: number): string {
  return (
    HrmCertificateWarningTypeOptions.find((item) => item.value === type)
      ?.label ?? '-'
  );
}

/** 预警类型标签颜色 */
export function getWarningTypeColor(type?: number): string {
  return type === HrmCertificateWarningType.REVIEW ? 'processing' : 'warning';
}

/** 处理状态文案 */
export function getStatusLabel(status?: number): string {
  return (
    HrmCertificateWarningStatusOptions.find((item) => item.value === status)
      ?.label ?? '-'
  );
}

/** 处理状态标签颜色 */
export function getStatusColor(status?: number): string {
  return status === undefined || status === null
    ? 'default'
    : (HrmCertificateWarningStatusColor[status] ?? 'default');
}

/** 通知状态文案 */
export function getNotifyStatusLabel(status?: number): string {
  return status === undefined || status === null
    ? '-'
    : (HrmCertificateWarningNotifyStatusLabel[status] ?? '-');
}
