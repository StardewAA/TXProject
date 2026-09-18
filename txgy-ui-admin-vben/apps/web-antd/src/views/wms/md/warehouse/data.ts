import type { VbenFormApi, VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { h } from 'vue';

import { generateWmsCode } from '@vben/constants';

import { Button } from 'ant-design-vue';

import { z } from '#/adapter/form';
import { getSimpleRoleList } from '#/api/system/role';
import { getSimpleUserList, getUserPage } from '#/api/system/user';

const WAREHOUSE_MANAGER_ROLE_NAME = '仓库管理员';

const toUserOption = (user: {
  id?: number;
  nickname?: string;
  username?: string;
}) => ({
  ...user,
  label:
    user.id != null
      ? `${user.id} · ${user.nickname || user.username || '未命名用户'}`
      : user.nickname || user.username || '未命名用户',
  value: user.id,
});

const getWarehouseManagerUsers = async () => {
  const role = (await getSimpleRoleList()).find(
    (item) => item.status === 0 && item.name === WAREHOUSE_MANAGER_ROLE_NAME,
  );
  if (!role?.id) {
    return [];
  }
  const page = await getUserPage({ pageNo: 1, pageSize: 100, roleId: role.id });
  return page.list.filter((user) => user.status === 0).map(toUserOption);
};

const getWarehouseLogisticsUsers = async () => {
  const users = await getSimpleUserList();
  return users
    // /system/user/simple-list 已经只返回启用用户；UserSimpleRespVO 不含 status。
    .map(toUserOption);
};

const warehouseTypes = [
  { label: '原材料仓', value: 'RAW_MATERIAL' },
  { label: '成品仓', value: 'FINISHED_GOODS' },
];

/** 新增/修改仓库的表单 */
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
      label: '仓库名称',
      component: 'Input',
      componentProps: {
        maxLength: 50,
        placeholder: '请输入仓库名称',
      },
      rules: z.string().min(1, '仓库名称不能为空').max(50),
    },
    {
      fieldName: 'code',
      label: '仓库编号',
      component: 'Input',
      componentProps: {
        maxLength: 20,
        placeholder: '请输入仓库编号',
      },
      rules: z.string().min(1, '仓库编号不能为空').max(20),
      suffix: () => {
        return h(
          Button,
          {
            type: 'default',
            onClick: () => {
              formApi?.setFieldValue('code', generateWmsCode('W'));
            },
          },
          { default: () => '生成' },
        );
      },
    },
    {
      fieldName: 'warehouseType',
      label: '仓库类型',
      component: 'Select',
      componentProps: { options: warehouseTypes, placeholder: '请选择仓库类型' },
      rules: z.enum(['RAW_MATERIAL', 'FINISHED_GOODS']),
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
      },
      rules: z.number().default(0),
    },
    {
      fieldName: 'managerUserId',
      label: '仓库负责人',
      component: 'ApiSelect',
      componentProps: {
        api: getWarehouseManagerUsers,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择仓库负责人',
        showSearch: true,
      },
      rules: z.number({ message: '请选择仓库负责人' }),
    },
    {
      fieldName: 'memberUserIds',
      label: '仓库员工',
      component: 'ApiSelect',
      componentProps: {
        api: getWarehouseLogisticsUsers,
        labelField: 'label',
        valueField: 'value',
        mode: 'multiple',
        placeholder: '请选择仓库员工',
        showSearch: true,
        optionFilterProp: 'label',
      },
      rules: z.array(z.number()).default([]),
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
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '仓库名称',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库名称',
      },
    },
    {
      fieldName: 'code',
      label: '仓库编号',
      component: 'Input',
      componentProps: {
        allowClear: true,
        placeholder: '请输入仓库编号',
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '仓库名称',
      minWidth: 160,
    },
    {
      field: 'code',
      title: '仓库编号',
      minWidth: 140,
    },
    {
      field: 'warehouseType',
      title: '仓库类型',
      minWidth: 130,
      formatter: ({ cellValue }) => warehouseTypes.find(type => type.value === cellValue)?.label ?? '未分类',
    },
    {
      field: 'managerName',
      title: '负责人',
      minWidth: 120,
    },
    {
      field: 'memberCount',
      title: '员工数量',
      width: 100,
      align: 'center',
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: 220,
    },
    {
      field: 'sort',
      title: '排序',
      width: 100,
      align: 'center',
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
