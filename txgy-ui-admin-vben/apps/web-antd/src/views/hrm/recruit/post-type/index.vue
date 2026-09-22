<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmRecruitPostTypeApi } from '#/api/hrm/recruit/post/type';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { handleTree } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteRecruitPostType,
  getRecruitPostTypeList,
  updateRecruitPostTypeStatus,
} from '#/api/hrm/recruit/post/type';
import { $t } from '#/locales';

import { useGridColumns } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmRecruitPostType' });

const [FormModal, formModalApi] = useVbenModal({ connectedComponent: Form, destroyOnClose: true });
function handleRefresh() { gridApi.query(); }
function handleCreate() { formModalApi.setData(null).open(); }
function handleEdit(row: HrmRecruitPostTypeApi.RecruitPostType) { formModalApi.setData(row).open(); }
async function handleDelete(row: HrmRecruitPostTypeApi.RecruitPostType) {
  await confirm(`确认删除职位类型“${row.name}”吗？`);
  await deleteRecruitPostType(row.id!);
  message.success($t('ui.actionMessage.operationSuccess'));
  handleRefresh();
}
async function handleStatusChange(status: number, row: HrmRecruitPostTypeApi.RecruitPostType) {
  try { await confirm(`确认要${getDictLabel(DICT_TYPE.COMMON_STATUS, status)}职位类型“${row.name}”吗？`); }
  catch { return false; }
  await updateRecruitPostTypeStatus({ id: row.id!, status });
  message.success($t('ui.actionMessage.operationSuccess'));
  return true;
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(handleStatusChange),
    height: 'auto',
    keepSource: true,
    // 该接口返回的是完整数组（非分页结构），必须关掉分页器；
    // 否则 vxe 的 proxy 拿不到 list/total，会判定为 0 条并渲染空表格。
    pagerConfig: {
      enabled: false,
    },
    treeConfig: {
      expandAll: true,
      parentField: 'parentId',
      reserve: true,
      rowField: 'id',
      transform: true,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          // 与「组织管理」页一致：先把扁平列表构造成树再交给表格。
          return handleTree(await getRecruitPostTypeList());
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmRecruitPostTypeApi.RecruitPostType>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-tools>
        <TableAction :actions="[{ label: '新增', type: 'primary', icon: ACTION_ICON.ADD, auth: ['hrm:recruit:post-type:create'], onClick: handleCreate }]" />
      </template>
      <template #actions="{ row }">
        <TableAction :actions="[
          { label: $t('common.edit'), type: 'link', icon: ACTION_ICON.EDIT, auth: ['hrm:recruit:post-type:update'], onClick: handleEdit.bind(null, row) },
          { label: $t('common.delete'), type: 'link', danger: true, icon: ACTION_ICON.DELETE, auth: ['hrm:recruit:post-type:delete'], onClick: handleDelete.bind(null, row) },
        ]" />
      </template>
    </Grid>
  </Page>
</template>
