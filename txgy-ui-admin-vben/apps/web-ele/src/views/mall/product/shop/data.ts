import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { z } from '#/adapter/form';
import { getRangePickerDefaultProps } from '#/utils';
import { getApprovedEnterpriseList } from '#/api/mall/product/enterprise';

export function useFormSchema(): VbenFormSchema[] {
  return [
    { component: 'Input', fieldName: 'id', dependencies: { triggerFields: [''], show: () => false } },
    { fieldName: 'enterpriseId', label: '入驻企业', component: 'ApiSelect', componentProps: { api: getApprovedEnterpriseList, labelField: 'name', valueField: 'id', placeholder: '请选择已审核企业' }, rules: z.number().int().positive('请选择企业') },
    { fieldName: 'name', label: '店铺名称', component: 'Input', componentProps: { placeholder: '请输入店铺名称' }, rules: 'required' },
    { fieldName: 'logoUrl', label: '店铺 Logo', component: 'ImageUpload', componentProps: { placeholder: '请上传店铺 Logo' } },
    { fieldName: 'contactName', label: '联系人', component: 'Input', componentProps: { placeholder: '请输入联系人' } },
    { fieldName: 'contactMobile', label: '联系电话', component: 'Input', componentProps: { placeholder: '请输入联系电话' } },
    { fieldName: 'managerUserId', label: '负责人用户编号', component: 'InputNumber', componentProps: { class: '!w-full', min: 1, placeholder: '后台用户编号（可选）' } },
    { fieldName: 'status', label: '店铺状态', component: 'RadioGroup', componentProps: { options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'), buttonStyle: 'solid', optionType: 'button' }, rules: z.number().default(CommonStatusEnum.ENABLE) },
    { fieldName: 'introduction', label: '店铺简介', component: 'Textarea', componentProps: { rows: 4, placeholder: '请输入店铺简介' } },
  ];
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    { fieldName: 'name', label: '店铺名称', component: 'Input', componentProps: { placeholder: '请输入店铺名称', allowClear: true } },
    { fieldName: 'enterpriseId', label: '企业编号', component: 'InputNumber', componentProps: { min: 1, placeholder: '请输入企业编号', allowClear: true } },
    { fieldName: 'status', label: '店铺状态', component: 'Select', componentProps: { options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'), placeholder: '请选择状态', allowClear: true } },
    { fieldName: 'createTime', label: '创建时间', component: 'RangePicker', componentProps: { ...getRangePickerDefaultProps(), allowClear: true } },
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'name', title: '店铺名称', minWidth: 180 },
    { field: 'enterpriseName', title: '企业名称', minWidth: 180 },
    { field: 'enterpriseId', title: '企业编号', width: 110 },
    { field: 'tenantId', title: '租户编号', width: 110 },
    { field: 'logoUrl', title: 'Logo', width: 90, cellRender: { name: 'CellImage' } },
    { field: 'contactName', title: '联系人', width: 100 },
    { field: 'contactMobile', title: '联系电话', width: 140 },
    { field: 'managerUserId', title: '负责人用户编号', width: 140 },
    { field: 'status', title: '状态', width: 90, cellRender: { name: 'CellDict', props: { type: DICT_TYPE.COMMON_STATUS } } },
    { field: 'createTime', title: '创建时间', width: 180, formatter: 'formatDateTime' },
    { title: '操作', width: 180, fixed: 'right', slots: { default: 'actions' } },
  ];
}
