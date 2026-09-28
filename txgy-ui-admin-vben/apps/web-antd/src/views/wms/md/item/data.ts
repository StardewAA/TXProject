import type { VbenFormApi, VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { h, markRaw } from 'vue';

import { useAccess } from '@vben/access';
import { CommonStatusEnum, generateWmsCode } from '@vben/constants';

import { Button } from 'ant-design-vue';

import { z } from '#/adapter/form';
import { getItemBrandSimpleList } from '#/api/wms/md/item/brand';
import { materialTypeLabel } from '#/views/wms/utils/material-type';

import { WmsSwitchGroup } from './components';
import { WmsItemBrandSelect } from './brand/components';
import { WmsItemCategorySelect } from './category/components';

/** 管理开关字段名（在表单里合并为一个对象字段，见 useFormSchema 注释） */
export const MANAGE_FIELDS = [
  'batchManage',
  'shelfLifeManage',
  'serialNumberManage',
  'dangerousGoods',
] as const;

/** 管理开关默认值 */
export const MANAGE_DEFAULTS: Record<string, boolean> = {
  batchManage: false,
  shelfLifeManage: false,
  serialNumberManage: false,
  dangerousGoods: false,
};

/** 管理开关的行内配置 */
const MANAGE_ITEMS = [
  { field: 'batchManage', label: '批次管理', help: '启用后该商品按批次入库、出库与追溯' },
  { field: 'shelfLifeManage', label: '保质期管理' },
  { field: 'serialNumberManage', label: '序列号管理' },
  { field: 'dangerousGoods', label: '危险品', checkedChildren: '危险品', unCheckedChildren: '普通商品' },
];

/** 新增/修改商品的表单 */
export function useFormSchema(formApi?: VbenFormApi): VbenFormSchema[] {
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
      label: '商品名称',
      component: 'Input',
      componentProps: {
        maxLength: 60,
        placeholder: '请输入商品名称',
      },
      rules: z.string().min(1, '商品名称不能为空').max(60),
    },
    {
      fieldName: 'categoryId',
      label: '商品分类',
      component: markRaw(WmsItemCategorySelect),
      rules: 'required',
    },
    {
      fieldName: 'code',
      label: '商品编号',
      component: 'Input',
      componentProps: {
        maxLength: 20,
        placeholder: '请输入商品编号',
      },
      rules: z.string().min(1, '商品编号不能为空').max(20),
      suffix: () => {
        return h(
          Button,
          {
            type: 'default',
            onClick: () => {
              formApi?.setFieldValue('code', generateWmsCode('I'));
            },
          },
          { default: () => '生成' },
        );
      },
    },
    {
      fieldName: 'unit',
      label: '商品单位',
      component: 'Input',
      componentProps: {
        maxLength: 20,
        placeholder: '请输入单位',
      },
    },
    {
      fieldName: 'brandId',
      label: '商品品牌',
      component: markRaw(WmsItemBrandSelect),
    },
    {
      fieldName: 'materialType',
      label: '物料类型',
      component: 'Select',
      componentProps: {
        options: [
          { label: '原材料', value: 'RAW_MATERIAL' },
          { label: '成品', value: 'FINISHED_GOODS' },
        ],
        placeholder: '请选择物料类型',
      },
    },
    // ========== 管理开关 ==========
    // 4 个开关合并成一个对象字段 manage，由 WmsSwitchGroup 横排渲染。
    // 若各自占一个表单项，双栏布局下右侧会留下大片空白。
    // 依赖判断统一用 manage.xxx（dependencies 支持点号路径）。
    {
      fieldName: 'manage',
      label: '管理方式',
      component: markRaw(WmsSwitchGroup),
      componentProps: {
        items: MANAGE_ITEMS,
      },
      formItemClass: 'col-span-2',
      defaultValue: { ...MANAGE_DEFAULTS },
    },
    // ========== 批次管理扩展字段（批次管理=是 时展开）==========
    {
      fieldName: 'batchNumberRule',
      label: '批次号规则',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '如：供应商编号+生产日期，如 XS20260210',
      },
      formItemClass: 'col-span-2',
      dependencies: {
        triggerFields: ['manage.batchManage'],
        show: (values) => !!values.manage?.batchManage,
      },
    },
    {
      fieldName: 'expirationRule',
      label: '有效期计算',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '如：生产日期 + 24个月',
      },
      formItemClass: 'col-span-2',
      dependencies: {
        triggerFields: ['manage.batchManage', 'manage.shelfLifeManage'],
        show: (values) =>
          !!values.manage?.batchManage || !!values.manage?.shelfLifeManage,
      },
    },
    {
      fieldName: 'shelfLifeMonths',
      label: '保质期(月)',
      component: 'InputNumber',
      componentProps: {
        class: 'w-full',
        min: 0,
        precision: 0,
        placeholder: '请输入保质期月数',
      },
      dependencies: {
        triggerFields: ['manage.shelfLifeManage'],
        show: (values) => !!values.manage?.shelfLifeManage,
      },
    },
    {
      fieldName: 'expiryWarningDays',
      label: '到期预警(天)',
      component: 'InputNumber',
      componentProps: {
        class: 'w-full',
        min: 0,
        precision: 0,
        placeholder: '默认 30',
      },
      defaultValue: 30,
      dependencies: {
        triggerFields: ['manage.shelfLifeManage'],
        show: (values) => !!values.manage?.shelfLifeManage,
      },
    },
    {
      fieldName: 'expiredOutboundBlocked',
      label: '过期禁出库',
      component: 'Switch',
      componentProps: {
        checkedValue: true,
        unCheckedValue: false,
        checkedChildren: '是',
        unCheckedChildren: '否',
      },
      defaultValue: true,
      dependencies: {
        triggerFields: ['manage.shelfLifeManage'],
        show: (values) => !!values.manage?.shelfLifeManage,
      },
    },
    // ========== 包装 ==========
    {
      fieldName: 'packageSpec',
      label: '包装规格',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '如：20kg/组（主剂16kg+固化剂4kg）',
      },
    },
    {
      fieldName: 'boxSpec',
      label: '箱规',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '如：1组/箱',
      },
    },
    {
      fieldName: 'palletSpec',
      label: '托盘规格',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '如：20组/托盘（400kg）',
      },
    },
    {
      fieldName: 'minPackageUnit',
      label: '最小包装单位',
      component: 'Input',
      componentProps: {
        maxLength: 32,
        placeholder: '如：组',
      },
    },
    // ========== 存储 ==========
    {
      fieldName: 'storageCondition',
      label: '存储条件',
      component: 'Textarea',
      componentProps: {
        maxLength: 500,
        rows: 2,
        placeholder: '如：密封避光，防止固化剂受潮',
      },
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'temperatureRequirement',
      label: '温度要求',
      component: 'Input',
      componentProps: {
        maxLength: 64,
        placeholder: '如：5-35℃',
      },
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Textarea',
      componentProps: {
        maxLength: 255,
        placeholder: '请输入备注',
        rows: 3,
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'code',
      label: '商品编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品编号',
      },
    },
    {
      fieldName: 'name',
      label: '商品名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品名称',
      },
    },
    {
      fieldName: 'brandId',
      label: '商品品牌',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: getItemBrandSimpleList,
        labelField: 'name',
        placeholder: '请选择商品品牌',
        showSearch: true,
        valueField: 'id',
      },
    },
  ];
}

