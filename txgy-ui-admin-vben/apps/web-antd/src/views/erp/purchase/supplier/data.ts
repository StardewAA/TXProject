import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';

const supplierCreditStatusText: Record<string, string> = {
  APPROVED: '认证通过',
  BLACKLISTED: '黑名单',
  CANCELLED: '注销',
  CRITICAL: '重大风险',
  DRAFT: '草稿',
  EXPIRED: '已到期',
  EXPIRING_SOON: '即将到期',
  FROZEN: '冻结',
  HIGH: '高风险',
  LIMITED: '部分受限',
  LOW: '低风险',
  MEDIUM: '中风险',
  NONE: '无限制',
  NORMAL: '正常',
  PENDING: '待认证',
  PENDING_REVIEW: '待复核',
  REJECTED: '已驳回',
  REVOKED: '已撤销',
  VALID: '有效',
  WARNING: '风险提醒',
};

function formatSupplierCreditStatus(value?: string) {
  return value ? supplierCreditStatusText[value] || value : '-';
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'name',
      label: '供应商名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入供应商名称',
      },
    },
    {
      fieldName: 'contact',
      label: '联系人',
      component: 'Input',
      componentProps: {
        placeholder: '请输入联系人',
      },
    },
    {
      fieldName: 'mobile',
      label: '手机号码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号码',
      },
    },
    {
      fieldName: 'telephone',
      label: '联系电话',
      component: 'Input',
      componentProps: {
        placeholder: '请输入联系电话',
      },
    },
    {
      fieldName: 'email',
      label: '电子邮箱',
      component: 'Input',
      componentProps: {
        placeholder: '请输入电子邮箱',
      },
    },
    {
      fieldName: 'fax',
      label: '传真',
      component: 'Input',
      componentProps: {
        placeholder: '请输入传真',
      },
    },
    {
      fieldName: 'status',
      label: '开启状态',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入排序',
      },
      rules: 'required',
    },
    {
      fieldName: 'taxNo',
      label: '纳税人识别号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入纳税人识别号',
      },
    },
    {
      fieldName: 'taxPercent',
      label: '税率(%)',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入税率',
        min: 0,
        precision: 2,
      },
    },
    {
      fieldName: 'bankName',
      label: '开户行',
      component: 'Input',
      componentProps: {
        placeholder: '请输入开户行',
      },
    },
    {
      fieldName: 'bankAccount',
      label: '开户账号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入开户账号',
      },
    },
    {
      fieldName: 'bankAddress',
      label: '开户地址',
      component: 'Input',
      componentProps: {
        placeholder: '请输入开户地址',
      },
    },
    {
      fieldName: 'userId',
      label: '绑定登录账号',
      component: 'InputNumber',
      componentProps: {
        placeholder: '供应商门户账号的用户编号，留空表示未绑定',
        min: 1,
      },
      help: '绑定后该账号登录时只能看到发给本供应商的询价单，且只能为本供应商报价',
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        rows: 3,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '供应商名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入供应商名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'mobile',
      label: '手机号码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入手机号码',
        allowClear: true,
      },
    },
    {
      fieldName: 'telephone',
      label: '联系电话',
      component: 'Input',
      componentProps: {
        placeholder: '请输入联系电话',
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '供应商名称',
      minWidth: 150,
    },
    {
      field: 'contact',
      title: '联系人',
      minWidth: 120,
    },
    {
      field: 'mobile',
      title: '手机号码',
      minWidth: 130,
    },
    {
      field: 'telephone',
      title: '联系电话',
      minWidth: 130,
    },
    {
      field: 'email',
      title: '电子邮箱',
      minWidth: 180,
    },
    {
      field: 'status',
      title: '状态',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.COMMON_STATUS },
      },
    },
    {
      field: 'authStatus',
      title: '认证状态',
      minWidth: 100,
      formatter: ({ cellValue }) => formatSupplierCreditStatus(cellValue),
    },
    {
      field: 'qualificationStatus',
      title: '资质状态',
      minWidth: 110,
      formatter: ({ cellValue }) => formatSupplierCreditStatus(cellValue),
    },
    {
      field: 'riskLevel',
      title: '风险等级',
      minWidth: 90,
      formatter: ({ cellValue }) => formatSupplierCreditStatus(cellValue),
    },
    {
      field: 'purchaseLimitStatus',
      title: '采购限制',
      minWidth: 110,
      formatter: ({ cellValue }) => formatSupplierCreditStatus(cellValue),
    },
    {
      field: 'sort',
      title: '排序',
      minWidth: 80,
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 150,
      showOverflow: 'tooltip',
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
