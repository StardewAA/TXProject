<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ReportDatasetApi } from '#/api/report/dataset';

import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteDataset, getDatasetPage } from '#/api/report/dataset';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import SimpleForm from './modules/simple-form.vue';

const router = useRouter();

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [SimpleFormModal, simpleFormModalApi] = useVbenModal({
  connectedComponent: SimpleForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建数据集（简单模式） */
function handleCreateSimple() {
  simpleFormModalApi.setData(null).open();
}

/** 编辑数据集 */
function handleEdit(row: ReportDatasetApi.Dataset) {
  formModalApi.setData(row).open();
}

/** 删除数据集 */
async function handleDelete(row: ReportDatasetApi.Dataset) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteDataset(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 退出配置中心：通过现有路由返回报表中心页面 */
function handleExitCenter() {
  router.push('/mes/report-center');
}

/** 打开预览：跳转到独立预览页面（当前页跳转，浏览器返回可回到列表） */
function handlePreview(row: ReportDatasetApi.Dataset) {
  if (!row.code) {
    return;
  }
  router.push(`/mes/report-dataset/preview/${row.code}`);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getDatasetPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<ReportDatasetApi.Dataset>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <SimpleFormModal @success="handleRefresh" />

    <Grid table-title="报表配置中心">
      <template #toolbar-tools>
        <a-button class="mr-2" @click="handleExitCenter">
          <IconifyIcon class="size-5" icon="lucide:log-out" />
          退出配置中心
        </a-button>
        <TableAction
          :actions="[
            {
              label: '新增报表',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['report:dataset:create'],
              onClick: handleCreateSimple,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '预览',
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['report:dataset:execute'],
              onClick: handlePreview.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['report:dataset:update'],
              disabled: row.tenantId === 0,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['report:dataset:delete'],
              disabled: row.tenantId === 0,
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
