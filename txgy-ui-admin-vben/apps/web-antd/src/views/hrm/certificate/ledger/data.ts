import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmCertificateLedgerApi } from '#/api/hrm/certificate/ledger';

import { markRaw } from 'vue';

import { z } from '@vben/common-ui';

import EmployeeSelect from '#/views/hrm/employee/components/employee-select.vue';

import {
  HrmCertificateStatusColor,
  HrmCertificateStatusOptions,
} from '../constants';
import CertificateTypeSelect from './components/certificate-type-select.vue';

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '证件名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'no',
      label: '证件编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'typeId',
      label: '证件类型',
      component: markRaw(CertificateTypeSelect),
      componentProps: {
        onlyEnabled: false,
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '证件状态',
      component: 'Select',
      componentProps: {
        options: [...HrmCertificateStatusOptions],
        placeholder: '请选择证件状态',
        allowClear: true,
      },
    },
  ];
}

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<HrmCertificateLedgerApi.CertificateLedger>['columns'] {
  return [
    { field: 'employeeName', title: '员工姓名', width: 110 },
    { field: 'employeeJobNumber', title: '工号', width: 140 },
    { field: 'typeName', title: '证件类型', minWidth: 150 },
    { field: 'name', title: '证件名称', minWidth: 180 },
    { field: 'no', title: '证件编号', minWidth: 170 },
    {
      field: 'endTime',
      title: '有效期至',
      width: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'reviewTime',
      title: '复审日期',
      width: 170,
      formatter: ({ cellValue }) =>
        cellValue ? formatDateTimeCell(cellValue) : '-',
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
      title: '证件状态',
      width: 110,
      align: 'center',
      slots: { default: 'status' },
    },
    {
      field: 'pendingWarningCount',
      title: '待处理预警',
      width: 110,
      align: 'center',
      slots: { default: 'pendingWarningCount' },
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 新增/修改表单 */
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
      fieldName: 'employeeId',
      label: '员工',
      component: markRaw(EmployeeSelect),
      rules: 'required',
      componentProps: {
        placeholder: '请选择员工',
        allowClear: true,
      },
    },
    {
      fieldName: 'typeId',
      label: '证件类型',
      component: markRaw(CertificateTypeSelect),
      componentProps: {
        onlyEnabled: true,
        allowClear: true,
      },
    },
    {
      fieldName: 'name',
      label: '证件名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入证件名称',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'no',
      label: '证件编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件编号',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'level',
      label: '证件级别',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件级别',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'startTime',
      label: '有效开始日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'endTime',
      label: '有效结束日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'reviewTime',
      label: '复审日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'issuingAuthority',
      label: '发证机构',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发证机构',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'issuingTime',
      label: '发证日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'attachmentUrl',
      label: '证件附件',
      component: 'Input',
      componentProps: {
        placeholder: '请输入证件附件的文件地址',
        maxlength: 512,
        allowClear: true,
      },
    },
    {
      fieldName: 'sort',
      label: '显示顺序',
      component: 'InputNumber',
      defaultValue: 0,
      rules: z.number().min(0, { message: '显示顺序不能小于 0' }).default(0),
      componentProps: {
        placeholder: '请输入显示顺序',
        min: 0,
        class: 'w-full',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        maxlength: 500,
        rows: 3,
        allowClear: true,
      },
    },
  ];
}

/** 续证/复审表单 */
export function useRenewalFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'type',
      label: '办理类型',
      component: 'RadioGroup',
      defaultValue: 1,
      rules: 'required',
      componentProps: {
        options: [
          { label: '续证', value: 1 },
          { label: '复审', value: 2 },
        ],
        buttonStyle: 'solid',
        optionType: 'button',
      },
    },
    {
      fieldName: 'newNo',
      label: '新证件编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入办理后的证件编号',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'newStartTime',
      label: '新有效开始',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'newEndTime',
      label: '新有效结束',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'reviewTime',
      label: '复审日期',
      component: 'DatePicker',
      dependencies: {
        triggerFields: ['type'],
        // 复审必须填写复审日期，续证不允许填写
        disabled: (values) => values.type !== 2,
      },
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'issuingAuthority',
      label: '发证机构',
      component: 'Input',
      componentProps: {
        placeholder: '请输入发证机构',
        maxlength: 255,
        allowClear: true,
      },
    },
    {
      fieldName: 'issuingTime',
      label: '发证日期',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        class: 'w-full',
      },
    },
    {
      fieldName: 'attachmentUrl',
      label: '新证件附件',
      component: 'Input',
      componentProps: {
        placeholder: '请输入新证件附件的文件地址',
        maxlength: 512,
        allowClear: true,
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
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
    return '长期有效';
  }
  if (remainDays < 0) {
    return `已过期 ${Math.abs(remainDays)} 天`;
  }
  return `剩余 ${remainDays} 天`;
}

/** 剩余天数标签颜色 */
export function getRemainDaysColor(remainDays?: number): string {
  if (remainDays === undefined || remainDays === null) {
    return 'default';
  }
  if (remainDays < 0) {
    return 'error';
  }
  if (remainDays <= 30) {
    return 'warning';
  }
  return 'success';
}

/** 证件状态标签颜色 */
export function getStatusColor(status?: number): string {
  return status === undefined || status === null
    ? 'default'
    : (HrmCertificateStatusColor[status] ?? 'default');
}

/** 证件状态文案 */
export function getStatusLabel(status?: number): string {
  return (
    HrmCertificateStatusOptions.find((item) => item.value === status)?.label ??
    '-'
  );
}

function formatDateTimeCell(cellValue: number): string {
  const date = new Date(cellValue);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
    date.getSeconds(),
  )}`;
}
