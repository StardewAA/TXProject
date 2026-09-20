import type { VbenFormSchema } from '#/adapter/form';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';

import { useAccess } from '@vben/access';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { fenToYuan } from '@vben/utils';

import { z } from '#/adapter/form';
import { getShopList } from '#/api/mall/product/shop';
import { getRangePickerDefaultProps } from '#/utils';

/** 售后接口的退款金额单位为分，列表只在这里转换一次为元。 */
function formatFenAmount(value: unknown): string {
  const fen = typeof value === 'number' ? value : Number(value ?? 0);
  return `${fenToYuan(Number.isFinite(fen) ? fen : 0)} 元`;
}

/** 拒绝售后表单的 schema 配置 */
export function useDisagreeFormSchema(): VbenFormSchema[] {
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
      component: 'Textarea',
      fieldName: 'reason',
      label: '拒绝原因',
      componentProps: {
        placeholder: '请输入拒绝原因',
        rows: 4,
      },
      rules: z.string().min(2, { message: '拒绝原因不能少于 2 个字符' }),
    },
  ];
}
/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  const { hasAccessByCodes } = useAccess();
  const shopOptionsAllowed = hasAccessByCodes(['product:shop:query']);
  return [
    {
      fieldName: 'shopId',
      label: shopOptionsAllowed ? '所属店铺' : '店铺编号',
      component: shopOptionsAllowed ? 'ApiSelect' : 'InputNumber',
      componentProps: shopOptionsAllowed
        ? { api: getShopList, labelField: 'name', valueField: 'id', allowClear: true, showSearch: true, optionFilterProp: 'label', placeholder: '全部授权店铺' }
        : { min: 0, precision: 0, placeholder: '按店铺编号筛选' },
    },
    {
      fieldName: 'spuName',
      label: '商品名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入商品名称',
      },
    },
    {
      fieldName: 'no',
      label: '退款编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入退款编号',
      },
    },
    {
      fieldName: 'orderNo',
      label: '订单编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入订单编号',
      },
    },
    {
      fieldName: 'way',
      label: '售后方式',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.TRADE_AFTER_SALE_WAY, 'number'),
        placeholder: '请选择售后方式',
        allowClear: true,
      },
    },
    {
      fieldName: 'type',
      label: '售后类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.TRADE_AFTER_SALE_TYPE, 'number'),
        placeholder: '请选择售后类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}
/** 表格列配置 */
export function useGridColumns(): VxeGridPropTypes.Columns {
  return [
    {
      field: 'no',
      title: '退款编号',
      fixed: 'left',
      minWidth: 200,
      slots: { default: 'afterSaleNo' },
    },
    {
      field: 'orderNo',
      title: '订单编号',
      fixed: 'left',
      minWidth: 200,
      slots: { default: 'orderNo' },
    },
    {
      field: 'productInfo',
      title: '商品信息',
      minWidth: 600,
      slots: { default: 'productInfo' },
    },
    {
      field: 'shopName',
      title: '责任店铺',
      width: 140,
      formatter: ({ row }) =>
        row.shopName ||
        (row.shopId === 0
          ? '平台自营'
          : row.shopId
            ? `店铺 ${row.shopId}`
            : '平台自营'),
    },
    {
      field: 'refundPrice',
      title: '退款金额',
      width: 120,
      // 后端金额统一使用“分”，列表展示只换算一次为“元”
      formatter: ({ cellValue }) => formatFenAmount(cellValue),
    },
    {
      field: 'user.nickname',
      title: '买家',
      minWidth: 120,
    },
    {
      field: 'createTime',
      title: '申请时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      field: 'status',
      title: '售后状态',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: {
          type: DICT_TYPE.TRADE_AFTER_SALE_STATUS,
        },
      },
    },
    {
      field: 'way',
      title: '售后方式',
      width: 100,
      cellRender: {
        name: 'CellDict',
        props: {
          type: DICT_TYPE.TRADE_AFTER_SALE_WAY,
        },
      },
    },
    {
      field: 'processBlockReason',
      title: '处理进度',
      minWidth: 180,
      slots: { default: 'processing' },
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      align: 'center',
      slots: { default: 'actions' },
    },
  ];
}
