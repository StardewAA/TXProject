<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ReportDatasetApi } from '#/api/report/dataset';

import { computed, reactive, ref } from 'vue';

import { DocAlert, Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteDataset,
  executeDataset,
  getDatasetPage,
} from '#/api/report/dataset';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import SimpleForm from './modules/simple-form.vue';

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

/** 创建数据集（SQL 模式） */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 创建数据集（简单模式，无需写 SQL） */
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

// ========== 预览执行 ==========

const previewOpen = ref(false);
const previewDataset = ref<ReportDatasetApi.Dataset>();
const previewLoading = ref(false);
const previewResult = ref<ReportDatasetApi.ExecuteResult>();
const previewError = ref('');
const previewValues = reactive<Record<string, any>>({});

const previewParams = computed(() => previewDataset.value?.params ?? []);

const previewColumns = computed(() =>
  (previewResult.value?.dimensions ?? []).map((name) => ({
    title: name,
    dataIndex: name,
    key: name,
    ellipsis: true,
    minWidth: 120,
  })),
);

/** 打开预览抽屉 */
function handlePreview(row: ReportDatasetApi.Dataset) {
  previewDataset.value = row;
  previewResult.value = undefined;
  previewError.value = '';
  Object.keys(previewValues).forEach((key) => delete previewValues[key]);
  (row.params ?? []).forEach((param) => {
    previewValues[param.name] = param.defaultValue ?? '';
  });
  previewOpen.value = true;
}

/** 执行预览查询 */
async function handlePreviewRun() {
  if (!previewDataset.value?.code) {
    return;
  }
  previewLoading.value = true;
  previewError.value = '';
  try {
    // 空值不入参，交由服务端使用默认值
    const params: Record<string, any> = {};
    Object.entries(previewValues).forEach(([key, value]) => {
      if (value !== '' && value !== null && value !== undefined) {
        params[key] = value;
      }
    });
    previewResult.value = await executeDataset(
      previewDataset.value.code,
      params,
    );
  } catch (error: any) {
    previewResult.value = undefined;
    previewError.value = error?.message ?? '执行失败';
  } finally {
    previewLoading.value = false;
  }
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
    <template #doc>
      <DocAlert
        title="报表数据集"
        url="https://doc.iocoder.cn/report/"
      />
    </template>

    <FormModal @success="handleRefresh" />
    <SimpleFormModal @success="handleRefresh" />

    <a-drawer
      v-model:open="previewOpen"
      :title="`数据集预览：${previewDataset?.name ?? ''}`"
      destroy-on-close
      width="960"
    >
      <div v-if="previewParams.length > 0" class="mb-3">
        <div
          v-for="param in previewParams"
          :key="param.name"
          class="mb-2 flex items-center gap-2"
        >
          <span class="w-40 text-right text-sm">
            {{ param.label || param.name }}
          </span>
          <a-input
            v-model:value="previewValues[param.name]"
            :placeholder="param.defaultValue || '请输入'"
            allow-clear
            style="max-width: 320px"
          />
        </div>
      </div>

      <div class="mb-3 flex items-center gap-3">
        <a-button
          type="primary"
          :loading="previewLoading"
          @click="handlePreviewRun"
        >
          执行查询
        </a-button>
        <span v-if="previewResult" class="text-xs text-gray-500">
          返回 {{ previewResult.rowCount }} 行，耗时 {{ previewResult.durationMs }} ms
        </span>
      </div>

      <a-alert
        v-if="previewError"
        :message="previewError"
        class="mb-3"
        show-icon
        type="error"
      />

      <a-table
        v-if="previewResult"
        :columns="previewColumns"
        :data-source="previewResult.source"
        :pagination="{ pageSize: 20 }"
        :row-key="(_record: any, index: number) => String(index)"
        :scroll="{ x: 'max-content' }"
        size="small"
      />
    </a-drawer>

    <Grid table-title="报表数据集列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增报表',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['report:dataset:create'],
              onClick: handleCreateSimple,
            },
            {
              label: '高级模式',
              auth: ['report:dataset:create'],
              onClick: handleCreate,
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
