import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { getAfterSaleWarrantyContractStatus } from '#/api/crm/after-sale/warranty';
import { getContractSimpleList } from '#/api/crm/contract';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';

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
      component: 'ApiSelect',
      fieldName: 'customerId',
      label: '客户名称',
      rules: 'required',
      componentProps: {
        api: getCustomerSimpleList,
        labelField: 'name',
        valueField: 'id',
        placeholder: '请选择客户',
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'contractId',
      label: '关联合同',
      rules: 'required',
      dependencies: {
        triggerFields: ['customerId', 'id'],
        // 编辑时不允许更换来源合同（后端同样会忽略该字段）；创建时需先选客户
        disabled: (values) => !values.customerId || !!values.id,
        async componentProps(values) {
          if (!values.customerId) {
            return { options: [], placeholder: '请先选择客户' };
          }
          const contracts = await getContractSimpleList(values.customerId);
          // 业务约束：一个合同仅允许一份质保档案。
          // 这里批量查出这批合同的质保档案状态，已创建的合同在选项上标注并置灰，
          // 从根本上避免操作人员选中已建档案的合同（后端同样会拦截并给出提示）。
          const statusList = await getAfterSaleWarrantyContractStatus(
            contracts.map((item: any) => item.id),
          );
          const statusMap: Record<number, any> = {};
          statusList.forEach((item) => {
            statusMap[item.contractId] = item;
          });
          return {
            options: contracts.map((item: any) => {
              const exists = statusMap[item.id];
              return {
                label: exists
                  ? `${item.name}（已创建质保档案${
                      exists.warrantyNo ? `：${exists.warrantyNo}` : ''
                    }）`
                  : item.name,
                value: item.id,
                disabled: !!exists,
              };
            }),
            placeholder: '请选择合同（已创建质保档案的合同不可重复选择）',
            allowClear: true,
            showSearch: true,
          } as any;
        },
      },
    },
    {
      component: 'Input',
      fieldName: 'projectName',
      label: '工程项目名称',
      rules: 'required',
      componentProps: {
        placeholder: '请输入工程项目名称',
        allowClear: true,
        maxLength: 128,
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'projectAcceptDate',
      label: '完工验收日期',
      rules: 'required',
      componentProps: {
        placeholder: '请选择项目完工验收日期',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'warrantyYears',
      label: '质保年限',
      rules: 'required',
      componentProps: {
        class: '!w-full',
        min: 1,
        max: 5,
        precision: 0,
        placeholder: '请输入质保年限（1-5 年）',
      },
      defaultValue: 1,
    },
    {
      component: 'DatePicker',
      fieldName: 'warrantyEndDate',
      label: '质保到期日期',
      componentProps: {
        placeholder: '留空则按验收日期 + 年限 - 1 天自动计算',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'ownerUserId',
      label: '业务负责人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择业务负责人',
        allowClear: true,
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'serviceUserId',
      label: '售后责任人',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
        placeholder: '请选择售后责任人',
        allowClear: true,
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '质保状态',
      componentProps: {
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS,
          'number',
        ),
        placeholder: '请选择质保状态',
        allowClear: true,
      },
    },
    {
      component: 'Textarea',
      fieldName: 'warrantyScope',
      label: '质保范围',
      componentProps: {
        placeholder: '请输入质保范围（防腐部位/介质环境/责任边界等）',
        rows: 3,
        maxLength: 500,
      },
      formItemClass: 'col-span-2',
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: {
        placeholder: '请输入备注',
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
      fieldName: 'no',
      label: '质保编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入质保编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'projectName',
      label: '项目名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入工程项目名称',
        allowClear: true,
      },
    },
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
      label: '质保状态',
      component: 'Select',
      componentProps: {
        allowClear: true,
        placeholder: '请选择质保状态',
        options: getDictOptions(
          DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS,
          'number',
        ),
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'no',
      title: '质保编号',
      minWidth: 160,
      fixed: 'left',
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
      field: 'contractNo',
      title: '合同编号',
      minWidth: 160,
    },
    {
      field: 'projectAcceptDate',
      title: '完工验收日期',
      minWidth: 140,
    },
    {
      field: 'warrantyYears',
      title: '质保年限',
      minWidth: 100,
    },
    {
      field: 'warrantyEndDate',
      title: '质保到期日期',
      minWidth: 140,
    },
    {
      field: 'remainDays',
      title: '剩余天数',
      minWidth: 110,
    },
    {
      field: 'status',
      title: '质保状态',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AFTER_SALE_WARRANTY_STATUS },
      },
    },
    {
      field: 'createTime',
      title: '创建时间',
      formatter: 'formatDateTime',
      minWidth: 180,
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
