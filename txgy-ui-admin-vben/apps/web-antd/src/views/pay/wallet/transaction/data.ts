import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

/** 钱包业务分类映射（与后端 PayWalletBizTypeEnum 保持一致） */
const BIZ_TYPE_MAP: Record<number, string> = {
  1: '充值',
  2: '充值退款',
  3: '支付',
  4: '支付退款',
  5: '更新余额',
  6: '转账',
};

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'walletId',
      label: '钱包编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入钱包编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'userId',
      label: '用户编号',
      component: 'Input',
      componentProps: {
        placeholder: '请输入用户编号',
        allowClear: true,
      },
    },
    {
      fieldName: 'userType',
      label: '用户类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(DICT_TYPE.USER_TYPE, 'number'),
        placeholder: '请选择用户类型',
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      title: '编号',
      field: 'id',
      minWidth: 100,
    },
    {
      title: '钱包编号',
      field: 'walletId',
      minWidth: 100,
    },
    {
      title: '业务分类',
      field: 'bizType',
      minWidth: 100,
      formatter: ({ cellValue }) =>
        BIZ_TYPE_MAP[cellValue as number] ?? `未知(${cellValue})`,
    },
    {
      title: '交易标题',
      field: 'title',
      minWidth: 200,
    },
    {
      title: '交易金额',
      field: 'price',
      minWidth: 120,
      formatter: 'formatAmount2',
    },
    {
      title: '交易后余额',
      field: 'balance',
      minWidth: 120,
      formatter: 'formatAmount2',
    },
    {
      title: '交易时间',
      field: 'createTime',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
  ];
}
