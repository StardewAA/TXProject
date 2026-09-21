<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmDeptHeadcountApi } from '#/api/hrm/employee/headcount';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteDeptHeadcount,
  getDeptHeadcountPage,
} from '#/api/hrm/employee/headcount';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmDeptHeadcount' });

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

function handleRefresh() {
  gridApi.query();
}

function handleCreate() {
  formModalApi.setData(null).open();
}

function handleEdit(row: HrmDeptHeadcountApi.DeptHeadcount) {
  formModalApi.setData(row).open();
}

async function handleDelete(row: HrmDeptHeadcountApi.DeptHeadcount) {
  await message.warning('删除后该部门将不再显示编制对比，确认删除吗？');
  await deleteDeptHeadcount(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useGridFormSchema() },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getDeptHeadcountPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmDeptHeadcountApi.DeptHeadcount>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '配置部门编制',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:employee:headcount:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #gap="{ row }">
        <span v-if="row.overStaffed">
          <Tag color="error">超编 {{ Math.abs(row.gapCount ?? 0) }} 人</Tag>
        </span>
        <span v-else-if="(row.gapCount ?? 0) > 0">
          <Tag color="warning">缺 {{ row.gapCount }} 人</Tag>
        </span>
        <span v-else>
          <Tag color="success">已满编</Tag>
        </span>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:employee:headcount:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['hrm:employee:headcount:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.deptName]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
