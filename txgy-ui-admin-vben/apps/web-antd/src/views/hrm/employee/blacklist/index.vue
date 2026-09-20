<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { HrmEmployeeBlacklistApi } from '#/api/hrm/employee/blacklist';

import { confirm, Page, useVbenModal } from '@vben/common-ui';

import { message, Tag } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteEmployeeBlacklist,
  getEmployeeBlacklistPage,
  releaseEmployeeBlacklist,
} from '#/api/hrm/employee/blacklist';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

defineOptions({ name: 'HrmEmployeeBlacklist' });

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

function handleEdit(row: HrmEmployeeBlacklistApi.Blacklist) {
  formModalApi.setData(row).open();
}

async function handleRelease(row: HrmEmployeeBlacklistApi.Blacklist) {
  await confirm(`确认解除"${row.name}"的黑名单吗？解除后可以重新录用。`);
  await releaseEmployeeBlacklist(row.id!);
  message.success('已解除');
  handleRefresh();
}

async function handleDelete(row: HrmEmployeeBlacklistApi.Blacklist) {
  await confirm(`确认删除"${row.name}"的黑名单记录吗？`);
  await deleteEmployeeBlacklist(row.id!);
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
          return await getEmployeeBlacklistPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<HrmEmployeeBlacklistApi.Blacklist>,
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
              label: '加入黑名单',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['hrm:employee:blacklist:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'error' : 'default'">
          {{ row.statusName ?? '-' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['hrm:employee:blacklist:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '解除',
              type: 'link',
              auth: ['hrm:employee:blacklist:update'],
              disabled: row.status !== 1,
              onClick: handleRelease.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['hrm:employee:blacklist:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.name]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