/** 列表的字段（对应【4-2 商品档案总览】） */
export function useGridColumns(
  onStatusChange?: (
    newStatus: number,
    row: any,
  ) => PromiseLike<boolean | undefined>,
): VxeTableGridOptions['columns'] {
  const { hasAccessByCodes } = useAccess();

  return [
    {
      field: 'itemCode',
      title: '商品编号',
      width: 130,
    },
    {
      field: 'itemInfo',
      title: '商品名称',
      minWidth: 180,
      slots: { default: 'itemInfo' },
    },
    {
      field: 'categoryName',
      title: '商品分类',
      minWidth: 140,
    },
    {
      field: 'unit',
      title: '单位',
      width: 80,
    },
    {
      field: 'brandName',
      title: '品牌',
      minWidth: 110,
    },
    {
      field: 'materialType',
      title: '物料类型',
      width: 110,
      align: 'center',
      formatter: ({ cellValue }) =>
        materialTypeLabel(cellValue as string | undefined),
    },
    {
      field: 'batchManage',
      title: '批次管理',
      width: 100,
      align: 'center',
      slots: { default: 'batchManage' },
    },
    {
      field: 'shelfLife',
      title: '保质期',
      width: 90,
      align: 'center',
      slots: { default: 'shelfLife' },
    },
    {
      field: 'dangerousGoods',
      title: '危险品标识',
      width: 110,
      align: 'center',
      slots: { default: 'dangerousGoods' },
    },
    {
      field: 'temperatureRequirement',
      title: '温度要求',
      width: 110,
    },
    {
      field: 'storageCondition',
      title: '存储条件',
      minWidth: 160,
    },
    {
      field: 'status',
      title: '状态',
      width: 110,
      align: 'center',
      cellRender: {
        attrs: { beforeChange: onStatusChange },
        name: 'CellSwitch',
        props: {
          checkedValue: CommonStatusEnum.ENABLE,
          disabled: !hasAccessByCodes(['wms:item:update']),
          unCheckedValue: CommonStatusEnum.DISABLE,
        },
      },
    },
    {
      field: 'actions',
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** SKU 选择弹窗搜索表单 */
export function useSkuSelectGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'itemName',
      label: '商品名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品名称',
      },
    },
    {
      fieldName: 'itemCode',
      label: '商品编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入商品编号',
      },
    },
    {
      fieldName: 'name',
      label: '规格型号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入规格型号',
      },
    },
    {
      fieldName: 'code',
      label: '规格编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入规格编号',
      },
    },
    {
      fieldName: 'barCode',
      label: '条码',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入条码',
      },
    },
  ];
}

/** SKU 选择弹窗列表字段 */
export function useSkuSelectGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 50 },
    {
      field: 'itemInfo',
      title: '商品信息',
      minWidth: 220,
      slots: { default: 'itemInfo' },
    },
    {
      field: 'skuInfo',
      title: '规格信息',
      minWidth: 220,
      slots: { default: 'skuInfo' },
    },
    {
      field: 'priceInfo',
      title: '金额(元)',
      minWidth: 160,
      slots: { default: 'priceInfo' },
    },
    {
      field: 'weightInfo',
      title: '重量(kg)',
      minWidth: 160,
      slots: { default: 'weightInfo' },
    },
    {
      field: 'dimensionInfo',
      title: '长宽高(cm)',
      minWidth: 180,
      align: 'right',
      slots: { default: 'dimensionInfo' },
    },
  ];
}
