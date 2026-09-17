import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { reactive } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { useUserStore } from '@vben/stores';
import { erpPriceMultiply } from '@vben/utils';

import { z } from '#/adapter/form';
import { getBusinessPage, getSimpleBusinessList } from '#/api/crm/business';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';

/** 赢单客户名称 -> 客户编号，提交时把已选客户还原为编号 */
const wonCustomerNameIdMap = new Map<string, number>();
/** 赢单客户下拉选项：value 使用客户名称，保证选中后输入框展示的是名称 */
const wonCustomerOptions = reactive<{ label: string; value: string }[]>([]);
let wonCustomerLoaded = false;

/** 加载赢单客户下拉项（全局仅加载一次） */
async function ensureWonCustomersLoaded() {
  if (wonCustomerLoaded) {
    return;
  }
  wonCustomerLoaded = true;
  try {
    const wonIds = new Set<number>();
    // 商机精简接口(simple-all-list)不返回 endStatus，这里改用分页接口拉取完整字段，
    // 并循环翻页，避免被分页截断而漏掉赢单客户
    let pageNo = 1;
    const pageSize = 100;
    for (;;) {
      const page: any = await getBusinessPage({ pageNo, pageSize });
      const list = page.list ?? [];
      for (const business of list) {
        if (business.endStatus === 1) {
          wonIds.add(business.customerId);
        }
      }
      if (list.length < pageSize) {
        break;
      }
      pageNo += 1;
    }
    const customers: any = await getCustomerSimpleList();
    for (const customer of customers) {
      if (wonIds.has(customer.id)) {
        wonCustomerOptions.push({ label: customer.name, value: customer.name });
        wonCustomerNameIdMap.set(customer.name, customer.id);
      }
    }
  } catch (error) {
    wonCustomerLoaded = false;
    console.error('[quotation won customer]', error);
  }
}

/** 根据客户名称获取已存在的赢单客户编号 */
export function getWonCustomerId(name?: string) {
  return name ? wonCustomerNameIdMap.get(name.trim()) : undefined;
}

/** 缓存客户名称与编号（编辑回显、智能解析时使用） */
export function cacheCustomerNameId(name?: string, id?: number) {
  if (name && id) {
    wonCustomerNameIdMap.set(name.trim(), id);
  }
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  const userStore = useUserStore();
  // 初始化赢单客户选项，避免依赖触发导致下拉闪烁
  void ensureWonCustomersLoaded();
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
      fieldName: 'no',
      label: '报价单号',
      component: 'Input',
      componentProps: {
        placeholder: '保存时自动生成',
        disabled: true,
      },
    },
    {
      fieldName: 'name',
      label: '报价名称',
      component: 'Input',
      rules: 'required',
      componentProps: {
        placeholder: '请输入报价名称',
      },
    },
    {
      fieldName: 'ownerUserId',
      label: '负责人',
      component: 'ApiSelect',
      componentProps: {
        api: getSimpleUserList,
        labelField: 'nickname',
        valueField: 'id',
      },
      dependencies: {
        triggerFields: ['id'],
        disabled: (values) => values.id,
      },
      defaultValue: userStore.userInfo?.id,
      rules: 'required',
    },
    {
      fieldName: 'customerName',
      label: '客户名称',
      component: 'AutoComplete',
      rules: 'required',
      componentProps: {
        options: wonCustomerOptions,
        // 展示下拉按钮，可点击选择赢单客户
        showArrow: true,
        allowClear: true,
        filterOption: true,
        // combobox 模式下若选项为空且未设置 notFoundContent，antd 会强制关闭下拉，
        // 导致点击箭头无反应；显式设置后即使无赢单客户也能正常展开并给出提示
        notFoundContent: '暂无赢单客户，可直接输入新客户名称',
        placeholder: '请选择赢单客户，或直接输入客户名称',
      },
    },
    {
      fieldName: 'businessId',
      label: '商机名称',
      component: 'Select',
      componentProps: {
        options: [],
        placeholder: '请选择商机',
      },
      dependencies: {
        triggerFields: ['customerName'],
        disabled: (values) => !getWonCustomerId(values.customerName),
        async componentProps(values) {
          const customerId = getWonCustomerId(values.customerName);
          if (!customerId) {
            return {
              options: [],
              placeholder: '请先选择赢单客户',
            };
          }
          const res = await getSimpleBusinessList();
          const list = res.filter((item) => item.customerId === customerId);
          return {
            options: list.map((item) => ({
              label: item.name,
              value: item.id,
            })),
            placeholder: '请选择商机',
          };
        },
      },
    },
    {
      fieldName: 'orderDate',
      label: '报价日期',
      component: 'DatePicker',
      rules: 'required',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择报价日期',
      },
    },
    {
      fieldName: 'validUntil',
      label: '有效期至',
      component: 'DatePicker',
      componentProps: {
        showTime: false,
        format: 'YYYY-MM-DD',
        valueFormat: 'x',
        placeholder: '请选择报价有效期至',
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
    },
    {
      fieldName: 'product',
      label: '产品清单',
      component: 'Input',
      formItemClass: 'col-span-3',
    },
    {
      fieldName: 'totalProductPrice',
      label: '产品总金额',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        precision: 2,
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
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'no',
      label: '报价单号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入报价单号',
        allowClear: true,
      },
    },
    {
      fieldName: 'name',
      label: '报价名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入报价名称',
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
  ];
}

export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      title: '报价单号',
      field: 'no',
      minWidth: 180,
      fixed: 'left',
    },
    {
      title: '报价名称',
      field: 'name',
      minWidth: 160,
      fixed: 'left',
      slots: { default: 'name' },
    },
    {
      title: '客户名称',
      field: 'customerName',
      minWidth: 120,
      slots: { default: 'customerName' },
    },
    {
      title: '商机名称',
      field: 'businessName',
      minWidth: 130,
      slots: { default: 'businessName' },
    },
    {
      title: '报价金额（元）',
      field: 'totalPrice',
      minWidth: 140,
      formatter: 'formatAmount2',
    },
    {
      title: '报价日期',
      field: 'orderDate',
      minWidth: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '有效期至',
      field: 'validUntil',
      minWidth: 120,
      formatter: 'formatDateTime',
    },
    {
      title: '备注',
      field: 'remark',
      minWidth: 200,
    },
    {
      title: '负责人',
      field: 'ownerUserName',
      minWidth: 120,
    },
    {
      title: '所属部门',
      field: 'ownerUserDeptName',
      minWidth: 100,
    },
    {
      title: '更新时间',
      field: 'updateTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '创建时间',
      field: 'createTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '创建人',
      field: 'creatorName',
      minWidth: 120,
    },
    {
      title: '审批状态',
      field: 'auditStatus',
      fixed: 'right',
      minWidth: 120,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.CRM_AUDIT_STATUS },
      },
    },
    {
      title: '操作',
      field: 'actions',
      fixed: 'right',
      minWidth: 150,
      slots: { default: 'actions' },
    },
  ];
}
