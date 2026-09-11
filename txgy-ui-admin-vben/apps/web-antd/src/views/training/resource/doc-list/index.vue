<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TrainingResourceApi } from '../api';

import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { message, Switch, Tag, TreeSelect } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import {
  buildDocumentPreviewUrl,
  changeDocumentStatus,
  deleteDocument,
  getDocumentPage,
  getResourceCategoryTree,
  reportDocumentReading,
} from '../api';
import {
  formatFileSize,
  RESOURCE_STATUS_OPTIONS,
  statusColor,
  statusText,
} from '../constants';
import DocFormContent from './doc-form.vue';

defineOptions({ name: 'TrainingResourceDocList' });

// 分类与归档筛选（独立于搜索表单）
const categoryOptions = ref<any[]>([]);
const categoryId = ref<number | undefined>();
const includeArchived = ref(false);

function buildCategoryTree(nodes?: TrainingResourceApi.CategoryNode[]): any[] | undefined {
  if (!nodes?.length) {
    return undefined;
  }
  return nodes.map((node) => ({
    title: node.name,
    value: node.id,
    children: buildCategoryTree(node.children),
  }));
}

async function loadCategories() {
  const tree = await getResourceCategoryTree('doc');
  categoryOptions.value = buildCategoryTree(tree) || [];
}

function handleSearch() {
  gridApi.query();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        fieldName: 'keyword',
        label: '标题',
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '请输入文档标题',
        },
      },
      {
        fieldName: 'status',
        label: '状态',
        component: 'Select',
        componentProps: {
          allowClear: true,
          placeholder: '全部状态',
          options: RESOURCE_STATUS_OPTIONS,
        },
      },
    ],
  },
  gridOptions: {
    columns: [
      { field: 'title', title: '标题', minWidth: 200, showOverflow: true },
      { field: 'fileType', title: '类型', width: 80, align: 'center' },
      {
        field: 'fileSize',
        title: '大小',
        width: 110,
        formatter: ({ cellValue }) => formatFileSize(cellValue),
      },
      { field: 'estMinutes', title: '预估(分)', width: 90, align: 'center' },
      { field: 'viewCount', title: '预览数', width: 80, align: 'center' },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'uploaderName', title: '上传人', width: 110 },
      {
        field: 'createTime',
        title: '上传时间',
        width: 170,
        formatter: 'formatDateTime',
      },
      {
        title: '操作',
        width: 240,
        fixed: 'right',
        slots: { default: 'actions' },
      },
    ],
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getDocumentPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            categoryId: categoryId.value,
            includeArchived: includeArchived.value,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<TrainingResourceApi.DocumentResource>,
});

// ========== 上传 / 编辑弹窗 ==========
const [DocFormModal, docFormModal] = useVbenModal({
  connectedComponent: DocFormContent,
  destroyOnClose: true,
});

function handleUpload() {
  docFormModal.setData(null).open();
}

function handleEdit(row: TrainingResourceApi.DocumentResource) {
  docFormModal.setData(row).open();
}

// ========== 预览（预览即计数） ==========
function handlePreview(row: TrainingResourceApi.DocumentResource) {
  window.open(buildDocumentPreviewUrl(row.id!), '_blank');
  reportDocumentReading(row.id!).catch(() => {});
}

// ========== 状态流转 ==========
async function handleStatus(
  row: TrainingResourceApi.DocumentResource,
  action: string,
  tip: string,
) {
  await changeDocumentStatus(row.id!, action);
  message.success(tip);
  handleSearch();
}

async function handleDelete(row: TrainingResourceApi.DocumentResource) {
  await deleteDocument(row.id!);
  message.success($t('ui.actionMessage.deleteSuccess'));
  handleSearch();
}

function buildActions(row: TrainingResourceApi.DocumentResource) {
  const actions: any[] = [
    {
      label: '预览',
      type: 'link',
      icon: ACTION_ICON.VIEW,
      onClick: () => handlePreview(row),
    },
    {
      label: '编辑',
      type: 'link',
      icon: ACTION_ICON.EDIT,
      onClick: () => handleEdit(row),
    },
  ];
  const status = row.status;
  if (status !== 4) {
    if (status === 1 || status === 3) {
      actions.push({
        label: '上线',
        type: 'link',
        popConfirm: {
          title: '确认上线该文档？',
          confirm: () => handleStatus(row, 'ONLINE', '文档已上线'),
        },
      });
    }
    if (status === 1 || status === 2) {
      actions.push({
        label: '下架',
        type: 'link',
        danger: true,
        popConfirm: {
          title: '确认下架该文档？',
          confirm: () => handleStatus(row, 'OFFLINE', '文档已下架'),
        },
      });
    }
    actions.push({
      label: '归档',
      type: 'link',
      popConfirm: {
        title: '归档后不在默认列表展示，确认归档？',
        confirm: () => handleStatus(row, 'ARCHIVE', '文档已归档'),
      },
    });
  }
  actions.push({
    label: '删除',
    type: 'link',
    danger: true,
    icon: ACTION_ICON.DELETE,
    popConfirm: {
      title: '被课程引用时无法删除，确认删除？',
      confirm: () => handleDelete(row),
    },
  });
  return actions;
}

loadCategories();
</script>

<template>
  <Page auto-content-height>
    <DocFormModal @success="handleSearch" />

    <!-- 分类 / 归档 筛选 -->
    <div class="mb-3 flex flex-wrap items-center gap-3">
      <TreeSelect
        v-model:value="categoryId"
        class="!w-56"
        :allow-clear="true"
        tree-default-expand-all
        placeholder="全部分类"
        :tree-data="categoryOptions"
        @change="handleSearch"
      />
      <span class="text-sm text-gray-500">包含归档</span>
      <Switch v-model:checked="includeArchived" @change="handleSearch" />
    </div>

    <Grid table-title="文档资源库（PDF/图片 ≤50MB，上传即上线）">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['文档']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              onClick: handleUpload,
            },
          ]"
        />
      </template>

      <template #fileType="{ row }">
        <Tag color="blue">{{ (row.fileType || '').toUpperCase() }}</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="statusColor(row.status) || 'default'">
          {{ statusText(row.status) }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction :actions="buildActions(row)" />
      </template>
    </Grid>
  </Page>
</template>
