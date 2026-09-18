import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';
import { erpPriceMultiply } from '@vben/utils';

import { z } from '#/adapter/form';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';

/** 商机状态组取值（与后端 CrmBusinessStateEnum 保持一致） */
export const BUSINESS_STATE_WON = 3;
export const BUSINESS_STATE_LOST = 4;

/** 商机阶段 → 预估赢单率(%)（与后端 CrmBusinessStageEnum 保持一致） */
export const STAGE_PERCENT: Record<number, number> = {
  1: 5,
  2: 10,
  3: 40,
  4: 60,
  5: 70,
};

/** 商机是否已关闭（已赢单/已丢单）；关闭后关键字段只读、隐藏赢单/输单等按钮 */
export function isBusinessClosed(businessState?: number) {
  return (
    Number(businessState) === BUSINESS_STATE_WON ||
    Number(businessState) === BUSINESS_STATE_LOST
  );
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  const userStore = useUserStore();
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
      fieldName: 'name',
      label: '商机名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入商机名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'ownerUserId',
      label: '负责人',
      component: 'ApiSelect',
      dependencies: {
        triggerFields: ['id'],
        disabled: (values) => values.id,
      },
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择负责人',
        allowClear: true,
      },
      defaultValue: userStore.userInfo?.id,
      rules: 'required',
    },
    {
      fieldName: 'customerId',
      label: '客户名称',
      component: 'ApiSelect',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
      dependencies: {
        triggerFields: ['id'],
        disabled: (values) => values.customerDefault,
      },
      rules: 'required',
    },
    {
      fieldName: 'contactId',
      label: '合同名称',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'stage',
      label: '商机阶段',
      component: 'Select',
      rules: 'required',
      defaultValue: 1,
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_BUSINESS_STAGE, 'number'),
        placeholder: '请选择商机阶段',
      },
      dependencies: {
        // 关闭后阶段只读
        triggerFields: ['businessState'],
        disabled: (values) => isBusinessClosed(values.businessState),
        // 阶段变化时同步展示「商机状态组」与「预估赢单率」（后端也会按阶段重新派生）
        trigger(values, form) {
          if (isBusinessClosed(values.businessState)) {
            return; // 已关闭商机保留「已赢单/已丢单」与 100/0，不被阶段覆盖
          }
          const stage = Number(values.stage) || undefined;
          form.setFieldValue('winRate', stage ? STAGE_PERCENT[stage] : undefined);
          form.setFieldValue(
            'businessState',
            stage ? (stage === 1 ? 1 : 2) : undefined,
          );
        },
      },
    },
    {
      fieldName: 'businessState',
      label: '商机状态组',
      component: 'Select',
      // 只读派生字段：由「商机阶段 + 赢单/输单」自动映射，不提供编辑入口
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_BUSINESS_STATE, 'number'),
        placeholder: '由商机阶段自动映射',
        disabled: true,
      },
    },
    {
      fieldName: 'winRate',
      label: '预估赢单率(%)',
      component: 'Input',
      componentProps: {
        placeholder: '由商机阶段自动计算',
        disabled: true,
      },
    },
    {
      fieldName: 'dealTime',
      label: '预计成交日期',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD HH:mm:ss',
        valueFormat: 'x',
        placeholder: '请选择预计成交日期',
      },
    },
    {
      fieldName: 'product',
      label: '产品清单',
      component: 'Input',
      formItemClass: 'col-span-3',
      componentProps: {
        placeholder: '请输入产品清单',
        allowClear: true,
      },
    },
    {
      fieldName: 'totalProductPrice',
      label: '产品总金额',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        disabled: true,
        placeholder: '请输入产品总金额',
      },
      rules: z.number().min(0).optional().default(0),
    },
    {
      fieldName: 'discountPercent',
      label: '整单折扣（%）',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        placeholder: '请输入整单折扣',
      },
      rules: z.number().min(0).max(100).optional().default(0),
    },
    {
      fieldName: 'totalPrice',
      label: '折扣后金额',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
        disabled: true,
        placeholder: '请输入折扣后金额',
      },
      dependencies: {
        triggerFields: ['totalProductPrice', 'discountPercent'],
        trigger(values, form) {
          const discountPrice =
            erpPriceMultiply(
              values.totalProductPrice,
              values.discountPercent / 100,
            ) ?? 0;
          form.setFieldValue(
            'totalPrice',
            values.totalProductPrice - discountPrice,
          );
        },
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        placeholder: '请输入备注',
        rows: 4,
      },
      formItemClass: 'col-span-3',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '商机名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入商机名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'stage',
      label: '商机阶段',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_BUSINESS_STAGE, 'number'),
        placeholder: '请选择商机阶段',
        allowClear: true,
      },
    },
    {
      fieldName: 'businessState',
      label: '商机状态组',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_BUSINESS_STATE, 'number'),
        placeholder: '请选择商机状态组',
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
      title: '商机名称',
      fixed: 'left',
      width: 160,
      slots: { default: 'name' },
    },
    {
      field: 'customerName',
      title: '客户名称',
      fixed: 'left',
      width: 120,
      slots: { default: 'customerName' },
    },
    {
      field: 'totalPrice',
      title: '商机金额（元）',
      width: 140,
      formatter: 'formatAmount2',
    },
    {
      field: 'dealTime',
      title: '预计成交日期',
      formatter: 'formatDate',
      width: 180,
    },
    {
      field: 'remark',
      title: '备注',
      width: 200,
    },
    {
      field: 'contactNextTime',
      title: '下次联系时间',
      formatter: 'formatDateTime',
      width: 180,
    },
    {
      field: 'ownerUserName',
      title: '负责人',
      width: 100,
    },
    {
      field: 'ownerUserDeptName',
      title: '所属部门',
      width: 100,
    },
    {
      field: 'contactLastTime',
      title: '最后跟进时间',
      formatter: 'formatDateTime',
      width: 180,
    },
    {
      field: 'updateTime',
      title: '更新时间',
      formatter: 'formatDateTime',
      width: 180,
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      width: 180,
    },
    {
      field: 'creatorName',
      title: '创建人',
      width: 100,
    },
    {
      field: 'stage',
      title: '商机阶段',
      fixed: 'right',
      width: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_BUSINESS_STAGE },
      },
    },
    {
      field: 'businessState',
      title: '商机状态组',
      fixed: 'right',
      width: 110,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_BUSINESS_STATE },
      },
    },
    {
      field: 'winRate',
      title: '预估赢单率(%)',
      fixed: 'right',
      width: 130,
    },
    {
      title: '操作',
      width: 190,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
