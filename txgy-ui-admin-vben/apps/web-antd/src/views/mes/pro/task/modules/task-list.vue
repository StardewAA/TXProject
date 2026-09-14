<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MesProTaskApi } from '#/api/mes/pro/task';

import { computed, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  autoGenerateTasksByRoute,
  deleteTask,
  getTaskPage,
} from '#/api/mes/pro/task';
import { $t } from '#/locales';

import { useTaskGridColumns } from '../data';
import TaskForm from './task-form.vue';

const props = defineProps<{
  colorCode?: string;
  disabled?: boolean;
  itemId?: number;
  processId: number;
  routeId: number;
  workOrderId: number;
}>();

const editable = computed(() => !props.disabled); // 是否可维护任务

const [TaskFormModal, taskFormModalApi] = useVbenModal({
  connectedComponent: TaskForm,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增任务 */
function handleCreate() {
  taskFormModalApi
    .setData({
      colorCode: props.colorCode,
      itemId: props.itemId,
      processId: props.processId,
      routeId: props.routeId,
      workOrderId: props.workOrderId,
    })
    .open();
}

/** 按工艺路线一键生成全部缺失工序的任务（幂等，已有任务的工序自动跳过） */
async function handleAutoGenerate() {
  const hideLoading = message.loading({
    content: '正在按工艺路线生成任务...',
    duration: 0,
  });
  try {
    const created = await autoGenerateTasksByRoute(props.workOrderId);
    if (created > 0) {
      message.success(`已按工艺路线生成 ${created} 个任务`);
    } else {
      message.info('所有工序均已存在任务，无需生成');
    }
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 编辑任务 */
function handleEdit(row: MesProTaskApi.Task) {
  taskFormModalApi.setData({ id: row.id }).open();
}

/** 删除任务 */
async function handleDelete(row: MesProTaskApi.Task) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.code]),
    duration: 0,
  });
  try {
    await deleteTask(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.code]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useTaskGridColumns(editable.value),
    height: 360,
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          if (!props.workOrderId) {
            return { list: [], total: 0 };
          }
          return await getTaskPage({
            pageNo: 1,
            pageSize: 100,
            processId: props.processId,
            routeId: props.routeId,
            workOrderId: props.workOrderId,
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
    },
  } as VxeTableGridOptions<MesProTaskApi.Task>,
});

// 工序步骤切换时重新加载当前工序的任务
watch(
  () => props.processId,
  () => handleRefresh(),
);
</script>

<template>
  <div>
    <TaskFormModal @success="handleRefresh" />
    <Grid table-title="生产任务">
      <template v-if="editable" #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '一键生成全部工序任务',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:pro-task:create'],
              popConfirm: {
                title: '将按工艺路线为所有缺失任务的工序生成任务（计划时长每道 8 小时），确认生成？',
                confirm: handleAutoGenerate,
              },
            },
            {
              label: '新增任务',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['mes:pro-task:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #colorCode="{ row }">
        <div
          class="mx-auto size-5 rounded"
          :style="{ background: row.colorCode || '#00AEF3' }"
        ></div>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['mes:pro-task:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['mes:pro-task:delete'],
              popConfirm: {
                title: $t('ui.actionMessage.deleteConfirm', [row.code]),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </div>
</template>
