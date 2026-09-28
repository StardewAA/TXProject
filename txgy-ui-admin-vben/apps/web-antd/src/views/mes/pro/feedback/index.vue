<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProFeedbackApi } from '#/api/mes/pro/feedback';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';
import { MesProFeedbackStatusEnum } from '@vben/constants';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteFeedback,
  exportFeedback,
  getFeedbackPage,
} from '#/api/mes/pro/feedback';
import { $t } from '#/locales';
import { router } from '#/router';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建生产报工 */
function handleCreate() {
  formModalApi.setData({ formType: 'create' }).open();
}

/** 编辑生产报工 */
function handleEdit(row: MesProFeedbackApi.Feedback) {
  formModalApi.setData({ formType: 'update', id: row.id }).open();
}

/** 提交生产报工 */
function handleSubmit(row: MesProFeedbackApi.Feedback) {
  formModalApi.setData({ formType: 'submit', id: row.id }).open();
}

/**
 * 跳转 BPM 待办处理报工审批
 *
 * 报工审批已接入 BPM 流程引擎，MES 侧不再提供直连审批入口，
 * 审批人统一在「工作流程 - 待办任务」中处理（见《报工审批 BPM 接入设计方案》6.2）
 */
function handleGoBpmTodo() {
  router.push({ name: 'BpmTodoTask' });
}

/** 详情生产报工 */
function handleDetail(row: MesProFeedbackApi.Feedback) {
  formModalApi.setData({ formType: 'detail', id: row.id }).open();
}

/** 删除生产报工 */
async function handleDelete(row: MesProFeedbackApi.Feedback) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.code]),
    duration: 0,
  });
  try {
    await deleteFeedback(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.code]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 导出表格 */
async function handleExport() {
  const data = await exportFeedback(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '生产报工.xls', source: data });
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
          return await getFeedbackPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { isHover: true, keyField: 'id' },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<MesProFeedbackApi.Feedback>,
});
</script>

<template>
  <Page auto-content-height>
    <template #doc>
      <DocAlert
        title="【生产】生产报工"
        url="https://doc.iocoder.cn/mes/pro/feedback/"
      />
    </template>
    <FormModal @success="handleRefresh" />
    <Grid table-title="生产报工列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['生产报工']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:pro-feedback:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'primary',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['mes:pro-feedback:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #code="{ row }">
        <Button type="link" @click="handleDetail(row)">
          {{ row.code }}
        </Button>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              auth: ['mes:pro-feedback:update'],
              ifShow: () => row.status === MesProFeedbackStatusEnum.PREPARE,
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.submit'),
              type: 'link',
              auth: ['mes:pro-feedback:update'],
              ifShow: () => row.status === MesProFeedbackStatusEnum.PREPARE,
              onClick: handleSubmit.bind(null, row),
            },
            {
              label: '去 BPM 待办',
              type: 'link',
              auth: ['bpm:task:query'],
              ifShow: () => row.status === MesProFeedbackStatusEnum.APPROVING,
              onClick: handleGoBpmTodo,
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['mes:pro-feedback:delete'],
              ifShow: () => row.status === MesProFeedbackStatusEnum.PREPARE,
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.code]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
