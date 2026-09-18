import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import dayjs from 'dayjs';

import { getAfterSaleWarrantyOptions } from '#/api/crm/after-sale/warranty';
import { getCustomerSimpleList } from '#/api/crm/customer';

/**
 * 新增/修改的表单
 *
 * @param getAcceptDateTs 关联质保档案的完工验收日期（毫秒时间戳）取值函数，
 *                        用于限制"回访时间"的可选范围（不早于验收日期、不晚于今天）
 */
export function useFormSchema(
  getAcceptDateTs?: () => number | undefined,
  /**
   * 「关联任务编号」是否锁定（置灰不可编辑）：
   * 1）编辑已有回访记录时锁定，避免改绑任务；
   * 2）由任务列表「填写回访记录」跳转填写时锁定。
   * 仅「回访记录模块内新增」时允许选择。
   */
  isTaskLocked?: () => boolean,
): VbenFormSchema[] {
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
      component: 'ApiSelect',
      fieldName: 'warrantyId',
      label: '关联质保档案',
      rules: 'required',
      componentProps: {
        api: getAfterSaleWarrantyOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择关联质保档案',
        allowClear: true,
        showSearch: true,
        optionFilterProp: 'label',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'taskBizNo',
      label: '关联任务编号',
      // 业务编号：在「所属质保档案」范围内独立编号（从 1 开始），选择质保档案后自动带出
      dependencies: {
        triggerFields: ['warrantyId'],
        componentProps() {
          return {
            // 编辑已有记录 / 由任务跳转填写时锁定（仅可查看）
            disabled: isTaskLocked?.() ?? false,
          };
        },
      },
      componentProps: {
        class: '!w-full',
        min: 1,
        precision: 0,
        placeholder: '选择质保档案后自动带出，可修改',
      },
    },
    {
      // 提交给后端的任务主键：由「关联任务编号」按质保档案反查得到，不在界面直接暴露
      component: 'Input',
      fieldName: 'taskId',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'Select',
      fieldName: 'visitType',
      label: '回访方式',
      rules: 'required',
      componentProps: {
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE, 'number'),
        placeholder: '请选择回访方式',
        allowClear: true,
      },
      defaultValue: 1,
    },
    {
      component: 'DatePicker',
      fieldName: 'visitTime',
      label: '回访时间',
      componentProps: {
        showTime: true,
        placeholder: '留空则为当前时间',
        // 后端返回的时间为毫秒时间戳（与合同等模块一致），此处需用 'x' 才能正确回显，
        // 否则加载已有记录时会出现 Invalid Date
        valueFormat: 'x',
        format: 'YYYY-MM-DD HH:mm:ss',
        // 选择时即限制范围：不能晚于今天，也不能早于项目完工验收日期
        disabledDate: (current: any) => {
          if (!current) {
            return false;
          }
          if (current.isAfter(dayjs().endOf('day'))) {
            return true;
          }
          const acceptTs = getAcceptDateTs?.();
          return Boolean(
            acceptTs && current.isBefore(dayjs(acceptTs).startOf('day')),
          );
        },
      },
    },
    {
      component: 'Select',
      fieldName: 'satisfactionLevel',
      label: '满意度',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_SATISFACTION,
          'number',
        ),
        placeholder: '请选择满意度',
        allowClear: true,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'nextVisitDate',
      label: '建议下次回访日期',
      componentProps: {
        placeholder: '请选择建议下次回访日期',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '回访内容',
      rules: 'required',
      componentProps: {
        placeholder: '请输入回访内容',
        rows: 4,
        maxLength: 2000,
      },
      formItemClass: 'col-span-2',
    },
    {
      component: 'Textarea',
      fieldName: 'satisfactionRemark',
      label: '满意度备注',
      componentProps: {
        placeholder: '请输入满意度备注',
        rows: 3,
        maxLength: 500,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'customerId',
      label: '客户',
      component: 'ApiSelect',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '记录状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择记录状态',
        options: [
          { label: '待填写', value: 1 },
          { label: '已填写', value: 2 },
        ],
      },
    },
    {
      fieldName: 'visitType',
      label: '回访方式',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择回访方式',
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE, 'number'),
      },
    },
    {
      fieldName: 'satisfactionLevel',
      label: '满意度',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择满意度',
        options: getDictOptions(DICT_TYPE.CRM_AFTER_SALE_SATISFACTION, 'number'),
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'warrantyNo',
      title: '质保编号',
      minWidth: 160,
      fixed: 'left',
    },
    {
      field: 'taskBizNo',
      title: '关联任务编号',
      minWidth: 120,
    },
    {
      field: 'projectName',
      title: '工程项目名称',
      minWidth: 200,
    },
    {
      field: 'customerName',
      title: '客户名称',
      minWidth: 160,
    },
    {
      field: 'status',
      title: '记录状态',
      minWidth: 110,
      slots: { default: 'status' },
    },
    {
      field: 'visitType',
      title: '回访方式',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_VISIT_TYPE },
      },
    },
    {
      field: 'visitTime',
      title: '回访时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      field: 'content',
      title: '回访内容',
      minWidth: 260,
    },
    {
      field: 'satisfactionLevel',
      title: '满意度',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_SATISFACTION },
      },
    },
    {
      field: 'nextVisitDate',
      title: '下次回访日期',
      minWidth: 140,
    },
    {
      field: 'creatorNickname',
      title: '回访人',
      minWidth: 120,
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
